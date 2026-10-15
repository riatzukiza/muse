(ns eta-mu.extern.review-batch-native-test
  "Retained authentic diagnostic DATA plus explicitly edited negative/synthetic
   controls. Never launches a host, model or publisher; no review credit."
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [eta-mu.extern.review-batch :as wire]
            [eta-mu.extern.review-invocation :as extern]
            ["node:fs" :as fs]))

(def root ".ημ/review-evidence/bounded-review-batching-20261015/joint-native/")
(def ^:dynamic *fixture-root* root)
(defn read-bytes [file] (.readFileSync fs (str *fixture-root* file)))
(defn read-json [file] (extern/strict-json (.toString (read-bytes file) "utf8")))
(defn data []
  (let [proof (read-json "proof.json") file (get-in proof [:verdict :acceptedInvocation :submissionFile])
        context (extern/prepare-review-invocation-context
                  (read-bytes "input.diff") (read-bytes "input-manifest.json")
                  (clj->js (get-in proof [:source :tools])) file nil
                  (str (subs file 0 (- (count file) (count "submission.json"))) "pr-context.md"))]
    (aset context "batchHost" (clj->js (:expectedHost proof)))
    {:proof proof :context context :submission (read-json "submission.json")
     :events (mapv extern/strict-json (str/split-lines (.toString (read-bytes "native.jsonl") "utf8")))}))
(defn raw-events [events] (str (str/join "\n" (map wire/encode events)) "\n"))
(defn verdict [events submission context]
  (js->clj (extern/verify-review-invocation (raw-events events) (wire/encode submission) context)
           :keywordize-keys true))
(defn change-output [events index f]
  (update-in events [index :part :state :output] #(wire/encode (f (extern/strict-json %)))))

(deftest retained-actual-native-batch-and-supervisor-custody
  (let [{:keys [proof context]} (data)
        before (mapv #(wire/sha256 (read-bytes %)) ["native.jsonl" "submission.json" "input.diff"])
        result (js->clj (extern/verify-review-invocation (read-bytes "native.jsonl")
                                                         (read-bytes "submission.json") context)
                        :keywordize-keys true)]
    (is (:ok result))
    (is (= 2 (:pageCount result)))
    (is (= 9 (:reviewCallCount result)))
    (is (= 4 (count (:pageWitnesses result))))
    (is (= [1 2] (get-in result [:pageWitnesses 0 :members])))
    (is (= ["owned-native-batch-1" "owned-native-batch-2"]
           (vec (distinct (map :call-id (:pageWitnesses result))))))
    (is (= (get-in proof [:stdout :sha256]) (get-in result [:acceptedInvocation :responseSha256])))
    (is (= before (mapv #(wire/sha256 (read-bytes %)) ["native.jsonl" "submission.json" "input.diff"])))
    (is (true? (get-in proof [:capture :canonicalBeforeAfterEqualityActuallyAsserted])))))

(deftest retained-current-native-wire-and-source-binding
  (binding [*fixture-root* (str root "review-fixes/")]
    (let [{:keys [proof context]} (data)
          result (js->clj (extern/verify-review-invocation (read-bytes "native.jsonl")
                                                           (read-bytes "submission.json") context)
                          :keywordize-keys true)]
      (is (:ok result))
      (is (= 4 (count (:pageWitnesses result))))
      (is (true? (:canonicalVerifierStableAcrossCaptures proof)))
      (doseq [[file digest] (:sourceFiles proof)]
        (let [file (if (keyword? file) (subs (str file) 1) file)]
          (is (= digest (wire/sha256 (.readFileSync fs file))) file)))
      (is (= 2 (:pageCount result)))
      (is (= 9 (:reviewCallCount result))))))

(deftest edited-actual-trace-never-manufactures-coverage
  (let [{:keys [proof context events submission]} (data)
        position (fn [tool] (dec (:position (first (filter #(= tool (:tool %)) (:actualCalls proof))))))
        r (position "review_read_diff_batch") a (position "review_assess_diff_batch")
        cases
        {"forged delivered coverage" (change-output events r #(assoc-in % [:coverage :delivered] 0))
         "forged assessment coverage" (change-output events a #(assoc-in % [:coverage :assessed] 0))
         "forged missing coverage" (change-output events r #(assoc-in % [:coverage :missing] [1]))
         "membership" (change-output events r #(assoc-in % [:batch :ids] [2 1]))
         "member order" (change-output events r #(update % :chunks (comp vec reverse)))
         "same-size altered content" (change-output events r #(update-in % [:chunks 0 :text] (fn [s] (str "X" (subs s 1)))))
         "forged digest" (change-output events r #(assoc-in % [:chunks 0 :sha256] (apply str (repeat 64 "0"))))
         "duplicate call" (assoc-in events [a :part :callID] (get-in events [r :part :callID]))
         "duplicate part" (assoc-in events [a :part :id] (get-in events [r :part :id]))
         "missing assessment" (update-in events [a :part :state :input :assessments] pop)
         "blank note" (assoc-in events [a :part :state :input :assessments 1 :note] " ")
         "foreign session" (-> events (assoc-in [a :sessionID] "foreign") (assoc-in [a :part :sessionID] "foreign"))
         "clipped flag" (assoc-in events [r :part :state :metadata :truncated] true)
         "saved clipped output" (assoc-in events [r :part :state :metadata :outputPath] "/synthetic/negative-only")
         "extra output line" (update-in events [r :part :state :output] str "\n")
         "oversized output" (update-in events [r :part :state :output]
                                       #(str % (apply str (repeat (- 48001 (wire/utf8-bytes %)) " "))))}]
    (doseq [[label edited] cases]
      (testing (str label " — edited test data, not a native observation")
        (let [result (verdict edited submission context)]
          (is (false? (:ok result)))
          (is (nil? (:acceptedInvocation result))))))
    (let [note (apply str (repeat 2048 "\u0000"))
          edited (update-in events [a :part :state :input :assessments]
                            #(mapv (fn [m] (assoc m :note note)) %))
          matching (update submission :input-assessments #(mapv (fn [m] (assoc m :note note)) %))
          result (verdict edited matching context)]
      (is (> (wire/utf8-bytes (wire/encode (get-in edited [a :part :state :input]))) 16384))
      (is (= "structured-input-unestablished" (:code result))))))

(deftest explicit-synthetic-mixed-single-and-batch-LAST-controls
  (let [{:keys [proof context events submission]} (data)
        rows (:actualCalls proof)
        r (dec (:position (first (filter #(= "review_read_diff_batch" (:tool %)) rows))))
        a (dec (:position (first (filter #(= "review_assess_diff_batch" (:tool %)) rows))))
        original (extern/strict-json (get-in events [r :part :state :output]))
        page (first (:chunks original))
        ;; This extra event is visibly synthetic UNIT TEST DATA. It is never
        ;; written back into the retained authentic trace or credited as native.
        synthetic (-> (nth events r)
                      (assoc-in [:part :id] "synthetic-mixed-part")
                      (assoc-in [:part :callID] "synthetic-mixed-call")
                      (assoc-in [:part :tool] "review_read_diff_chunk")
                      (assoc-in [:part :state :input] {:id 1})
                      (assoc-in [:part :state :output]
                                (wire/encode (merge (select-keys original [:context-protocol :context-source :context-observation])
                                                    {:ok? true :chunk page}))))
        insert (fn [index event]
                 (let [t (:timestamp (nth events index))]
                   (vec (concat (subvec events 0 index) [(assoc event :timestamp t)] (subvec events index)))))
        healthy (verdict (insert a synthetic) submission context)
        late (verdict (insert (inc a) synthetic) submission context)]
    (is (:ok healthy))
    (is (= 5 (count (:pageWitnesses healthy))))
    (is (false? (:ok late)))
    (is (= "last-read-order" (:code late)))
    (is (nil? (:acceptedInvocation late)))))


(deftest retained-prior-source-native-transcript-stays-authentic-data
  (binding [*fixture-root* (str root "current/")]
    (let [{:keys [proof context]} (data)
          result (js->clj (extern/verify-review-invocation (read-bytes "native.jsonl")
                                                           (read-bytes "submission.json") context)
                          :keywordize-keys true)]
      (is (:ok result))
      (is (= (get-in proof [:stdout :sha256]) (wire/sha256 (read-bytes "native.jsonl"))))
      (is (= 4 (count (:pageWitnesses result)))))))

(ns eta-mu.law.review-context-delivery-test
  "Synthetic controls using the inspected pinned HOST read format; no review credit."
  (:require [cljs.test :refer [deftest is testing]]
            [clojure.string :as str]
            [eta-mu.law.review-context :as context-law]
            [eta-mu.law.review-invocation :as invocation]
            [eta-mu.law.review-invocation-test :as fixture]))

(def context-path "/fixture/pr-context.txt")
(def source {:path context-path :bytes 11 :sha256 (apply str (repeat 64 "c"))})
(def source-lines ["alpha" "beta"])

(defn declared [binding]
  {:context-protocol context-law/protocol :context-source binding
   :context-observation (if binding {:status :present :source binding}
                            {:status :absent :source nil})})

(defn renumber [events]
  (mapv (fn [index event]
          (cond-> (assoc event :timestamp (inc index))
            (= "tool_use" (:type event))
            (assoc-in [:part :state :time] {:start (inc index) :end (inc index)})))
        (range) events))

(defn host-read
  "Fixture rendering is literal pinned framing, independent of the candidate parser."
  [id offset lines total footer loaded suffix]
  (let [numbered (str/join "\n" (map-indexed #(str (+ offset %1) ": " %2) lines))
        truncated? (not (str/starts-with? footer "(End of file"))
        event (fixture/tool-event (+ 100 id) "read"
                                  {:filePath context-path :offset offset :limit (max 1 (count lines))}
                                  (str "<path>" context-path "</path>\n<type>file</type>\n<content>\n"
                                       numbered "\n\n" footer "\n</content>" suffix))]
    (assoc-in event [:part :state :metadata]
              {:preview (str/join "\n" (take 20 lines)) :truncated truncated? :loaded loaded
               :display {:type "file" :path context-path :text (str/join "\n" lines)
                         :lineStart offset :lineEnd (+ offset (count lines) -1)
                         :totalLines total :truncated truncated?}})))

(def complete-read
  (host-read 1 1 source-lines 2 "(End of file - total 2 lines)" [] ""))
(def partial-read
  (host-read 1 1 ["alpha"] 2 "(Showing lines 1-1 of 2. Use offset=2 to continue.)" [] ""))
(def tail-read
  (host-read 2 2 ["beta"] 2 "(End of file - total 2 lines)" [] ""))

(defn case-data
  ([reads] (case-data source source-lines [5 4] reads))
  ([binding lines line-bytes reads]
   (let [events (mapv (fn [event]
                        (if (and (= "tool_use" (:type event))
                                 (str/starts-with? (get-in event [:part :tool]) "review_"))
                          (update-in event [:part :state :output] merge (declared binding)) event))
                      (fixture/trace))]
     {:context (assoc fixture/context :context-source binding :context-path context-path
                      :context-lines lines :context-line-bytes line-bytes)
      :submission (merge fixture/submission (select-keys (declared binding)
                                                        [:context-protocol :context-source]))
      :events (renumber (vec (concat [(first events)] reads (rest events))))})))

(defn verdict [{:keys [events submission context]}]
  (invocation/verify events submission context))

(defn refused [case]
  (let [result (verdict case)]
    (is (false? (:ok? result)))
    (is (= :unestablished-review-trace (:reason-kind result)))
    (is (nil? (:accepted-invocation result)))))

(deftest named-delivery-shape-is-explicit
  (let [valid {:source source :path context-path :lines source-lines :line-bytes [5 4]}]
    (is (context-law/delivery-input-valid? valid))
    (is (context-law/delivery-input-valid? (assoc valid :source nil :lines [] :line-bytes [])))
    (doseq [bad [(dissoc valid :source) (dissoc valid :path)
                 (assoc valid :lines [false]) (assoc valid :line-bytes [-1])
                 (assoc valid :source {:path context-path})]]
      (is (not (context-law/delivery-input-valid? bad))))))

(deftest actual-complete-reads-supply-inspectable-coverage
  (doseq [reads [[complete-read] [partial-read tail-read]
                [partial-read complete-read]
                [(host-read 1 1 source-lines 2 "(End of file - total 2 lines)"
                            ["/fixture/AGENTS.md"]
                            "\n\n<system-reminder>\nAncillary untrusted instruction text.\n</system-reminder>")]]]
    (let [result (verdict (case-data reads))]
      (is (:ok? result))
      (is (= 2 (get-in result [:context-read-coverage :total-lines])))
      (is (= 2 (get-in result [:context-read-coverage :delivered-lines])))
      (is (true? (get-in result [:context-read-coverage :eof?])))))
  (let [empty-source {:path context-path :bytes 0
                      :sha256 "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"}
        result (verdict (case-data empty-source [] []
                                  [(host-read 1 1 [] 0 "(End of file - total 0 lines)" [] "")]))]
    (is (:ok? result))
    (is (= 0 (get-in result [:context-read-coverage :delivered-lines])))
    (is (true? (get-in result [:context-read-coverage :eof?]))))
  (let [result (verdict (case-data nil [] [] []))]
    (is (:ok? result))
    (is (= :absent (get-in result [:context-read-coverage :status])))))

(deftest copied-custody-without-complete-host-delivery-refuses
  (doseq [[label reads] [["no read" []] ["tail missing" [partial-read]]
                         ["head missing" [tail-read]]]]
    (testing label (refused (case-data reads))))
  (let [case (case-data [])
        text {:type "text" :timestamp 2 :session-id "session-fixture"
              :part {:type "text" :id "context-prose" :session-id "session-fixture"
                     :text (get-in complete-read [:part :state :output])}}]
    (refused (update case :events #(renumber (vec (concat [(first %)] [text] (rest %)))))))
  (let [case (case-data [])
        nested (assoc-in complete-read [:part :tool] "bash")]
    (refused (update case :events #(renumber (vec (concat [(first %)] [nested] (rest %)))))))
  (refused (case-data {:path context-path :bytes 0
                      :sha256 "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"}
                     [] [] [])))

(deftest current-protocol-and-every-observation-are-required
  (let [case (case-data [complete-read])]
    (doseq [[label changed] [["legacy begin" (update-in case [:events 0 :part :state :output] dissoc :context-protocol)]
                             ["omitted begin source" (update-in case [:events 0 :part :state :output] dissoc :context-source)]
                             ["unknown protocol" (assoc-in case [:events 0 :part :state :output :context-protocol] "unknown/v2")]
                             ["changed source" (assoc-in case [:events 0 :part :state :output :context-source :sha256] (apply str (repeat 64 "d")))]
                             ["stage omits custody" (update-in case [:events 4 :part :state :output] dissoc :context-observation)]
                             ["stage loses source" (update-in case [:events 4 :part :state :output] dissoc :context-source)]
                             ["stage changes binding" (assoc-in case [:events 4 :part :state :output :context-source] nil)]
                             ["unavailable stage" (assoc-in case [:events 4 :part :state :output :context-observation] {:status :unavailable :error-code "EACCES"})]
                             ["serialized source omitted" (update case :submission dissoc :context-source)]
                             ["serialized source changed" (assoc-in case [:submission :context-source :bytes] 12)]
                             ["preparation omitted" (update case :context dissoc :context-source)]]]
      (testing label (refused changed)))))

(deftest wrong-clipped-or-unknown-read-framing-refuses
  (doseq [[label event] [["wrong content" (update-in complete-read [:part :state :output] str/replace "2: beta" "2: wrong")]
                         ["wrong input path" (assoc-in complete-read [:part :state :input :filePath] "/other/context.txt")]
                         ["wrong output path" (update-in complete-read [:part :state :output] str/replace "<path>/fixture" "<path>/other")]
                         ["wrong type" (update-in complete-read [:part :state :output] str/replace "<type>file" "<type>directory")]
                         ["wrong metadata content" (assoc-in complete-read [:part :state :metadata :display :text] "claimed")]
                         ["wrong offset" (assoc-in complete-read [:part :state :input :offset] 2)]
                         ["zero limit cannot deliver two lines" (assoc-in complete-read [:part :state :input :limit] 0)]
                         ["unknown suffix" (update-in complete-read [:part :state :output] str "\nunknown trailing HOST framing")]
                         ["suffix with no loaded metadata" (update-in complete-read [:part :state :output] str "\n\n<system-reminder>\nclaim\n</system-reminder>")]
                         ["loaded metadata with no suffix" (assoc-in complete-read [:part :state :metadata :loaded] ["/fixture/AGENTS.md"])]
                         ["missing tail disguised by metadata" (assoc-in complete-read [:part :state :output]
                                                                 (get-in partial-read [:part :state :output]))]]]
    (testing label (refused (case-data [event]))))
  (let [long-line (apply str (repeat 2001 "x"))
        clipped (str (subs long-line 0 2000) "... (line truncated to 2000 chars)")]
    (refused (case-data (assoc source :bytes 2002) [long-line] [2001]
                       [(host-read 1 1 [clipped] 1 "(End of file - total 1 lines)" [] "")]))))

(deftest read-position-alone-does-not-prove-pre-stage-completion
  (let [case (case-data [complete-read])]
    (refused (assoc-in case [:events 4 :part :state :time :start] 1))
    (refused (update-in case [:events 1 :part :state] dissoc :time))
    (let [events (:events case) read (nth events 1)
          later (renumber (vec (concat [(first events)] (subvec events 2 9)
                                      [read] (subvec events 9))))]
      (refused (assoc case :events later)))))

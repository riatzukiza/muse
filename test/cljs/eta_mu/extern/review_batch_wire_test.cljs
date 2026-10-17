(ns eta-mu.extern.review-batch-wire-test
  "Owned filesystem/compiled API controls. Host observations are explicit test
   doubles here; only the separate native diagnostic observes a real host."
  (:require [cljs.test :refer [deftest is async]]
            [eta-mu.domain.review :as review]
            [eta-mu.extern.review-batch :as wire]
            [eta-mu.infra.review-pipeline :as infra]
            [eta-mu.law.review-batch :as batch]
            [eta-mu.law.review-batch-test :as fixture]
            [eta-mu.law.review-context :as context]
            [eta-mu.shape.review-batch :as shape]
            ["node:fs" :as fs]
            ["node:os" :as os]
            ["node:path" :as path]))

(defn manifest [text]
  {:schema "open-hax.review-input/v1" :base_sha (apply str (repeat 40 "a"))
   :head_sha (apply str (repeat 40 "b")) :diff_base_sha (apply str (repeat 40 "a"))
   :full_diff {:path "basehead.diff" :bytes (wire/utf8-bytes text) :sha256 (wire/sha256 text)}})
(def fields {:context-protocol context/protocol :context-source nil
             :context-observation {:status :absent :source nil} :restart-required? false})

(defn response-for [text]
  (let [s (-> (review/begin text) (assoc :input-source (manifest text) :native-session "wire-fixture")
              (update :diff-chunks #(mapv (fn [p] (assoc p :sha256 (wire/sha256 (:text p)))) %)))
        r (review/read-diff-batch s {:first-id 1 :count 1} [1])]
    (wire/encode (shape/read-response s r fields))))

(defn exact-response-text []
  (let [n (loop [lo 0 hi 8190]
            (if (= lo hi) lo
              (let [mid (quot (inc (+ lo hi)) 2)
                    size (wire/utf8-bytes (response-for (apply str (repeat mid "\u0000"))))]
                (if (<= size 48000) (recur mid hi) (recur lo (dec mid))))))
        base (apply str (repeat n "\u0000"))]
    (first (filter #(= 48000 (wire/utf8-bytes (response-for %)))
                   (map #(str base (apply str (repeat % "x"))) (range 8))))))

(defn fixture! [text]
  (let [dir (.mkdtempSync fs (.join path (.tmpdir os) "muse-batch-wire-"))
        ev (.join path dir ".opencode/review-evidence")]
    (.mkdirSync fs ev #js {:recursive true :mode 448})
    (.writeFileSync fs (.join path ev "basehead.diff") text)
    (.writeFileSync fs (.join path ev "input-manifest.json") (wire/encode (manifest text)))
    {:directory dir :session/id "wire-fixture" :evidence ev
     :review/observe-host (fn [_] fixture/host)}))
(defn state [ctx] (get @infra/!sessions (:evidence ctx)))
(defn clean! [ctx]
  (swap! infra/!sessions dissoc (:evidence ctx))
  (.rmSync fs (:directory ctx) #js {:recursive true :force true}))
(defn decode [value] (if (string? value) (js->clj (js/JSON.parse value) :keywordize-keys true) value))
(defn assessments [read note]
  {:profile batch/profile :source_sha256 (get-in read [:custody :source_sha256])
   :assessments (mapv #(assoc (select-keys % [:id :start :end :sha256]) :note note) (:chunks read))})
(defn delivered-assessments [read note]
  {:assessments (mapv #(assoc (select-keys % [:id]) :note note) (:chunks read))})

(defn exact-request [request]
  (let [padded (reduce (fn [r i]
                         (let [remaining (- 16384 (wire/utf8-bytes (wire/encode r)))
                               note (get-in r [:assessments i :note])
                               n (min (quot remaining 6) (- 2048 (wire/utf8-bytes note)))]
                           (update-in r [:assessments i :note] str (apply str (repeat n "\u0000")))))
                       request (range (count (:assessments request))))
        remaining (- 16384 (wire/utf8-bytes (wire/encode padded)))]
    (update-in padded [:assessments 3 :note] str (apply str (repeat remaining "x")))))

(defn- ^:async run-controls! [done]
  (try
    (let [exact (exact-response-text)]
      (is (string? exact))
      (doseq [[text accepted?] [[exact true] [(str exact "x") false]]]
        (let [ctx (fixture! text)]
          (try
            (is (:ok? (await (infra/begin! {} ctx))))
            (let [raw (await (infra/read-diff-batch! {:first-id 1 :count 1} ctx)) result (decode raw)]
              (is (= accepted? (:ok? result)))
              (if accepted?
                (do (is (= 48000 (wire/utf8-bytes raw))) (is (= #{1} (:delivered-chunks (state ctx)))))
                (do (is (= #{} (:delivered-chunks (state ctx))))
                    (is (review/restart-required? (state ctx))))))
            (finally (clean! ctx))))))
    (let [ctx (fixture! (str (apply str (repeat 8192 "x")) (apply str (repeat 8192 "\u0000"))))]
      (try
        (await (infra/begin! {} ctx))
        (let [result (decode (await (infra/read-diff-batch! {:first-id 1 :count 2} ctx)))]
          (is (:ok? result)) (is (= [1] (get-in result [:batch :ids])))
          (is (= 2 (get-in result [:batch :next-id])))
          (is (= #{1} (:delivered-chunks (state ctx)))))
        (finally (clean! ctx))))
    (doseq [[make-args assess!] [[assessments infra/assess-diff-batch!]
                                [delivered-assessments infra/assess-delivered-diff-batch!]]
            mode [:regular :exact :one-over :escaped]]
      (let [ctx (fixture! (apply str (repeat (* 4 8192) "x")))]
        (try
          (await (infra/begin! {} ctx))
          (let [read (decode (await (infra/read-diff-batch! {:first-id 1 :count 4} ctx)))
                escaped (make-args read (apply str (repeat 2048 "\u0000")))
                exact (exact-request (make-args read "Page-specific scripted fixture"))
                overflow? (contains? #{:one-over :escaped} mode)]
            (is (= [1 2 3 4] (get-in read [:batch :ids])))
            (is (> (wire/utf8-bytes (wire/encode escaped)) 16384))
            (is (= 16384 (wire/utf8-bytes (wire/encode exact))))
            (let [args (case mode :escaped escaped :exact exact
                            :one-over (update-in exact [:assessments 3 :note] str "x")
                            (make-args read "A separate scripted per-page custody note"))
                  result (decode (await (assess! args ctx)))]
              (is (= (not overflow?) (:ok? result)))
              (if overflow?
                (do (is (= {} (:assessed-chunks (state ctx))))
                    (is (= #{1 2 3 4} (:delivered-chunks (state ctx)))))
                (is (= [] (get-in result [:coverage :missing]))))))
          (finally (clean! ctx)))))
    (doseq [[make-args assess!] [[assessments infra/assess-diff-batch!]
                                [delivered-assessments infra/assess-delivered-diff-batch!]]
            note [(apply str (repeat 512 "😀")) (str (apply str (repeat 512 "😀")) "x")]]
      (let [ctx (fixture! "one page")]
        (try
          (await (infra/begin! {} ctx))
          (let [read (decode (await (infra/read-diff-batch! {:first-id 1 :count 1} ctx)))
                result (decode (await (assess! (make-args read note) ctx)))
                accepted? (= 2048 (wire/utf8-bytes note))]
            (is (= accepted? (:ok? result)))
            (is (= (if accepted? 1 0) (count (:assessed-chunks (state ctx))))))
          (finally (clean! ctx)))))
    (doseq [[session accepted?] [[(apply str (repeat 64 "é")) true]
                               [(str (apply str (repeat 64 "é")) "x") false]
                               [(str (apply str (repeat 21 "\u0000")) "xx") true]
                               [(str (apply str (repeat 21 "\u0000")) "xxx") false]]]
      (let [ctx (assoc (fixture! "one page") :session/id session)]
        (try
          (await (infra/begin! {} ctx))
          (let [result (decode (await (infra/read-diff-batch! {:first-id 1 :count 1} ctx)))]
            (is (= accepted? (:ok? result)))
            (is (= (if accepted? #{1} #{}) (:delivered-chunks (state ctx)))))
          (finally (clean! ctx)))))
    (catch :default error (is false (.-message error)))
    (finally (done))))

(deftest actual-wire-cap-before-credit-and-whole-request-refusal
  (async done (run-controls! done)))

(defn- ^:async change-context-during-host-observation! [file _directory]
  ;; Controlled host-adapter test double; this is not native observation proof.
  (.writeFileSync fs file "Changed context while host observation is awaited.")
  fixture/host)

(defn- ^:async context-await-controls! [done]
  (try
    (doseq [operation [:read :strict :delivered]]
      (let [ctx (fixture! "one immutable page")
            file (.join path (:evidence ctx) "pr-context.md")]
        (try
          (is (:ok? (await (infra/begin! {} ctx))))
          (let [assessment? (not= :read operation)
                args (if assessment?
                       ((if (= :delivered operation) delivered-assessments assessments)
                        (decode (await (infra/read-diff-batch! {:first-id 1 :count 1} ctx)))
                        "Separate page assessment")
                       {:first-id 1 :count 1})
                changed (assoc ctx :review/observe-host
                               (partial change-context-during-host-observation! file))
                result (decode (await ((case operation
                                        :strict infra/assess-diff-batch!
                                        :delivered infra/assess-delivered-diff-batch!
                                        infra/read-diff-batch!) args changed)))]
            ;; Restore before the next call: later guards cannot erase the
            ;; custody violation witnessed at the actual mutation boundary.
            (.unlinkSync fs file)
            (is (false? (:ok? result)))
            (is (review/restart-required? (state ctx)))
            (is (= (if assessment? #{1} #{}) (:delivered-chunks (state ctx))))
            (is (= {} (:assessed-chunks (state ctx))))
            (is (false? (:ok? (decode (await (infra/read-diff-batch! {:first-id 1 :count 1} ctx)))))))
          (finally (clean! ctx)))))
    (catch :default error (is false (.-message error)))
    (finally (done))))

(deftest recheck-context-after-awaited-host-observation-before-batch-credit
  (async done (context-await-controls! done)))

(defn- ^:async begin-context-await-control! [done]
  (let [ctx (fixture! "one immutable page")
        file (.join path (:evidence ctx) "pr-context.md")]
    (try
      (let [changed (assoc ctx :review/observe-host
                           (partial change-context-during-host-observation! file))
            result (await (infra/begin! {} changed))]
        (.unlinkSync fs file)
        (is (false? (:ok? result)))
        (is (review/restart-required? (state ctx)))
        (is (not (:begin-pending? (state ctx))))
        (is (= #{} (:delivered-chunks (state ctx))))
        (is (= {} (:assessed-chunks (state ctx))))
        (is (false? (:ok? (decode (await (infra/read-diff-batch! {:first-id 1 :count 1} ctx)))))))
      (catch :default error (is false (.-message error)))
      (finally (clean! ctx) (done)))))

(deftest recheck-context-after-begin-host-await-before-completing-admission
  (async done (begin-context-await-control! done)))

(deftest host-fingerprint-binds-the-profile-settings-and-selectors
  (let [env #js {:HOME "/owned" :OPENCODE_CONFIG "/owned/config.json"}
        observe #(wire/host-observation batch/host-binary-sha256 % env "/owned/consumer")
        base (observe {})]
    (is (= base (observe {:unrelated-debug-field "not part of output profile"})))
    (is (not= base (observe {:tool_output {:max_bytes 48000}})))
    (is (not= base (observe {:tool_output {:max_bytes 51200}})))
    (is (not= base (wire/host-observation batch/host-binary-sha256 {}
                                         #js {:HOME "/other"} "/owned/consumer")))))

(defn- ^:async delivered-refusal-controls! [done]
  (try
    (doseq [mode [:partial :unknown-root :unknown-member :duplicate :undelivered
                  :nil-note :number-note :host-change :session-change]]
      (let [ctx (fixture! (str (apply str (repeat 8192 "x")) "final ημ page"))]
        (try
          (is (:ok? (await (infra/begin! {} ctx))))
          (let [read (decode (await (infra/read-diff-batch! {:first-id 1 :count 1} ctx)))
                prior (decode (await (infra/assess-delivered-diff-batch!
                                      (delivered-assessments read "First page retained") ctx)))
                prefix (:assessed-chunks (state ctx))
                _ (when-not (= :undelivered mode)
                    (await (infra/read-diff-batch! {:first-id 2 :count 1} ctx)))
                good {:assessments [{:id 2 :note "Final partial page"}]}
                args (case mode
                       :partial {:assessments [{:id 2 :note "Valid member"} {:id 3 :note "Absent member"}]}
                       :unknown-root (assoc good :source_sha256 "not accepted by this grammar")
                       :unknown-member (assoc-in good [:assessments 0 :start] 8192)
                       :duplicate (update good :assessments #(vec (concat % %)))
                       :nil-note (assoc-in good [:assessments 0 :note] nil)
                       :number-note (assoc-in good [:assessments 0 :note] 42)
                       good)
                changed (case mode
                          :host-change (assoc ctx :review/observe-host (constantly (assoc fixture/host :binary-sha256 "changed")))
                          :session-change (assoc ctx :session/id "foreign")
                          ctx)
                stale (.join path (:evidence ctx) "submission.json")
                _ (.writeFileSync fs stale "uncredited stale fixture")
                result (decode (await (infra/assess-delivered-diff-batch! args changed)))]
            (is (:ok? prior))
            (is (false? (:ok? result)) (name mode))
            (is (review/restart-required? (state ctx)))
            (is (= prefix (:assessed-chunks (state ctx))))
            (is (not (.existsSync fs stale)))
            (is (false? (:ok? (decode (await (infra/assess-delivered-diff-batch! good ctx)))))))
          (finally (clean! ctx)))))
    (catch :default error (is false (.-message error)))
    (finally (done))))

(deftest delivered-request-refuses-atomically-and-retains-prefix-and-restart
  (async done (delivered-refusal-controls! done)))

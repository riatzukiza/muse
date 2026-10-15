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
    (doseq [overflow? [false true]]
      (let [ctx (fixture! (apply str (repeat (* 4 8192) "x")))]
        (try
          (await (infra/begin! {} ctx))
          (let [read (decode (await (infra/read-diff-batch! {:first-id 1 :count 4} ctx)))
                escaped (assessments read (apply str (repeat 2048 "\u0000"))) ]
            (is (= [1 2 3 4] (get-in read [:batch :ids])))
            (is (> (wire/utf8-bytes (wire/encode escaped)) 16384))
            (let [args (if overflow? escaped (assessments read "A separate scripted per-page custody note"))
                  result (decode (await (infra/assess-diff-batch! args ctx)))]
              (is (= (not overflow?) (:ok? result)))
              (if overflow?
                (do (is (= {} (:assessed-chunks (state ctx))))
                    (is (= #{1 2 3 4} (:delivered-chunks (state ctx)))))
                (is (= [] (get-in result [:coverage :missing]))))))
          (finally (clean! ctx)))))
    (catch :default error (is false (.-message error)))
    (finally (done))))

(deftest actual-wire-cap-before-credit-and-whole-request-refusal
  (async done (run-controls! done)))

(deftest host-fingerprint-binds-the-profile-settings-and-selectors
  (let [env #js {:HOME "/owned" :OPENCODE_CONFIG "/owned/config.json"}
        observe #(wire/host-observation batch/host-binary-sha256 % env "/owned/consumer")
        base (observe {})]
    (is (= base (observe {:unrelated-debug-field "not part of output profile"})))
    (is (not= base (observe {:tool_output {:max_bytes 48000}})))
    (is (not= base (observe {:tool_output {:max_bytes 51200}})))
    (is (not= base (wire/host-observation batch/host-binary-sha256 {}
                                         #js {:HOME "/other"} "/owned/consumer")))))

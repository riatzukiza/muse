(ns eta-mu.extern.review-batch-projection-test
  "Optional frozen-real-corpus projection. Explicitly unavailable without the
   private source path; never a model/host invocation or semantic assessment."
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            [eta-mu.domain.review :as review]
            [eta-mu.extern.review-batch :as wire]
            [eta-mu.law.review-batch :as law]
            [eta-mu.law.review-context :as context]
            [eta-mu.shape.review-batch :as shape]
            ["node:fs" :as fs]))

(def frozen-sha "c89f700130fb0d977138c13597cf2b650bc7aea1f3620d1b1d0cad3bb75b4248")
(def profile-root ".ημ/review-evidence/bounded-review-batching-20261015/host-wire-profile/")
(defn records [file]
  (mapv #(js->clj (js/JSON.parse %) :keywordize-keys true)
        (str/split-lines (.readFileSync fs (str profile-root file) "utf8"))))
(defn fields-for [source]
  {:context-protocol context/protocol :context-source source
   :context-observation {:status :present :source source} :restart-required? false})

(deftest current-production-wire-retains-frozen1491-page-projection
  (if-let [file (aget (.-env js/process) "ETA_MU_REVIEW_BATCH_FROZEN_DIFF")]
    (let [raw (.readFileSync fs file)
          text (.decode (js/TextDecoder. "utf-8" #js {:fatal true}) raw)
          original (records "corpus-pages.jsonl") expected (records "corpus-batches.jsonl")
          pages (mapv #(assoc % :sha256 (wire/sha256 (:text %))) (review/diff-chunks text))
          source {:path "/profile-fixture/x" :bytes 1 :sha256 (wire/sha256 "x")}
          base-bytes (wire/utf8-bytes (wire/encode (fields-for source)))
          fields (fields-for (update source :path str (apply str (repeat (quot (- 4096 base-bytes) 2) "p"))))
          input {:base_sha "f95a53a4da8ed90588b0be320ba8e86c6a03668d"
                 :head_sha "3ab5ffbb6e34a2ee3607a74bf421785d47d5391d"
                 :diff_base_sha "f95a53a4da8ed90588b0be320ba8e86c6a03668d"
                 :full_diff {:path "basehead.diff" :bytes 11840401 :sha256 frozen-sha}}
          session (assoc (review/begin text) :diff-chunks pages :input-source input
                         :native-session (apply str (repeat 128 "s"))
                         :delivered-chunks (set (map :id pages)))
          measured (loop [first-id 1 groups []]
                     (if (> first-id (count pages)) groups
                       (let [request {:first-id first-id :count 4}
                             candidates (review/batch-candidates session request)
                             results (mapv (fn [members]
                                             (let [r (review/read-diff-batch session request (mapv :id members))
                                                   encoded (wire/encode (shape/read-response session r fields))]
                                               (assoc (wire/measure encoded) :ids (mapv :id members)
                                                      :sha256 (wire/sha256 encoded)))) candidates)
                             selected (last (take-while law/response-fits? results))]
                         (when-not selected (throw (js/Error. "Whole original page exceeds selected profile")))
                         (recur (+ first-id (count (:ids selected))) (conj groups selected)))))
          mismatch (first (keep-indexed #(when-not (= %2 (nth measured %1 nil))
                                          {:group %1 :expected %2 :actual (nth measured %1 nil)}) expected))]
      (is (= 11840401 (.-length raw)))
      (is (= frozen-sha (wire/sha256 raw)))
      (is (= 1491 (count pages)))
      (is (= (mapv #(select-keys % [:id :start :end :text_sha256]) original)
             (mapv #(-> (select-keys % [:id :start :end]) (assoc :text_sha256 (:sha256 %))) pages)))
      (is (= 4096 (wire/utf8-bytes (wire/encode fields))))
      (is (= 384 (count measured)))
      (is (= 47982 (apply max (map :bytes measured))))
      (is (= (mapv :id pages) (vec (mapcat :ids measured))))
      (is (nil? mismatch) (pr-str mismatch))
      (is (= frozen-sha (wire/sha256 (.readFileSync fs file))))
      (println (if mismatch "REFUSE production-wire projection: retained-byte discrepancy"
                   "PASS actual production-wire projection:1491unchanged pages/384groups/768calls, not model throughput")))
    (println "UNAVAILABLE frozen-real-corpus projection: ETA_MU_REVIEW_BATCH_FROZEN_DIFF not supplied; no projection credit")))

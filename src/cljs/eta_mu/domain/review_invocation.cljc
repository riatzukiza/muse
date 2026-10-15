(ns eta-mu.domain.review-invocation
  "Compose context delivery with retained structural/full-diff invocation law.
   GPL-3.0-or-later."
  (:require [eta-mu.domain.review-context-delivery :as delivery]
            [eta-mu.law.review-invocation :as law]
            [eta-mu.shape.review-batch :as batch-shape]))

(def stages law/stages)
(def review-tools law/review-tools)
(def context-valid? law/context-valid?)
(def last-read-violations law/last-read-violations)

(defn verify [events submission context]
  (let [result (law/verify events submission context)
        inspection (delivery/inspect events submission context)]
    (if-let [error (:error inspection)]
      {:ok? false :reason-kind :unestablished-review-trace :code error
       :violations (:violations result)}
      (cond-> result (:ok? result)
        (assoc :context-read-coverage (:coverage inspection)
               :page-witnesses (batch-shape/page-witnesses events))))))

(defn classify-length-ended-review [events submission context custody]
  (let [result (law/classify-length-ended-review events submission context custody)
        inspection (delivery/inspect events submission context)]
    (if-let [error (:error inspection)]
      {:eligible? false :classification :unestablished-length-ended-review
       :code error :violations (:violations result)}
      result)))

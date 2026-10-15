(ns eta-mu.shape.review-batch
  "Wire data composition; original page identities and text are unchanged.
   GPL-3.0-or-later."
  (:require [clojure.walk :as walk]
            [eta-mu.law.review-batch :as law]))

(defn tool-args
  "Expand owned named shapes for the existing non-registry DSL renderer.
   Enforcement remains with the original closed Malli/domain contract."
  [key]
  (letfn [(expand [v]
            (if-not (vector? v) v
              (case (first v)
                :ref (expand (get law/registry (second v)))
                := [:enum (second v)]
                :re :string
                :map (into [:map] (if (map? (second v)) (drop 2 v) (rest v)))
                v)))]
    (loop [schema (get law/registry key)]
      (let [next (walk/postwalk expand schema)]
        (if (= schema next) schema (recur next))))))

(defn source [session]
  (select-keys (:input-source session) [:base_sha :head_sha :diff_base_sha :full_diff]))

(defn custody [session]
  (let [input (source session)]
    {:profile law/profile :session (:native-session session)
     :source_sha256 (get-in input [:full_diff :sha256])
     :head_sha (:head_sha input) :base_sha (:base_sha input)}))

(defn read-response [session result fields]
  (let [members (:chunks result) ids (mapv :id members)]
    (merge fields
           {:ok? true :restart-required? false :chunks members :input-source (source session)
            :coverage (:coverage result)
            :batch {:ids ids :first-id (first ids) :last-id (last ids)
                    :next-id (when (< (last ids) (count (:diff-chunks session))) (inc (last ids)))
                    :profile law/profile}
            :custody (custody session)})))

(defn assessment-response [result fields]
  (merge fields (select-keys result [:ok? :chunk-ids :coverage]) {:restart-required? false}))

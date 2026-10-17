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
    ;; The qualified profile captured observation.source before observation.status.
    ;; Normalize that same data at this wire morphism; do not silently change
    ;; the retained profile's serialized bytes or weaken the byte comparison.
    (merge (array-map :context-protocol (:context-protocol fields)
                      :context-observation (array-map :source (get-in fields [:context-observation :source])
                                                       :status (get-in fields [:context-observation :status]))
                      :context-source (:context-source fields)
                      :restart-required? false)
           {:ok? true :chunks members :input-source (source session)
            :coverage (:coverage result)
            :batch {:ids ids :first-id (first ids) :last-id (last ids)
                    :next-id (when (< (last ids) (count (:diff-chunks session))) (inc (last ids)))
                    :profile law/profile}
            :custody (custody session)})))

(defn assessment-response [result fields]
  (merge fields (select-keys result [:ok? :chunk-ids :coverage]) {:restart-required? false}))


(defn page-witnesses
  "Project relations from real shaped HOST events; never expand synthetic events.
   Invoke only after the owning law/domain accepted the complete trace."
  [events]
  (vec (mapcat
         (fn [[position c]]
           (let [name (get-in c [:part :tool])
                 kind (case name
                        ("review_read_diff_chunk" "review_read_diff_batch") :read
                        ("review_assess_diff_chunk" "review_assess_diff_batch"
                         "review_assess_delivered_diff_batch") :assessment
                        nil)
                 args (get-in c [:part :state :input])
                 result (get-in c [:part :state :output])]
             (when (and (= "tool_use" (:type c)) kind (true? (:ok? result)))
               (let [members (case name
                               "review_read_diff_batch" (get-in result [:batch :ids])
                               ("review_assess_diff_batch" "review_assess_delivered_diff_batch")
                               (mapv :id (:assessments args))
                               [(:id args)])]
                 (mapv (fn [id] {:id id :kind kind
                                 :call-id (get-in c [:part :call-id]) :part-id (get-in c [:part :id])
                                 :position position :tool name :members members}) members)))))
         (map-indexed (fn [i event] [(inc i) event]) events))))

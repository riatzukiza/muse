(ns eta-mu.law.review-delivered-batch-test
  "Synthetic native-shaped controls; no model invocation or review credit.
   GPL-3.0-or-later."
  (:require [cljs.test :refer [deftest is]]
            [eta-mu.domain.review :as review]
            [eta-mu.domain.review-invocation :as invocation]
            [eta-mu.extern.review-batch :as wire]
            [eta-mu.law.review-batch :as batch]
            [eta-mu.law.review-batch-test :as strict]
            [eta-mu.law.review-invocation-test :as fixture]))

(def tool "review_assess_delivered_diff_batch")
(def text (str (apply str (repeat 8192 "x")) "tail"))
(def pages (mapv #(assoc % :sha256 (wire/sha256 (:text %)))
                 (:diff-chunks (review/begin text))))
(def manifest (assoc fixture/manifest :full_diff
                     {:path "basehead.diff" :bytes (wire/utf8-bytes text)
                      :sha256 (wire/sha256 text)}))
(def notes [{:id 1 :note "The complete first page changes the source body."}
            {:id 2 :note "The final partial page retains the tail."}])
(def request {:assessments notes})
(def coverage {:chunks 2 :delivered 2 :assessed 2 :missing []})
(def context (-> fixture/context
                 (assoc :input-source manifest :pages pages :page-count 2
                        :full-input-sha256 (get-in manifest [:full_diff :sha256])
                        :batch-host strict/host)
                 (update :review-tools into ["review_read_diff_batch" tool])))
(def submission
  (assoc fixture/submission :input-source manifest :input-coverage coverage
         :input-assessments (mapv #(merge (select-keys %1 [:id :start :end]) %2)
                                 pages notes)))

(defn trace []
  (-> (fixture/trace)
      (assoc-in [0 :part :state :output :input-source] manifest)
      (assoc-in [0 :part :state :output :batch-host] strict/host)
      (assoc-in [0 :part :state :output :diff-stats]
                {:files 1 :bytes (wire/utf8-bytes text) :truncated? false})
      (assoc-in [0 :part :state :output :input-coverage]
                {:chunks 2 :delivered 0 :assessed 0 :missing [1 2]})
      (assoc-in [1 :part :tool] "review_read_diff_batch")
      (assoc-in [1 :part :state :input] {:first-id 1 :count 2})
      (assoc-in [1 :part :state :output]
                (merge fixture/absent-context-fields
                       {:ok? true :restart-required? false :chunks pages
                        :input-source (select-keys manifest [:base_sha :head_sha :diff_base_sha :full_diff])
                        :coverage {:chunks 2 :delivered 2 :assessed 0 :missing [1 2]}
                        :batch {:ids [1 2] :first-id 1 :last-id 2 :next-id nil
                                :profile batch/profile}
                        :custody {:profile batch/profile :session "session-fixture"
                                  :source_sha256 (get-in manifest [:full_diff :sha256])
                                  :head_sha (:head_sha manifest) :base_sha (:base_sha manifest)}}))
      (assoc-in [2 :part :tool] tool)
      (assoc-in [2 :part :state :input] request)
      (assoc-in [2 :part :state :output]
                (merge fixture/absent-context-fields
                       {:ok? true :restart-required? false :chunk-ids [1 2]
                        :coverage coverage}))))

(deftest delivered-request-is-closed-and-keeps-existing-measured-caps
  (is (batch/delivered-assessment-shape? request 16384 [2048 2048]))
  (doseq [[args bytes sizes]
          [[request 16385 [1 1]] [request 100 [2049 1]]
           [(assoc request :profile batch/profile) 100 [1 1]]
           [(assoc request :source_sha256 (get-in manifest [:full_diff :sha256])) 100 [1 1]]
           [(assoc-in request [:assessments 0 :start] 0) 100 [1 1]]
           [(assoc-in request [:assessments 1 :id] 1) 100 [1 1]]
           [(update request :assessments #(vec (reverse %))) 100 [1 1]]
           [(assoc-in request [:assessments 0 :note] " ") 100 [1 1]]
           [{:assessments []} 100 []]
           [{:assessments (mapv #(assoc (first notes) :id %) (range 1 6))} 100 [1 1 1 1 1]]]]
    (is (false? (batch/delivered-assessment-shape? args bytes sizes)))))

(deftest delivered-native-trace-binds-two-pages-to-one-real-assessment-call
  (let [result (invocation/verify (trace) submission context)]
    (is (:ok? result) (pr-str result))
    (is (= [{:id 1 :kind :read :call-id "call-2" :part-id "part-2" :position 2
             :tool "review_read_diff_batch" :members [1 2]}
            {:id 2 :kind :read :call-id "call-2" :part-id "part-2" :position 2
             :tool "review_read_diff_batch" :members [1 2]}
            {:id 1 :kind :assessment :call-id "call-3" :part-id "part-3" :position 3
             :tool tool :members [1 2]}
            {:id 2 :kind :assessment :call-id "call-3" :part-id "part-3" :position 3
             :tool tool :members [1 2]}]
           (:page-witnesses result)))))

(deftest delivered-native-arguments-and-preceding-source-are-untrusted
  (doseq [events [(assoc-in (trace) [2 :part :state :input :assessments 0 :start] 0)
                  (assoc-in (trace) [2 :part :state :input :assessments 0 :id] 99)
                  (assoc-in (trace) [2 :part :state :input :assessments 1 :id] 1)
                  (assoc-in (trace) [2 :part :state :input :assessments 0 :note] " ")
                  (assoc-in (trace) [2 :part :state :output :chunk-ids] [1])
                  (assoc-in (trace) [1 :part :state :output :chunks 0 :text] "foreign")
                  (fixture/append-calls (trace)
                    [(fixture/tool-event 10 "review_read_diff_batch" {:first-id 1 :count 2}
                                         {:ok? false :error "Late read refused"})])]]
    (is (false? (:ok? (invocation/verify events submission context))))))

(deftest existing-single-and-strict-batch-remain-accepted-controls
  (is (:ok? (fixture/verdict (fixture/trace))))
  (is (:ok? (invocation/verify (strict/batch-trace) fixture/submission strict/context))))

(deftest reported-old-metadata-copy-errors-still-refuse-without-credit
  (let [s (strict/session)
        ids (mapv :id (:diff-chunks s))
        read (review/read-diff-batch s {:first-id 1 :count 4} ids)
        args {:profile batch/profile :source_sha256 (get-in fixture/manifest [:full_diff :sha256])
              :assessments (mapv #(assoc (select-keys % [:id :start :end :sha256]) :note "Old strict note")
                                 (:diff-chunks s))}]
    (is (:ok? (review/assess-diff-batch (:session read) args 100 [15 15 15])))
    (doseq [bad [(assoc-in args [:assessments 0 :sha256] (apply str (repeat 62 "a")))
                 (assoc-in args [:assessments 0 :start] 13750039)]]
      (let [result (review/assess-diff-batch (:session read) bad 100 [15 15 15])]
        (is (false? (:ok? result)))
        (is (= {} (get-in result [:session :assessed-chunks])))
        (is (review/restart-required? (:session result)))))))

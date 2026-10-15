(ns eta-mu.law.review-batch-test
  "Independent shaped native-call fixtures, not a provider review."
  (:require [cljs.test :refer [deftest is]]
            [eta-mu.law.review-batch :as batch]
            [eta-mu.law.review-invocation-test :as fixture]
            [eta-mu.domain.review :as review]
            [eta-mu.domain.review-invocation :as invocation]))

(def digest (apply str (repeat 64 "a")))
(def host {:binary-sha256 batch/host-binary-sha256 :config-sha256 digest
           :selectors-sha256 digest :max-bytes 51200 :max-lines 2000})
(def page {:id 1 :start 0 :end 4 :sha256 digest})
(def request {:profile batch/profile :source_sha256 (get-in fixture/manifest [:full_diff :sha256])
              :assessments [(assoc page :note "whole-page assessment")]})

(defn batch-trace []
  (-> (fixture/trace)
      (assoc-in [0 :part :state :output :batch-host] host)
      (assoc-in [1 :part :tool] "review_read_diff_batch")
      (assoc-in [1 :part :state :input] {:first-id 1 :count 1})
      (assoc-in [1 :part :state :output]
                (merge fixture/absent-context-fields
                       {:ok? true :restart-required? false
                        :chunks [(assoc page :text "diff")]
                        :input-source (select-keys fixture/manifest [:base_sha :head_sha :diff_base_sha :full_diff])
                        :coverage {:chunks 1 :delivered 1 :assessed 0 :missing [1]}
                        :batch {:ids [1] :first-id 1 :last-id 1 :next-id nil :profile batch/profile}
                        :custody {:profile batch/profile :session "session-fixture"
                                  :source_sha256 (get-in fixture/manifest [:full_diff :sha256])
                                  :head_sha (:head_sha fixture/manifest) :base_sha (:base_sha fixture/manifest)}}))
      (assoc-in [2 :part :tool] "review_assess_diff_batch")
      (assoc-in [2 :part :state :input] request)
      (assoc-in [2 :part :state :output :chunk-ids] [1])
      (update-in [2 :part :state :output] dissoc :chunk-id)))

(def context (-> fixture/context
                 (assoc :batch-host host :pages [(assoc page :text "diff")])
                 (update :review-tools into ["review_read_diff_batch" "review_assess_diff_batch"])))

(deftest exact-bounds-and-whole-assessment-shape
  (is (batch/qualified-host? host))
  (is (not (batch/qualified-host? (assoc host :max-bytes 48000))))
  (is (batch/response-fits? {:bytes 48000 :lines 1}))
  (is (not (batch/response-fits? {:bytes 48001 :lines 1})))
  (is (not (batch/response-fits? {:bytes 1 :lines 2})))
  (is (batch/assessment-shape? request 16384 [2048]))
  (is (not (batch/assessment-shape? request 16385 [2048])))
  (is (not (batch/assessment-shape? request 1000 [2049])))
  (is (not (batch/assessment-shape? (update request :assessments into (:assessments request)) 1000 [1 1])))
  (is (not (batch/assessment-shape? (assoc-in request [:assessments 0 :note] "  ") 1000 [2]))))

(deftest retained-single-page-trace-control
  (is (:ok? (fixture/verdict (fixture/trace)))))

(deftest batch-is-one-real-call-with-one-derived-page-witness
  (let [result (invocation/verify (batch-trace) fixture/submission context)]
    (is (:ok? result) (pr-str result))
    (is (= [{:id 1 :kind :read :call-id "call-2" :part-id "part-2" :position 2
             :tool "review_read_diff_batch" :members [1]}
            {:id 1 :kind :assessment :call-id "call-3" :part-id "part-3" :position 3
             :tool "review_assess_diff_batch" :members [1]}]
           (:page-witnesses result)))))

(deftest malformed-batch-and-late-failed-read-refuse
  (doseq [events [(assoc-in (batch-trace) [1 :part :state :output :batch :ids] [1 1])
                  (assoc-in (batch-trace) [1 :part :state :output :chunks 0 :text] "oops")
                  (assoc-in (batch-trace) [2 :part :state :input :assessments 0 :sha256] (apply str (repeat 64 "f")))
                  (fixture/append-calls (batch-trace)
                    [(fixture/tool-event 10 "review_read_diff_batch" {:first-id 1 :count 1}
                                         {:ok? false :error "refused"})])]]
    (is (false? (:ok? (invocation/verify events fixture/submission context))))))

(defn session []
  (-> (review/begin (str (apply str (repeat 8191 "x")) "😀" (apply str (repeat 8192 "y"))))
      (assoc :input-source fixture/manifest)
      (update :diff-chunks #(mapv (fn [p] (assoc p :sha256 digest)) %))))

(deftest whole-prefix-final-partial-and-atomic-assessment
  (let [s (session) request {:first-id 1 :count 4}
        candidates (review/batch-candidates s request)
        ids (mapv :id (last candidates))
        read (review/read-diff-batch s request ids)
        notes (mapv #(assoc (select-keys % [:id :start :end :sha256]) :note (str "Page " (:id %)))
                    (:diff-chunks s))
        args {:profile batch/profile :source_sha256 (get-in fixture/manifest [:full_diff :sha256])
              :assessments notes}
        assessed (review/assess-diff-batch (:session read) args 1000 (vec (repeat (count notes) 6)))]
    (is (= 3 (count ids)))
    (is (= [8191 8192 2] (mapv #(count (:text %)) (:diff-chunks s))) "Surrogate pair never split")
    (is (= (str (apply str (repeat 8191 "x")) "😀" (apply str (repeat 8192 "y")))
           (apply str (map :text (:chunks read)))))
    (is (:ok? read))
    (is (:ok? assessed))
    (is (= [] (get-in assessed [:coverage :missing])))
    (doseq [bad [(assoc-in args [:assessments 1 :id] 999)
                 (assoc-in args [:assessments 1 :sha256] (apply str (repeat 64 "f")))
                 (assoc-in args [:assessments 1 :note] " ")
                 (update args :assessments conj (first notes))]]
      (let [result (review/assess-diff-batch (:session read) bad 1000 (vec (repeat (count (:assessments bad)) 6)))]
        (is (false? (:ok? result)))
        (is (= {} (get-in result [:session :assessed-chunks])))
        (is (review/restart-required? (:session result)))))
    (let [oversized (review/assess-diff-batch (:session read) args 16385 [6 6 6])]
      (is (false? (:ok? oversized)))
      (is (= {} (get-in oversized [:session :assessed-chunks]))))
    (let [late (review/read-diff-batch (:session assessed) request ids)]
      (is (false? (:ok? late)))
      (is (= (:assessed-chunks (:session assessed)) (:assessed-chunks (:session late)))))))

(deftest undelivered-and-invalid-prefix-have-no-partial-credit
  (let [s (session)]
    (doseq [ids [[1 1] [1 3] [2] [1 2 3 4] []]]
      (let [result (review/read-diff-batch s {:first-id 1 :count 4} ids)]
        (is (false? (:ok? result)))
        (is (= #{} (get-in result [:session :delivered-chunks])))))
    (is (nil? (review/batch-candidates s {:first-id 1 :count 5})))
    (is (nil? (review/batch-candidates s {:first-id 999 :count 1})))
    (is (nil? (review/batch-candidates (review/begin "") {:first-id 1 :count 4})))))

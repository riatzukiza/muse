(ns eta-mu.law.review-context-test
  (:require [cljs.test :refer [deftest is testing]]
            [eta-mu.law.review-context :as law]))

(def source {:path "/owned/pr-context.md" :bytes 57022
             :sha256 (apply str (repeat 64 "a"))})
(def present {:status :present :source source})
(def absent {:status :absent :source nil})

(deftest available-binding-and-unavailable-are-distinct
  (is (= {:ok? true :source source} (law/admit-observation present)))
  (is (= {:ok? true :source nil} (law/admit-observation absent)))
  (doseq [observation [nil {} {:status :absent} {:status :present :source nil}
                       {:status :unavailable :error-code "EACCES"}
                       {:status :unavailable :error-code "secret arbitrary exception"}]]
    (is (false? (:ok? (law/admit-observation observation)))))
  (is (law/binding-valid? nil))
  (is (law/binding-valid? source))
  (is (law/observation-valid? {:status :unavailable :error-code "ENOENT"})))

(deftest custody-is-exact-and-fails-closed
  (is (= {:ok? true :source source} (law/custody-decision source present)))
  (is (= {:ok? true :source nil} (law/custody-decision nil absent)))
  (doseq [[binding observation] [[nil present] [source absent]
                                [source {:status :unavailable :error-code "EIO"}]
                                [{} present] [source {}]]]
    (is (false? (:ok? (law/custody-decision binding observation)))))
  (doseq [[field value] [[:path "/different/pr-context.md"] [:bytes 57021]
                        [:sha256 (apply str (repeat 64 "b"))]]]
    (testing (str "Any change in " field " refuses before effects")
      (is (false? (:ok? (law/custody-decision source
                         (assoc present :source (assoc source field value))))))))
  (doseq [bad [(dissoc source :path) (assoc source :bytes -1)
               (assoc source :bytes 1.5) (assoc source :sha256 "abc")
               (assoc source :extra "unexpected")]]
    (is (not (law/binding-valid? bad)))
    (is (false? (:ok? (law/custody-decision bad present))))))

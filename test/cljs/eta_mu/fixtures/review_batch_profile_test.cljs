(ns eta-mu.fixtures.review-batch-profile-test
  (:require [cljs.test :refer [deftest is run-tests]]
            [eta-mu.fixtures.review-batch-profile :as fixture]))

(deftest whole-response-boundaries
  (is (= :accept (:outcome (fixture/response-outcome {:bytes 48000 :lines 1}))))
  (is (= :refuse (:outcome (fixture/response-outcome {:bytes 48001 :lines 1}))))
  (is (= :refuse (:outcome (fixture/response-outcome {:bytes 100 :lines 2})))))

(deftest whole-request-boundaries
  (is (= :accept (:outcome (fixture/request-outcome 16384 [2048 10 10 10]
                                                 ["a" "b" "c" "d"]))))
  (is (= :refuse (:outcome (fixture/request-outcome 16385 [2048 10 10 10]
                                                 ["a" "b" "c" "d"]))))
  (is (= :refuse (:outcome (fixture/request-outcome 3000 [2049] ["a"]))))
  (is (= :refuse (:outcome (fixture/request-outcome 3000 [10 10 10 10 10]
                                                 ["a" "b" "c" "d" "e"]))))
  (is (= :refuse (:outcome (fixture/request-outcome 3000 [10 0] ["a" " "]))))
  (is (zero? (:members (fixture/request-outcome 16385 [10 10] ["a" "b"])))))

(deftest only-a-read-can-select-a-whole-prefix
  (is (= [1] (:ids (fixture/read-outcome
                    [{:ids [1] :bytes 48000 :lines 1}
                     {:ids [1 2] :bytes 48001 :lines 1}]))))
  (is (= :refuse (:outcome (fixture/read-outcome
                           [{:ids [1] :bytes 48001 :lines 1}])))))
  (is (= [1 2] (:ids (fixture/read-outcome
                      [{:ids [1] :bytes 100 :lines 1}
                       {:ids [1 2] :bytes 200 :lines 1}])))))

(run-tests)

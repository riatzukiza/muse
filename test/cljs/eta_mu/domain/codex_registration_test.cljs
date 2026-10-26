(ns eta-mu.domain.codex-registration-test
  (:require [cljs.test :refer [deftest is]]
            [eta-mu.domain.codex-registration :as registration]))

(deftest preserves-unrelated-and-refuses-replacement
  (let [existing {"other" {:command "other" :args []}}
        target {:command "node" :args ["/build/muse.js"]}]
    (is (= {:servers (assoc existing "muse" target) :previous existing}
           (registration/register existing "muse" target)))
    (is (thrown? js/Error (registration/register existing "other" target)))
    (is (= existing (registration/rollback (assoc existing "muse" target) "muse" target)))
    (is (thrown? js/Error (registration/rollback (assoc existing "muse" {:command "changed"}) "muse" target)))
    (is (thrown? js/Error (registration/register existing "bad.name" target)))
    (is (thrown? js/Error (registration/register existing "muse" {:command "node" :args ["relative.js"]})))))

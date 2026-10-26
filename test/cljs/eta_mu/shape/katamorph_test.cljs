(ns eta-mu.shape.katamorph-test
  (:require [cljs.test :refer [deftest is]]
            [eta-mu.opencode.config :as config]
            [eta-mu.shape.katamorph :as katamorph]))

(def example-config
  (config/load-config "examples/config/opencode/root.edn"))

(deftest assembles-validated-katamorph-resources
  (let [assembly (katamorph/assemble
                  (config/contract-fragments example-config))]
    (is (= :katamorph (:assembly/kind assembly)))
    (is (= {:provider 1
            :model-family 1
            :model 1
            :mcp-server 1
            :agent 1}
           (update-vals (:assembly/by-kind assembly) count)))))

(deftest duplicate-resource-identities-are-rejected
  (let [namespace-file (first (config/contract-fragments example-config))]
    (is (thrown-with-msg?
         js/Error
         #"Duplicate Katamorph resource identity"
         (katamorph/assemble [namespace-file namespace-file])))))

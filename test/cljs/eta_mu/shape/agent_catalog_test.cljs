(ns eta-mu.shape.agent-catalog-test
  (:require [cljs.test :refer [deftest is testing]]
            [eta-mu.opencode.config :as config]
            [eta-mu.shape.agent-catalog :as catalog]
            [eta-mu.shape.katamorph :as katamorph]))

(def example-config
  (config/load-config "examples/config/opencode/root.edn"))

(def example-catalog
  (-> (config/contract-fragments example-config)
      katamorph/assemble
      catalog/resolve-catalog))

(deftest resolves-portable-agent-runtime-references
  (is (= :agent-runtime (:catalog/kind example-catalog)))
  (is (= :open-hax/gpt-5.5 (:catalog/default-model example-catalog)))
  (testing "family and model links are qualified without changing definitions"
    (is (= :open-hax/proxx
           (:resolved/provider
            (catalog/resource example-catalog :model-family :open-hax/gpt))))
    (is (= {:resolved/model-family :open-hax/gpt
            :resolved/provider :open-hax/proxx}
           (select-keys
            (catalog/resource example-catalog :model :open-hax/gpt-5.5)
            [:resolved/model-family :resolved/provider])))
    (is (= "GPT-5.5"
           (get-in (catalog/resource example-catalog
                                     :model
                                     :open-hax/gpt-5.5)
                   [:resource/definition :model/label]))))
  (testing "the agent points at the portable model identity"
    (is (= :open-hax/gpt-5.5
           (:resolved/model
            (catalog/resource example-catalog :agent :open-hax/coding))))))

(deftest agents-without-an-explicit-model-inherit-the-catalog-default
  (let [resolved
        (-> [{:namespace :example
              :resources
              [{:provider/id :gateway
                :provider/api-shape :openai-chat}
               {:model/id "default"
                :model/provider :gateway
                :model/default true}
               {:agent/id :assistant}]}]
            katamorph/assemble
            catalog/resolve-catalog)]
    (is (= :example/default
           (:resolved/model
            (catalog/resource resolved :agent :example/assistant))))))

(deftest unresolved-references-fail-before-target-projection
  (is (thrown-with-msg?
       js/Error
       #"unknown provider"
       (-> [{:namespace :broken
             :resources [{:model/id "orphan"
                          :model/provider :missing}]}]
           katamorph/assemble
           catalog/resolve-catalog)))
  (is (thrown-with-msg?
       js/Error
       #"at most one default model"
       (-> [{:namespace :broken
             :resources [{:provider/id :gateway
                          :provider/api-shape :openai-chat}
                         {:model/id "one"
                          :model/provider :gateway
                          :model/default true}
                         {:model/id "two"
                          :model/provider :gateway
                          :model/default true}]}]
           katamorph/assemble
           catalog/resolve-catalog))))

(ns eta-mu.shape.review-submission-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            [eta-mu.shape.review-submission :as submission]))

(deftest submission-wire-preserves-data-and-existing-format
  (is (fn? submission/serialize) "Serialization is a pure morphism independent of Node I/O.")
  (when (fn? submission/serialize)
    (let [envelope {:schema "open-hax.github-review/v1" :context-source nil
                    :summary "Unicode ημ 😀, quote \" and newline\nremain data."
                    :comments [{:line 1 :enabled true :labels ["contract"]}]}
          wire (submission/serialize envelope)]
      (is (string? wire))
      (is (str/ends-with? wire "\n"))
      (is (str/includes? wire "\n  \"schema\""))
      (is (= envelope (js->clj (js/JSON.parse wire) :keywordize-keys true)))
      (is (= (str (js/JSON.stringify (clj->js envelope) nil 2) "\n") wire)
          "Preserve the qualified existing wire representation."))))

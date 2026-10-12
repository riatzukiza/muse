(ns eta-mu.extern.review-context-delivery-test
  "Actual exported-boundary controls over synthetic observed HOST syntax."
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            [eta-mu.extern.review-invocation :as extern]
            [eta-mu.extern.review-invocation-test :as raw-fixture]
            [eta-mu.law.review-context-delivery-test :as delivery]
            [eta-mu.law.review-invocation-test :as fixture]
            ["node:crypto" :as crypto]))

(def text "alpha\nbeta\n")
(defn utf8-bytes [value] (.encode (js/TextEncoder.) value))
(defn sha [value]
  (-> (js-invoke crypto "createHash" "sha256") (js-invoke "update" value)
      (js-invoke "digest" "hex")))

(defn prepare [raw-context path]
  ;; Invoke the actual JS boundary without teaching the compiler a future arity.
  (js/Reflect.apply extern/prepare-review-invocation-context nil
                    #js [(utf8-bytes "diff") (raw-fixture/json fixture/manifest)
                         (clj->js (:review-tools fixture/context))
                         (:submission-file fixture/context) raw-context path]))

(defn raw-response [events]
  (str (str/join "\n"
                 (map (fn [event]
                        (let [raw (raw-fixture/host-event event)]
                          (raw-fixture/json
                           (if (= "tool_use" (:type event))
                             (update-in raw [:part :state] merge
                                        (select-keys (get-in event [:part :state]) [:metadata :time])) raw))))
                      events)) "\n"))

(defn case-data [reads]
  (delivery/case-data (assoc delivery/source :sha256 (sha (utf8-bytes text)))
                      delivery/source-lines [5 4] reads))

(defn result [case context]
  (js->clj (extern/verify-review-invocation (utf8-bytes (raw-response (:events case)))
                                           (raw-fixture/json (:submission case)) context)
           :keywordize-keys true))

(deftest trusted-preparation-derives-current-source-not-copied-metadata
  (let [prepared (js->clj (prepare (utf8-bytes text) delivery/context-path) :keywordize-keys true)]
    (is (= {:path delivery/context-path :bytes 11 :sha256 (sha (utf8-bytes text))}
           (:contextSource prepared)))
    (is (= delivery/source-lines (:contextLines prepared)))
    (is (= [5 4] (:contextLineBytes prepared))))
  (let [prepared (js->clj (prepare nil delivery/context-path) :keywordize-keys true)]
    (is (contains? prepared :contextSource))
    (is (nil? (:contextSource prepared)))
    (is (= [] (:contextLines prepared))))
  (doseq [[raw path] [[(js/Uint8Array. #js [255]) delivery/context-path]
                     [(utf8-bytes text) "relative/context.txt"]
                     [js/undefined delivery/context-path]
                     [(utf8-bytes text) nil]]]
    (is (thrown? js/Error (prepare raw path))))
  (is (thrown? js/Error
               (js/Reflect.apply extern/prepare-review-invocation-context nil
                                 #js [(utf8-bytes "diff") (raw-fixture/json fixture/manifest)
                                      (clj->js (:review-tools fixture/context))
                                      (:submission-file fixture/context)]))))

(deftest actual-verifier-decoder-retains-time-metadata-and-context-read-obligation
  (let [context (prepare (utf8-bytes text) delivery/context-path)
        healthy (result (case-data [delivery/complete-read]) context)]
    (is (:ok healthy))
    (is (= 2 (get-in healthy [:contextReadCoverage :deliveredLines])))
    (is (true? (get-in healthy [:contextReadCoverage :eof]))))
  (doseq [case [(case-data []) (case-data [delivery/partial-read])
               (assoc-in (case-data [delivery/complete-read]) [:events 4 :part :state :time :start] 1)
               (update-in (case-data [delivery/complete-read]) [:events 1 :part :state] dissoc :metadata)]]
    (let [failed (result case (prepare (utf8-bytes text) delivery/context-path))]
      (is (false? (:ok failed)))
      (is (= "unestablished-review-trace" (:reasonKind failed)))
      (is (nil? (:acceptedInvocation failed))))))

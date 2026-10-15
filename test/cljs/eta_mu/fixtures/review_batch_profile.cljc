(ns eta-mu.fixtures.review-batch-profile
  "Diagnostic candidate-profile fixtures only. No production review credit.
   The joint task still owns batch state, law, native verifier and API."
  (:require [clojure.string :as str]))

(def profile
  {:response-bytes 48000 :response-lines 1 :max-pages 4
   :context-metadata-bytes 4096 :request-bytes 16384 :note-bytes 2048
   :session-id-bytes 128 :session-json-bytes 130})

(defn session-outcome [{:keys [bytes json-bytes nonblank?]}]
  {:outcome (if (and (true? nonblank?) (pos-int? bytes) (pos-int? json-bytes)
                     (<= bytes (:session-id-bytes profile))
                     (<= json-bytes (:session-json-bytes profile))) :accept :refuse)
   :credit :fixture-only})

(defn context-outcome [bytes]
  {:outcome (if (and (nat-int? bytes) (<= bytes (:context-metadata-bytes profile)))
              :accept :refuse)
   :credit :fixture-only})

(defn response-outcome [{:keys [bytes lines]}]
  {:outcome (if (and (nat-int? bytes) (pos-int? lines)
                     (<= bytes (:response-bytes profile))
                     (<= lines (:response-lines profile))) :accept :refuse)
   :credit :fixture-only})

(defn request-outcome [bytes note-bytes notes]
  (let [valid? (and (nat-int? bytes) (<= bytes (:request-bytes profile))
                    (<= 1 (count notes) (:max-pages profile))
                    (= (count notes) (count note-bytes))
                    (every? #(and (string? %) (not (str/blank? %))) notes)
                    (every? #(and (nat-int? %) (<= % (:note-bytes profile))) note-bytes))]
    {:outcome (if valid? :accept :refuse)
     :members (if valid? (count notes) 0)
     :credit :fixture-only}))

(defn read-outcome
  "Input is measured complete consecutive prefixes. No page splitting or state.
   Production custody/identity/native-call verification is deliberately absent."
  [prefixes]
  (if-let [selected (last (take-while
                           #(and (<= (count (:ids %)) (:max-pages profile))
                                 (= :accept (:outcome (response-outcome %))))
                           prefixes))]
    {:outcome :accept :ids (:ids selected) :credit :fixture-only}
    {:outcome :refuse :ids [] :credit :fixture-only}))

(ns eta-mu.law.review-batch
  "Qualified whole-page batch shapes and bounds. No I/O or host authority.
   GPL-3.0-or-later."
  (:require [clojure.string :as str]
            [malli.core :as m]))

(def profile "muse-review-batch-profile/2026-10-15-candidate-v2")
(def host-binary-sha256 "f9dab32248695e9ebd56b16a1921798fd85112cf5a69c7dfd0cabc1e17be4a11")
(def limits {:response-bytes 48000 :response-lines 1 :max-pages 4
             :context-bytes 4096 :request-bytes 16384 :note-bytes 2048
             :session-bytes 128 :session-json-bytes 130})

(def registry
  {::digest [:re "^[0-9a-f]{64}$"]
   ::id [:int {:min 1 :max 9007199254740991}]
   ::host [:map {:closed true}
           [:binary-sha256 [:ref ::digest]]
           [:config-sha256 [:ref ::digest]]
           [:selectors-sha256 [:ref ::digest]]
           [:max-bytes :int] [:max-lines :int]]
   ::read [:map {:closed true} [:first-id [:ref ::id]]
           [:count [:int {:min 1 :max 4}]]] 
   ::assessment [:map {:closed true} [:id [:ref ::id]]
                 [:start [:int {:min 0}]] [:end [:int {:min 1}]]
                 [:sha256 [:ref ::digest]] [:note [:string {:min 1}]]]
   ::assess [:map {:closed true} [:profile [:= profile]]
             [:source_sha256 [:ref ::digest]]
             [:assessments [:vector {:min 1 :max 4} [:ref ::assessment]]]]})

(defn- validator [key]
  (m/validator [:ref key] {:registry (merge (m/default-schemas) registry)}))
(def host-valid? (validator ::host))
(def read-valid? (validator ::read))
(def assess-valid? (validator ::assess))

(defn qualified-host? [host]
  (and (host-valid? host) (= host-binary-sha256 (:binary-sha256 host))
       (= 51200 (:max-bytes host)) (= 2000 (:max-lines host))))

(defn response-fits? [{:keys [bytes lines]}]
  (and (nat-int? bytes) (pos-int? lines)
       (<= bytes (:response-bytes limits)) (<= lines (:response-lines limits))))

(defn session-fits? [{:keys [bytes json-bytes nonblank?]}]
  (and (true? nonblank?) (pos-int? bytes) (pos-int? json-bytes)
       (<= bytes (:session-bytes limits)) (<= json-bytes (:session-json-bytes limits))))

(defn profile-fits?
  "Shared native measurements; neither producer nor verifier owns a second cap."
  [session-measurement context-bytes]
  (and (session-fits? session-measurement) (nat-int? context-bytes)
       (<= context-bytes (:context-bytes limits))))

(defn host-matches? [expected observed]
  (and (qualified-host? expected) (qualified-host? observed) (= expected observed)))

(defn assessment-shape?
  "Measurements are supplied by the wire boundary, never trusted model args."
  [request bytes note-bytes]
  (and (assess-valid? request) (nat-int? bytes) (<= bytes (:request-bytes limits))
       (= (count (:assessments request)) (count note-bytes))
       (every? #(and (nat-int? %) (<= % (:note-bytes limits))) note-bytes)
       (every? #(not (str/blank? (:note %))) (:assessments request))
       (let [ids (mapv :id (:assessments request))]
         (and (= ids (vec (sort ids))) (= (count ids) (count (set ids)))))))

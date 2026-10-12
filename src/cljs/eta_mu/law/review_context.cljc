(ns eta-mu.law.review-context
  "Portable staged context custody. Metadata equality is not authentication or
   evidence that a model read or assessed the bytes. GPL-3.0-or-later."
  (:require [malli.core :as m]))

(def protocol "open-hax.review-context/v1")

(def registry
  {::source [:map {:closed true}
             [:path [:string {:min 1}]]
             [:bytes [:int {:min 0 :max 9007199254740991}]]
             [:sha256 [:re "^[0-9a-f]{64}$"]]]
   ::binding [:maybe [:ref ::source]]
   ::observation
   [:multi {:dispatch :status}
    [:present [:map {:closed true} [:status [:= :present]] [:source [:ref ::source]]]]
    [:absent [:map {:closed true} [:status [:= :absent]] [:source :nil]]]
    [:unavailable [:map {:closed true} [:status [:= :unavailable]]
                   [:error-code [:enum "EACCES" "EIO" "ENOENT" "EPERM" "EISDIR"
                                 "ENOTDIR" "ELOOP" "ERR_INVALID_UTF8" "UNKNOWN"]]]]]})

(def binding-valid? (m/validator [:ref ::binding] {:registry (merge (m/default-schemas) registry)}))
(def observation-valid? (m/validator [:ref ::observation] {:registry (merge (m/default-schemas) registry)}))

(defn admit-observation
  "Bind a successful present/absent observation; unavailable never means nil."
  [_observation]
  {:ok? false :error "Context admission not implemented."})

(defn custody-decision
  "Admit effects only for a valid binding and a matching available observation."
  [_binding _observation]
  {:ok? false :error "Context custody not implemented."})

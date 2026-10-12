(ns eta-mu.extern.review-submission
  "Node serialization/write boundary for a domain-produced submission.
   Admission policy stays in the laws/infra caller. GPL-3.0-or-later."
  (:require ["node:fs" :as fs]))

(defn write! [path envelope]
  (js-invoke fs "writeFileSync" path
             (str (js/JSON.stringify (clj->js envelope) nil 2) "\n") "utf8")
  path)

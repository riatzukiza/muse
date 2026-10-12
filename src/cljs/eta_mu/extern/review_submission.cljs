(ns eta-mu.extern.review-submission
  "Node writer for an already-serialized submission wire string.
   Admission and serialization stay outside this edge. GPL-3.0-or-later."
  (:require ["node:fs" :as fs]))

(defn write! [path wire]
  (js-invoke fs "writeFileSync" path wire "utf8")
  path)

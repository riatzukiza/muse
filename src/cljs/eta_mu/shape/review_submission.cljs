(ns eta-mu.shape.review-submission
  "Pure submission envelope-to-wire morphism, separate from Node filesystem I/O.
   GPL-3.0-or-later.")

(defn serialize
  "Encode an envelope as the existing pretty JSON representation with a final
   newline. This pure CLJS morphism has no filesystem or provider effects."
  [envelope]
  (str (js/JSON.stringify (clj->js envelope) nil 2) "\n"))

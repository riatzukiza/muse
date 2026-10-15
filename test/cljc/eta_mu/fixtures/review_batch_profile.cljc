(ns eta-mu.fixtures.review-batch-profile
  "Diagnostic candidate-profile fixtures only. No production review credit.
   The joint task still owns batch state, law, native verifier and API.")

(def profile
  {:response-bytes 48000 :response-lines 1 :max-pages 4
   :request-bytes 16384 :note-bytes 2048})

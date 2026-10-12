(ns eta-mu.extern.review-context
  "Raw Node context observation, decoded to bounded Clojure data. Does not
   decide admission or expose content/exception messages. GPL-3.0-or-later."
  (:require ["node:fs" :as fs]
            ["node:crypto" :as crypto]))

(def safe-error-codes #{"EACCES" "EIO" "ENOENT" "EPERM" "EISDIR" "ENOTDIR" "ELOOP"})

(defn- unavailable [error]
  {:status :unavailable
   :error-code (if (contains? safe-error-codes (.-code error)) (.-code error) "UNKNOWN")})

(defn observe
  "Stat ENOENT is observed absence. An ENOENT after successful stat is an
   unavailable exists/read race, never absence. No atomic custody is claimed."
  [path]
  (let [presence (try
                   (if (js-invoke (js-invoke fs "statSync" path) "isFile")
                     {:status :present}
                     {:status :unavailable :error-code "EISDIR"})
                   (catch :default error
                     (if (= "ENOENT" (.-code error))
                       {:status :absent :source nil}
                       (unavailable error))))]
    (if (not= :present (:status presence)) presence
      (try
        (let [bytes (js-invoke fs "readFileSync" path)
              utf8? (try (.decode (js/TextDecoder. "utf-8" #js {:fatal true}) bytes) true
                         (catch :default _ false))]
          (if-not utf8? {:status :unavailable :error-code "ERR_INVALID_UTF8"}
            {:status :present
             :source {:path path :bytes (.-length bytes)
                      :sha256 (-> (js-invoke crypto "createHash" "sha256")
                                  (js-invoke "update" bytes)
                                  (js-invoke "digest" "hex"))}}))
        (catch :default error (unavailable error))))))

(ns eta-mu.extern.review-batch
  "Raw host/config and exact JSON/UTF8 measurement. Never changes host limits.
   GPL-3.0-or-later."
  (:require [clojure.string :as str]
            ["node:fs" :as fs]
            ["node:crypto" :as crypto]))

(defn encode [value] (js/JSON.stringify (clj->js value)))
(defn utf8-bytes [text] (js/Buffer.byteLength text "utf8"))
(defn sha256 [value]
  (-> (js-invoke crypto "createHash" "sha256")
      (js-invoke "update" value) (js-invoke "digest" "hex")))
(defn measure [wire]
  {:bytes (utf8-bytes wire) :lines (count (str/split wire #"\n" -1))})
(defn session-measurement [value]
  (when (string? value)
    {:bytes (utf8-bytes value) :json-bytes (utf8-bytes (encode value))
     :nonblank? (not (str/blank? value))}))

(def selector-names
  ["OPENCODE_CONFIG" "OPENCODE_CONFIG_DIR" "OPENCODE_CONFIG_CONTENT"
   "OPENCODE_MANAGED_CONFIG_DIR" "OPENCODE_DISABLE_PROJECT_CONFIG"
   "OPENCODE_TEST_MANAGED_CONFIG_DIR"
   "HOME" "XDG_CONFIG_HOME"])

(defn executable-sha256 [executable]
  (when (= 185030784 (.-size (js-invoke fs "statSync" executable)))
    (sha256 (js-invoke fs "readFileSync" executable))))

(defn host-observation
  "One canonical fingerprint for live SDK observations and supervisor capture.
   The caller authenticates capture/launch provenance; this function does not."
  [binary cfg environment directory]
  (let [selected (:tool_output cfg)
        selectors (mapv (fn [name] [name (aget environment name)]) selector-names)]
    {:binary-sha256 binary :config-sha256 (sha256 (encode cfg))
     :selectors-sha256 (sha256 (encode [directory selectors]))
     :max-bytes (if (contains? selected :max_bytes) (:max_bytes selected) 51200)
     :max-lines (if (contains? selected :max_lines) (:max_lines selected) 2000)}))

(defn- ^:async observe-host [client binary directory]
      (try
        (when (and client (.-config client) (fn? (.-get (.-config client))))
          (let [response (await (.get (.-config client) #js {:query #js {:directory directory}
                                                             :signal (js/AbortSignal.timeout 10000)
                                                             :throwOnError true}))
                data (.-data response)
                _ (when-not (and data (object? data) (not (js/Array.isArray data)))
                    (throw (js/Error. "Unavailable resolved config")))
                cfg (js->clj data :keywordize-keys true)]
            (host-observation @binary cfg (.-env js/process) directory)))
        (catch :default _ nil)))

(defn host-observer
  "Capture the executing binary, then query THIS host's resolved SDK config
   before each batch effect. Unknown/read failure returns nil, never defaults
   without the separately qualified immutable binary. No raw config is emitted."
  [host-input]
  (let [client (some-> host-input .-client)
        binary (delay (try (executable-sha256 (.-execPath js/process))
                           (catch :default _ nil)))]
    (fn [directory] (observe-host client binary directory))))

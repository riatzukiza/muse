(ns eta-mu.extern.review-invocation
  "Decode the actual OpenCode --format json host shape, never model prose.
   Parent exports verify-review-invocation as verifyReviewInvocation in CJS.
   Caller owns terminal EOF, raw hashes/source custody/retry authorization.
   GPL-3.0-or-later."
  (:require [clojure.string :as str]
            [eta-mu.law.review-invocation :as law]
            [eta-mu.law.review-batch :as batch]
            [eta-mu.extern.review-batch :as batch-wire]
            [eta-mu.domain.review-invocation :as invocation]
            [eta-mu.domain.review :as review]
            [eta-mu.shape.review-context-delivery :as context-shape]
            ["node:crypto" :as crypto]))

(defn- invalid! [] (throw (ex-info "Unestablished structured input" {})))

(defn- well-formed-text? [text]
  (loop [i 0]
    (if (>= i (count text)) true
      (let [c (.charCodeAt text i)]
        (cond
          (<= 55296 c 56319) (and (< (inc i) (count text))
                                 (<= 56320 (.charCodeAt text (inc i)) 57343)
                                 (recur (+ i 2)))
          (<= 56320 c 57343) false
          :else (recur (inc i)))))))

(defn- raw-text [raw]
  (let [text (cond (string? raw) raw
                   (instance? js/Uint8Array raw) (.decode (js/TextDecoder. "utf-8" #js {:fatal true}) raw)
                   :else (invalid!))]
    (when-not (well-formed-text? text) (invalid!))
    text))

(defn- raw-sha256 [raw]
  (let [text (raw-text raw)
        bytes (if (string? raw) (.encode (js/TextEncoder.) text) raw)]
    (-> (js-invoke crypto "createHash" "sha256")
        (js-invoke "update" bytes)
        (js-invoke "digest" "hex"))))

(defn strict-json
  "JSON grammar with duplicate member/nonfinite rejection. Parsing is only at
   the runtime boundary. Never recursively interpret decoded text as events."
  [text]
  (let [at (volatile! 0) size (count text)]
    (letfn [(peek-char [] (when (< @at size) (subs text @at (inc @at))))
            (space! [] (while (and (< @at size) (re-matches #"[\t\r\n ]" (peek-char))) (vswap! at inc)))
            (eat! [c] (when-not (= c (peek-char)) (invalid!)) (vswap! at inc))
            (string! []
              (let [start @at]
                (eat! "\"")
                (loop []
                  (when (>= @at size) (invalid!))
                  (case (peek-char)
                    "\"" (do (vswap! at inc)
                               (let [s (js/JSON.parse (subs text start @at))]
                                 (when-not (well-formed-text? s) (invalid!)) s))
                    "\\" (do (vswap! at + 2) (recur))
                    (do (vswap! at inc) (recur))))))
            (number! []
              (let [token (re-find #"^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?" (subs text @at))]
                (when-not token (invalid!))
                (vswap! at + (count token))
                (let [n (js/Number token)] (when-not (js/Number.isFinite n) (invalid!)) n)))
            (literal! [s v] (when-not (str/starts-with? (subs text @at) s) (invalid!))
              (vswap! at + (count s)) v)
            (array! [depth]
              (eat! "[") (space!)
              (if (= "]" (peek-char)) (do (eat! "]") [])
                (loop [items []]
                  (let [items (conj items (value! (inc depth)))]
                    (space!)
                    (case (peek-char)
                      "]" (do (eat! "]") items)
                      "," (do (eat! ",") (space!) (recur items))
                      (invalid!))))))
            (object! [depth]
              (eat! "{") (space!)
              (if (= "}" (peek-char)) (do (eat! "}") {})
                (loop [items {} names #{}]
                  (when-not (= "\"" (peek-char)) (invalid!))
                  (let [k (string!)]
                    (when (contains? names k) (invalid!))
                    (space!) (eat! ":")
                    (let [items (assoc items (keyword k) (value! (inc depth)))]
                      (space!)
                      (case (peek-char)
                        "}" (do (eat! "}") items)
                        "," (do (eat! ",") (space!) (recur items (conj names k)))
                        (invalid!)))))))
            (value! [depth]
              (when (> depth 128) (invalid!)) (space!)
              (case (peek-char)
                "{" (object! depth) "[" (array! depth) "\"" (string!)
                "t" (literal! "true" true) "f" (literal! "false" false)
                "n" (literal! "null" nil) (number!)))]
      (let [v (value! 0)] (space!) (when-not (= @at size) (invalid!)) v))))

(defn- batch-wire-valid?
  "Measure actual native output and arguments, not model-supplied counts."
  [name state session]
  (let [wire (:output state) result (strict-json wire) args (:input state)
        encode #(js/JSON.stringify (clj->js %))
        bytes #(js/Buffer.byteLength % "utf8")
        fields (select-keys result [:context-protocol :context-source :context-observation :restart-required?])]
    (and (not (true? (get-in state [:metadata :truncated])))
         (not (contains? (:metadata state) :outputPath))
         (batch/response-fits? {:bytes (bytes wire) :lines (count (str/split wire #"\n" -1))})
         (<= (bytes (encode fields)) (:context-bytes batch/limits))
         (batch/session-fits? {:bytes (bytes session) :json-bytes (bytes (encode session))
                               :nonblank? (not (str/blank? session))})
         (or (not= name "review_assess_diff_batch")
             (batch/assessment-shape? args (bytes (encode args))
                                     (mapv #(bytes (:note %)) (:assessments args)))))))

(defn prepare-review-batch-host
  "Supervisor API: actual selected executable path, raw resolved config JSON,
   exact child environment and selected directory. Capture before/after child
   with independently verified launch/custody. Reads binary bytes and reuses
   the producer's canonical fingerprint; data alone is not authentication."
  [executable raw-resolved-config environment directory]
  (try
    (when-not (and (string? executable) (str/starts-with? executable "/")
                   (object? environment) (not (js/Array.isArray environment))
                   (string? directory) (str/starts-with? directory "/")) (invalid!))
    (let [cfg (strict-json (raw-text raw-resolved-config))
          result (batch-wire/host-observation (batch-wire/executable-sha256 executable)
                                               cfg environment directory)]
      (when-not (and (map? cfg) (batch/qualified-host? result)) (invalid!))
      (clj->js result))
    (catch :default _ (throw (js/Error. "unestablished-review-trace: unsupported batch host")))))

(defn- decode-event [event]
  (when-not (map? event) (invalid!))
  (let [part (:part event) state (:state part) name (:tool part)
        review? (and (string? name) (str/starts-with? name "review_"))]
    (when (and (contains? law/batch-tools name)
               (not (batch-wire-valid? name state (:sessionID event))))
      (invalid!))
    {:type (:type event) :timestamp (:timestamp event) :session-id (:sessionID event)
     :part {:type (:type part) :id (:id part) :session-id (:sessionID part)
            :call-id (:callID part) :tool name :reason (:reason part)
            :state {:status (:status state) :input (:input state) :error (:error state)
                    :metadata (:metadata state) :time (:time state)
                    :output (if review?
                              (let [s (:output state)]
                                (when-not (and (string? s) (not (str/blank? s))) (invalid!))
                                (let [result (strict-json s)]
                                  (if (contains? result :context-observation)
                                    (update-in result [:context-observation :status]
                                               #(get {"present" :present "absent" :absent
                                                      "unavailable" :unavailable} % %))
                                    result)))
                              (:output state))}}}))

(defn- context-from-input [raw-diff source tools file]
  (let [
        text (raw-text raw-diff)
        bytes (if (string? raw-diff) (.encode (js/TextEncoder.) text) raw-diff)
        digest (-> (js-invoke crypto "createHash" "sha256")
                   (js-invoke "update" bytes)
                   (js-invoke "digest" "hex"))
        ;; Batch-capable callers retain exact content in private trusted memory.
        ;; Legacy callers keep the existing geometry-only preparation shape.
        pages (mapv #(if (some law/batch-tools tools)
                       (assoc % :sha256 (raw-sha256 (:text %)))
                       (select-keys % [:id :start :end])) (review/diff-chunks text))
        ctx {:input-source source :pages pages :page-count (count pages)
             :full-input-sha256 digest :review-tools tools :submission-file file}]
    (when-not (and (= (.-length bytes) (get-in source [:full_diff :bytes]))
                   (= digest (get-in source [:full_diff :sha256]))
                   (law/context-valid? ctx))
      (invalid!))
    ctx))

(defn- context-delivery-input [raw-context context-path]
  (when-not (and (string? context-path) (str/starts-with? context-path "/")
                 (or (identical? nil raw-context) (instance? js/Uint8Array raw-context)))
    (invalid!))
  (let [text (when (some? raw-context) (raw-text raw-context))
        lines (if text (context-shape/host-lines text) [])]
    {:context-source (when (some? raw-context)
                       {:path context-path :bytes (.-length raw-context)
                        :sha256 (raw-sha256 raw-context)})
     :context-path context-path :context-lines lines
     :context-line-bytes (mapv #(.-length (.encode (js/TextEncoder.) %)) lines)}))

(defn prepare-review-invocation-context
  "CJS prepareReviewInvocationContext(rawFullDiff, rawManifest, JS tools[],
   submissionFile, actualRawContextBytesOrNull, fixedAbsoluteContextPath):
   fatal UTF8/strict JSON/exact SHA256 byte count; canonical
   review/diff-chunks geometry. Throws only a safe typed message on refusal.
   Caller independently binds actual Git/native source before and after child;
   hold this returned context in trusted supervisor memory, not artifact JSON."
  [raw-full-diff raw-manifest raw-review-tools submission-file raw-context context-path]
  (try
    (when-not (js/Array.isArray raw-review-tools) (invalid!))
    (let [ctx (merge (context-from-input raw-full-diff (strict-json (raw-text raw-manifest))
                                         (js->clj raw-review-tools) submission-file)
                     (context-delivery-input raw-context context-path))]
      (clj->js {:inputSource (:input-source ctx) :pages (:pages ctx) :pageCount (:page-count ctx)
                :fullInputSha256 (:full-input-sha256 ctx) :reviewTools (:review-tools ctx)
                :submissionFile (:submission-file ctx)
                :contextSource (:context-source ctx) :contextPath (:context-path ctx)
                :contextLines (:context-lines ctx) :contextLineBytes (:context-line-bytes ctx)}))
    (catch :default _
      (throw (js/Error. "unestablished-review-trace: invalid full-input context")))))

(defn- decode-context [raw]
  (let [ctx (if (or (string? raw) (instance? js/Uint8Array raw))
              (strict-json (raw-text raw)) (js->clj raw :keywordize-keys true))
        prepared {:input-source (:inputSource ctx) :pages (:pages ctx)
                  :page-count (:pageCount ctx) :full-input-sha256 (:fullInputSha256 ctx)
                  :review-tools (:reviewTools ctx) :submission-file (:submissionFile ctx)}]
    ;; Optional full bytes are verified again. Without them this must be the
    ;; actual prepare export's result retained by the trusted caller; data
    ;; alone cannot authenticate its source or prove its preparation.
    (when (contains? ctx :fullDiff)
      (when-not (= prepared (context-from-input (:fullDiff ctx) (:inputSource ctx)
                                               (:reviewTools ctx) (:submissionFile ctx)))
        (invalid!)))
    {:input-source (:inputSource ctx) :pages (:pages ctx)
     :page-count (:pageCount ctx) :full-input-sha256 (:fullInputSha256 ctx)
     :review-tools (:reviewTools ctx) :submission-file (:submissionFile ctx)
     :session-id (:sessionID ctx) :batch-host (:batchHost ctx)
     :context-path (:contextPath ctx) :context-lines (:contextLines ctx)
     :context-line-bytes (:contextLineBytes ctx)
     ;; Missing is distinct from explicit null; legacy prepared data refuses.
     :context-source (if (contains? ctx :contextSource) (:contextSource ctx) ::omitted)}))

(defn verify-review-invocation
  "CJS API: rawResponseText/rawSubmission accept strings or UTF8 byte arrays;
   rawSubmission may be null (missing). expectedFullInputContext must be caller
   trusted: actual prepare-review-invocation-context result in supervisor
   memory, optional fullDiff bytes for revalidation, and
   optional sessionID. Returns plain JS {ok, reasonKind, code, violations,...}.
   The prepare export derives canonical geometry and hash-binds actual bytes
   to the manifest. This verifier cannot authenticate a caller or context
   provenance; final independent Git/source/byte validation remains required.
   A JS string cannot attest prior byte decoding; prefer fatal-decoded bytes."
  [raw-response raw-submission expected-context]
  (try
    (let [text (raw-text raw-response)
          lines (remove str/blank? (str/split-lines text))
          events (mapv #(decode-event (strict-json %)) lines)
          submission (when (some? raw-submission) (strict-json (raw-text raw-submission)))
          result (invocation/verify events submission (decode-context expected-context))]
      (clj->js {:ok (:ok? result)
                :reason (some-> (:reason-kind result) name)
                :reasonKind (some-> (:reason-kind result) name)
                :code (name (:code result)) :violations (:violations result)
                :sessionID (:session-id result) :pageCount (:page-count result)
                :reviewCallCount (:review-call-count result)
                :pageWitnesses (:page-witnesses result)
                :contextReadCoverage (when-let [coverage (:context-read-coverage result)]
                                       {:status (name (:status coverage))
                                        :totalLines (:total-lines coverage)
                                        :deliveredLines (:delivered-lines coverage) :eof (:eof? coverage)})
                :acceptedInvocation (when-let [accepted (:accepted-invocation result)]
                                      {:sessionID (:session-id accepted)
                                       :submissionCallID (:submission-call-id accepted)
                                       :submissionPosition (:submission-position accepted)
                                       :submissionFile (:submission-file accepted)
                                       :responseSha256 (raw-sha256 raw-response)
                                       :submissionSha256 (raw-sha256 raw-submission)
                                       :fullInputSha256 (:full-input-sha256 accepted)
                                       :pageCount (:page-count accepted)})}))
    (catch :default _
      #js {:ok false :reason "unestablished-review-trace" :reasonKind "unestablished-review-trace"
           :acceptedInvocation nil
           :code "structured-input-unestablished" :violations #js []})))

(defn- decode-length-context [raw]
  (let [ctx (if (or (string? raw) (instance? js/Uint8Array raw))
              (strict-json (raw-text raw)) (js->clj raw :keywordize-keys true))]
    ;; Unlike generic verify, this narrow classifier requires the actual full
    ;; bytes/text as well as a prepared context. decode-context revalidates its
    ;; digest, byte count and canonical pages via context-from-input.
    (when-not (contains? ctx :fullDiff) (invalid!))
    (assoc (decode-context raw) :full-input-text (raw-text (:fullDiff ctx)))))

(defn classify-length-ended-review
  "UNPUBLISHED candidate boundary. Raw response/submission/context have the
   verify API's syntax, but context MUST include fullDiff for exact revalidation.
   rawCustody is caller-trusted JSON or JS data with invocationState, exitCode,
   submissionState, responseSha256Before/After and contextBefore/After (each with
   fullDiff). Actual response digest is derived here; all admission policy is law.
   Returns {eligible, classification, code, violations,...}, with no full-review
   ok, acceptedInvocation or approval fields. Caller independently authenticates
   source and custody, retains the first failure, composes the shared MAX2 bound,
   and runs a fresh complete review normally. This API invokes nothing."
  [raw-response raw-submission expected-context raw-custody]
  (try
    (let [events (mapv #(decode-event (strict-json %))
                       (remove str/blank? (str/split-lines (raw-text raw-response))))
          submission (when (some? raw-submission) (strict-json (raw-text raw-submission)))
          ctx (decode-length-context expected-context)
          c (if (or (string? raw-custody) (instance? js/Uint8Array raw-custody))
              (strict-json (raw-text raw-custody)) (js->clj raw-custody :keywordize-keys true))
          custody {:invocation-state (:invocationState c) :exit-code (:exitCode c)
                   :submission-present? (some? raw-submission)
                   :submission-state (:submissionState c) :response-sha256 (raw-sha256 raw-response)
                   :response-sha256-before (:responseSha256Before c)
                   :response-sha256-after (:responseSha256After c)
                   :context-before (decode-length-context (clj->js (:contextBefore c)))
                   :context-after (decode-length-context (clj->js (:contextAfter c)))}
          result (invocation/classify-length-ended-review events submission ctx custody)]
      (clj->js {:eligible (:eligible? result) :classification (name (:classification result))
                :code (name (:code result)) :violations (:violations result)
                :requiredAction (some-> (:required-action result) name)
                :sessionID (:session-id result) :pageCount (:page-count result)
                :recordedStages (:recorded-stages result) :terminalPosition (:terminal-position result)
                :terminalReason (:terminal-reason result) :responseSha256 (:response-sha256 result)
                :fullInputSha256 (:full-input-sha256 result)}))
    (catch :default _
      #js {:eligible false :classification "unestablished-length-ended-review"
           :code "structured-input-unestablished" :violations #js []})))

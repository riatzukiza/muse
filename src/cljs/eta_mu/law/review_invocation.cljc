(ns eta-mu.law.review-invocation
  "Pure closure of one complete HOST tool trace. This is not all INPUT49,
   authentication, or authority to invoke a model. GPL-3.0-or-later."
  (:require [clojure.string :as str]
            [eta-mu.law.review-batch :as batch]))

(def stages ["deterministic" "map-change" "generate-candidates"
             "adversarial-validate" "publish"])

(def review-tools #{"review_begin" "review_read_diff_chunk"
                    "review_assess_diff_chunk" "review_record_evidence"
                    "review_propose_finding" "review_classify_finding"
                    "review_status" "review_submit"})

(def batch-tools #{"review_read_diff_batch" "review_assess_diff_batch"})
(def all-review-tools (into review-tools batch-tools))

(def host-tools
  "Known generic host tools. Additional names require actual staged registry
   membership; nested tool output remains data, never a control stream."
  #{"bash" "read" "glob" "grep" "list" "edit" "write" "patch" "apply_patch"
    "todowrite" "todoread" "webfetch" "websearch" "task" "skill"})

(defn- nonblank? [x] (and (string? x) (not (str/blank? x))))
(defn- natural? [x] (and (integer? x) (<= 0 x 9007199254740991)))
(defn- positive? [x] (and (natural? x) (pos? x)))
(defn- sha? [n x] (and (string? x) (boolean (re-matches (re-pattern (str "[0-9a-f]{" n "}")) x))))
(defn- oid? [x] (or (sha? 40 x) (sha? 64 x)))
(defn- success? [call] (true? (get-in call [:part :state :output :ok?])))
(defn- tool [call] (get-in call [:part :tool]))
(defn- input [call] (get-in call [:part :state :input]))
(defn- output [call] (get-in call [:part :state :output]))

(defn context-valid?
  "Validate caller-bound, canonically prepared geometry; this is not native
   source authority. Preserve the existing manifest's SHA40-or-SHA64 grammar."
  [{:keys [input-source pages page-count full-input-sha256 review-tools submission-file]}]
  (let [p (:provenance input-source) d (:full_diff input-source)]
    (and (map? input-source) (= "open-hax.review-input/v1" (:schema input-source))
         (every? #(oid? (get input-source %)) [:base_sha :head_sha :diff_base_sha])
         (map? d) (= "basehead.diff" (:path d)) (natural? (:bytes d)) (sha? 64 (:sha256 d))
         (map? p) (every? #(nonblank? (get p %))
                         [:repository :pull_request :run_id :run_attempt :workflow_ref])
         (oid? (:workflow_sha p))
         (vector? pages)
         (= (count pages) page-count) (= (:sha256 d) full-input-sha256)
         (loop [remaining pages id 1 start 0]
           (if-let [page (first remaining)]
             (and (map? page) (= id (:id page)) (= start (:start page))
                  (natural? (:start page)) (positive? (:end page)) (< start (:end page))
                  (recur (next remaining) (inc id) (:end page)))
             true))
         (vector? review-tools) (every? nonblank? review-tools)
         (= (count review-tools) (count (set review-tools)))
         (every? (set review-tools) eta-mu.law.review-invocation/review-tools)
         (nonblank? submission-file))))

(defn- event-error ([events expected-session] (event-error events expected-session "stop"))
  ([events expected-session terminal-reason]
   (let [sessions (set (map :session-id events))
        calls (filter #(= "tool_use" (:type %)) events)
        call-ids (map #(get-in % [:part :call-id]) calls)
        part-ids (map #(get-in % [:part :id]) calls)]
    (cond
      (not (and (vector? events) (seq events))) :missing-host-events
      (some #(not (and (map? %) (#{"step_start" "step_finish" "text" "tool_use"} (:type %))
                        (natural? (:timestamp %)) (nonblank? (:session-id %))
                        (map? (:part %)) (nonblank? (get-in % [:part :id]))
                        (= (:session-id %) (get-in % [:part :session-id]))
                        (= ({"step_start" "step-start" "step_finish" "step-finish"
                             "text" "text" "tool_use" "tool"} (:type %))
                           (get-in % [:part :type])))) events) :host-event-schema
      (not (apply <= (map :timestamp events))) :host-event-time-order
      (not= 1 (count sessions)) :host-session-binding
      (and expected-session (not= sessions #{expected-session})) :expected-session-binding
      (or (not (every? nonblank? call-ids)) (not= (count call-ids) (count (set call-ids)))
          (not= (count part-ids) (count (set part-ids)))) :host-call-cardinality
      (some #(not (and (= "tool" (get-in % [:part :type]))
                        (nonblank? (tool %)) (= "completed" (get-in % [:part :state :status]))
                        (map? (input %)) (not (get-in % [:part :state :error])))) calls) :host-tool-schema
      (not (and (= "step_finish" (:type (last events)))
                (= terminal-reason (get-in (last events) [:part :reason])))) :unterminated-host-trace
      :else nil))))

(defn- read-call? [c] (contains? #{"review_read_diff_chunk" "review_read_diff_batch"} (tool c)))
(defn- assessment-call? [c] (contains? #{"review_assess_diff_chunk" "review_assess_diff_batch"} (tool c)))

(defn- attempted-ids [c]
  (let [args (input c)]
    (case (tool c)
      "review_read_diff_chunk" [(:id args)]
      "review_assess_diff_chunk" [(:id args)]
      "review_read_diff_batch" (if (batch/read-valid? args)
                                 (vec (range (:first-id args) (+ (:first-id args) (:count args)))) [])
      "review_assess_diff_batch" (if (vector? (:assessments args)) (mapv :id (:assessments args)) [])
      [])))

(defn- delivered-ids [c]
  (if (= "review_read_diff_batch" (tool c)) (get-in (output c) [:batch :ids]) (attempted-ids c)))

(defn- assessment-notes [c]
  (if (= "review_assess_diff_batch" (tool c)) (:assessments (input c))
      [{:id (:id (input c)) :note (:note (input c))}]))

(defn- coverage-valid? [coverage pages]
  (and (map? coverage) (= (count pages) (:chunks coverage))
       (every? #(and (natural? %) (<= % (count pages))) [(:delivered coverage) (:assessed coverage)])
       (vector? (:missing coverage))
       (= (count (:missing coverage)) (count (set (:missing coverage))))
       (every? (set (map :id pages)) (:missing coverage))))

(defn- batch-call-error [c {:keys [pages input-source batch-host]}]
  (let [args (input c) result (output c) page-by-id (into {} (map (juxt :id identity) pages))
        read? (= "review_read_diff_batch" (tool c))
        ids (if read? (get-in result [:batch :ids]) (attempted-ids c))
        source (select-keys input-source [:base_sha :head_sha :diff_base_sha :full_diff])]
    (cond
      (not (batch/qualified-host? batch-host)) :batch-host-unestablished
      (if read? (not (batch/read-valid? args)) (not (batch/assess-valid? args))) :batch-input-shape
      (and (not read?) (or (not= batch/profile (:profile args))
                                  (not= (get-in source [:full_diff :sha256]) (:source_sha256 args))
                                  (not= (count ids) (count (set ids)))
                                  (not= ids (vec (sort ids)))
                                  (some #(not (and (nonblank? (:note %))
                                                   (= (select-keys % [:id :start :end :sha256])
                                                      (select-keys (page-by-id (:id %)) [:id :start :end :sha256]))))
                                        (:assessments args)))) :batch-assessment-binding
      (not (success? c)) nil
      (not (coverage-valid? (:coverage result) pages)) :batch-coverage-binding
      (and read?
           (not (and (vector? ids) (seq ids)
                     (= ids (vec (take (count ids) (attempted-ids c))))
                     (<= (count ids) (:count args))
                     (= ids (mapv :id (:chunks result)))
                     (= {:ids ids :first-id (first ids) :last-id (last ids)
                         :next-id (when (< (last ids) (count pages)) (inc (last ids)))
                         :profile batch/profile} (:batch result))
                     (= source (:input-source result))
                     (= {:profile batch/profile :session (:session-id c)
                         :source_sha256 (get-in source [:full_diff :sha256])
                         :head_sha (:head_sha source) :base_sha (:base_sha source)} (:custody result))
                     (every? (fn [p]
                               (let [expected (page-by-id (:id p))]
                                 (and expected (string? (:text p)) (string? (:sha256 expected))
                                      (= (select-keys expected [:id :start :end :sha256 :text])
                                         (select-keys p [:id :start :end :sha256 :text])))))
                             (:chunks result))))) :batch-returned-pages
      (and (not read?) (not= ids (:chunk-ids result))) :batch-returned-assessments
      :else nil)))

(defn- call-error [calls {:keys [pages review-tools] :as context}]
  (let [page-by-id (into {} (map (juxt :id identity) pages))
        batch-capable? (some batch-tools review-tools)]
    (some
     (fn [call]
       (let [name (tool call) args (input call) result (output call) id (:id args)
             page (get page-by-id id)]
         (cond
           (= "invalid" name) :unavailable-host-tool
           (and (str/starts-with? name "review_") (not (contains? (set review-tools) name))) :unexposed-review-tool
           (and (str/starts-with? name "review_") (not (contains? all-review-tools name))) :unknown-review-tool
           (and (not (str/starts-with? name "review_"))
                (not (or (contains? host-tools name) (contains? (set review-tools) name)))) :unknown-host-tool
           (not (str/starts-with? name "review_")) (when-not (string? result) :host-tool-output)
           (not (and (map? result) (or (true? (:ok? result)) (false? (:ok? result))))) :review-tool-output
           (and (false? (:ok? result)) (not (nonblank? (:error result)))) :review-tool-output
           (contains? batch-tools name) (batch-call-error call context)
           (and (#{"review_read_diff_chunk" "review_assess_diff_chunk"} name)
                (not (and (positive? id) page))) :page-id-binding
           (and (= "review_assess_diff_chunk" name) (not (nonblank? (:note args)))) :assessment-note-shape
           (and (= "review_record_evidence" name)
                (not (and ((set stages) (:stage args)) (nonblank? (:note args))))) :stage-input-shape
           (and (= "review_submit" name) (not (nonblank? (:summary args)))) :submission-call-shape
           (and (= "review_read_diff_chunk" name) (success? call)
                (not (and (map? (:chunk result))
                          (= (select-keys page [:id :start :end]) (select-keys (:chunk result) [:id :start :end]))
                          (string? (get-in result [:chunk :text]))
                          (= (- (:end page) (:start page)) (count (get-in result [:chunk :text])))
                          (or (not batch-capable?)
                              (and (string? (:text page)) (sha? 64 (:sha256 page))
                                   (= (select-keys page [:id :start :end :text :sha256])
                                      (select-keys (:chunk result) [:id :start :end :text :sha256]))))))) :returned-page-binding
           (and (= "review_assess_diff_chunk" name) (success? call)
                (not (and (= id (:chunk-id result))
                          (map? (:coverage result)) (= (count pages) (get-in result [:coverage :chunks]))
                          (every? #(and (natural? %) (<= % (count pages)))
                                  [(get-in result [:coverage :delivered]) (get-in result [:coverage :assessed])])
                          (vector? (get-in result [:coverage :missing]))
                          (every? page-by-id (get-in result [:coverage :missing]))))) :returned-assessment-binding
           :else nil)))
     calls)))

(defn- coverage-history-error
  "Every returned batch coverage must equal actual successful call history.
   Metadata cannot create delivery/assessment or conceal missing original pages."
  [calls pages]
  (loop [remaining calls delivered #{} assessed #{}]
    (when-let [c (first remaining)]
      (let [read? (and (read-call? c) (success? c))
            assessment? (and (assessment-call? c) (success? c))
            delivered-now (if read? (into delivered (delivered-ids c)) delivered)
            assessed-now (cond
                           read? (reduce disj assessed (delivered-ids c))
                           assessment? (into assessed (attempted-ids c))
                           :else assessed)
            expected {:chunks (count pages) :delivered (count delivered-now)
                      :assessed (count assessed-now)
                      :missing (filterv #(not (contains? assessed-now %)) (mapv :id pages))}
            returned? (and (success? c)
                           (or (contains? batch-tools (tool c))
                               (= "review_assess_diff_chunk" (tool c))))]
        (if (and returned? (not= expected (:coverage (output c))))
          (if (contains? batch-tools (tool c)) :batch-coverage-binding :returned-assessment-binding)
          (recur (next remaining) delivered-now assessed-now))))))

(defn last-read-violations
  "LAST predicate over delivered successful reads and every failed read/assessment
   calls and calls around resets. Never partition by host session or keep only
   the final assessment. Positions are complete host event positions."
  [calls]
  (let [last-read (into {} (for [c calls :when (read-call? c)
                                  id (if (success? c) (delivered-ids c) (attempted-ids c))]
                            [id (:position c)]))]
    (vec (for [c calls :when (assessment-call? c) id (attempted-ids c)
               :let [r (get last-read id)]
               :when (not (and r (< r (:position c))))]
           {:id id :assessment-position (:position c) :last-read-position r}))))

(defn- witnessed-stale? [calls violations]
  (some (fn [{:keys [id assessment-position last-read-position]}]
          (and last-read-position (> last-read-position assessment-position)
               (some #(and (= assessment-position (:position %)) (success? %)) calls)
               (some #(and (read-call? %) (success? %)
                           (contains? (set (delivered-ids %)) id) (> (:position %) assessment-position)) calls)
               (some #(and (read-call? %) (success? %)
                           (contains? (set (delivered-ids %)) id) (< (:position %) assessment-position)) calls)))
        violations))

(defn- begin-admitted? [calls review-calls begins]
  (let [successful (filterv success? begins) first-begin (first successful)]
    (and (= 1 (count successful)) (= first-begin (first review-calls))
         (every? (fn [c]
                   (or (= c first-begin)
                       (and (false? (:ok? (output c))) (true? (:restart-required? (output c)))
                            (> (:position c) (:position first-begin))
                            (let [prior (filterv #(< (:position %) (:position c)) calls)]
                              (witnessed-stale? prior (last-read-violations prior))))))
                 begins))))

(defn- serialized-error [submission context calls]
  (when submission
    (let [pages (:pages context) notes (into {} (for [c calls :when (and (assessment-call? c) (success? c))
                                                 item (assessment-notes c)] [(:id item) (:note item)]))
          assessments (:input-assessments submission)]
      (cond
        (not (and (map? submission) (= "open-hax.github-review/v1" (:schema submission))
                  (#{"APPROVE" "COMMENT" "REQUEST_CHANGES"} (:event submission))
                  (nonblank? (:summary submission)) (vector? (:comments submission)))) :serialized-submission-schema
        (not= (:input-source context) (:input-source submission)) :serialized-source-binding
        (not (and (vector? assessments)
                  (= (mapv #(select-keys % [:id :start :end]) pages)
                     (mapv #(select-keys % [:id :start :end]) assessments))
                  (every? #(and (nonblank? (:note %)) (= (get notes (:id %)) (:note %))) assessments)
                  (= {:chunks (count pages) :delivered (count pages) :assessed (count pages) :missing []}
                     (:input-coverage submission)))) :serialized-assessment-binding
        :else nil))))

(defn- submit-bound? [submission context submits]
  (let [call (first submits) result (output call)]
    (and (= 1 (count submits)) (success? call)
         (= (:summary submission) (:summary (input call)))
         (= (:event submission) (:event result))
         (= (:submission-file context) (:file result))
         (= (count (:comments submission)) (:inline-comments result)))))

(defn- invocation-error
  "Shared canonical guards; the caller selects a terminal reason without
   rewriting any event. Full-review verify still requires stop."
  [events submission context calls review-calls begins submits stage-calls
   stage-names violations begin began terminal-reason]
  (cond
               (not (context-valid? context)) :trusted-context-unestablished
               :else (or (event-error events (:session-id context) terminal-reason)
                         (call-error calls context)
                         (coverage-history-error calls (:pages context))
                         (when (and (seq review-calls) (not (begin-admitted? calls review-calls begins))) :begin-cardinality)
                         (when (and begin (not (and (= "deterministic" (:stage began)) (= stages (:stages began))
                                         (= (:input-source context) (:input-source began))
                                         (= (or (:end (last (:pages context))) 0) (get-in began [:diff-stats :bytes]))
                                         (= {:chunks (count (:pages context)) :delivered 0 :assessed 0
                                             :missing (mapv :id (:pages context))}
                                            (:input-coverage began))))) :begin-source-binding)
                         (when (and (some #(contains? batch-tools (tool %)) calls)
                                    (not (batch/host-matches? (:batch-host context) (:batch-host began)))) :batch-host-binding)
                         (when-not (and (<= (count stage-calls) 5)
                                         (= stage-names (vec (take (count stage-calls) stages)))
                                         (every? success? stage-calls)) :stage-order)
                         (when (> (count submits) 1) :submit-cardinality)
                         (when (and (some #(true? (:restart-required? (output %))) review-calls)
                                    (not (witnessed-stale? calls violations))) :restart-without-host-witness)
                         (serialized-error submission context calls)
                         (when (and submission (not (submit-bound? submission context submits))) :actual-submit-binding))))

(defn- input-before-first-stage?
  "All expected pages and every observed input call must precede stage one.
   No recorded stage is an unfinished prefix; empty verified input is vacuous."
  [review-calls stage-calls context]
  (if-let [first-stage (first stage-calls)]
    (let [reads (filterv read-call? review-calls)
          assessed (filterv assessment-call? review-calls)
          ids (set (map :id (:pages context)))]
      (and (= ids (set (mapcat delivered-ids reads)))
           (= ids (set (mapcat attempted-ids assessed)))
           (every? #(< (:position %) (:position first-stage)) (concat reads assessed))))
    true))

(defn verify
  "Consume shaped host events, optional actual submission, trusted full-input
   context. :stale-review-coverage is an observed cause, never retry authority.
   Missing/malformed/session/source/cardinality evidence is unestablished."
  [events submission context]
  (let [calls (vec (keep-indexed #(when (= "tool_use" (:type %2)) (assoc %2 :position (inc %1))) events))
        review-calls (filterv #(contains? all-review-tools (tool %)) calls)
        begins (filterv #(= "review_begin" (tool %)) review-calls)
        submits (filterv #(= "review_submit" (tool %)) review-calls)
        stage-calls (filterv #(= "review_record_evidence" (tool %)) review-calls)
        stage-names (mapv #(get (input %) :stage) stage-calls)
        violations (last-read-violations calls)
        begin (first (filter success? begins)) began (output begin)
        code (invocation-error events submission context calls review-calls begins
                               submits stage-calls stage-names violations begin began "stop")
        refuse (fn [kind reason] {:ok? false :reason-kind kind :code reason :violations violations})]
    (cond
      code (refuse :unestablished-review-trace code)
      (and (seq violations) (witnessed-stale? calls violations)) (refuse :stale-review-coverage :last-read-order)
      (seq violations) (refuse :unestablished-review-trace :read-assessment-not-established)
      (not (every? success? review-calls)) (refuse :unestablished-review-trace :failed-review-tool)
      (not (input-before-first-stage? review-calls stage-calls context))
      (refuse :unestablished-review-trace :stage-chronology)
      (and (nil? submission) (empty? submits))
      (refuse :missing-review-submit :healthy-unfinished-review)
      (not begin) (refuse :unestablished-review-trace :begin-not-established)
      (not= stages stage-names) (refuse :unestablished-review-trace :incomplete-five-stages)
      (not (and submission (= 1 (count submits))
                (= "review_submit" (tool (last review-calls))))) (refuse :unestablished-review-trace :submission-not-established)
      :else
      (let [submit (first submits)
            reads (filterv read-call? review-calls)
            assessed (filterv assessment-call? review-calls)
            ids (set (map :id (:pages context)))]
        (cond
          (not (and (= ids (set (mapcat delivered-ids reads)))
                    (= ids (set (mapcat attempted-ids assessed))))) (refuse :unestablished-review-trace :incomplete-page-calls)
          (not (and (< (:position begin) (:position (first stage-calls)))
                    (< (:position (last stage-calls)) (:position submit)))) (refuse :unestablished-review-trace :stage-chronology)
          (not (submit-bound? submission context submits)) (refuse :unestablished-review-trace :actual-submit-binding)
          :else {:ok? true :reason-kind nil :code :verified-review-invocation
                 :session-id (:session-id (first events)) :review-call-count (count review-calls)
                 :page-count (count ids) :violations []
                 :accepted-invocation {:session-id (:session-id (first events))
                                       :submission-call-id (get-in submit [:part :call-id])
                                       :submission-position (:position submit)
                                       :submission-file (:submission-file context)
                                       :full-input-sha256 (:full-input-sha256 context)
                                       :page-count (count ids)}})))))

(defn classify-length-ended-review
  "Classify ONLY eligibility for a new complete invocation, never acceptance.
   Context includes boundary-verified :full-input-text. Custody is caller-trusted
   completed-child evidence and response/context snapshots before/after checking;
   data cannot authenticate the caller or independently establish native source.
   No attempt bound, invocation, retention or old review credit is granted."
  [events submission context custody]
  (if-not (and (vector? events) (context-valid? context))
    {:eligible? false :classification :unestablished-length-ended-review
     :code (if (vector? events) :trusted-context-unestablished :missing-host-events)
     :violations []}
    (let [calls (vec (keep-indexed #(when (= "tool_use" (:type %2))
                                    (assoc %2 :position (inc %1))) events))
          review-calls (filterv #(contains? all-review-tools (tool %)) calls)
          begins (filterv #(= "review_begin" (tool %)) review-calls)
          submits (filterv #(= "review_submit" (tool %)) review-calls)
          stage-calls (filterv #(= "review_record_evidence" (tool %)) review-calls)
          stage-names (mapv #(get (input %) :stage) stage-calls)
          violations (last-read-violations calls)
          begin (first (filter success? begins))
          reads (filterv read-call? review-calls)
          assessed (filterv assessment-call? review-calls)
          ids (set (map :id (:pages context)))
          text (:full-input-text context)
          code (or (invocation-error events submission context calls review-calls
                                     begins submits stage-calls stage-names violations
                                     begin (output begin) "length")
                   (when-not (and (= "completed" (:invocation-state custody))
                                  (= 0 (:exit-code custody))
                                  (= "missing" (:submission-state custody)))
                     :completed-missing-submit-child-not-established)
                   (when-not (and (sha? 64 (:response-sha256 custody))
                                  (= (:response-sha256 custody)
                                     (:response-sha256-before custody)
                                     (:response-sha256-after custody))
                                  (= context (:context-before custody) (:context-after custody)))
                     :invocation-custody-changed)
                   (when (or (some? submission) (:submission-present? custody) (seq submits))
                     :submission-present)
                   (when-not (= (count events) (count (set (map #(get-in % [:part :id]) events))))
                     :host-part-cardinality)
                   (when-not (and (= 1 (count (filter #(and (= "step_finish" (:type %))
                                                          (= "length" (get-in % [:part :reason]))) events)))
                                  (every? #(contains? #{"stop" "tool-calls" "length"}
                                                     (get-in % [:part :reason]))
                                          (filter #(= "step_finish" (:type %)) events)))
                     :host-finish-reason)
                   (when (seq violations) :last-read-order)
                   (when-not (every? success? review-calls) :failed-review-tool)
                   (when-not begin :begin-not-established)
                   (when-not (false? (get-in (output begin) [:diff-stats :truncated?]))
                     :full-input-not-established)
                   (when-not (and (seq ids) (string? text)
                                  (= (count text) (:end (last (:pages context)))))
                     :full-input-not-established)
                   (when-not (and (= ids (set (mapcat delivered-ids reads)))
                                  (= ids (set (mapcat attempted-ids assessed))))
                     :incomplete-page-calls)
                   (when-not (every? (fn [c]
                                      (every? (fn [{:keys [start end text]}]
                                                (= text (subs (:full-input-text context) start end)))
                                              (if (= "review_read_diff_batch" (tool c))
                                                (:chunks (output c)) [(:chunk (output c))]))) reads)
                     :returned-page-content)
                   (when-not (seq stage-calls) :first-stage-not-established)
                   (when-not (and (< (:position begin) (:position (first stage-calls)))
                                  (every? #(< (:position %) (:position (first stage-calls)))
                                          (concat reads assessed)))
                     :input-after-first-stage)
                   (when-not (= {:chunks (count ids) :delivered (count ids)
                                 :assessed (count ids) :missing []}
                                (:coverage (output (last assessed))))
                     :complete-assessment-coverage-not-established))]
      (if code
        {:eligible? false :classification :unestablished-length-ended-review
         :code code :violations violations}
        {:eligible? true :classification :length-ended-unfinished-review
         :code :new-complete-invocation-required :violations []
         :required-action :new-complete-review-invocation
         :session-id (:session-id (first events)) :page-count (count ids)
         :recorded-stages stage-names :terminal-position (count events)
         :terminal-reason "length" :response-sha256 (:response-sha256 custody)
         :full-input-sha256 (:full-input-sha256 context)}))))

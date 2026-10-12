(ns plugins.review-pipeline
  "Evidence-first pull-request review state machine as tools.

   The reviewer drives one bounded pass by calling these tools in order:
   review_begin -> review_read_diff_chunk / review_assess_diff_chunk (all pages)
   -> review_record_evidence (per stage) -> review_propose_finding /
   review_classify_finding -> review_submit. Every call is validated by
   eta-mu.domain.review; the final submission is machine-written to
   .opencode/review-evidence/submission.json. Nothing is parsed from model
   output text."
  (:require [eta-mu.infra.review-pipeline :as infra]
            [eta-mu.dsl :refer [deftool defplugin]]))

;; ---------------------------------------------------------------------------
;; Tools
;; ---------------------------------------------------------------------------

(deftool begin
  {:id          :review/begin
   :description "Begin an evidence-first pull-request review. Verifies input-manifest.json and the complete basehead.diff in .opencode/review-evidence, indexes changed lines and returns the bounded-reader contract and staged context-source reference (path, UTF8 bytes, SHA256; explicit null for absence), never the inline context body. Read complete context with bounded HOST reads before the first stage note; check offset/limit coverage, EOF and clipping notices. Stop without submission if recovery fails. Custody metadata is not model-read or assessment proof. Call this first. Input repair belongs to the host; a failed begin ends this invocation without submission, retry or restart. After successful admission, complete one bounded review pass. A second begin requires a fresh model process."
   :args        [:map]
   :tags        #{:review}}
  [params ctx]
  (infra/begin! params ctx))

(deftool read-diff-chunk
  {:id :review/read_diff_chunk
   :description "Read one lossless bounded page of the verified immutable full diff. Read every page; this records delivery, not assessment. Pair each page read with review_assess_diff_chunk for that same page id; use the assessment result's coverage.missing to track unassessed pages. Retain the returned page content for later stages. Finish all reads of a page before assessing it. Rereading an assessed page invalidates this invocation; same-session restart or reassessment cannot repair its history."
   :args [:map [:id :int]] :tags #{:review}}
  [params ctx]
  (infra/read-diff-chunk! params ctx))

(deftool assess-diff-chunk
  {:id :review/assess_diff_chunk
   :description "Record your changed-hunk assessment of an already delivered page: changed invariants, candidate risks or why no defect is supported. Use the same page id as review_read_diff_chunk. The returned coverage distinguishes delivered from assessed pages and lists every missing assessment; continue until coverage.missing is empty. Complete all page reads and assessments before the FIRST deterministic stage note; do not reassess after stage evidence."
   :args [:map [:id :int] [:note [:string {:min 1}]]] :tags #{:review}}
  [params ctx]
  (infra/assess-diff-chunk! params ctx))

(deftool record-evidence
  {:id          :review/record_evidence
   :description "Record the evidence note for the current review stage and advance to the next stage. Complete every full-input page read and assessment BEFORE the first deterministic note; incomplete input refuses advancement. Retain page content without rereading or reassessing after stage evidence. Stages: deterministic, map-change, generate-candidates, adversarial-validate, publish. Any refusal ends this invocation; only the host owns verified bounded recovery."
   :args        [:map
                 [:stage [:enum "deterministic" "map-change" "generate-candidates" "adversarial-validate" "publish"]]
                 [:note [:string {:min 1}]]]
   :tags        #{:review}}
  [params ctx]
  (infra/record-evidence! params ctx))

(deftool propose-finding
  {:id          :review/propose_finding
   :description "Register a candidate finding. The path and line are validated against the staged diff at call time: only added lines on the PR head side are reviewable. A candidate is not a defect until classified."
   :args        [:map
                 [:id [:string {:min 1}]]
                 [:severity [:enum "critical" "high" "medium" "low"]]
                 [:category [:enum "semantic-regression" "security" "contract" "state-transition" "test-gap"]]
                 [:claim [:string {:min 1}]]
                 [:path [:string {:min 1}]]
                 [:line :int]
                 [:body [:string {:min 1}]]
                 [:confidence [:double {:min 0.0 :max 1.0}]]
                 [:blocking {:optional true} :boolean]]
   :tags        #{:review}}
  [params ctx]
  (infra/propose-finding! params ctx))

(deftool classify-finding
  {:id          :review/classify_finding
   :description "Classify a pending candidate after adversarial validation: confirmed (independently plausible failure trace), rejected (disproved), or needs-human (cannot be settled from available evidence). Legal only at adversarial-validate or publish. From generate-candidates, first record that stage's evidence note with review_record_evidence to advance into adversarial-validate; in that stage, prefer classifying candidates before recording the adversarial-validate evidence note. Candidates may still be classified at publish after that evidence note. Any failed review_* call ends this invocation; do not submit, retry, restart, or make further review calls."
   :args        [:map
                 [:id [:string {:min 1}]]
                 [:status [:enum "confirmed" "rejected" "needs-human"]]
                 [:rationale [:string {:min 1}]]]
   :tags        #{:review}}
  [params ctx]
  (infra/classify-finding! params ctx))

(deftool status
  {:id          :review/status
   :description "Read the current review session: stage, evidence count, diff stats, and every candidate with its classification."
   :args        [:map]
   :tags        #{:review}}
  [params ctx]
  (infra/status! params ctx))

(deftool submit
  {:id          :review/submit
   :description "Finish the review. Every full-input page must be delivered and assessed, every candidate classified and every stage evidenced. The review event (APPROVE, COMMENT, REQUEST_CHANGES) is derived from confirmed findings by law. On success the machine-readable submission is written to .opencode/review-evidence/submission.json for the deterministic publisher."
   :args        [:map
                 [:summary [:string {:min 1}]]]
   :tags        #{:review}}
  [params ctx]
  (infra/submit! params ctx))

(defplugin plugin {:id :eta-mu/review-pipeline}
  begin
  read-diff-chunk
  assess-diff-chunk
  record-evidence
  propose-finding
  classify-finding
  status
  submit)

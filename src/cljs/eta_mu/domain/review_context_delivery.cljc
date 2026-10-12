(ns eta-mu.domain.review-context-delivery
  "Context custody and genuine HOST delivery decisions. Delivery is not
   assessment or caller authentication. GPL-3.0-or-later."
  (:require [clojure.string :as str]
            [eta-mu.law.review-context :as law]
            [eta-mu.shape.review-context-delivery :as shape]))

(defn- safe-natural? [x] (and (integer? x) (<= 0 x 9007199254740991)))
(defn- positive? [x] (and (safe-natural? x) (pos? x)))
(defn- absolute? [path] (and (string? path) (str/starts-with? path "/")))
(defn context-valid? [context]
  (let [{:keys [context-source context-path context-lines context-line-bytes]} context]
    (and (contains? context :context-source)
         (law/delivery-input-valid? {:source context-source :path context-path
                                    :lines context-lines :line-bytes context-line-bytes})
         (absolute? context-path)
         (= (count context-lines) (count context-line-bytes))
         (if context-source (= context-path (:path context-source))
           (and (empty? context-lines) (empty? context-line-bytes))))))

(defn- custody-error [calls submission source]
  (or (some (fn [call]
              (let [result (get-in call [:part :state :output])
                    status? (= "review_status" (get-in call [:part :tool]))]
                (when (or (true? (:ok? result))
                          (contains? result :context-protocol)
                          (contains? result :context-observation))
                  (when-not (and (= law/protocol (:context-protocol result))
                                 (contains? result :context-source)
                                 (= source (:context-source result))
                                 (if status? (false? (:context-custody-refused? result))
                                   (:ok? (law/custody-decision source (:context-observation result)))))
                    :context-custody-unestablished)))) calls)
      (when (and submission
                 (not (and (= law/protocol (:context-protocol submission))
                           (contains? submission :context-source)
                           (= source (:context-source submission)))))
        :serialized-context-binding)))

(defn- ancillary-valid? [output expected loaded]
  (and (string? output) (vector? loaded)
       (every? #(and (absolute? %) (not (str/blank? %))) loaded)
       (if (empty? loaded) (= output expected)
         (and (str/starts-with? output expected)
              (boolean (re-matches #"\n\n<system-reminder>\n[\s\S]*\n</system-reminder>"
                                   (subs output (count expected))))))))

(defn- read-evidence [call context first-stage]
  (let [state (get-in call [:part :state]) args (:input state)
        offset (if (contains? args :offset) (:offset args) 1)
        limit (if (contains? args :limit) (:limit args) 2000)
        lines (:context-lines context) total (count lines)
        time (:time state) stage-start (get-in first-stage [:part :state :time :start])]
    (when (and (= (:context-path context) (:filePath args))
               (positive? offset) (positive? limit)
               (or (<= offset total) (and (= total 0) (= offset 1)))
               (< (:position call) (:position first-stage))
               (safe-natural? (:start time)) (safe-natural? (:end time))
               (<= (:start time) (:end time)) (safe-natural? stage-start)
               (<= (:end time) stage-start))
      (let [expected (shape/read-rendering (:context-path context) lines
                                            (:context-line-bytes context) offset limit)
            metadata (:metadata state)]
        (when (and (not (:clipped? expected))
                   (= (:display expected) (:display metadata))
                   (= (:preview expected) (:preview metadata))
                   (= (:truncated expected) (:truncated metadata))
                   (ancillary-valid? (:output state) (:output expected) (:loaded metadata)))
          (select-keys expected [:line-ids :eof?]))))))

(defn inspect
  "Return refusal or inspectable complete coverage. Reads only count when the
   exact returned output/metadata and completed interval precede stage one."
  [events submission context]
  (if-not (vector? events) {:error :missing-host-events}
   (let [calls (vec (keep-indexed #(when (= "tool_use" (:type %2))
                                   (assoc %2 :position (inc %1))) events))
        review-calls (filterv #(str/starts-with? (or (get-in % [:part :tool]) "") "review_") calls)
        first-stage (first (filter #(= "review_record_evidence" (get-in % [:part :tool])) calls))
        source (:context-source context)]
    (cond
      (not (context-valid? context)) {:error :trusted-context-unestablished}
      (custody-error review-calls submission source)
      {:error (custody-error review-calls submission source)}
      (nil? source) {:coverage {:status :absent :total-lines 0 :delivered-lines 0 :eof? false}}
      (nil? first-stage) {:coverage {:status :present :total-lines (count (:context-lines context))
                                    :delivered-lines 0 :eof? false}}
      :else
      (let [reads (filterv #(and (= "read" (get-in % [:part :tool]))
                                 (= (:context-path context) (get-in % [:part :state :input :filePath]))) calls)
            evidence (mapv #(read-evidence % context first-stage) reads)
            ids (set (mapcat :line-ids evidence)) total (count (:context-lines context))
            eof? (boolean (some :eof? evidence))]
        (if (and (seq evidence) (every? some? evidence)
                 (= ids (set (range 1 (inc total)))) eof?)
          {:coverage {:status :present :total-lines total :delivered-lines (count ids) :eof? true}}
          {:error :context-read-coverage-unestablished}))))))

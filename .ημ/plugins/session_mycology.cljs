(ns plugins.session-mycology
  "Per-turn retrospection with p-scores, skill-spore incubation, and promotion
   of recurring spores into LIVE skills on the real skill search path
   (~/.agents/skills/<slug>/{SKILL.md,CONTRACT.edn}).

   Promotion is executed by the tool/hook itself — the way the legacy pi-era
   TS extension (pi/agent/extensions/session-mycology.ts) meant it — and each
   promotion is fanned out to the workspace memory channels so future
   instances inherit the lesson:

     1. events.edn               append-only global promotion log (always on)
     2. ~/.agents git push       fork-tax distribution to every machine
     3. OpenPlanner graph        edge upsert to local :7777 AND the cloud
                                 (https://knoxx.promethean.rest) — the brain
     4. mongo skill_lessons      ETA_MU_MONGO_URI (default local mongod)
     5. session-mycology actor   muse-visible ledger notification

   Every channel is best-effort, timeout-bounded, and individually killable
   via ETA_MU_MYCOLOGY_NO_GIT / _NO_GRAPH / _NO_MONGO / _NO_LEDGER (any
   non-blank value). Other knobs:
     ETA_MU_MYCOLOGY_SKILLS_DIR       skills target (default ~/.agents/skills;
                                      a custom value disables git-publish)
     ETA_MU_MYCOLOGY_GRAPH_URLS       csv override of graph targets
     ETA_MU_MYCOLOGY_CLOUD_GRAPH_URL  override of the cloud graph target
     OPENPLANNER_BASE_URL / _API_KEY  local graph target + auth
     ETA_MU_MONGO_URI / ETA_MU_MONGO_DB  mongo sink (db default eta_mu)

   Pure logic in eta-mu.domain.mycology; fs/proc via the node boundaries."
  (:require [clojure.string :as str]
            [eta-mu.actor :as actor]
            [eta-mu.boundaries.fetch :as http]
            [eta-mu.boundaries.mongo.client :as mongo]
            [eta-mu.boundaries.node.fs :as bfs]
            [eta-mu.boundaries.node.proc :as proc]
            [eta-mu.domain.mycology :as myco]
            [eta-mu.dsl :refer [deftool defhook defplugin]]
            [promesa.core :as p]))

(defn- state-dir [] (bfs/state-dir "session-mycology"))
(defn- reflections-file [] (bfs/join (state-dir) "turn-reflections.jsonl"))
(defn- spores-file [] (bfs/join (state-dir) "skill-spores.jsonl"))
(defn- promotions-file [] (bfs/join (state-dir) "skill-promotions.jsonl"))
(defn- drafts-dir [] (bfs/join (state-dir) "spores"))
(defn- events-file [] (bfs/join (state-dir) "events.edn"))
(defn- skills-dir []
  (or (bfs/env "ETA_MU_MYCOLOGY_SKILLS_DIR")
      (bfs/join (bfs/home-dir) ".agents" "skills")))
(defn- agents-repo [] (bfs/join (bfs/home-dir) ".agents"))

(defn- off?
  "Channel kill-switch: any non-blank value disables the channel."
  [name] (some? (bfs/env name)))

(defn- promotion-opts []
  {:min-recurrence (or (some-> (bfs/env "ETA_MU_MYCOLOGY_PROMOTION_MIN_RECURRENCE")
                               js/parseInt)
                       myco/default-promotion-min-recurrence)
   :hint-p (or (some-> (bfs/env "ETA_MU_MYCOLOGY_PROMOTION_HINT_P")
                       js/parseFloat)
               myco/default-promotion-hint-p)})

(defn- with-timeout
  "Resolve `v` (value or promise) within `ms`, else resolve to `timeout-val`.
   Argument order suits `->` threading. The underlying work is never
   cancelled — this only bounds how long the fan-out waits before reporting
   a timeout and moving on."
  [v ms timeout-val]
  (p/race [(p/promise v) (p/delay ms timeout-val)]))

;; ---------------------------------------------------------------------------
;; Spore ledger queries
;; ---------------------------------------------------------------------------

(defn- recent-spores [directory limit]
  (->> (bfs/read-jsonl (spores-file) 400)
       (filter #(or (nil? directory) (= (:cwd %) directory)))
       (take-last limit)
       reverse
       vec))

(defn- latest-spore-global
  "Latest spore for `slug` across ALL cwds. Recurrence is a cross-project
   signal: the same pattern biting in three repos is stronger than three
   occurrences in one."
  [slug]
  (->> (bfs/read-jsonl (spores-file) 1200)
       (filter #(= (:slug %) slug))
       last))

(defn- latest-spores-by-slug [directory]
  (->> (bfs/read-jsonl (spores-file) 1200)
       (filter #(and (:slug %)
                     (or (nil? directory) (= (:cwd %) directory))))
       (reduce (fn [acc spore] (assoc acc (:slug spore) spore)) {})
       vals
       vec))

;; ---------------------------------------------------------------------------
;; Distribution channels — all best-effort, never throw
;; ---------------------------------------------------------------------------

(def ^:private producer
  {:package/name    "@eta-mu/session-mycology"
   :package/version "0.2.0"
   :command         "opencode-plugin"})

(defn- envelope
  "Session-mycology package event envelope. Authority:
   eta-mu.session-mycology.law.reflection (packages/session-mycology). The
   bundle re-emits the shape as plain data so the plugin need not carry the
   package on its classpath; schema ids and versions must stay in sync with
   that law."
  [schema-id version subject payload]
  {:event/id          (str (random-uuid))
   :event/type        (keyword "session-mycology" (name schema-id))
   :event/recorded-at (bfs/now-iso)
   :event/schema      {:id schema-id :version version}
   :event/producer    producer
   :event/subject     subject
   :event/payload     payload})

(defn- journal-event!
  "Channel 0 (always on): append a package-shaped event envelope to the
   global EDN journal. The mnemosyne ingester transcribes this journal into
   the clio brain ledger."
  [env]
  (try
    (bfs/append-line! (events-file) (pr-str env))
    {:ok true}
    (catch :default e {:ok false :error (str e)})))

(defn- record-event!
  "Journal the promotion as a promotion-recorded package event."
  [event]
  (journal-event!
   (envelope :eta-mu.session-mycology/promotion-recorded 1
             (str "skill:" (:skill/slug event))
             (cond-> {:slug          (:skill/slug event)
                      :skill-path    (get-in event [:skill/paths :skill])
                      :contract-path (get-in event [:skill/paths :contract])
                      :recurrence    (or (:skill/recurrence event) 1)
                      :cwd           (or (:source-cwd event) "")}
               (:lesson event)      (assoc :lesson (:lesson event))
               (:better-path event) (assoc :better-path (:better-path event))))))

(defn- publish-skill-now!
  "Path-scoped commit + push of the promoted skill to the ~/.agents remote.
   Stages and commits ONLY skills/<slug> — concurrent agents' unrelated dirt
   is never swept in. A non-fast-forward push gets one pull --rebase retry."
  [event]
  (let [slug (:skill/slug event)
        repo (agents-repo)
        relpath (str "skills/" slug)
        msg (str "mycology: promote " slug
                 " (recurrence " (:skill/recurrence event) ")")]
    (p/let [add (proc/run! ["git" "add" "--" relpath] {:cwd repo})]
      (if-not (:ok add)
        {:ok false :stage "add" :error (:err add)}
        (p/let [commit (proc/run! ["git" "commit" "-m" msg "--" relpath]
                                  {:cwd repo})]
          (cond
            (:ok commit)
            (p/let [push (proc/run! ["git" "push" "origin" "HEAD"] {:cwd repo})]
              (if (:ok push)
                {:ok true :stage "push"}
                (if (re-find #"fetch first|non-fast-forward|rejected" (str (:err push)))
                  (p/let [rebase (proc/run! ["git" "pull" "--rebase"] {:cwd repo})]
                    (if-not (:ok rebase)
                      {:ok false :stage "rebase" :error (:err rebase)}
                      (p/let [retry (proc/run! ["git" "push" "origin" "HEAD"] {:cwd repo})]
                        {:ok (:ok retry) :stage "push-retry"
                         :error (when-not (:ok retry) (:err retry))})))
                  {:ok false :stage "push" :error (:err push)})))

            (re-find #"nothing to commit" (str (:out commit) (:err commit)))
            {:ok true :stage "commit" :skipped "nothing-to-commit"}

            :else
            {:ok false :stage "commit" :error (:err commit)}))))))

(defonce ^:private git-chain* (atom (p/resolved nil)))

(defn- publish-skill!
  "Channel 1, serialized: git operations against the shared ~/.agents repo
   queue on a per-process promise chain, so a sweep promoting ten spores
   never races index.lock."
  [event]
  (cond
    (off? "ETA_MU_MYCOLOGY_NO_GIT")
    (p/resolved {:ok false :skipped "disabled"})

    (bfs/env "ETA_MU_MYCOLOGY_SKILLS_DIR")
    (p/resolved {:ok false :skipped "custom-skills-dir"})

    (not (bfs/exists? (bfs/join (agents-repo) ".git")))
    (p/resolved {:ok false :skipped "no-repo"})

    :else
    (let [run (atom nil)]
      (swap! git-chain*
             (fn [chain]
               (let [next (-> chain
                              (p/catch (fn [_] nil))
                              (p/then (fn [_] (publish-skill-now! event)))
                              (with-timeout 30000 {:ok false :error "git-timeout"})
                              (p/catch (fn [e] {:ok false :error (str e)})))]
                 (reset! run next)
                 ;; Keep the chain alive even if THIS call's result fails.
                 (p/catch next (fn [_] nil)))))
      @run)))

(defn- graph-targets []
  (if-let [csv (bfs/env "ETA_MU_MYCOLOGY_GRAPH_URLS")]
    (into [] (comp (map str/trim) (remove str/blank?)) (str/split csv #","))
    ;; Default: the local graph only. A cloud graph target is opt-in via
    ;; ETA_MU_MYCOLOGY_CLOUD_GRAPH_URL — as of 2026-08-28 no public
    ;; OpenPlanner API route exists on knoxx.promethean.rest (that hostname
    ;; serves the SPA; POST /v1/* 405s at nginx).
    (cond-> [(or (bfs/env "OPENPLANNER_BASE_URL") "http://127.0.0.1:7777")]
      (bfs/env "ETA_MU_MYCOLOGY_CLOUD_GRAPH_URL")
      (conj (bfs/env "ETA_MU_MYCOLOGY_CLOUD_GRAPH_URL")))))

(defn- post-graph! [url api-key body]
  (-> (http/post-json! (str (str/replace (str url) #"/+$" "") "/v1/graph/edges/upsert")
                       {:headers {"Authorization" (str "Bearer " api-key)
                                  "X-Tenant-ID"   "eta-mu"}
                        :body body})
      (with-timeout 5000 {:ok false :status 0 :body {:raw "graph-timeout"}})
      (p/catch (fn [e] {:ok false :error (str e)}))))

(defn- graph-distribute!
  "Channel 2: upsert the lesson into every configured OpenPlanner graph."
  [event]
  (cond
    (off? "ETA_MU_MYCOLOGY_NO_GRAPH")
    (p/resolved {:ok false :skipped "disabled"})

    :else
    (if-let [api-key (bfs/env "OPENPLANNER_API_KEY")]
      (p/let [body    (myco/lesson-graph-body event "eta-mu")
              results (p/all (mapv (fn [url]
                                     (p/then (post-graph! url api-key body)
                                             #(assoc % :url url)))
                                   (graph-targets)))]
        {:ok (boolean (some :ok results)) :targets results})
      (p/resolved {:ok false :skipped "no-api-key"}))))

(defn- mongo-uri []
  (or (bfs/env "ETA_MU_MONGO_URI")
      ;; Fall back to the workspace's mongo root credentials against the local
      ;; docker mongod, so the channel works without dedicated configuration.
      (when-let [user (bfs/env "MONGODB_ROOT_USERNAME")]
        (when-let [pass (bfs/env "MONGODB_ROOT_PASSWORD")]
          (str "mongodb://" (js/encodeURIComponent user) ":" (js/encodeURIComponent pass)
               "@127.0.0.1:27017/?directConnection=true&authSource=admin")))
      mongo/default-uri))

(defn- mongo-record!
  "Channel 3: insert the promotion event into the skill_lessons collection."
  [event]
  (if (off? "ETA_MU_MYCOLOGY_NO_MONGO")
    (p/resolved {:ok false :skipped "disabled"})
    (-> (p/let [uri     (mongo-uri)
                db-name (or (bfs/env "ETA_MU_MONGO_DB") "eta_mu")
                conn    (mongo/connect! uri db-name {:timeout-ms 2000})
                coll    (.collection ^js (:db conn) "skill_lessons")
                _       (.insertOne coll (clj->js (assoc event :recorded-at (bfs/now-iso))))]
          (mongo/close! conn)
          {:ok true})
        (with-timeout 6000 {:ok false :error "mongo-timeout"})
        (p/catch (fn [e] {:ok false :error (str e)})))))

(defn- ledger-notify!
  "Channel 4: notify the session-mycology actor so muses and future sessions
   see the promotion through the actor ledger system."
  [event]
  (if (off? "ETA_MU_MYCOLOGY_NO_LEDGER")
    (p/resolved {:ok false :skipped "disabled"})
    (-> (p/let [existing (actor/actor-meta "session-mycology")
                _        (when-not existing
                           (actor/spawn! "session-mycology" {:kind :mycology}))
                eid      (actor/tell! "session-mycology" "session-mycology"
                                      "skill.promoted" event)]
          {:ok true :event-id eid})
        (with-timeout 5000 {:ok false :error "ledger-timeout"})
        (p/catch (fn [e] {:ok false :error (str e)})))))

(defn- distribute!
  "Fan a promotion out to every memory channel. Never rejects."
  [spore promotion]
  (let [event (myco/promotion-event spore promotion)
        local (record-event! event)]
    (-> (p/let [git    (publish-skill! event)
                graph  (graph-distribute! event)
                mdb    (mongo-record! event)
                ledger (ledger-notify! event)]
          {:events-edn local :git git :graph graph :mongo mdb :ledger ledger})
        (p/catch (fn [e] {:error (str e)})))))

(defn- summarize-distribution [d]
  (into {}
        (map (fn [[k v]] [k (select-keys v [:ok :skipped :error :stage])]))
        d))

;; ---------------------------------------------------------------------------
;; Promotion
;; ---------------------------------------------------------------------------

(defn- promote-spore!
  "Write a live skill from an eligible spore onto the real skill search path.
   Idempotent: existing SKILL.md/CONTRACT.edn are never overwritten."
  [spore]
  (if-not (myco/promotion-eligible? spore (promotion-opts))
    {:slug (:slug spore) :promoted false :eligible false}
    (let [dir           (bfs/join (skills-dir) (:slug spore))
          skill-path    (bfs/join dir "SKILL.md")
          contract-path (bfs/join dir "CONTRACT.edn")
          created-skill (when-not (bfs/exists? skill-path)
                          (bfs/write-text! skill-path (myco/live-skill spore))
                          true)
          created-contract (when-not (bfs/exists? contract-path)
                             (bfs/write-text! contract-path (myco/live-contract spore))
                             true)]
      (when (or created-skill created-contract)
        (bfs/append-jsonl! (promotions-file)
                           {:ts (bfs/now-iso)
                            :slug (:slug spore)
                            :name (:name spore)
                            :recurrence (:recurrence spore)
                            :skillCandidateP (:skillCandidateP spore)
                            :cwd (:cwd spore)
                            :skillPath skill-path
                            :contractPath contract-path}))
      {:slug (:slug spore)
       :promoted (boolean (or created-skill created-contract))
       :eligible true
       :skillPath skill-path
       :contractPath contract-path})))

(defn- promote-and-distribute! [spore]
  (let [result (promote-spore! spore)]
    (if-not (:promoted result)
      (p/resolved result)
      (p/let [d (distribute! spore result)]
        (assoc result :distribution (summarize-distribution d))))))

;; ---------------------------------------------------------------------------
;; Actions
;; ---------------------------------------------------------------------------

(defn- list-recent [ctx]
  {:action "list_recent"
   :spores (recent-spores (:directory ctx) 5)})

(defn- promote [{:keys [slug]} ctx]
  (let [target     (str/trim (str (or slug "all")))
        all?       (= (str/lower-case target) "all")
        directory  (when-not all? (:directory ctx))
        candidates (cond->> (latest-spores-by-slug directory)
                     (not all?) (filter #(= (:slug %) (myco/slugify target))))]
    (p/let [results (p/all (mapv promote-and-distribute! candidates))]
      {:action "promote"
       :target target
       :considered (count results)
       :promoted (vec (filter :promoted results))
       :skipped (vec (remove :promoted results))})))

(defn- reflect [params ctx]
  (let [now        (bfs/now-iso)
        directory  (:directory ctx)
        reflection {:ts now
                    :cwd directory
                    :session (:session/id ctx)
                    :agent (:agent ctx)
                    :efficiency-p (myco/clamp-01 (:efficiencyP params) 0.5)
                    :friction-p (myco/clamp-01 (:frictionP params) 0.5)
                    :skill-candidate-p (myco/clamp-01 (:skillCandidateP params) 0.5)
                    :lesson (str/trim (str (or (:lesson params) "")))
                    :betterPath (str/trim (str (or (:betterPath params) "")))}
        _          (bfs/append-jsonl! (reflections-file)
                                      (assoc reflection
                                             :efficiencyP (:efficiency-p reflection)
                                             :frictionP (:friction-p reflection)
                                             :skillCandidateP (:skill-candidate-p reflection)))
        cand-name  (str/trim (str (or (:candidateName params) "")))
        cand-desc  (str/trim (str (or (:candidateDescription params) "")))
        incubate?  (and (seq cand-name) (seq cand-desc)
                        (myco/incubate? reflection))
        _          (when (seq (:lesson reflection))
                     (journal-event!
                      (envelope :eta-mu.session-mycology/reflection-recorded 1
                                (str "repo:" (or directory ""))
                                (cond-> {:repo   (str (or directory ""))
                                         :lesson (:lesson reflection)}
                                  (:session reflection)
                                  (assoc :session/id (:session reflection))))))]
    (if-not incubate?
      {:action "reflect"
       :recorded true
       :incubated false
       :reflection (select-keys reflection
                                [:ts :efficiency-p :friction-p :skill-candidate-p])}
      (let [slug  (myco/slugify cand-name)
            prior (latest-spore-global slug)
            spore {:ts now
                   :name cand-name
                   :slug slug
                   :description cand-desc
                   :reuseScope (myco/normalize-reuse-scope (:reuseScope params))
                   :cwd directory
                   :reflectionTs now
                   :reflectionKind (myco/reflection-kind reflection)
                   :recurrence (max 1 (inc (long (or (:recurrence prior) 0))))
                   :efficiencyP (:efficiency-p reflection)
                   :frictionP (:friction-p reflection)
                   :skillCandidateP (:skill-candidate-p reflection)
                   :lesson (:lesson reflection)
                   :betterPath (:betterPath reflection)}
            draft-path (or (:draftPath prior)
                           (bfs/join (drafts-dir) (str slug ".md")))
            spore (assoc spore :draftPath draft-path)]
        (bfs/write-text! draft-path (myco/spore-draft reflection spore))
        (bfs/append-jsonl! (spores-file) spore)
        (journal-event!
         (envelope :eta-mu.session-mycology/spore-incubated 1
                   (str "skill:" slug)
                   (cond-> {:slug        slug
                            :name        cand-name
                            :description cand-desc
                            :recurrence  (:recurrence spore)
                            :cwd         (str (or directory ""))
                            :reuse-scope (:reuseScope spore)
                            :p-scores    {:efficiency (:efficiency-p reflection)
                                          :friction   (:friction-p reflection)
                                          :candidate  (:skill-candidate-p reflection)}}
                     (seq (:lesson reflection))
                     (assoc :lesson (:lesson reflection))
                     (seq (:betterPath reflection))
                     (assoc :better-path (:betterPath reflection)))))
        (p/let [promotion (promote-and-distribute! spore)]
          {:action "reflect"
           :recorded true
           :incubated true
           :spore (select-keys spore [:name :slug :recurrence :draftPath :reuseScope])
           :promotion promotion})))))

;; ---------------------------------------------------------------------------
;; Session-end sweep hook
;; ---------------------------------------------------------------------------

(defn- sweep!
  "Mechanical end-of-session sweep over ALL spores (every cwd): promote
   whatever has become eligible, distribute whatever is newly promoted.
   Judgment happened at reflect time; the sweep is pure mechanics."
  []
  (-> (p/let [candidates (latest-spores-by-slug nil)
              results    (p/all (mapv promote-and-distribute! candidates))]
        {:considered (count results)
         :promoted   (mapv :slug (filter :promoted results))})
      (p/catch (fn [e] {:error (str e)}))))

(defhook mycology-session-sweep
  {:id       :session/mycology-sweep
   :event    :session/closed
   :priority 10}
  [_input _output]
  ;; Fire-and-forget: never block or reject session teardown on mycology I/O.
  (sweep!)
  nil)

;; ---------------------------------------------------------------------------
;; Tool
;; ---------------------------------------------------------------------------

(deftool session-mycology
  {:id          :session/mycology
   :description "Record a per-turn retrospective with p-scores (efficiency, friction, skill-candidate) and incubate reusable skill spores when work felt harder than it should have. action=reflect near the end of a substantive turn; action=list_recent to inspect recent spores; action=promote to promote recurring spores into live skills on the real search path (~/.agents/skills) with distribution to the graph/mongo/ledger memory channels (optional slug, default all eligible)."
   :args        [:map
                 [:action [:enum "reflect" "list_recent" "promote"]]
                 [:efficiencyP {:optional true} :number]
                 [:frictionP {:optional true} :number]
                 [:skillCandidateP {:optional true} :number]
                 [:lesson {:optional true} :string]
                 [:betterPath {:optional true} :string]
                 [:candidateName {:optional true} :string]
                 [:candidateDescription {:optional true} :string]
                 [:reuseScope {:optional true} [:enum "turn" "session" "multi-session"]]
                 [:slug {:optional true} :string]]
   :tags        #{:mycology :retrospective :skills}}
  [{:keys [action] :as params} ctx]
  (case action
    "list_recent" (list-recent ctx)
    "promote"     (promote params ctx)
    "reflect"     (reflect params ctx)))

(defplugin plugin {:id :eta-mu/session-mycology}
  session-mycology
  mycology-session-sweep)

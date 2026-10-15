;; GPL-3.0-or-later. Reproduce diagnostic wires in an owned private directory.
;; No model, production batching API, host reconfiguration or publication.
(ns extern.verify-review-batch-profile
  (:require [clojure.string :as str]
            [nbb.core :as nbb]
            [eta-mu.fixtures.review-batch-profile :as fixture]
            ["node:fs" :as fs]
            ["node:path" :as path]
            ["node:crypto" :as crypto]
            ["node:module" :as modules]))

(defn utf8-bytes [s] (js/Buffer.byteLength s "utf8"))
(defn sha [value] (.digest (.update (crypto/createHash "sha256") value) "hex"))
(defn json [v] (js/JSON.stringify (clj->js v)))
(defn metrics [wire]
  {:bytes (utf8-bytes wire) :lines (count (str/split wire #"\n" -1)) :sha256 (sha wire)})
(defn decode [file] (js->clj (js/JSON.parse (fs/readFileSync file "utf8")) :keywordize-keys true))
(defn write! [out name value]
  (fs/writeFileSync (path/join out name) (str (json value) "\n") #js {:mode 384}))

(def zod-identity
  {:schema "regular-file-tree-sha256/1" :name "zod" :version "4.4.3"
   :files 718 :bytes 4558122
   :sha256 "4e9594f99b06cc281e1f38c5319c6442ba017073f533fb526c1ba9ac628c4cd1"})

(defn capture-zod [root]
  ;; Hash captured regular bytes, then execute copies of those same bytes.
  ;; Relative slash paths sort lexically; records are path NUL bytes NUL SHA LF.
  (let [captured (atom [])
        total (atom 0)
        walk (fn walk [dir prefix]
               (doseq [name (js->clj (fs/readdirSync dir))]
                 (let [file (path/join dir name)
                       relative (str prefix name)
                       stat (fs/lstatSync file)]
                   (cond
                     (.isDirectory stat) (walk file (str relative "/"))
                     (.isFile stat)
                     (do
                       (assert (and (< (count @captured) (:files zod-identity))
                                    (<= (+ @total (.-size stat)) (:bytes zod-identity)))
                               "Zod package exceeds qualified file/byte bounds.")
                       (let [bytes (fs/readFileSync file)]
                         (swap! total + (.-length bytes))
                         (assert (<= @total (:bytes zod-identity)) "Zod package exceeds qualified byte bound.")
                         (swap! captured conj {:path relative :bytes bytes :sha256 (sha bytes)})))
                     :else (assert false "Zod package must contain only directories and regular files.")))))]
    (walk root "")
    (let [files (vec (sort-by :path @captured))
          digest (sha (apply str (map #(str (:path %) "\u0000" (.-length (:bytes %))
                                           "\u0000" (:sha256 %) "\n") files)))]
      (assert (= [(:files zod-identity) (:bytes zod-identity) (:sha256 zod-identity)]
                 [(count files) @total digest]) "Zod package content identity differs from qualified package.")
      files)))

(defn materialize-zod! [root files]
  (doseq [{:keys [path bytes]} files]
    (let [file (path/join root path)]
      (fs/mkdirSync (path/dirname file) #js {:recursive true :mode 448})
      (fs/writeFileSync file bytes #js {:mode 384 :flag "wx"}))))

(defn repository-destination? [directory]
  (let [exists? #(some? (fs/lstatSync % #js {:throwIfNoEntry false}))]
    (loop [at directory]
      (if (or (exists? (path/join at ".git"))
              (and (exists? (path/join at "HEAD"))
                   (exists? (path/join at "objects"))
                   (exists? (path/join at "refs"))))
        true
        (let [parent (path/dirname at)]
          (if (= at parent) false (recur parent)))))))

(defn session-measurement [value]
  {:bytes (utf8-bytes value) :json-bytes (utf8-bytes (json value))
   :nonblank? (not (str/blank? value))})

(assert (= "v22.20.0" (.-version js/process)) "Qualified reproduction requires Node22.20.0.")
(assert (= "1.3.204" (nbb/version)) "Qualified reproduction requires NBB1.3.204.")
(assert (even? (count *command-line-args*)) "Arguments are flag/value pairs.")
(let [opts (apply hash-map *command-line-args*)
      required #{"--corpus" "--manifest" "--bundle" "--zod" "--out"}
      _ (assert (= required (set (keys opts)))
                "Required: --corpus FILE --manifest FILE --bundle FILE --zod DIRECTORY --out NEW_PRIVATE_DIRECTORY")
      corpus (fs/readFileSync (get opts "--corpus"))
      manifest (decode (get opts "--manifest"))
      original (fs/readFileSync (get opts "--bundle"))
      zod (fs/realpathSync (get opts "--zod"))
      requested-out (path/resolve (get opts "--out"))
      out (path/join (fs/realpathSync (path/dirname requested-out)) (path/basename requested-out))
      repo (path/resolve (path/dirname (fs/realpathSync (nbb/invoked-file))) "../..")
      _ (assert (and (not= repo out) (not (str/starts-with? out (str repo path/sep))))
                "Protected corpus wires must be written outside this checkout.")
      _ (assert (not (repository-destination? (path/dirname out)))
                "Protected corpus output must be outside every Git worktree or repository.")
      _ (assert (not (fs/existsSync out)) "Output must be a new owned directory.")
      _ (assert (= "9902552ea853a30fd20ec1d35e980416406ae2b161217017f58480ba324056ff" (sha original))
                "Only the exact qualified original compiled bundle is executable.")
      _ (assert (= "c89f700130fb0d977138c13597cf2b650bc7aea1f3620d1b1d0cad3bb75b4248" (sha corpus)
                   (get-in manifest [:full_diff :sha256])) "Frozen full corpus differs.")
      _ (assert (= 11840401 (.-length corpus) (get-in manifest [:full_diff :bytes])))
      _ (assert (= "3ab5ffbb6e34a2ee3607a74bf421785d47d5391d" (:head_sha manifest)))
      _ (assert (= "f95a53a4da8ed90588b0be320ba8e86c6a03668d" (:base_sha manifest) (:diff_base_sha manifest)))
      _ (assert (= {:path "basehead.diff" :bytes 11840401
                    :sha256 "c89f700130fb0d977138c13597cf2b650bc7aea1f3620d1b1d0cad3bb75b4248"}
                   (:full_diff manifest)) "Serialized full_diff metadata must match the frozen source exactly.")
      zod-files (capture-zod zod)
      _ (fs/mkdirSync out #js {:mode 448})
      _ (fs/mkdirSync (path/join out "node_modules") #js {:mode 448})
      _ (materialize-zod! (path/join out "node_modules/zod") zod-files)
      bridge-file (path/join out "qualified-test-bridge.mjs")
      suffix (str "\n// Private test exports; qualified original prefix unchanged.\n"
                  "export function profileEncode(v){return eta_mu.boundaries.opencode.encode_result(cljs.core.js__GT_clj(v,cljs.core.keyword('keywordize-keys'),true));}\n"
                  "export function profilePages(t){return cljs.core.clj__GT_js(eta_mu.domain.review.diff_chunks(t));}\n"
                  "export function profileContext(s){let o=cljs.core.js__GT_clj({source:s},cljs.core.keyword('keywordize-keys'),true);o=cljs.core.assoc(o,cljs.core.keyword('status'),cljs.core.keyword('present'));return cljs.core.clj__GT_js(cljs.core.assoc(eta_mu.infra.review_pipeline.observed_fields(o),cljs.core.keyword('restart-required?'),false));}\n")
      _ (fs/writeFileSync bridge-file (js/Buffer.concat #js [original (js/Buffer.from suffix)]) #js {:mode 384})
      load (modules/createRequire (path/join out "fixture-driver.cjs"))
      bridge (load bridge-file)
      encode (fn [v] (.profileEncode bridge (clj->js v)))
      text (.decode (js/TextDecoder. "utf-8" #js {:fatal true}) corpus)
      original-pages (js->clj (.profilePages bridge text) :keywordize-keys true)
      _ (assert (= text (apply str (map :text original-pages))))
      _ (assert (= 1491 (count original-pages)))
      pages (mapv #(assoc % :sha256 (sha (:text %))) original-pages)
      ids (mapv :id pages)
      profile-id "muse-review-batch-profile/2026-10-15-candidate-v2"
      context-fields (fn [source]
                       (js->clj (.profileContext bridge (clj->js source)) :keywordize-keys true))
      context-source {:path "/profile-fixture/x" :bytes 1 :sha256 (sha "x")}
      context-base-bytes (utf8-bytes (encode (context-fields context-source)))
      context-padding (quot (- (:context-metadata-bytes fixture/profile) context-base-bytes) 2)
      context-source (update context-source :path str (apply str (repeat context-padding "p")))
      context (context-fields context-source)
      context-bytes (utf8-bytes (encode context))
      over-context (context-fields (update context-source :path str "p"))
      over-context-bytes (utf8-bytes (encode over-context))
      _ (assert (= 4096 context-bytes))
      _ (assert (= :accept (:outcome (fixture/context-outcome context-bytes))))
      _ (assert (= 4098 over-context-bytes))
      _ (assert (= :refuse (:outcome (fixture/context-outcome over-context-bytes))))
      _ (assert (= context-source (:context-source context) (get-in context [:context-observation :source])))
      _ (assert (= #{:context-protocol :context-source :context-observation :restart-required?} (set (keys context))))
      _ (assert (= false (:restart-required? context)))
      source {:base_sha (:base_sha manifest) :head_sha (:head_sha manifest)
              :diff_base_sha (:diff_base_sha manifest)
              :full_diff {:path "basehead.diff" :bytes 11840401 :sha256 (sha corpus)}}
      coverage {:chunks (count pages) :delivered (count pages) :assessed 0 :missing ids}
      session (apply str (repeat 128 "s"))
      _ (assert (= :accept (:outcome (fixture/session-outcome (session-measurement session)))))
      session-controls (mapv (fn [[label value]]
                               (let [measurement (session-measurement value)]
                                 {:label label :value value :measurement measurement
                                  :result (fixture/session-outcome measurement)}))
                             [["unicode-exact" (apply str (repeat 64 "é"))]
                              ["unicode-over" (str (apply str (repeat 64 "é")) "x")]
                              ["escaped-exact" (str (apply str (repeat 21 "\u0000")) "xx")]
                              ["escaped-over" (str (apply str (repeat 21 "\u0000")) "xxx")]])
      _ (assert (= [:accept :refuse :accept :refuse] (mapv #(get-in % [:result :outcome]) session-controls)))
      custody {:profile profile-id :session session
               :source_sha256 (sha corpus) :head_sha (:head_sha manifest) :base_sha (:base_sha manifest)}
      response (fn [members]
                 (merge context {:ok? true :chunks members :input-source source :coverage coverage
                  :batch {:ids (mapv :id members) :first-id (:id (first members))
                          :last-id (:id (last members))
                          :next-id (when (< (:id (last members)) (count pages)) (inc (:id (last members))))
                          :profile profile-id}
                  :custody custody}))
      request (fn [members note]
                {:assessments (mapv #(assoc (select-keys % [:id :start :end :sha256]) :note note) members)
                 :profile profile-id :source_sha256 (sha corpus)})
      request-case (fn [v]
                     (let [wire (json v) notes (mapv :note (:assessments v))]
                       {:value v :wire wire :measurement (metrics wire)
                        :result (fixture/request-outcome (utf8-bytes wire) (mapv utf8-bytes notes) notes)}))
      groups (loop [at 0 result []]
               (if (= at (count pages)) result
                 (let [prefixes (mapv (fn [n]
                                       (let [members (subvec pages at (+ at n))]
                                         (assoc (metrics (encode (response members))) :ids (mapv :id members))))
                                     (range 1 (inc (min 4 (- (count pages) at)))))
                       selected (fixture/read-outcome prefixes)
                       _ (assert (= :accept (:outcome selected)) "A whole original page cannot fit.")
                       n (count (:ids selected))]
                   (recur (+ at n) (conj result (nth prefixes (dec n)))))))
      _ (assert (= ids (vec (mapcat :ids groups))))
      largest (apply max-key :bytes groups)
      long-session (apply str (repeat 256 "s"))
      long-session-wire (encode (assoc-in (response (mapv #(nth pages (dec %)) (:ids largest)))
                                         [:custody :session] long-session))
      session-growth {:ids (:ids largest) :bounded-response-bytes (:bytes largest)
                      :unsupported-response (metrics long-session-wire)
                      :unsupported-session (session-measurement long-session)
                      :result (fixture/session-outcome (session-measurement long-session))}
      _ (assert (= [47982 48110 :refuse]
                   [(:bytes largest) (get-in session-growth [:unsupported-response :bytes])
                    (get-in session-growth [:result :outcome])]))
      synthetic-wire (fn [value member-count]
                       (let [all (mapv #(assoc % :sha256 (sha (:text %)))
                                       (js->clj (.profilePages bridge value) :keywordize-keys true))
                             members (subvec all 0 member-count)
                             digest (sha value)
                             synthetic-source (assoc source :base_sha (apply str (repeat 40 "a"))
                                                      :diff_base_sha (apply str (repeat 40 "a"))
                                                      :head_sha (apply str (repeat 40 "b")))
                             envelope (-> (response members)
                                          (assoc :input-source
                                                 (assoc synthetic-source :full_diff
                                                        {:path "synthetic.diff" :bytes (utf8-bytes value) :sha256 digest}))
                                          (assoc :coverage {:chunks (count all) :delivered (count all)
                                                            :assessed 0 :missing (mapv :id all)})
                                          (assoc-in [:custody :source_sha256] digest)
                                          (assoc-in [:custody :base_sha] (:base_sha synthetic-source))
                                          (assoc-in [:custody :head_sha] (:head_sha synthetic-source))
                                          (assoc-in [:batch :next-id]
                                                    (when (< member-count (count all)) (inc member-count))))]
                         {:ids (mapv :id members) :wire (encode envelope)
                          :source-text value :pages (count all)}))
      boundary-value (fn [prefix target member-count]
                       (let [candidate (fn [n] (str prefix (apply str (repeat n "\u0000"))))
                             n (loop [lo 0 hi 8192]
                                 (if (= lo hi) lo
                                   (let [mid (quot (inc (+ lo hi)) 2)
                                         wire (:wire (synthetic-wire (candidate mid) member-count))]
                                     (if (<= (utf8-bytes wire) target) (recur mid hi) (recur lo (dec mid))))))
                             base (candidate n)
                             result (first (keep (fn [extra]
                                                   (let [value (str base (apply str (repeat extra "x")))
                                                         result (synthetic-wire value member-count)]
                                                     (when (= target (utf8-bytes (:wire result))) result)))
                                                 (range 13)))]
                         (assert result "Cannot construct exact complete synthetic wire.")
                         result))
      exact-synthetic (boundary-value "" 48000 1)
      over-synthetic (boundary-value "" 48001 1)
      two-page-over (boundary-value (apply str (repeat 8192 "x")) 48001 2)
      first-page (synthetic-wire (:source-text two-page-over) 1)
      cardinality-source (str/join "\n" (repeat 513 "x"))
      four-pages (synthetic-wire cardinality-source 4)
      five-pages (synthetic-wire cardinality-source 5)
      one-line (:wire (synthetic-wire "Line-boundary control" 1))
      two-lines (str (subs one-line 0 1) "\n" (subs one-line 1))
      exact-response (:wire exact-synthetic)
      over-response (:wire over-synthetic)
      padded-request (loop [v (request (subvec pages 0 4) "Fixture only") at 0]
                       (let [left (- 16384 (utf8-bytes (json v))) note (get-in v [:assessments at :note])
                             n (min (quot left 6) (- 2048 (utf8-bytes note)))
                             next (update-in v [:assessments at :note] str (apply str (repeat n "\u0000")))]
                         (if (< at 3) (recur next (inc at))
                           (update-in next [:assessments at :note] str
                                      (apply str (repeat (- 16384 (utf8-bytes (json next))) "x"))))))
      requests {:exact-bytes (request-case padded-request)
                :over-bytes (request-case (update-in padded-request [:assessments 3 :note] str "x"))
                :exact-note (request-case (request (subvec pages 0 1) (apply str (repeat 2048 "n"))))
                :over-note (request-case (request (subvec pages 0 1) (apply str (repeat 2049 "n"))))
                :exact-cardinality (request-case (request (subvec pages 0 4) "Fixture note"))
                :over-cardinality (request-case (request (subvec pages 0 5) "Fixture note"))
                :max-escaped-notes (request-case (request (subvec pages 0 4) (apply str (repeat 2048 "\u0000"))))}
      responses {:exact {:wire exact-response :measurement (metrics exact-response)
                         :result (fixture/response-outcome (metrics exact-response))}
                 :over {:wire over-response :measurement (metrics over-response)
                        :result (fixture/response-outcome (metrics over-response))}
                 :exact-lines {:wire one-line :measurement (metrics one-line)
                               :result (fixture/response-outcome (metrics one-line))}
                 :over-lines {:wire two-lines :measurement (metrics two-lines)
                              :result (fixture/response-outcome (metrics two-lines))}}
      read-vectors {:first-page first-page :two-page-over two-page-over :single-page-over over-synthetic
                    :four-pages four-pages :five-pages five-pages}
      read-measurement (fn [v] (assoc (metrics (:wire v)) :ids (:ids v)))
      read-cases {:whole-prefix (fixture/read-outcome (mapv read-measurement [first-page two-page-over]))
                  :whole-page-refusal (fixture/read-outcome [(read-measurement over-synthetic)])
                  :cardinality-exact (fixture/read-outcome [(read-measurement four-pages)])
                  :cardinality-over (fixture/read-outcome [(read-measurement five-pages)])
                  :cardinality-prefix (fixture/read-outcome (mapv read-measurement [four-pages five-pages]))}
      unicode (mapv (fn [[name value]]
                      (let [p (js->clj (.profilePages bridge value) :keywordize-keys true)
                            wire (:wire (synthetic-wire value (count p)))
                            decoded (js->clj (js/JSON.parse wire) :keywordize-keys true)]
                        (assert (= value (apply str (map :text p))))
                        (assert (= value (apply str (map :text (:chunks decoded)))))
                        (assert (= (sha value) (get-in decoded [:input-source :full_diff :sha256])
                                   (get-in decoded [:custody :source_sha256])))
                        (assert (= (utf8-bytes value) (get-in decoded [:input-source :full_diff :bytes])))
                        (assert (= (count p) (get-in decoded [:coverage :chunks])))
                        (assert (= (mapv :id p) (get-in decoded [:coverage :missing])
                                   (get-in decoded [:batch :ids])))
                        (assert (nil? (get-in decoded [:batch :next-id])))
                        (assert (= (get-in decoded [:input-source :head_sha])
                                   (get-in decoded [:custody :head_sha])))
                        (assert (= (get-in decoded [:input-source :base_sha])
                                   (get-in decoded [:custody :base_sha])))
                        {:name name :value value :wire wire :measurement (metrics wire)}))
                    [["unicode" "ημ 😀é漢字"] ["escaping" "\"\\\n\r\t\u0000"]
                     ["long-line" (apply str (repeat 8192 "z"))]
                     ["surrogate-boundary" (str (apply str (repeat 8191 "x")) "😀tail")]])]
  (doseq [key [:exact-bytes :exact-note :exact-cardinality]]
    (assert (= :accept (get-in requests [key :result :outcome]))))
  (doseq [key [:over-bytes :over-note :over-cardinality :max-escaped-notes]]
    (assert (= :refuse (get-in requests [key :result :outcome])))
    (assert (zero? (get-in requests [key :result :members]))))
  (assert (= 49792 (get-in requests [:max-escaped-notes :measurement :bytes])))
  (assert (= "84cb38fee26f6b27ff3e8bb9fd644ab2a961afaf57db4ebb4ee86495e66afb5a"
             (get-in requests [:max-escaped-notes :measurement :sha256])))
  (doseq [[n v] [[4 four-pages] [5 five-pages]]]
    (assert (= n (count (:ids v))))
    (assert (= 5 (:pages v)))
    (assert (< (utf8-bytes (:wire v)) 48000)))
  (let [four (js->clj (js/JSON.parse (:wire four-pages)) :keywordize-keys true)
        five (js->clj (js/JSON.parse (:wire five-pages)) :keywordize-keys true)]
    (assert (= (:input-source four) (:input-source five)))
    (assert (= (:custody four) (:custody five)))
    (assert (= (:chunks four) (subvec (:chunks five) 0 4))))
  (assert (= (js->clj (js/JSON.parse one-line)) (js->clj (js/JSON.parse two-lines))))
  (assert (= [1 2] (mapv #(get-in responses [% :measurement :lines]) [:exact-lines :over-lines])))
  (assert (every? #(< (get-in responses [% :measurement :bytes]) 48000) [:exact-lines :over-lines]))
  (assert (= :accept (get-in responses [:exact-lines :result :outcome])))
  (assert (= :refuse (get-in responses [:over-lines :result :outcome])))
  (assert (= [1 2 3 4] (get-in read-cases [:cardinality-exact :ids])
             (get-in read-cases [:cardinality-prefix :ids])))
  (assert (= :refuse (get-in read-cases [:cardinality-over :outcome])))
  (assert (= [] (get-in read-cases [:cardinality-over :ids])))
  (assert (= :accept (get-in responses [:exact :result :outcome])))
  (assert (= :refuse (get-in responses [:over :result :outcome])))
  (assert (= [1] (get-in read-cases [:whole-prefix :ids])))
  (assert (= :refuse (get-in read-cases [:whole-page-refusal :outcome])))
  (write! out "synthetic-vectors.json" {:responses responses :requests requests :read-cases read-cases :read-vectors read-vectors :unicode unicode
                                       :sessions session-controls
                                       :session-growth session-growth
                                       :context {:fields context :measurement (metrics (encode context))
                                                 :over-fields over-context :over-measurement (metrics (encode over-context))}})
  (write! out "host-control-inputs.json"
          {:byte-exact (apply str (repeat 51200 "x"))
           :byte-over (apply str (repeat 51201 "x"))
           :line-exact (str (apply str (repeat 1999 "x\n")) "x")
           :line-over (str (apply str (repeat 2000 "x\n")) "x")})
  (write! out "corpus-pages.json"
          (mapv (fn [p]
                  {:id (:id p) :start (:start p) :end (:end p)
                   :text_utf8_bytes (utf8-bytes (:text p)) :text_sha256 (:sha256 p)
                   :single_response (metrics (encode (response [p])))}) pages))
  (write! out "corpus-groups.json" groups)
  (write! out "summary.json"
          {:fixture-only true :production-api false :model-calls 0
           :runtime (.-version js/process) :nbb (nbb/version) :corpus-sha256 (sha corpus)
           :bundle-sha256 (sha original)
           :dependency zod-identity
           :session-limits (select-keys fixture/profile [:session-id-bytes :session-json-bytes])
           :session-controls (mapv #(dissoc % :value) session-controls)
           :session-growth session-growth
           :profile profile-id :context {:limit 4096 :synthetic-present true
                                        :measurement (metrics (encode context))
                                        :outcome (fixture/context-outcome context-bytes)
                                        :next-valid-over-measurement (metrics (encode over-context))
                                        :next-valid-over-outcome (fixture/context-outcome over-context-bytes)}
           :pages (count pages) :groups (count groups)
           :calls (* 2 (count groups)) :baseline-calls (* 2 (count pages))
           :responses (into {} (map (fn [[k v]] [k (dissoc v :wire)]) responses))
           :requests (into {} (map (fn [[k v]] [k (dissoc v :value :wire)]) requests))
           :read-cases read-cases
           :read-vectors (into {} (map (fn [[k v]] [k {:ids (:ids v) :pages (:pages v)
                                                       :measurement (metrics (:wire v))}]) read-vectors))})
  (println (str "PASS diagnostic vectors, complete4096-byte context,1491original pages/" (count groups)
                "whole groups; model0/productionAPI=false")))

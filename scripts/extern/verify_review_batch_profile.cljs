;; GPL-3.0-or-later. Reproduce diagnostic wires in an owned private directory.
;; No model, production batching API, host reconfiguration or publication.
(ns extern.verify-review-batch-profile
  (:require [clojure.string :as str]
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

(assert (= "v22.20.0" (.-version js/process)) "Qualified reproduction requires Node22.20.0.")
(assert (even? (count *command-line-args*)) "Arguments are flag/value pairs.")
(let [opts (apply hash-map *command-line-args*)
      required #{"--corpus" "--manifest" "--bundle" "--zod" "--out"}
      _ (assert (= required (set (keys opts)))
                "Required: --corpus FILE --manifest FILE --bundle FILE --zod DIRECTORY --out NEW_PRIVATE_DIRECTORY")
      corpus (fs/readFileSync (get opts "--corpus"))
      manifest (decode (get opts "--manifest"))
      original (fs/readFileSync (get opts "--bundle"))
      zod (fs/realpathSync (get opts "--zod"))
      out (path/resolve (get opts "--out"))
      repo (fs/realpathSync (.cwd js/process))
      _ (assert (and (not= repo out) (not (str/starts-with? out (str repo path/sep))))
                "Protected corpus wires must be written outside this checkout.")
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
      _ (assert (= "4.4.3" (:version (decode (path/join zod "package.json")))) "Use measured zod4.4.3.")
      _ (fs/mkdirSync out #js {:mode 448})
      _ (fs/mkdirSync (path/join out "node_modules") #js {:mode 448})
      _ (fs/symlinkSync zod (path/join out "node_modules/zod") "dir")
      bridge-file (path/join out "qualified-test-bridge.mjs")
      suffix (str "\n// Private test exports; qualified original prefix unchanged.\n"
                  "export function profileEncode(v){return eta_mu.boundaries.opencode.encode_result(cljs.core.js__GT_clj(v,cljs.core.keyword('keywordize-keys'),true));}\n"
                  "export function profilePages(t){return cljs.core.clj__GT_js(eta_mu.domain.review.diff_chunks(t));}\n")
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
      profile-id "muse-review-batch-profile/2026-10-15-candidate-v1"
      source (select-keys manifest [:base_sha :head_sha :diff_base_sha :full_diff])
      coverage {:chunks (count pages) :delivered (count pages) :assessed 0 :missing ids}
      custody {:profile profile-id :session (apply str (repeat 128 "s"))
               :source_sha256 (sha corpus) :head_sha (:head_sha manifest) :base_sha (:base_sha manifest)}
      response (fn [members]
                 {:ok? true :chunks members :input-source source :coverage coverage
                  :batch {:ids (mapv :id members) :first-id (:id (first members))
                          :last-id (:id (last members))
                          :next-id (when (< (:id (last members)) (count pages)) (inc (:id (last members))))
                          :profile profile-id}
                  :custody custody})
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
      _ (assert (= 375 (count groups)))
      _ (assert (= ids (vec (mapcat :ids groups))))
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
      four-pages (synthetic-wire (str/join "\n" (repeat 512 "x")) 4)
      five-pages (synthetic-wire (str/join "\n" (repeat 513 "x")) 5)
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
                        :result (fixture/response-outcome (metrics over-response))}}
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
  (assert (= "f4eb1fef4f4a0440e28d889d968cff0d5d03ec38ffe469c36bf9f052233958e0"
             (get-in requests [:max-escaped-notes :measurement :sha256])))
  (doseq [[n v] [[4 four-pages] [5 five-pages]]]
    (assert (= n (:pages v) (count (:ids v))))
    (assert (< (utf8-bytes (:wire v)) 48000)))
  (assert (= [1 2 3 4] (get-in read-cases [:cardinality-exact :ids])
             (get-in read-cases [:cardinality-prefix :ids])))
  (assert (= :refuse (get-in read-cases [:cardinality-over :outcome])))
  (assert (= [] (get-in read-cases [:cardinality-over :ids])))
  (assert (= :accept (get-in responses [:exact :result :outcome])))
  (assert (= :refuse (get-in responses [:over :result :outcome])))
  (assert (= [1] (get-in read-cases [:whole-prefix :ids])))
  (assert (= :refuse (get-in read-cases [:whole-page-refusal :outcome])))
  (write! out "synthetic-vectors.json" {:responses responses :requests requests :read-cases read-cases :read-vectors read-vectors :unicode unicode})
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
           :runtime (.-version js/process) :corpus-sha256 (sha corpus)
           :bundle-sha256 (sha original) :pages (count pages) :groups (count groups)
           :calls (* 2 (count groups)) :baseline-calls (* 2 (count pages))
           :responses (into {} (map (fn [[k v]] [k (dissoc v :wire)]) responses))
           :requests (into {} (map (fn [[k v]] [k (dissoc v :value :wire)]) requests))
           :read-cases read-cases
           :read-vectors (into {} (map (fn [[k v]] [k {:ids (:ids v) :pages (:pages v)
                                                       :measurement (metrics (:wire v))}]) read-vectors))})
  (println "PASS diagnostic vectors, every selected exact/over outcome,1491original pages/375whole groups; model0/productionAPI=false"))

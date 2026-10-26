(ns eta-mu.shape.katamorph
  "Pure assembly of validated Katamorph namespace resource files.

   Katamorph owns admissibility and resource identity. Muse owns the
   assembly that target projections consume."
  (:require [katamorph.manifest :as manifest]
            [katamorph.schema :as schema]))

(defn validated-definitions
  "Expand and validate one Katamorph namespace file."
  [namespace-file]
  (schema/assert! :namespace namespace-file)
  (let [definitions (manifest/namespace-file-definitions namespace-file)]
    (doseq [{:resource/keys [kind definition]} definitions]
      (let [{:keys [ok errors]} (schema/validate kind definition)]
        (when-not ok
          (throw
           (ex-info
            (str "Invalid Katamorph " (name kind) " resource "
                 (:resource/qualified-id definition) ": " (pr-str errors))
            {:resource/kind kind
             :resource/id (:resource/qualified-id definition)
             :contract/errors errors
             :contract/definition definition})))))
    definitions))

(defn- resource-key
  [{:resource/keys [kind definition]}]
  [kind (:resource/qualified-id definition)])

(defn- assert-unique!
  [resources]
  (when-let [[key duplicates]
             (->> resources
                  (group-by resource-key)
                  (filter (fn [[_ entries]] (> (count entries) 1)))
                  first)]
    (throw (ex-info "Duplicate Katamorph resource identity"
                    {:resource/key key
                     :resource/definitions (mapv :resource/definition duplicates)})))
  resources)

(defn assemble
  "Validate namespace files and return an inspectable, target-neutral
   resource assembly."
  [namespace-files]
  (let [resources (->> namespace-files
                       (mapcat validated-definitions)
                       vec
                       assert-unique!)
        by-kind (->> resources
                     (group-by :resource/kind)
                     (map (fn [[kind entries]] [kind (vec entries)]))
                     (into {}))]
    {:assembly/kind :katamorph
     :assembly/resources resources
     :assembly/by-kind by-kind}))

(ns eta-mu.shape.agent-catalog
  "Target-neutral resolution for Katamorph agent runtime resources.

   The catalog preserves every validated contract definition and adds only
   qualified links under :resolved/*. Host projections consume these links
   instead of independently reinterpreting provider/model relationships."
  (:require [clojure.string :as str]))

(def catalog-kinds
  [:provider :model-family :model :agent :mcp-server])

(defn- resource-id
  [entry]
  (get-in entry [:resource/definition :resource/qualified-id]))

(defn- definition
  [entry]
  (:resource/definition entry))

(defn- enabled?
  [entry]
  (not (false? (:enabled (definition entry)))))

(defn- assembly-entries
  [assembly kind]
  (->> (get-in assembly [:assembly/by-kind kind] [])
       (filter enabled?)
       vec))

(defn- index-entries
  [entries]
  (into {} (map (juxt resource-id identity)) entries))

(defn qualify-reference
  "Resolve a local keyword/string reference relative to its owning
   definition. Qualified references pass through unchanged."
  [owner value]
  (cond
    (qualified-keyword? value) value
    (keyword? value) (keyword (name (:namespace owner)) (name value))
    (and (string? value) (str/includes? value "/")) (keyword value)
    (string? value) (keyword (name (:namespace owner)) value)
    :else nil))

(defn- resolve-family
  [provider-index entry]
  (let [family (definition entry)
        provider-ref (some->> (:model-family/provider family)
                              (qualify-reference family))]
    (when (and provider-ref (nil? (get provider-index provider-ref)))
      (throw (ex-info "Model family references an unknown provider"
                      {:model-family/id (resource-id entry)
                       :model-family/provider provider-ref})))
    (assoc entry :resolved/provider provider-ref)))

(defn- model-family-reference
  [model]
  (or (:model/family model)
      ;; Compatibility for standalone legacy contracts. Namespace manifests
      ;; use :model/family because :K/id always registers K.
      (:model-family/id model)))

(defn- resolve-model
  [provider-index family-index entry]
  (let [model (definition entry)
        family-ref (some->> (model-family-reference model)
                            (qualify-reference model))
        family-entry (get family-index family-ref)
        provider-ref (if-let [reference (:model/provider model)]
                       (qualify-reference model reference)
                       (:resolved/provider family-entry))]
    (when (and family-ref (nil? family-entry))
      (throw (ex-info "Model references an unknown model family"
                      {:model/id (resource-id entry)
                       :model/family family-ref})))
    (when-not provider-ref
      (throw (ex-info "Model does not resolve to a provider"
                      {:model/id (resource-id entry)
                       :model/family family-ref})))
    (when-not (get provider-index provider-ref)
      (throw (ex-info "Model references an unknown provider"
                      {:model/id (resource-id entry)
                       :model/provider provider-ref})))
    (assoc entry
           :resolved/model-family family-ref
           :resolved/provider provider-ref)))

(defn- default-model-id
  [model-entries]
  (let [defaults (filterv #(true? (:model/default (definition %)))
                          model-entries)]
    (when (> (count defaults) 1)
      (throw (ex-info "Agent catalog requires at most one default model"
                      {:model/defaults (mapv resource-id defaults)})))
    (some-> (first defaults) resource-id)))

(defn- resolve-agent
  [model-index default-model entry]
  (let [agent (definition entry)
        explicit-ref (get-in agent [:agent :model])
        model-ref (if explicit-ref
                    (qualify-reference agent explicit-ref)
                    default-model)]
    (when (and model-ref (nil? (get model-index model-ref)))
      (throw (ex-info "Agent references an unknown model"
                      {:agent/id (resource-id entry)
                       :agent/model model-ref})))
    (assoc entry :resolved/model model-ref)))

(defn entries
  "Resolved entries of kind, in declaration order."
  [catalog kind]
  (get-in catalog [:catalog/by-kind kind] []))

(defn resource
  "Resolved entry for one qualified resource id."
  [catalog kind qualified-id]
  (get-in catalog [:catalog/by-id kind qualified-id]))

(defn definitions
  "Original validated definitions of kind, in declaration order."
  [catalog kind]
  (mapv definition (entries catalog kind)))

(defn resolve-catalog
  "Resolve an assembled Katamorph resource set into a host-neutral agent
   catalog. Disabled resources are absent; references to absent resources
   fail explicitly."
  [assembly]
  (let [providers (assembly-entries assembly :provider)
        provider-index (index-entries providers)
        families (mapv (partial resolve-family provider-index)
                       (assembly-entries assembly :model-family))
        family-index (index-entries families)
        models (mapv (partial resolve-model provider-index family-index)
                     (assembly-entries assembly :model))
        model-index (index-entries models)
        default-model (default-model-id models)
        agents (mapv (partial resolve-agent model-index default-model)
                     (assembly-entries assembly :agent))
        mcp-servers (assembly-entries assembly :mcp-server)
        by-kind {:provider providers
                 :model-family families
                 :model models
                 :agent agents
                 :mcp-server mcp-servers}]
    {:catalog/kind :agent-runtime
     :catalog/resources (into [] (mapcat by-kind) catalog-kinds)
     :catalog/by-kind by-kind
     :catalog/by-id (update-vals by-kind index-entries)
     :catalog/default-model default-model}))

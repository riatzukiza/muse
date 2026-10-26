(ns eta-mu.domain.codex-registration
  "Pure ownership checks for the standalone Codex MCP registration slice."
  (:require [clojure.string :as str]))

(defn- validate-target! [server-name {:keys [command args]}]
  (when-not (and (string? server-name) (re-matches #"[A-Za-z0-9_-]+" server-name)
                 (= "node" command) (vector? args) (= 1 (count args))
                 (string? (first args)) (str/starts-with? (first args) "/"))
    (throw (ex-info "Expected a named Node MCP server with an absolute artifact path"
                    {:server-name server-name}))))

(defn register
  "Add only an absent name; preserve all unrelated definitions verbatim."
  [servers server-name target]
  (validate-target! server-name target)
  (when (contains? servers server-name)
    (throw (ex-info "Codex MCP server name already registered" {:server-name server-name})))
  {:servers (assoc servers server-name target) :previous servers})

(defn rollback
  "Remove only the exact definition installed by this operation."
  [servers server-name expected]
  (validate-target! server-name expected)
  (when-not (= expected (get servers server-name))
    (throw (ex-info "Codex MCP registration changed; rollback refused" {:server-name server-name})))
  (dissoc servers server-name))

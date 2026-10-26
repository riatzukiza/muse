(ns eta-mu.extern.codex-registration
  "Explicit isolated Codex CLI adapter; never edits the active user configuration."
  (:require ["child_process" :as cp]
            ["fs" :as fs]
            ["path" :as path]
            ["os" :as os]
            [eta-mu.domain.codex-registration :as registration]))

(defn- isolated-home! [home]
  (when-not (and (string? home) (path/isAbsolute home) (fs/existsSync home))
    (throw (ex-info "An existing absolute isolated Codex home is required" {})))
  (let [actual (fs/realpathSync home)
        active (or (.-CODEX_HOME js/process.env) (path/join (os/homedir) ".codex"))]
    (when (= actual (if (fs/existsSync active) (fs/realpathSync active) (path/resolve active)))
      (throw (ex-info "Refusing the active Codex home; select an isolated directory" {})))
    actual))

(defn- invoke [home args]
  (let [env (js/Object.assign #js {} js/process.env #js {:CODEX_HOME home})
        result (cp/spawnSync "codex" (clj->js (into ["mcp"] args))
                             #js {:env env :encoding "utf8" :timeout 30000})]
    (when-not (= 0 (.-status result))
      (throw (ex-info "Codex MCP command failed" {:operation (first args) :status (.-status result)})))
    (.-stdout result)))

(defn- snapshot [home]
  (into {} (map (juxt :name identity))
        (js->clj (js/JSON.parse (invoke home ["list" "--json"])) :keywordize-keys true)))

(defn install! [home server-name artifact]
  (let [home (isolated-home! home)
        artifact (fs/realpathSync artifact)
        before (snapshot home)
        target {:command "node" :args [artifact]}]
    (registration/register before server-name target)
    (invoke home ["add" server-name "--" "node" artifact])
    (let [after (snapshot home) installed (get after server-name)]
      (when-not (and (= before (dissoc after server-name))
                     (= "node" (get-in installed [:transport :command]))
                     (= [artifact] (get-in installed [:transport :args])))
        (throw (ex-info "Registration readback mismatch; preserve configuration for inspection" {})))
      {:home home :server-name server-name :installed installed :previous before})))

(defn rollback! [{:keys [home server-name installed previous]}]
  (let [home (isolated-home! home) current (snapshot home)]
    (when-not (and (= installed (get current server-name))
                   (= previous (dissoc current server-name)))
      (throw (ex-info "Configuration changed; rollback refused" {})))
    (invoke home ["remove" server-name])
    (when-not (= previous (snapshot home))
      (throw (ex-info "Rollback readback mismatch" {})))
    {:status :rolled-back :server-name server-name}))

(ns verify-codex-registration
  (:require ["fs" :as fs]
            ["os" :as os]
            ["path" :as path]
            [eta-mu.extern.codex-registration :as registration]))

(let [home (fs/mkdtempSync (path/join (os/tmpdir) "muse-codex-registration-"))
      artifact (path/join home "server.mjs")]
  (fs/writeFileSync artifact "// Registration test artifact; no MCP qualification claimed.\n")
  (let [unrelated (registration/install! home "existing-server" artifact)
        receipt (registration/install! home "muse-hac01" artifact)]
    (try
      (registration/install! home "muse-hac01" artifact)
      (throw (ex-info "Duplicate was incorrectly accepted" {}))
      (catch :default e
        (when-not (= "Codex MCP server name already registered" (ex-message e)) (throw e))))
    (prn (registration/rollback! receipt))
    (prn (registration/rollback! unrelated))
    (println "PASS real Codex CLI isolated registration, duplicate denial, readback and rollback")))

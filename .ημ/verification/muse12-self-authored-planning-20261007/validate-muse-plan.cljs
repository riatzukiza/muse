(ns validate-muse-plan (:require [clojure.string :as str] [eta-mu.receipt-river.api :as api] ["node:fs" :as fs]))
(let [rows (vec (remove str/blank? (str/split-lines (fs/readFileSync (first *command-line-args*) "utf8")))) results (mapv (fn [i line] {:line (inc i) :result (api/validate-line line)}) (range) rows) own (last results) failures (filterv #(not (get-in % [:result :ok])) results)]
 (println (pr-str {:scope :actual-api-all-physical-lines :count (count rows) :failures (mapv (fn [r] {:line (:line r) :errors (get-in r [:result :errors])}) failures) :declared-owned-line (:line own) :owned-valid (get-in own [:result :ok]) :owned-schema (get-in own [:result :source/schema])}))
 (when-not (get-in own [:result :ok]) (throw (ex-info "Declared owned row refused" {}))))

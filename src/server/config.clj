(ns server.config
    (:require [clojure.tools.logging :as log]
      [clojure.string :refer [replace-first]]
      [clojure.edn :as edn]
      ))

(defn- strip [namespace k]
       (keyword (replace-first (name k) (str (name namespace) "-") "")))

(defn- namespaced [config namespace]
       (if (contains? config namespace)
         (config namespace)
         (into {} (map (fn [[k v]] [(strip (name namespace) k) v])
                       (filter (fn [[k v]]
                                   (.startsWith (name k) (str (name namespace) "")))
                               config)))))

(defn deep-merge
      "Deep merge two maps"
      [& values]
      (if (every? map? values)
        (apply merge-with deep-merge values)
        (last values)))

(defn load-config
      [& filenames]
      (reduce deep-merge (map (comp edn/read-string slurp)
                              filenames)))

(defn load-defaults [config]
      (merge (load-config (clojure.java.io/resource "default-config.edn")) config)
      )


(defn parse [config namespaces]
      (let [namespaced-configs (into {} (map (juxt identity (partial namespaced config)) namespaces))]
           (doseq [[namespace namespaced-config] namespaced-configs]
                  (log/info "Destructured" namespace "into" namespaced-config))
           namespaced-configs))

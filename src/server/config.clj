(ns server.config
    (:require [clojure.tools.logging :as log]
      [clojure.string :refer [replace-first]]))

(defn- strip [namespace k]
       (keyword (replace-first (name k) (str (name namespace) "-") "")))

(defn- namespaced [config namespace]
       (if (contains? config namespace)
         (config namespace)
         (into {} (map (fn [[k v]] [(strip (name namespace) k) v])
                       (filter (fn [[k v]]
                                   (.startsWith (name k) (str (name namespace) "")))
                               config)))))

(defn parse [config namespaces]
      (let [namespaced-configs (into {} (map (juxt identity (partial namespaced config)) namespaces))]
           (doseq [[namespace namespaced-config] namespaced-configs]
                  (log/info "Destructured" namespace "into" namespaced-config))
           namespaced-configs))

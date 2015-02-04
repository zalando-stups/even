(ns server.config
    (:require [clojure.tools.logging :as log]
      [clojure.string :refer [replace-first]]
      [clojure.edn :as edn]
      [amazonica.aws.kms :as kms]
      [clojure.data.codec.base64 :as b64]
      ))

(def aws-kms-crypto-prefix "aws:kms:crypto:")

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
      (merge (load-config (clojure.java.io/resource "default-config.edn")) config))

(defn- is-sensitive-key [k]
       (or (.contains (name k) "pass") (.contains (name k) "private")))

(defn mask [config]
      "Mask sensitive information such as passwords"
      (into {} (for [[k v] config] [k (if (is-sensitive-key k)  "MASKED" v)])))

(defn- get-kms-ciphertext-blob [s]
       "Convert config string to ByteBuffer"
       (-> (clojure.string/replace-first s aws-kms-crypto-prefix "")
           .getBytes
           b64/decode
           java.nio.ByteBuffer/wrap))

(defn decrypt-value-with-aws-kms [value aws-region-id]
      "Use AWS Key Management Service to decrypt the given string (must be encoded as Base64)"
      (apply str (map char (.array (:plaintext (kms/decrypt {:endpoint aws-region-id} :ciphertext-blob (get-kms-ciphertext-blob value)))))))

(defn decrypt-value [value aws-region-id]
      "Decrypt a single value, returns original value if it's not encrypted"
      (if (.startsWith value aws-kms-crypto-prefix) (decrypt-value-with-aws-kms value aws-region-id) value))

(defn decrypt [config]
      "Decrypt all values in a config map"
      (into {} (for [[k v] config] [k (decrypt-value v (:aws-region-id config))])))


(defn parse [config namespaces]
      (let [namespaced-configs (into {} (map (juxt identity (partial namespaced config)) namespaces))]
           (doseq [[namespace namespaced-config] namespaced-configs]
                  (log/info "Destructured" namespace "into" (mask namespaced-config)))
           namespaced-configs))

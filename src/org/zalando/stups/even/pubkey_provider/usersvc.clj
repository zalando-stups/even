(ns org.zalando.stups.even.pubkey-provider.usersvc
  (:require
    [clojure.tools.logging :as log]
    [clj-http.client :as client]
    [org.zalando.stups.friboo.system.oauth2 :as oauth2]
    [com.netflix.hystrix.core :refer [defcommand]]
    [org.zalando.stups.friboo.config :as config]
    [clojure.java.io :as io]
    [amazonica.aws.s3 :as s3])
  (:import [java.util.regex Pattern]
           [java.util UUID]
           [com.amazonaws.services.s3.model AmazonS3Exception]))

(defrecord Usersvc [config tokens])

(def user-placeholder (Pattern/quote "{user}"))

(defcommand get-public-key-from-service
  "Get a user's public SSH key from HTTP service"
  [name {:keys [config tokens] :as usersvc}]
  (let [template (config/require-config config :ssh-public-key-url-template)
        url (.replaceAll template user-placeholder name)]
       (:body (client/get url
                          {:oauth-token (oauth2/access-token "user-service" tokens)}))))

(defn get-s3-key [name]
  "Get S3 object key from public key name"
  (str "public-keys/" name ".pub"))

(defcommand get-public-key-from-s3
  "Try to load SSH public key from S3 cache bucket"
  [name {:keys [config] :as usersvc}]
  (try
    (let [bucket (config/require-config config :cache-bucket)
          result (s3/get-object :bucket-name bucket
                                :key (get-s3-key name))]
         (slurp (:input-stream result)))
  (catch AmazonS3Exception se
         ; just return null if the S3 object does not exist
         (when-not (= 404 (.getStatusCode se))
                   (throw se)))))

(defcommand store-public-key-on-s3
  "Store SSH public key in S3 cache bucket"
  [name public-key {:keys [config] :as usersvc}]
  (let [bucket (config/require-config config :cache-bucket)
        ^File tmp-file (io/file "/tmp" (str name ".tmp-" (UUID/randomUUID)))]
       (spit tmp-file public-key)
       (s3/put-object :bucket-name bucket
                      :key (get-s3-key name)
                      :file tmp-file)
       (io/delete-file tmp-file true)))

(defn get-public-key
  "Get user's public SSH key, first try HTTP service, then S3 cache bucket"
  [name {:keys [config] :as usersvc}]
  (try
    (let [public-key (get-public-key-from-service name usersvc)]
         (when (:cache-bucket config)
               (store-public-key-on-s3 name public-key usersvc))
         public-key)
    (catch Throwable ex
      (if (:cache-bucket config)
          (do
              (log/warn "Failed to get SSH public key from HTTP service, falling back to S3 cache bucket:" (.getMessage ex) (when (.getCause ex) (.getMessage (.getCause ex))))
              (get-public-key-from-s3 name usersvc))
          (throw ex)))))

(defn ^Usersvc new-usersvc [config]
  (log/info "Configuring User Service with" (config/mask config))
  (map->Usersvc {:config config}))

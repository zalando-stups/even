(ns org.zalando.stups.even.pubkey-provider.usersvc
  (:require
    [clojure.tools.logging :as log]
    [clj-http.client :as client]
    [org.zalando.stups.friboo.system.oauth2 :as oauth2]
    [com.netflix.hystrix.core :refer [defcommand]]
    [org.zalando.stups.friboo.config :as config]))

(defrecord Usersvc [config tokens])

(defcommand get-public-key [name {:keys [config tokens] :as usersvc}]
            "Get a user's public SSH key"
            (:body (client/get (.replaceFirst (:ssh-public-key-url-template config) "{user}" name)
                               {:oauth-token (oauth2/access-token :user-service-ro-api tokens)
                                :as          :json})))



(defn ^Usersvc new-usersvc [config]
  (log/info "Configuring User Service with" (config/mask config))
  (map->Usersvc {:config config}))
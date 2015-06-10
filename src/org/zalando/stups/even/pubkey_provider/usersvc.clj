(ns org.zalando.stups.even.pubkey-provider.usersvc
  (:require
    [clojure.tools.logging :as log]
    [clj-http.client :as client]
    [org.zalando.stups.friboo.system.oauth2 :as oauth2]
    [com.netflix.hystrix.core :refer [defcommand]]
    [org.zalando.stups.friboo.config :as config])
  (:import [java.util.regex Pattern]))

(defrecord Usersvc [config tokens])

(def user-placeholder (Pattern/quote "{user}"))

(defcommand get-public-key [name {:keys [config tokens] :as usersvc}]
            "Get a user's public SSH key"
            (let [template (config/require-config config :ssh-public-key-url-template)
                  url (.replaceAll template user-placeholder name)]
              (:body (client/get url
                                 {:oauth-token (oauth2/access-token "user-service" tokens)}))))

(defn ^Usersvc new-usersvc [config]
  (log/info "Configuring User Service with" (config/mask config))
  (map->Usersvc {:config config}))
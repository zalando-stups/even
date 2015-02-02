(ns server.ldap
    (:require
      [clojure.tools.logging :as log]
      [com.stuartsierra.component :as component]
      [clj-ldap.client :as ldap])
    )

(defrecord Ldap [config pool])

(defn get-ldap-user-dn [name {:keys [ldap-base-dn]}]
      "Build LDAP DN for a given user name"
      (str "uid=" name "," ldap-base-dn))

(defn ldap-config [{:keys [ldap-host ldap-bind-dn ldap-password ldap-ssl ldap-connect-timeout]}]
      {:host ldap-host
       :bind-dn ldap-bind-dn
       :password ldap-password
       :ssl? (Boolean/parseBoolean ldap-ssl)
       :connect-timeout (Integer/parseInt (or ldap-connect-timeout "10000"))})

(defn ldap-connect [{:keys [config pool] :as ldap-server}]
      (if pool
        pool
        (let [ldap-config (ldap-config config)]
             (log/info "Connecting to LDAP server " ldap-config + " ..")
             (let [conn (ldap/connect ldap-config)]
                  (assoc ldap-server :pool conn)
                  conn)
             )))

(defn get-public-key [name {:keys [config] :as ldap-server}]
      "Get a user's public SSH key"
      (let [conn (ldap-connect ldap-server)]
           (:sshPublicKey (ldap/get conn (get-ldap-user-dn name config) [:sshPublicKey]))))

(defn ^Ldap new-ldap [config]
      (log/info "Configuring LDAP with" config)
      (map->Ldap {:config config}))
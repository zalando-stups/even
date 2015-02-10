(ns server.pubkey-provider.ldap
  (:require
    [clojure.tools.logging :as log]
    [com.stuartsierra.component :as component]
    [clj-ldap.client :as ldap]
    [server.config :as config]
    [clojure.set :refer [rename-keys]])
  )

(defrecord Ldap [config pool])

(defn get-ldap-user-dn [name {:keys [base-dn]}]
  "Build LDAP DN for a given user name"
  (str "uid=" name "," base-dn))

(defn ldap-config [{:keys [host bind-dn password ssl connect-timeout]}]
  {:host host
   :bind-dn bind-dn
   :password password
   :ssl? (Boolean/parseBoolean ssl)
   :connect-timeout (Integer/parseInt (or connect-timeout "10000"))})

(defn ldap-connect [{:keys [config pool] :as ldap-server}]
  (if pool
    pool
    (let [ldap-config (ldap-config config)]
      (log/info "Connecting to LDAP server " (config/mask ldap-config) + " ..")
      (let [conn (ldap/connect ldap-config)]
        (assoc ldap-server :pool conn)
        conn)
      )))

(defn ldap-auth? [{:keys [username password]} {:keys [config] :as ldap-server}]
  (let [conn (ldap-connect ldap-server)]
    (ldap/bind? conn (get-ldap-user-dn username config) password)))

(defn get-public-key [name {:keys [config] :as ldap-server}]
  "Get a user's public SSH key"
  (let [conn (ldap-connect ldap-server)]
    (:sshPublicKey (ldap/get conn (get-ldap-user-dn name config) [:sshPublicKey]))))

(defn get-groups [name {:keys [config] :as ldap-server}]
  (let [conn (ldap-connect ldap-server)]
    (:memberOf (ldap/get conn (get-ldap-user-dn name config) [:memberOf]))))

(defn get-networks [name ldap-server]
  (let [conn (ldap-connect ldap-server)]
    (rename-keys (filter :ipHostNumber (map #(ldap/get conn % [:ipHostNumber])
                             (get-groups name ldap-server))) {:ipHostNumber :cidr :dn :name})))

(defn ^Ldap new-ldap [config]
  (log/info "Configuring LDAP with" (config/mask config))
  (map->Ldap {:config config}))
(ns server.net
  (:require     [clj-dns.core :as dns])
  (:import [org.apache.commons.net.util SubnetUtils]))

(defn is-in-range? [net ip]
  "Check whether the given IP is in the given network (CIDR notation)"
  (-> net
      SubnetUtils.
      .getInfo
      (.isInRange ip)))


(defn network-matches? [{:keys [cidr] :as network} ip]
  (not (empty? (filter #(is-in-range? % (.getHostAddress ip)) cidr))))

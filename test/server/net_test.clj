(ns server.net-test    (:require
                         [clojure.test :refer :all]
                         [server.net :refer :all]
                         [clj-dns.core :as dns]))

(deftest test-is-in-range
  (are [res net ip] (= res (is-in-range? net ip))
                    true "10.0.0.0/8" "10.2.3.4"
                    false "10.0.0.0/8" "1.2.3.4"))


(deftest test-network-matches
  (are [res net ip] (= res (network-matches? {:cidr net} (dns/to-inet-address ip)))
                    false [] "1.2.3.4"
                    true ["10.1.2.3/8"] "10.1.2.4"
                    true ["10.0.0.0/8", "11.0.0.0/8"] "11.1.2.3"
                    true ["10.0.0.0/8", "1.2.3.4/32"] "1.2.3.4"
                    true ["10.0.0.0/8", "1.2.3.4/31"] "1.2.3.4"
                    ))



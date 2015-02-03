 (ns server.pubkey-provider.ldap-test
     (:require
       [clojure.test :refer :all]
       [server.pubkey-provider.ldap :refer :all]))


 (deftest test-get-ldap-user-dn
          (is (= "uid=jdoe,ou=users,dc=example,dc=org" (get-ldap-user-dn "jdoe" {:ldap-base-dn "ou=users,dc=example,dc=org"}))))

 (deftest test-ldap-config
          (is (= 123 (:connect-timeout (ldap-config {:connect-timeout "123"})))))

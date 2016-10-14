(ns org.zalando.stups.even.api-test

  (import java.net.InetAddress)
  (:require [clojure.test :refer :all]
            [org.zalando.stups.even.api :refer :all]
            [midje.sweet :refer :all]
            [schema.core :as s]
            [clojure.string :as str]
            [org.zalando.stups.even.ssh :as ssh]
            [org.zalando.stups.even.sql :as sql]
            [org.zalando.stups.even.api :as api]
            [org.zalando.stups.even.audit :as audit]
            ))

(deftest test-access-request-validation-fails
  (are [req] (thrown? Exception (validate-request req))
             {}
             {:username "a"}
             {:hostname "a"}
             ))

(deftest test-access-request-validation-succeeds
  (are [req] (= req (validate-request req))
             ; username is optional
             {:hostname "b" :reason "a"}
             {:username "my-user" :hostname "some.host" :reason "test"}
             {:username "my-user" :hostname "1.2.3.4" :reason "test"}
             ))

(deftest test-ensure-username
  (is (= {:username "a" :blub "b"} (ensure-username {:username "a"} {:blub "b"}))))

(deftest test-request-access-wrong-network
  (with-redefs [get-allowed-hostnames (constantly ["odd-.*.myteam.example.org"])
                sql/create-access-request (constantly [])
                sql/update-access-request! (constantly nil)]
    (is (= {:status 403 :headers {} :body "Forbidden. Host /2.3.4.5 is not matching any allowed hostname: [odd-.*.myteam.example.org]"}
           (request-access-with-auth
             {:username "user1" :teams ["myteam"]}
             {:hostname "2.3.4.5"}
             {:configuration {:allowed-hostname-template "odd-.*.{team}.example.org"}}
             {}
             {}
             {}
             (constantly nil)
             )))))

(deftest test-request-access-success
  (with-redefs [sql/create-access-request (constantly [])
                sql/update-access-request! (constantly nil)
                resolve-hostname (constantly "odd-eu-west-1.myteam.example.org/127.0.0.1")
                ssh/execute-ssh (constantly {:exit 0})
                audit/create-event (constantly {})]
    (is (= {:status 200 :headers {} :body "Access to host odd-eu-west-1.myteam.example.org/127.0.0.1 for user user1 was granted."}
           (request-access-with-auth
             {:username "user1" :teams ["myteam"]}
             {:hostname "odd-eu-west-1.myteam.example.org" :username "user1"}
             {:configuration {:allowed-hostname-template "odd-.*.{team}.example.org"}}
             {}
             {}
             {}
             (constantly nil))))))

(deftest test-request-no-auth
  (is (= {:status 401 :headers {} :body "Unauthorized. Please authenticate with a valid OAuth2 token."} (request-access {:request {:hostname "someStr" :reason "someStr"}} {} {} {} {} (constantly nil)))))

(deftest ^:unit test-log-fn-being-called-or-not
  (defn log-fn [] nil)
  (def inet-address (InetAddress/getByName "www.name.de"))

  (facts "about log-fn"

   (fact "it is being called on successfull handling of ssh access request"
     (api/request-access-with-auth .auth. {:hostname "www.name.de"} .ring-request. .ssh. .db. .usersvc. log-fn) => (contains {:status 200})
       (provided
         .auth.           =contains=> {:username "userx" :teams '("someteam")}
         .ring-request.   =contains=> {:configuration {:allowed-hostname-template "www.name.de"}}
         .ssh.            => irrelevant
         .db.             => irrelevant
         .usersvc.        => irrelevant
         (api/resolve-hostname anything) => inet-address
         (sql/cmd-create-access-request anything anything) => '()
         (ssh/execute-ssh anything anything anything) => {:exit 0}
         (sql/update-access-request-status anything anything anything anything anything ) => irrelevant
         (audit/create-event .auth. {:hostname "www.name.de"} inet-address '("www.name.de")) => .created-event.
         (log-fn .created-event.) => {} :times 1))

   (fact "it is never called if executing ssh command returns with error"
      (api/request-access-with-auth .auth. {:hostname "www.name.de"} .ring-request. .ssh. .db. .usersvc. log-fn) => (contains {:status 400})
        (provided
          .auth.           =contains=> {:username "userx"}
          .ring-request.   =contains=> {:configuration {:allowed-hostname-template "www.name.de"}}
          .ssh.            => irrelevant
          .db.             => irrelevant
          .usersvc.        => irrelevant
          (api/resolve-hostname anything) => irrelevant
          (sql/cmd-create-access-request anything anything) => '()
          (ssh/execute-ssh anything anything anything) => {:exit 1}
          (sql/update-access-request-status anything anything anything anything anything ) => irrelevant
          (log-fn anything) => {} :times 0))

    (fact "it is never called if no matching hostname was found"
      (api/request-access-with-auth .auth. {:hostname "www.name.de"} .ring-request. .ssh. .db. .usersvc. log-fn) => (contains {:status 403})
        (provided
          .auth.           =contains=> {:username "userx"}
          .ring-request.   =contains=> {:configuration {:allowed-hostname-template "not matching"}}
          .ssh.            => irrelevant
          .db.             => irrelevant
          .usersvc.        => irrelevant
          (api/resolve-hostname anything) => irrelevant
          (sql/cmd-create-access-request anything anything) => '()
          (sql/update-access-request-status anything anything anything anything anything) => irrelevant
          (log-fn anything) => {} :times 0))))


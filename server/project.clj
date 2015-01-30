(defproject server "0.1.0-SNAPSHOT"
  :description "SSH public key server"
  :url "https://github.com/zalando/ssh-access-granting-service"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [environ "1.0.0"]
                 ; REST APIs
                 [metosin/compojure-api "0.16.4"]
                 [metosin/ring-http-response "0.5.2"]
                 [metosin/ring-swagger-ui "2.0.17"]
                 ; LDAP
                 [org.clojars.pntblnk/clj-ldap "0.0.9"]

                 ]
  :plugins [[lein-ring "0.8.13"]
            [lein-environ "1.0.0"]]
  :ring {:handler server.handler/app}
  :aot :all
  :main server.handler
  :uberjar-name "ssh-access-granting-service.jar"
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})

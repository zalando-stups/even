(defproject even "0.1.0-SNAPSHOT"
            :description "SSH access granting service"
            :url "https://github.com/zalando-stups/even"
            :license {:name "Apache License"
                      :url "http://www.apache.org/licenses/"}
            :scm {:url "git@github.com:zalando-stups/even"}
            :min-lein-version "2.0.0"
            :dependencies [[org.clojure/clojure "1.6.0"]
                           [org.zalando.stups/friboo "0.6.0-SNAPSHOT"]
                           [metosin/ring-http-response "0.5.2"]
                           ; lifecycle management
                           [com.stuartsierra/component "0.2.2"]
                           [environ "1.0.0"]
                           ; LDAP
                           [org.clojars.pntblnk/clj-ldap "0.0.9"]
                           ; SSH client
                           [clj-ssh "0.5.11"]
                           ; amazon aws (if upgrading, also check the joda-time version)
                           [amazonica "0.3.19" :exclusions [joda-time commons-logging]]
                           [joda-time "2.5"]
                           [org.clojure/data.json "0.2.5"]
                           [org.clojure/data.codec "0.1.0"]
                           [com.brweber2/clj-dns "0.0.2"]
                           [commons-net/commons-net "3.3"]]
            :plugins [[lein-environ "1.0.0"]
                      [lein-cloverage "1.0.2"]
                      [lein-kibit "0.0.8"]]

            :aliases {"cloverage" ["with-profile" "test" "cloverage"]}

            :main ^:skip-aot org.zalando.stups.even.core
            :uberjar-name "even.jar"

            :docker {:image-name "stups/even"}

            :profiles {
                       :log {:dependencies [[org.apache.logging.log4j/log4j-core "2.1"]
                                            [org.apache.logging.log4j/log4j-slf4j-impl "2.1"]]}
                       :no-log {:dependencies [[org.slf4j/slf4j-nop "1.7.7"]]}
                       :dev {
                             :repl-options {:init-ns user}
                             :source-paths ["dev"]
                             :dependencies [[org.clojure/tools.namespace "0.2.9"]
                                            [org.clojure/java.classpath "0.2.0"]]}
                       :test    {:dependencies [[clj-http-lite "0.2.1"]
                                                [org.clojure/java.jdbc "0.3.6"]]}

                       :repl [:no-log]
                       :uberjar {:aot :all :resource-paths ["swagger-ui"]}})

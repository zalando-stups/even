(defproject org.zalando.stups/even "0.3-SNAPSHOT"
  :description "SSH access granting service"
  :url "https://github.com/zalando-stups/even"
  :license {:name "Apache License"
            :url  "http://www.apache.org/licenses/"}
  :scm {:url "git@github.com:zalando-stups/even"}
  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.zalando.stups/friboo "0.8.0-SNAPSHOT"]
                 [metosin/ring-http-response "0.5.2"]
                 ; LDAP
                 [org.clojars.pntblnk/clj-ldap "0.0.9"]
                 ; SSH client
                 [clj-ssh "0.5.11"]
                 [amazonica "0.3.19"]

                 [yesql "0.5.0-rc2"]
                 [org.postgresql/postgresql "9.3-1102-jdbc41"]
                 [squirrel "0.1.1"]

                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/data.codec "0.1.0"]
                 [com.brweber2/clj-dns "0.0.2"]
                 [commons-net/commons-net "3.3"]]

  :main ^:skip-aot org.zalando.stups.even.core
  :uberjar-name "even.jar"

  :plugins [[lein-environ "1.0.0"]
            [lein-cloverage "1.0.2"]
            [lein-kibit "0.0.8"]
            [io.sarnowski/lein-docker "1.1.0"]]

  :docker {:image-name "stups/even"}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["clean"]
                  ["uberjar"]
                  ["docker" "build"]
                  ["docker" "push"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :aliases {"cloverage" ["with-profile" "test" "cloverage"]}

  :profiles {:uberjar {:aot :all}

             :test    {:dependencies [[clj-http-lite "0.2.1"]
                                      [org.clojure/java.jdbc "0.3.6"]]}

             :dev     {:repl-options {:init-ns user}
                       :source-paths ["dev"]
                       :dependencies [[org.clojure/tools.namespace "0.2.10"]
                                      [org.clojure/java.classpath "0.2.2"]
                                      [clj-http-lite "0.2.1"]
                                      [org.clojure/java.jdbc "0.3.6"]]}})


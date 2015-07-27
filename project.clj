(defproject org.zalando.stups/even "0.11.0-SNAPSHOT"
  :description "SSH access granting service"
  :url "https://github.com/zalando-stups/even"
  :license {:name "Apache License"
            :url  "http://www.apache.org/licenses/"}
  :scm {:url "git@github.com:zalando-stups/even"}
  :min-lein-version "2.0.0"

  :dependencies [[org.zalando.stups/friboo "0.30.0"]
                 [metosin/ring-http-response "0.6.2"]
                 ; LDAP
                 [org.clojars.pntblnk/clj-ldap "0.0.9"]
                 ; SSH client
                 [clj-ssh "0.5.11"]
                 [amazonica "0.3.24"]

                 [yesql "0.5.0-rc3"]
                 [squirrel "0.1.1"]

                 [org.clojure/data.codec "0.1.0"]
                 [com.brweber2/clj-dns "0.0.2"]]

  :main ^:skip-aot org.zalando.stups.even.core
  :uberjar-name "even.jar"

  :plugins [[lein-environ "1.0.0"]
            [lein-cloverage "1.0.6"]
            [org.zalando.stups/lein-scm-source "0.2.0"]
            [io.sarnowski/lein-docker "1.1.0"]]

  :docker {:image-name #=(eval (str (some-> (System/getenv "DEFAULT_DOCKER_REGISTRY")
                                                      (str "/"))
                                              "stups/even"))}

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["clean"]
                  ["uberjar"]
                  ["scm-source"]
                  ["docker" "build"]
                  ["docker" "push"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]

  :aliases {"cloverage" ["with-profile" "test" "cloverage"]}

  :profiles {:uberjar {:aot :all}

             :test    {:dependencies [[clj-http-lite "0.2.1"]
                                      [org.clojure/java.jdbc "0.3.7"]]}

             :dev     {:repl-options {:init-ns user}
                       :source-paths ["dev"]
                       :dependencies [[org.clojure/tools.namespace "0.2.10"]
                                      [org.clojure/java.classpath "0.2.2"]
                                      [clj-http-lite "0.2.1"]
                                      [org.clojure/java.jdbc "0.3.7"]]}})


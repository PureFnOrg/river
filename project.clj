(defproject org.purefn/river "0.1.5-SNAPSHOT"

  :description "Minimalist Consumers for Kafka Topics."
  :url "https://github.com/purefnorg/river"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [clj-time "0.15.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [org.purefn/kurosawa "2.1.9"
                  :exclusions
                  [org.purefn/kurosawa.web]]
                 [org.apache.kafka/kafka-clients "2.0.0"]]
  
  :java-source-paths ["java"]

  :deploy-repositories
  [["releases" {:url "https://repo.clojars.org" :creds :gpg}]]

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [org.purefn/kurosawa.aws "2.1.4"]
                                  [com.stuartsierra/component.repl "0.2.0"]
                                  [com.taoensso/nippy "2.13.0"]
                                  [criterium "0.4.5"]]
                   :source-paths ["dev"]}})

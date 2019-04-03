(defproject org.purefn/river "0.1.0-SNAPSHOT"

  :description "Minimalist Consumers for Kafka Topics."
  :url "https://github.com/purefnorg/river"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [com.stuartsierra/component "0.3.2"]
                 [org.purefn/kurosawa "2.0.11"
                  :exclusions
                  [org.purefn/kurosawa.web]]
                 [org.apache.kafka/kafka-clients "2.0.0"]]
  
  :java-source-paths ["java"] ; Java source is stored separately.

  :profiles {:dev {:dependencies [[org.clojure/tools.namespace "0.2.11"]
                                  [com.stuartsierra/component.repl "0.2.0"]
                                  [com.taoensso/nippy "2.13.0"]]
                   :source-paths ["dev"]}})

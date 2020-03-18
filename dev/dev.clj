(ns dev
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application.

  Call `(reset)` to reload modified code and (re)start the system.

  The system under development is `system`, referred from
  `com.stuartsierra.component.repl/system`.

  See also https://github.com/stuartsierra/component.repl"
  (:require
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer [javadoc]]
   [clojure.pprint :refer [pprint]]
   [clojure.reflect :refer [reflect]]
   [clojure.repl :refer [apropos dir doc find-doc pst source]]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer [refresh refresh-all clear]]
   [com.stuartsierra.component :as component]
   [com.stuartsierra.component.repl :refer [reset set-init start stop system]]
   [criterium.core :as criterium]
   [org.purefn.kurosawa.config :as conf]
   [org.purefn.kurosawa.log.core :as klog]
   [org.purefn.river :as river]
   [org.purefn.river.batch :as batch]
   [org.purefn.river.flush :as flush]
   [org.purefn.river.serdes.nippy :as serdes]
   [taoensso.timbre :as log])
  (:import [java.io File]
           [java.util UUID]
           [org.apache.kafka.clients.producer KafkaProducer ProducerRecord]))

;; Do not try to load source code from 'resources' directory
(clojure.tools.namespace.repl/set-refresh-dirs "dev" "src" "test")

;;--------------------------------------------------------------------------------
;; Kafka Producer

(defrecord Producer
    [producer config]

  component/Lifecycle
  (start [this]
    (let [{:keys [config producer]} this]
      (if producer
        this
        (assoc this :producer (KafkaProducer. config
                                              (serdes/nippy-serializer)
                                              (serdes/nippy-serializer))))))

  (stop [this]
    (when producer
      (.close producer))
    (assoc this :producer nil)))

(defn producer
  []
  (map->Producer
   {:config {"bootstrap.servers"  "localhost:9092"
             "client.id"          (str (UUID/randomUUID))
             "acks"               "all"
             "retries"            (Integer. 0)
             "batch.size"         (Integer. 16384)
             "linger.ms"          1
             "max.block.ms"       (Integer. 1000)
             "request.timeout.ms" (Integer. 1000)
             "compression.type"   "none"
             "buffer.memory"      33554432}}))

(defn send-record
  [^Producer producer topic key value]
  (.send (:producer producer) (ProducerRecord. topic key value)))

(defn send-same-partition
  []
  (send-record (:producer system) "firefly" 444 (UUID/randomUUID)))

(defn send-guid
  []
  (send-record (:producer system) "firefly" (UUID/randomUUID) (UUID/randomUUID)))

;;--------------------------------------------------------------------------------
;; System

(defn to-map
  [r]
  {:topic (.topic r)
   :partition (.partition r)
   :offset (.offset r)
   :timestamp (.timestamp r)
   :timetamp-type (.timestampType r)
   :serialized-key-size (.serializedKeySize r)
   :serialized-value-size (.serializedValueSize r)
   :key (.key r)
   :value (.value r)
   :headers (.headers r)})

(def cr nil)

(defn file-writer
  [{:keys [file]} state records commit]
  (with-open [w (io/writer file :append true)]
    (doseq [{:keys [key value] :as r} records]
      (def cr r)
      (.write w (str key ":" value "\n"))))
  (commit))

(defn batch-writer
  [{:keys [file]} records]
  (log/info "got" (count records))
  (with-open [w (io/writer file :append true)]
    (.write w (reduce
               (fn [acc val]
                 (str acc val "\n"))
               ""
               records))))

(def processor
  (-> batch-writer
      (flush/flush)
      (flush/seen 5)
      (flush/timed 1000)
      (flush/max-records 10)
      (flush/accumulate)
      (flush/transform (comp
                        (filter (constantly false))
                        (map :value)))))

(defn dev-system
  "Constructs a system map suitable for interactive development."
  []
  (component/system-map
   :producer (producer)
   :consumer (component/using
              (batch/batch-consumer
               (assoc (batch/default-config)
                      ::batch/timeout 5000
                      ::batch/bootstrap-servers "localhost:9092"
                      ::batch/topics ["firefly"]
                      ::batch/group-id "serenity"
                      ::batch/max-poll-records     1
                      ::batch/max-poll-interval-ms 300000)
               #'processor)
              [:file])
   :file (File. "./simon.txt")
   ))

(set-init (fn [_] (let [sys (dev-system)]
                    (klog/init-dev-logging sys)
                    sys)))

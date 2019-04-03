(ns org.purefn.river.batch
  "A consumer suitable for batch reading operations from Kafka."
  (:require [clojure.pprint :as pprint]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            ;; temp, remove
            [org.purefn.river.serdes.nippy :as serdes]
            ;;----
            [taoensso.timbre :as log])
  (:import [org.apache.kafka.clients.consumer KafkaConsumer]
           [org.apache.kafka.common TopicPartition]
           [org.apache.kafka.common.errors WakeupException]))

(defn kafka-consumer
  [group-id servers]
  (KafkaConsumer. {"auto.offset.reset" "earliest"
                   "bootstrap.servers" servers
                   "enable.auto.commit" false
                   "group.id" group-id}
                  (serdes/nippy-deserializer)
                  (serdes/nippy-deserializer)))

(defn create-consumers
  [{:keys [::topics ::group-id ::threads ::bootstrap-servers] :as config}]
  (log/info config)
  (->> (range threads)
       (map (fn [_]
              (log/info "Creating consumer" {:group-id group-id
                                             :topics topics})
                (doto (kafka-consumer group-id bootstrap-servers)
                  (.subscribe topics))))))

(defn process
  [^KafkaConsumer consumer
   ^clojure.lang.Atom closing
   {:keys [::timeout] :as config}
   process-fn]
  (log/info consumer)
  (let [commit #(.commitAsync consumer)]
    (try
      (loop [state {}]
        (when-not @closing
          (let [records (seq (.poll consumer timeout))
                next-state (if records
                             (process-fn state records commit)
                             state)]
            (recur next-state))))

      (catch WakeupException ex
        (when-not @closing
          (throw ex)))

      (catch Exception ex
        (log/error ex)
        (throw ex))

      (finally
        (log/info "Closing consumer for"
                  (->> (.assignment consumer)
                       (map (juxt (memfn topic)
                                  (memfn partition)))
                       (pprint/pprint)
                       (with-out-str)))
        (.close consumer)))))


(defrecord BatchConsumer
    [config consumers process-fn]

  component/Lifecycle
  (start [this]
    (log/info config)
    (assoc this
           :consumers
           (mapv (fn [c]
                  (let [closing (atom false)]
                    [(future (process c closing config process-fn))
                     c
                     closing]))
                 (create-consumers config))))

  (stop [this]
    (doseq [[f c closing] (:consumers this)]
      (reset! closing true)
      (.wakeup c)
      (log/info "Waiting for" c "to wakeup")
      (deref f))
    this))

(defn default-config
  []
  "The default configuration for a batch consumer.

  - `::topics` The topics to consumer from.

  - `::bootstrap-servers` hostname:port of a broker in the Kafka cluster to sink from.

  - `::threads` The number of threads (consumers) to create for each topic. 
  (default 4)

  - `::group-id` The group-id of the conumser group, used when committing offsets."

  {::threads 4
   ::timeout 10000})

(defn batch-consumer
  [config process-fn]
  {:pre [(s/assert* ::config config)]}
  (map->BatchConsumer {:config config :process-fn process-fn}))

(s/def ::topic string?)
(s/def ::topics (s/coll-of ::topic))
(s/def ::threads pos-int?)
(s/def ::bootstrap-servers string?)
(s/def ::group-id string?)
(s/def ::timeout pos-int?)

(s/def ::config
  (s/keys :req [::bootstrap-servers
                ::timeout
                ::topics
                ::threads
                ::group-id]))
                   
                   

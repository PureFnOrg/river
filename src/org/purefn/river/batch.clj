(ns org.purefn.river.batch
  "A consumer suitable for batch reading operations from Kafka."
  (:require [clojure.pprint :as pprint]
            [clojure.spec.alpha :as s]
            [com.stuartsierra.component :as component]
            ;; TODO, remove when serdes is configurable
            [org.purefn.river.serdes.nippy :as serdes]
            ;;----
            [taoensso.timbre :as log])
  (:import [org.apache.kafka.clients.consumer KafkaConsumer]
           [org.apache.kafka.common TopicPartition]
           [org.apache.kafka.common.errors WakeupException]
           [org.purefn.river Message]))

(defn kafka-consumer
  [{:keys [::group-id ::bootstrap-servers ::deserializer
           ::max-poll-records ::max-poll-interval-ms]}]
  (let [consumer-conf
        (cond-> {"auto.offset.reset" "earliest"
                 "bootstrap.servers" bootstrap-servers
                 "enable.auto.commit" false
                 "group.id" group-id
                 "client.id" group-id}

                max-poll-records 
                (assoc "max.poll.records" max-poll-records)

                max-poll-interval-ms
                (assoc "max.poll.interval.ms" max-poll-interval-ms))]
    (if deserializer 
      (KafkaConsumer.
       (assoc consumer-conf 
              "key.deserializer" (::key.deserializer deserializer)
              "value.deserializer" (::value.deserializer deserializer)))
      (KafkaConsumer.
       consumer-conf       
       (serdes/nippy-deserializer)
       (serdes/nippy-deserializer)))))

(defn create-consumers
  [{:keys [::topics ::group-id ::threads] :as config}]
  (log/info "Creating consumers from" config)
  (->> (range threads)
       (map (fn [_]
              (log/info "Creating consumer" {:group-id group-id
                                             :topics topics})
                (doto (kafka-consumer config)
                  (.subscribe topics))))))

(defn max-arg-count
  [f]
  {:pre [(instance? clojure.lang.AFunction f)]}
  (->> (class f)
       (.getDeclaredMethods)
       (map (comp alength (memfn getParameterTypes)))
       (reduce max)))

(defn process
  [^KafkaConsumer consumer
   ^clojure.lang.Atom closing
   {:keys [::timeout] :as config}
   dependencies
   process-fn]
  (let [commit #(.commitSync consumer)
        as-fn (if (var? process-fn)
                (var-get process-fn)
                process-fn)
        pfn (if (= 4 (max-arg-count as-fn))
              (partial process-fn dependencies)
              process-fn)]
    (try
      (loop [state {}]
        (when-not @closing
          (let [records (seq (.poll consumer timeout))
                next-state (pfn state
                                (map #(Message. %) records)
                                commit)]
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
    (assoc this
           :consumers
           (mapv (fn [consumer]
                  (let [closing (atom false)]
                    [(future (process consumer
                                      closing
                                      config
                                      (dissoc this :config :consumers :process-fn)
                                      process-fn))
                     consumer
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
  "The default configuration for a batch consumer.

  - `::topics` The topics to consumer from.

  - `::bootstrap-servers` hostname:port of a broker in the Kafka cluster to sink from.

  - `::threads` The number of threads (consumers) to create for each topic. 
  (default 4)

  - `::group-id` The group-id of the conumser group, used when committing offsets."
  ([]
   (default-config {}))
  ([config]
   {::threads 4
    ::timeout 10000
    ::bootstrap-servers (get-in config ["kafka" "bootstrap.servers"])}))

(defn batch-consumer
  [config process-fn]
  {:pre [(s/assert* ::config config)
         (s/assert* ::process-fn process-fn)]}
  (map->BatchConsumer {:config config :process-fn process-fn}))

(defn- class-exists?
  [s]
  (try
    (Class/forName s)
    (catch ClassNotFoundException ex
      (log/warn "No class found for name" s))))

(s/def ::topic string?)
(s/def ::topics (s/coll-of ::topic))
(s/def ::threads pos-int?)
(s/def ::bootstrap-servers string?)
(s/def ::group-id string?)
(s/def ::timeout pos-int?)
(s/def ::key.deserializer (comp some? class-exists?))
(s/def ::value.deserializer (comp some? class-exists?))
(s/def ::deserializer (s/keys :req [::key.deserializer
                                    ::value.deserializer]))
(s/def ::max-poll-records     pos-int?)
(s/def ::max-poll-interval-ms pos-int?) 

(s/def ::config
  (s/keys :req [::bootstrap-servers
                ::timeout
                ::topics
                ::threads
                ::group-id]
          :opt [::deserializer
                ::max-poll-records
                ::max-poll-interval-ms]))

(s/def ::process-fn
  (s/or
   :var (s/and var?
               (comp #{3 4} max-arg-count var-get))
   :fn (s/and fn?
              (comp #{3 4} max-arg-count))))

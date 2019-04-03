(ns org.purefn.river.messaging
  (:require [clojure.spec.alpha :as s])
  (:import org.apache.kafka.clients.producer.ProducerRecord
           org.apache.kafka.clients.consumer.ConsumerRecord))

(gen-class
 :name org.purefn.river.Message
 :extends ConsumerRecord
 :implements [clojure.lang.ILookup]
 :constructors [ConsumerRecord]
 :init 
 :prefix "-")

(defn -valAt
  ([this k]
   "foo")
  ([this k not-found]
   "foo"))

(defmacro ^:private compile-if
  "Evaluate `exp` and if it returns logical true and doesn't error, expand to
  `then`.  Else expand to `else`.
  (compile-if (Class/forName \"java.util.concurrent.ForkJoinTask\")
    (do-cool-stuff-with-fork-join)
    (fall-back-to-executor-services))"
  [exp then else]
  (if (try (eval exp)
           (catch Throwable _ false))
    `(do ~then)
    `(do ~else)))

(compile-if
 (do (require 'taoensso.nippy)
     true)

 (do
   (ns org.purefn.river.serdes.nippy
     (:require [taoensso.nippy :as nippy])
     (:import [org.apache.kafka.common.serialization
               Serializer Deserializer Serde]))

   (deftype NippySerializer
       [opts]

     Serializer
     (close [_] nil)
     (configure [_ _ _] nil)
     (serialize [_ _ x] (nippy/freeze x opts)))

   (defn nippy-serializer
     "Returns a Kafka Serializer using nippy from an optional map of
  nippy opts."
     ([]
      (nippy-serializer {}))
     ([opts]
      (->NippySerializer opts)))


   (deftype NippyDeserializer
       [opts]

     Deserializer
     (close [_] nil)
     (configure [_ _ _] nil)
     (deserialize [_ _ x] (when x (nippy/thaw x opts))))

   (defn nippy-deserializer
     "Returns a Kafka Deserializer using nippy from an optional map of
  nippy opts."
     ([]
      (nippy-deserializer {}))
     ([opts]
      (->NippyDeserializer opts)))


   (deftype NippySerde
       [opts]

     Serde
     (close [_] nil)
     (configure [_ _ _] nil)
     (serializer [_] (nippy-serializer opts))
     (deserializer [_] (nippy-deserializer opts)))

   (defn nippy-serde
     "Returns a Kafka Serde (a \"factory\" for serializers and
  deserializers) using Nippy from an optional map of nippy opts."
     ([]
      (nippy-serde {}))
     ([opts]
      (->NippySerde opts))))


 (ns org.purefn.river.serdes.nippy))

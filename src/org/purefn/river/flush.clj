(ns org.purefn.river.flush
  "Experimental.

  Higher order functions used to compose an auto-flushing processing function."
  (:refer-clojure :exclude [flush])
  (:require [clj-time.core :as t]
            [taoensso.timbre :as log]))

(defn accumulate
  "Accumulate records into state, and increments a count."
  [processor]
  (fn 
    [deps state records commit]
    (processor
     deps
     (-> (update state :records concat records)
         (update :count (fnil + 0) (count records)))
     []
     commit)))

(defn transform
  [processor xform]
  (fn
    [deps state records commit]))

(defn max-records
  "Identifies a maximum record count flush condition."
  [processor n]
  (fn 
    [deps state records commit]
    (let [cnt (count (:records state))]
      (processor
       deps
       (cond-> state
         (>= cnt n) (update :flush? conj (str "Count > " n)))
       records
       commit))))

(defn timed
  "Identifies buffer age as a condition for flushing."
  [processor ms]
  (fn 
    [deps state records commit]
    (let [elapsed (some-> (:epoch state)
                          (t/interval (t/now))
                          (t/in-millis))
          records? (> (:count state) 0)]
      (processor
       deps
       (cond-> state
         records? (update :epoch #(or % (t/now)))
         (and records?
              elapsed
              (>= elapsed ms)) (update :flush? conj (str elapsed "ms elapsed.")))
       records
       commit))))

(defn flush
  "Wrap the flush fn, which must be 2-arity - [deps records]"
  [flush-fn]
  (fn 
    [deps state records commit]
    (let [reason (-> state :flush? seq)
          records (-> state :records seq)]
      (if (and reason records)
        (do
          (log/info "Flushing" :reason reason)
          (try 
            (flush-fn deps records)
            (commit)
            {}
            (catch Exception ex
              (log/error ex "Failed to flush records")
              state)))
        state))))

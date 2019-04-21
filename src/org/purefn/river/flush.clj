(ns org.purefn.river.flush
  "Experimental.

  Higher order functions used to compose an auto-flushing processing function."
  (:refer-clojure :exclude [flush])
  (:require [taoensso.timbre :as log]))

(defn accumulate
  [processor]
  (fn 
    ([state records commit]
     (accumulate {} state records commit))
    ([deps state records commit]
     (processor
      deps
      (update state :records concat records)
      []
      commit))))

(defn record-count
  [processor n]
  (fn 
    ([state records commit]
     (record-count {} state records commit))
    ([deps state records commit]
     (let [cnt (count (:records state))]
       (processor
        deps
        (cond-> (assoc state :count cnt)
          (>= cnt n) (update :flush? conj (str "Count > " n)))
        records
        commit)))))

(defn flush
  [flush-fn]
  (fn 
    ([state records commit]
     (flush {} state records commit))
    ([deps state records commit]
     (if-let [reason (-> state :flush? seq)]
       (do (log/info "Flushing" :reason reason)
           (flush-fn deps (:records state))
           (commit)
           {})
       state))))

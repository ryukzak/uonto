(ns uonto.misc
  (:require
   [uonto.raw :as raw]))

(defn singleton-unwrap [coll]
  (when (< 1 (count coll))
    (throw (ex-info "Expected singleton collection" {:coll coll})))
  (first coll))


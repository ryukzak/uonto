(ns uonto.misc)

(defn singleton-unwrap! [coll]
  (when-not (= 1 (count coll))
    (throw (ex-info "Expected singleton collection" {:coll coll})))
  (first coll))


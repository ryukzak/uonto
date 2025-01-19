(ns uonto.misc)

(defn unwrap-singleton! [coll]
  (when-not (= 1 (count coll))
    (throw (ex-info "Expected singleton collection" {:coll coll})))
  (first coll))

(defn ->reduce [st f coll] (reduce f st coll))

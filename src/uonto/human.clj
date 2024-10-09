(ns uonto.human
  (:require
   [clojure.spec.alpha :as s]
   [uonto.raw :as raw]))

(s/check-asserts true)

(defn def-object! [object relations]
  (let [id (raw/object->id object)
        relations' (->> relations
                        (map (fn [[relation-class related-objs]]
                               (let [relation-class-id  (raw/object->id relation-class)
                                     related-object-ids (->> related-objs
                                                             (map raw/object->id)
                                                             doall)]
                                 [relation-class-id related-object-ids])))
                        (into {}))
        object-id (raw/def-object! id relations')]
    (raw/id->object object-id)))

(defn def-instance! [object clss]
  (def-object! object {:core/is-instance [clss]}))

(defn classes [object]
  (->> object
       raw/object->id
       raw/classes
       (map raw/id->object)
       (into #{})))

(defn objects []
  (->> (raw/objects)
       (map raw/id->object)
       (into #{})))

(defn repr
  "represent knowlage about objects"
  [objects]
  (->> objects
       (map raw/object->id)
       (map raw/repr-object)
       (into #{})))

(defn repr-verbose
  "represent knowlage about objects"
  [objects]
  (->> objects
       (map raw/object->id)
       (map (fn [id]
              [(raw/repr-object id)
               (->> (raw/classes id)
                    (map raw/repr-object)
                    (into #{}))]))
       (into {})))

(defn is-instance?
  ([cls] (partial is-instance? cls))
  ([cls obj]
   (let [cls-id (raw/object->id cls)
         obj-id (raw/object->id obj)]
     (raw/is-instance? cls-id obj-id))))

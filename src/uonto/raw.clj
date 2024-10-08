(ns uonto.raw
  "API for work with ids & mapping id<->object"
  (:require
   [clojure.spec.alpha :as s]))

(s/check-asserts true)

;; NOTE: Currently we preffer minimalistic state istead of performance.

(def *objects "<key id> -> [<class id>]" (atom {}))

(def *object->id "<object-representation> -> <id>" (atom {}))

;; Mapping

(defn object->id! "If object is not known then ad it with new id."
  [o]
  (let [id (get @*object->id o)]
    (cond
      (some? id) id

      (and (vector? o)
           (every? (comp not number?) o))
      (object->id! (mapv object->id! o))

      :else
      (do (swap! *object->id (fn [m] (assoc m o (count m))))
          (let [id' (object->id! o)]
            (swap! *objects assoc id' #{})
            id')))))

(defn id->object [id]
  (if (< id (count @*object->id))
    (->> @*object->id
         (filter (fn [[_ i]] (= i id)))
         first first)
    (throw (ex-info "Id not found" {:id id}))))

(defn classes
  "get list of the object class"
  [id]
  (s/assert int? id)
  (get @*objects id))

;; Representation

(defn repr-object [id]
  (let [object (id->object id)]
    (if (or (vector? object) (list? object))
      (mapv repr-object object)
      object)))

(defn repr
  "represent knowlage about objects"
  [object-ids]
  (s/assert (s/coll-of int?) object-ids)
  (->> object-ids
       (map repr-object)
       (into #{})))

(defn repr-verbose
  "represent knowlage about objects"
  [object-ids]
  (s/assert (s/coll-of int?) object-ids)
  (->> object-ids
       (map (fn [id]
              [(repr-object id)
               (->> (classes id)
                    (map repr-object)
                    set)]))
       (into {})))

;; Predicates for filtrations

(defn is-instance?
  ([class-id] (partial is-instance? class-id))
  ([class-id object-id]
   (s/assert int? class-id)
   (s/assert ::is-class class-id)
   (s/assert int? object-id)
   (contains? (classes object-id) class-id)))

(defn is-first?
  ([object-id] (partial is-first? object-id))
  ([object-id tuple-id] (= object-id (first (id->object tuple-id)))))

(defn is-second?
  ([object-id] (partial is-second? object-id))
  ([object-id tuple-id] (= object-id (second (id->object tuple-id)))))

(defn object-satisfies?
  ([pred] (partial object-satisfies? pred))
  ([pred object-id] (pred (id->object object-id))))

;; Object selectors

(defn all-objects []
  (keys @*objects))

(defn objects []
  (->> (all-objects)
       (filter #(not (coll? (id->object %))))))

(defn tuples []
  (->> (all-objects)
       (filter #(vector? (id->object %)))))

(defn instances [class-id] (->> (all-objects)
                                (filter #(contains? (classes %) class-id))
                                (into [])))

(defn- is-single-core? [id]
  (let [obj (id->object id)]
    (and (keyword? obj)
         (= "core" (namespace obj)))))

(defn- is-core? [id]
  (or (is-single-core? id)
      (let [obj (id->object id)]
        (and (vector? obj)
             (every? is-single-core? obj)))))

(defn no-core
  "remove all :core/* objects and tuples with :core/* objects."
  [obj-ids]
  (s/assert (s/coll-of int?) obj-ids)
  (->> obj-ids
       (remove is-core?)))

(defn remove-tuple [obj-ids]
  (s/assert (s/coll-of int?) obj-ids)
  (->> obj-ids
       (remove (fn [id] (vector? (id->object id))))))

;; NOTE: Maybe better to it by classes instead of object introspections.

;; Basic classes

(def core:class       (object->id! :core/class))

(s/def ::is-class (fn [id]
                    (prn ::is-class id (classes id))
                    (contains? (classes id) core:class)))

(def core:is-instance (object->id! :core/is-instance))

(defn def-object! [object-id relations]
  (s/assert int? object-id)
  (s/assert (s/map-of int? (s/coll-of int?)) relations)
  (let [is-instance-of (get relations core:is-instance)]
    (->> is-instance-of
         (map (fn [class-id]
                (swap! *objects
                       (fn [objects]
                         (update objects object-id conj class-id)))))
         doall)
    (->> relations
         (map (fn [[rel-class-id obj-ids]]
                (->> obj-ids
                     (map (fn [second-obj-id]
                            (let [rel-object (vector object-id second-obj-id)
                                  rel-object-id (object->id! rel-object)]
                              (swap! *objects
                                     update rel-object-id
                                     conj rel-class-id))))
                     doall)))
         doall)
    object-id))

(def core:is-subclass (object->id! :core/is-subclass))

(def-object! core:class {core:is-instance [core:class]})
(def-object! core:is-instance {core:is-instance [core:class]})
(def-object! core:is-subclass {core:is-instance [core:class]})

(defn infer-transitive-subclasses! []
  (let [is-subclasses         (->> (tuples)
                                   (filter (is-instance? core:is-subclass)))
        is-subclasses-tuples  (->> is-subclasses
                                   (map id->object)
                                   (into #{}))
        is-subclasses-dict    (->> is-subclasses-tuples
                                   (reduce (fn [st [down top]]
                                             (if (contains? st down)
                                               (update st down conj top)
                                               (assoc st down #{top}))) {}))

        infer-subclass-tuples (for [[down tops] is-subclasses-dict
                                    top tops
                                    :let [hops (get is-subclasses-dict top)]
                                    hop hops
                                    :when (not (contains? is-subclasses-tuples [down hop]))]
                                [down hop])
        infer-subclasses-relations (->> infer-subclass-tuples
                                        (map object->id!)
                                        (into #{}))]

    (->> infer-subclass-tuples
         (map (fn [[down top]]

                (def-object! down {core:is-subclass [top]})))
         doall)
    (if (empty? infer-subclasses-relations)
      infer-subclasses-relations
      (concat infer-subclasses-relations
              (infer-transitive-subclasses!)))))

(defn infer-subclasses-instances! []
  (let [instances (for [subclass-relation (instances core:is-subclass)
                        :let [[down-cls top-cls] (id->object subclass-relation)]
                        object (instances down-cls)
                        :when (not (is-instance? top-cls object))]
                    [object top-cls])]
    (->> instances
         (map (fn [[object top]]
                (def-object! object {core:is-instance [top]})
                (object->id! [object top])))
         (into #{})
         (into [])
         doall)))

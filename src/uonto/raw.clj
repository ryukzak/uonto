(ns uonto.raw
  "API for work with ids & mapping id<->object"
  (:require
   [clojure.spec.alpha :as s]
   [uonto.raw :as raw]))

;; NOTE: Currently we preffer minimalistic state istead of performance. For real
;; application -- we can add a lot of indexes.

(s/check-asserts true)

;; interface specs

(s/def ::object-id int?)
(s/def ::object-id-set (s/coll-of ::object-id :kind set?))

(s/def ::object (s/or :string  string?
                      :keyword keyword?
                      :tuple   ::tuple))

(s/def ::tuple  (s/and vector? (s/coll-of ::object-id)))

;; onto-state specs

(s/def ::onto-state (s/keys :req-un [:onto-state/objects
                                     :onto-state/object->id]))

(s/def :onto-state/objects    (s/every (s/tuple ::object-id ::object-id-set) :into {}))
(s/def :onto-state/object->id (s/every (s/tuple ::object    ::object-id)     :into {}))

;; state

(def ^:dynamic *inside-onto?* false)

(def ^:dynamic *onto-state*
  (atom {:objects {}
         :object->id {}}
        :validator #(s/valid? ::onto-state %)))

(defmacro with-onto
  "Experiment with ontology model and clean result on the exit."
  [{onto    :onto
    isolate :isolate
    return  :return
    :or {isolate true
         return :result}} & body]
  `(binding [*inside-onto?* true
             *onto-state* (if ~isolate
                            (atom @*onto-state*)
                            *onto-state*)]
     (let [result# (do ~@body)]
       (case ~return
         :result result#
         :onto   @*onto-state*))))

(defn object->id [obj]
  (assert *inside-onto?* "should be called inside with-onto macro")
  (let [id (get-in @*onto-state* [:object->id obj])]
    (cond
      (some? id) id

      (and (vector? obj)
           (every? #(not (number? %)) obj))
      (do (s/assert ::tuple obj)
          (object->id (mapv object->id obj)))

      :else
      (do (swap! *onto-state* (fn [onto-state]
                                (let [id (count (:object->id onto-state))]
                                  (-> onto-state
                                      (assoc-in [:object->id obj] id)
                                      (assoc-in [:objects id] #{})))))
          (object->id obj)))))

;; split write and read only usage

(defn id->object [id]
  (assert *inside-onto?* "should be called inside with-onto macro")
  (if (< id (count (:objects @*onto-state*)))
    (->> (:object->id @*onto-state*)
         (filter (fn [[_ i]] (= i id)))
         first first)
    (throw (ex-info "object id is not found" {:id id}))))

(defn classes
  "get list of the object class"
  [id]
  (assert *inside-onto?* "should be called inside with-onto macro")
  (s/assert ::object-id id)
  (get-in @*onto-state* [:objects id]))

;; Representation

(defn repr-object [id]
  (let [object (id->object id)]
    (if (or (vector? object)
            ;; FIXME: remove list? check
            (list? object))
      (mapv repr-object object)
      object)))

(defn repr
  "represent knowlage about objects"
  [object-ids]
  (s/assert (s/coll-of ::object-id) object-ids)
  (->> object-ids
       (map repr-object)
       (into #{})))

(defn repr-verbose
  "represent knowlage about objects"
  [object-ids]
  (s/assert (s/coll-of ::object-id) object-ids)
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

;; (defn is-first?
;;   ([object-id] (partial is-first? object-id))
;;   ([object-id tuple-id] (= object-id (first (id->object* tuple-id)))))

;; (defn is-second?
;;   ([object-id] (partial is-second? object-id))
;;   ([object-id tuple-id] (= object-id (second (id->object* tuple-id)))))

;; (defn object-satisfies?
;;   ([pred] (partial object-satisfies? pred))
;;   ([pred object-id] (pred (id->object* object-id))))

;; ;; Object selectors

(defn all-objects []
  (keys (:objects @*onto-state*)))

(defn objects []
  (->> (all-objects)
       (filter #(not (coll? (id->object %))))))

(defn tuples []
  (->> (all-objects)
       (filter #(vector? (id->object %)))))

(defn instances [class-id]
  (s/assert ::object-id class-id)
  (->> (all-objects)
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
  (s/assert (s/coll-of ::object-id) obj-ids)
  (->> obj-ids
       (remove is-core?)))

;; (defn remove-tuple [obj-ids]
;;   (s/assert (s/coll-of int?) obj-ids)
;;   (->> obj-ids
;;        (remove (fn [id] (vector? (id->object* id))))))

;; ;; NOTE: Maybe better to it by classes instead of object introspections.

;; Basic classes

(declare core:class core:is-instance core:is-subclass)

(s/def ::is-class #(contains? (classes %) core:class))

(defn def-object! [object-id relations]
  (s/assert ::object-id object-id)
  (s/assert (s/map-of ::object-id (s/coll-of ::object-id)) relations)
  (let [is-instance-of (get relations core:is-instance)]
    (->> is-instance-of
         (map (fn [class-id]
                (swap! *onto-state*
                       (fn [onto-state]
                         (update-in onto-state [:objects object-id] conj class-id)))))
         doall)
    (->> relations
         (map (fn [[rel-class-id obj-ids]]
                (->> obj-ids
                     (map (fn [second-obj-id]
                            (let [rel-object [object-id second-obj-id]
                                  rel-object-id (object->id rel-object)]
                              (swap! *onto-state*
                                     update-in [:objects rel-object-id]
                                     conj rel-class-id))))
                     doall)))
         doall)
    object-id))

(with-onto {:isolate false}
  (def core:class       (object->id :core/class))
  (def core:is-instance (object->id :core/is-instance))
  (def core:is-subclass (object->id :core/is-subclass))

  (def-object! core:class {core:is-instance [core:class]})
  (def-object! core:is-instance {core:is-instance [core:class]})
  (def-object! core:is-subclass {core:is-instance [core:class]}))

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
                                        (map object->id)
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
                (object->id [object top])))
         (into #{})
         (into [])
         doall)))

(ns uonto.core
  "Key concepts:

  - onto -- object to represent ontology

      Mostly user shouldn't see what inside except a few special cases to
      imitate StateMonad:

      - `:value` -- if we need to return more, than just changed ontology;
      - `:with-default-classes` -- helper to reduce boilerplate, allow to
        specify classes for all defined instances.


  - classification -- human representation as map: `{object #{class1}}`


  onto is a map with private structure except :value key, which used
  like result in state monad to return specific value.
  "
  (:require
   [clojure.spec.alpha :as s]))

(s/check-asserts true)

(s/def ::object-id     int?)
(s/def ::object-id-set (s/coll-of ::object-id :kind set?))

(s/def ::abstract-object    keyword?)
(s/def ::information-object (s/or :string-object string?
                                  :number-object number?))
(s/def ::tuple-object       (s/coll-of ::object :kind vector?))

(s/def ::object (s/or :abstract-object    ::abstract-object
                      :information-object ::information-object
                      :tuple-object       ::tuple-object))

(s/def ::objects    (s/coll-of ::object))
(s/def ::object-set (s/coll-of ::object :kind set?))

(s/def :onto/object->id     (s/map-of ::object ::object-id))
(s/def :onto/classification (s/map-of ::object-id ::object-id-set))
(s/def :onto/with-default-classes ::objects)
(s/def :onto/value          some?)

(s/def ::onto (s/keys :opt-un [:onto/object->id
                               :onto/classification
                               :onto/with-default-classes
                               :onto/value]))

(s/def ::classification          (s/map-of ::object ::object-set))

(s/def ::relation-class->objects (s/map-of ::object (s/or :empty   nil?
                                                          :objects ::objects)))

(defn object->id [onto object]
  (get-in onto [:object->id object]))

(def ^{:doc "on unknown id return this amount of objects"}
  known-object-limit 20)

(declare non-tuples)

(defn object->id! [onto object]
  (s/assert ::onto onto)
  (s/assert ::object object)
  (let [id (object->id onto object)]
    (when (nil? id)
      (throw (ex-info "Can't resolve object to id"
                      (let [objects (non-tuples onto)
                            n (count objects)]
                        (cond->
                         {:object             object
                          :some-known-objects (take known-object-limit objects)}
                          (> n known-object-limit)
                          (assoc :not-listed-object-count (- n known-object-limit)))))))
    id))

(defn id->object! [onto id]
  (s/assert ::onto onto)
  (s/assert ::object-id id)
  (let [object (->> (:object->id onto)
                    (keep (fn [[object i]] (when (= i id)
                                             object)))
                    first)]
    (when (nil? object)
      (throw (ex-info "object id is not found"
                      {:id id
                       :max-id (dec (count (:object->id onto)))})))
    object))

(defn- next-id [onto] (count (:object->id onto)))

(defn- add-object->id [onto object]
  (let [id (next-id onto)]
    (-> onto
        (assoc-in [:object->id object] id)
        (assoc :value id))))

(defn register-object [onto object & {:keys [deep-register]}]
  (s/assert ::onto onto)
  (s/assert ::object object)
  (let [id (object->id onto object)]
    (cond
      (some? id) (assoc onto :value id)

      (s/valid? ::tuple-object object)
      (let [onto (if deep-register
                   (reduce (fn [st obj] (register-object st obj :register-elements true))
                           onto object)
                   onto)]
        (assert (every? some? (mapv #(object->id onto %) object))
                (str "some objects in tuple are not registered: " object))
        (add-object->id onto object))

      (or (s/valid? ::information-object object)
          (s/valid? ::abstract-object object))
      (add-object->id onto object)

      :else (throw (ex-info "object is not valid" object)))))

(defn- add-class [old-class-ids new-class-id]
  (s/assert (s/or :object-id-set ::object-id-set
                  :nil           nil?)
            old-class-ids)
  (s/assert ::object-id new-class-id)
  (if (some? old-class-ids)
    (conj old-class-ids new-class-id)
    #{new-class-id}))

(defn classify-object! [onto object classes]
  (let [object-id (object->id! onto object)]
    (reduce (fn [st class]
              (let [class-id             (object->id! st class)
                    is-instance-id       (object->id! st :core/is-instance)
                    is-instance-tuple    [object class]
                    st'                  (register-object st is-instance-tuple)
                    is-instance-tuple-id (object->id! st' is-instance-tuple)
                    st'' (-> st'
                             (update-in [:classification object-id] add-class class-id)
                             (update-in [:classification is-instance-tuple-id] add-class is-instance-id))]
                st''))
            onto classes)))

(defn register-relation [onto class a b]
  (let [class-id (object->id! onto class)
        {tuple-id :value :as onto} (register-object onto [a b])]
    (-> onto
        (update-in [:classification tuple-id] add-class class-id))))

(defn register-relations [onto relation-class a bs]
  (reduce (fn [st b]
            (register-relation st relation-class a b))
          onto bs))

(defn flip-reduce [st f coll] (reduce f st coll))

(defmacro with-classes-> [onto classes & body]
  (let [classes (vec classes)]
    `(let [onto# ~onto]
       (-> onto#
           (add-default-classes ~@classes)
           ~@body
           (remove-default-classes ~@classes)))))

(defn add-default-classes [onto & classes]
  (s/assert ::onto onto)
  (s/assert (s/coll-of ::abstract-object) classes)
  (update onto :with-default-classes (fn [default-classes]
                                       (into default-classes classes))))

(defn remove-default-classes [onto & classes]
  (s/assert ::onto onto)
  (s/assert (s/coll-of ::abstract-object) classes)
  (let [classes (set classes)]
    (update onto :with-default-classes (fn [default-classes]
                                         (remove #(contains? classes %) default-classes)))))

(defn def-object! [onto object relation-class->objects]
  (s/assert ::onto onto)
  (s/assert ::object object)
  (s/assert ::relation-class->objects relation-class->objects)
  (-> onto
      (register-object object)
      (classify-object! object (concat (:core/is-instance relation-class->objects)
                                       (:with-default-classes onto)))
      (flip-reduce (fn [st [class bs]]
                     (register-relations st class object bs))
                   (dissoc relation-class->objects :core/is-instance))
      (assoc :value object)))

(defn object-classes [onto object]
  (->> (get-in onto [:classification (object->id onto object)])
       (map #(id->object! onto %))
       (into #{})))

(defn object-classes! [onto object]
  (->> (get-in onto [:classification (object->id! onto object)])
       (map #(id->object! onto %))
       (into #{})))

(defn object-classification [onto selected-objects]
  (s/assert ::object-set selected-objects)
  (->> selected-objects
       (map (fn [object]
              [object (object-classes onto object)]))
       (into {})))

(defn- in-core-namespace? [object]
  (and (keyword? object)
       (= "core" (namespace object))))

(defn remove-core-from-classification [classification]
  (s/assert ::classification classification)
  (->> classification
       (remove (fn [[object _classes]]
                 (or (in-core-namespace? object)
                     (and (vector? object) (every? in-core-namespace? object)))))

       (into {})))

(defn all-objects         [onto] (s/assert ::onto onto) (->> onto :object->id keys (into #{})))
(defn information-objects [onto] (s/assert ::onto onto) (->> onto all-objects (filter #(s/valid? ::information-object %)) (into #{})))
(defn abstract-objects    [onto] (s/assert ::onto onto) (->> onto all-objects (filter #(s/valid? ::abstract-object    %)) (into #{})))
(defn tuples              [onto] (s/assert ::onto onto) (->> onto all-objects (filter #(s/valid? ::tuple-object       %)) (into #{})))
(defn non-tuples          [onto] (s/assert ::onto onto) (->> onto all-objects (remove #(s/valid? ::tuple-object       %)) (into #{})))

(def base
  (-> {}
      (register-object :core/class)
      (register-object :core/is-instance)
      (register-object :core/is-subclass)
      (def-object! :core/class       {:core/is-instance []})
      (def-object! :core/is-instance {:core/is-instance [:core/class]})
      (def-object! :core/is-subclass {:core/is-instance [:core/class]})))

(defn infer-transitive-subclasses
  "
  User defines subclass hierarchy (top) `A <- B <- C` (bottom) by two pairs:

  - `(register-relations :core/is-subclass A B)`
  - `(register-relations :core/is-subclass B C)`

  and we can infer the relation:

  - `(register-relations :core/is-subclass A C)`
  "
  [onto]
  (let [subclass-tuples (->> (tuples onto)
                             (filter #(contains? (object-classes onto %) :core/is-subclass))
                             (into #{}))
        down->tops (->> subclass-tuples
                        (reduce (fn [st [down top]]
                                  (if (contains? st down)
                                    (update st down conj top)
                                    (assoc st down #{top}))) {}))

        infer-subclass-tuples (set
                               (for [[down tops] down->tops
                                     top tops
                                     :let [hops (get down->tops top)]
                                     hop hops
                                     :when (not (contains? subclass-tuples [down hop]))]
                                 [down hop]))
        onto (reduce (fn [st [down top]]
                       (def-object! st down {:core/is-subclass [top]}))
                     onto
                     infer-subclass-tuples)]
    (if (empty? infer-subclass-tuples)
      (assoc onto :value infer-subclass-tuples)
      (let [{value :value  :as onto} (infer-transitive-subclasses onto)]
        (assoc onto :value (into infer-subclass-tuples value))))))

(defn infer-subclasses-instances
  "
  User defines subclass hierarchy (top) `A <- B` (bottom) and objects: `a: A`, `b: B`:

  - `(register-relations :core/is-subclass A B)`
  - `(classify-object a A)`
  - `(classify-object b B)`

  and we can infer the relation:

  - `(classify-object b A)`
  "
  [onto]
  (let [new-instances
        (set (for [[down top :as tuple] (tuples onto)
                   :when (contains? (object-classes onto tuple) :core/is-subclass)
                   object (all-objects onto)
                   :when (contains? (object-classes onto object) down)
                   :when (not (contains? (object-classes onto object) top))]
               [object top]))]
    (-> onto
        (flip-reduce (fn [st [object class]]
                       (classify-object! st object [class]))
                     new-instances)
        (assoc :value new-instances))))

(defn infer-all [onto]
  (-> onto
      infer-transitive-subclasses
      infer-subclasses-instances))

(defn unify
  "
  Unify several ontologies into one. On collision: don't change object identity,
  only change ids.
  "
  [a-onto b-onto & other]
  (let [onto (-> a-onto
                 (flip-reduce (fn [ab-onto object]
                                (register-object ab-onto object :deep-register true))
                              (all-objects b-onto))
                 (flip-reduce (fn [ab-onto [object classes]]
                                (let [class-ids (map (partial object->id! ab-onto) classes)]
                                  (update-in ab-onto [:classification (object->id! ab-onto object)]
                                             #(if (empty? %)
                                                (set class-ids)
                                                (into % class-ids)))))
                              (->> (all-objects b-onto)
                                   (object-classification b-onto))))]
    (if (empty? other)
      onto
      (apply unify onto other))))

(ns uonto.core
  (:require
   [clojure.spec.alpha :as s]))

(s/check-asserts true)

(s/def ::abstract-object keyword?)
(s/def ::information-object string?)
(s/def ::tuple-object (s/coll-of ::object
                                 :kind vector?))
(s/def ::object-id-set (s/coll-of ::object-id :kind set?))

(s/def ::object (s/or :abstract-object    ::abstract-object
                      :information-object ::information-object
                      :tuple-object       ::tuple-object))

(s/def ::object-id int?)
(s/def ::object-id int?)

(defn try-object->id [onto object]
  (get-in onto [:object->id object]))

(defn object->id [onto object]
  (let [id (try-object->id onto object)]
    (assert (some? id) (str "object is not registered: " object))
    id))

(defn id->object [onto id]
  ;; (assert *inside-onto?* "should be called inside with-onto macro")
  ;; (s/assert ::object-id id)
  (if (< id (count (:object->id onto)))
    (->> (:object->id onto)
         (filter (fn [[_ i]] (= i id)))
         first first)
    (throw (ex-info "object id is not found" {:id id}))))

(defn register-object [onto object & {:keys [auto-register]}]
  (s/assert ::object object)
  (let [id (try-object->id onto object)
        new-id (count (:object->id onto))]
    (cond
      (some? id) (assoc onto :value id)

      (s/valid? ::tuple-object object)
      (let [onto (if auto-register
                   (reduce (fn [st obj] (register-object st obj :register-elements true))
                           onto object)
                   onto)]
        (assert (every? some? (mapv #(try-object->id onto %) object))
                (str "some objects in tuple are not registered: " object))
        (-> onto
            (update-in [:object->id object] #(or % new-id))
            (assoc :value new-id)))

      (or (s/valid? ::information-object object)
          (s/valid? ::abstract-object object))
      (-> onto
          (update-in [:object->id object] #(or % new-id))
          (assoc :value new-id))

      :else (throw (ex-info "object is not valid" object)))))

(defn- add-class [old-class-ids new-class-id]
  (s/assert (s/or :object-id-set ::object-id-set
                  :nil           nil?)
            old-class-ids)
  (s/assert ::object-id new-class-id)
  (if (some? old-class-ids)
    (conj old-class-ids new-class-id)
    #{new-class-id}))

(defn classify-object [onto object classes]
  (assert (every? some? (map #(try-object->id onto %) (cons object classes)))
          (str "some objects are not registered" (cons object classes)))
  (let [object-id (object->id onto object)]
    (reduce (fn [st class]
              (let [class-id             (object->id st class)
                    is-instance-id       (object->id st :core/is-instance)
                    is-instance-tuple    [object class]
                    st'                  (register-object st is-instance-tuple)
                    is-instance-tuple-id (object->id st' is-instance-tuple)]
                (-> st'
                    (update-in [:classification object-id] add-class class-id)
                    (update-in [:classification is-instance-tuple-id] add-class is-instance-id))))
            onto classes)))

(defn register-relation [onto class a b]
  (let [class-id (object->id onto class)
        {tuple-id :value :as onto} (register-object onto [a b])]
    (-> onto
        (update-in [:classification tuple-id] add-class class-id))))

(defn register-relations [onto class a bs]
  (reduce (fn [st b]
            (register-relation st class a b))
          onto bs))

(defn flip-reduce [st f coll] (reduce f st coll))

(defn def-object [onto object relations]
  (-> onto
      (register-object object)
      (classify-object object (:core/is-instance relations))
      (flip-reduce (fn [onto [class bs]]
                     (register-relations onto class object bs))
                   (dissoc relations :core/is-instance))))

(defn object-classes [onto object]
  (->> (get-in onto [:classification (object->id onto object)])
       (map #(id->object onto %))
       (into #{})))

(defn object-classification [onto objects]
  (->> objects
       (map (fn [object]
              [object (object-classes onto object)]))
       (into {})))

(defn all-objects         [onto] (->> onto :object->id keys (into #{})))
(defn information-objects [onto] (->> onto all-objects (filter #(s/valid? ::information-object %)) (into #{})))
(defn abstract-objects    [onto] (->> onto all-objects (filter #(s/valid? ::abstract-object    %)) (into #{})))
(defn non-tuples          [onto] (->> onto all-objects (remove #(s/valid? ::tuple-object       %)) (into #{})))
(defn tuples              [onto] (->> onto all-objects (filter #(s/valid? ::tuple-object       %)) (into #{})))

#_(defn remove-tuple-classifications [onto]
    (->> (tuples onto)
         (map (fn [tuple]
                (let [tuple-id (object->id onto tuple)]
                  (update-in onto [:classification tuple-id] dissoc :core/is-instance))))
         (reduce (fn [st [tuple-id classification]]
                   (assoc st :classification classification))
                 onto)))

(def base
  (-> {}
      (register-object :core/class)
      (register-object :core/is-instance)
      (register-object :core/is-subclass)
      (def-object :core/class       {:core/is-instance []})
      (def-object :core/is-instance {:core/is-instance [:core/class]})
      (def-object :core/is-subclass {:core/is-instance [:core/class]})))

(defn infer-transitive-subclasses [onto]
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
                       (def-object st down {:core/is-subclass [top]}))
                     onto
                     infer-subclass-tuples)]
    (if (empty? infer-subclass-tuples)
      (assoc onto :value infer-subclass-tuples)
      (let [{value :value  :as onto} (infer-transitive-subclasses onto)]
        (assoc onto :value (into infer-subclass-tuples value))))))

(defn infer-subclasses-instances [onto]
  (let [new-instances
        (set (for [[down top :as tuple] (tuples onto)
                   :when (contains? (object-classes onto tuple) :core/is-subclass)
                   object (all-objects onto)
                   :when (contains? (object-classes onto object) down)
                   :when (not (contains? (object-classes onto object) top))]
               [object top]))]
    (-> onto
        (flip-reduce (fn [st [object class]]
                       (classify-object st object [class]))
                     new-instances)
        (assoc :value new-instances))))

(defn unify [a-onto b-onto]
  (-> a-onto
      (flip-reduce (fn [ab-onto object]
                     (register-object ab-onto object))
                   (all-objects b-onto))
      (flip-reduce (fn [ab-onto [object classes]]
                     (let [class-ids (map (partial object->id ab-onto) classes)]
                       (update-in ab-onto [:classification (object->id ab-onto object)]
                                  #(if (empty? %)
                                     (set class-ids)
                                     (into % class-ids)))))
                   (->> (all-objects b-onto)
                        (object-classification b-onto)))))

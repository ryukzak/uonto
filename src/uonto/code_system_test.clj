(ns uonto.code-system-test
  "
  Problem from here: https://github.com/HealthSamurai/termbox/discussions/44
  "
  (:require [uonto.core :as core]
            [uonto.misc :as misc]
            [clojure.spec.alpha :as s]
            [clojure.test :as t :refer [deftest is]]))

;; TODO: move to core

(defn def-instance! [onto object & [classes relation-class->objects]]
  (s/assert ::core/onto onto)
  (s/assert ::core/object object)
  (s/assert (s/or :no-classes          nil?
                  :classes             ::core/abstract-objects) classes)
  (s/assert (s/or :no-relation-classes nil?
                  :relation-classes    ::core/relation-class->objects)
            relation-class->objects)
  (let [all-classes (concat classes
                            (:core/is-instance relation-class->objects))]
    (core/def-object! onto object (assoc relation-class->objects
                                         :core/is-instance all-classes))))

(defn select-by-classes [onto classes & [objects]]
  (s/assert ::core/onto onto)
  (s/assert ::core/abstract-objects classes)
  (s/assert (s/or :all-objects nil?
                  :selected-objects ::core/objects) objects)
  (->> (or objects (core/all-objects onto))
       (filter (fn [object]
                 (every? (fn [class]
                           (contains? (core/object-classes onto object) class))
                         classes)))
       (into #{})))

;; TODO: support multiple inheritance. Return [[A] [B C] [D]]
(defn subclasses-upward-hierarchy [onto concepts]
  (s/assert ::core/onto onto)
  (s/assert ::core/objects concepts)
  (let [hierarchy-tuple
        (->> (core/tuples onto)
             (select-by-classes onto [:core/is-subclass])
             (filter (fn [tuple] (some #(contains? concepts %) tuple))))

        sorted-concepts
        (loop [tuples hierarchy-tuple
               concepts concepts
               acc '()]
          (if (empty? concepts)
            acc
            (let [subclasses (->> tuples (map first) (into #{}))
                  concept
                  (->> concepts
                       (remove (fn [concept] (contains? subclasses concept)))
                       (misc/unwrap-singleton!))]
              (recur (remove #(-> % second (= concept)) tuples)
                     (remove #(= % concept) concepts)
                     (cons concept acc)))))]

    (when-not (or (= 1 (count concepts))
                  (= (set (apply concat hierarchy-tuple))
                     (set concepts)))
      (throw (ex-info "Concepts to build subclasses upward hierarchy should be connected by :core/is-subclass."
                      {:concepts concepts
                       :hierarchy-tuple hierarchy-tuple})))

    sorted-concepts))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def language
  ^{:doc "Objects for multilanguage support."}
  (-> core/base
      (def-instance! :language/-itself [:core/class])
      (core/with-classes-> [:language/-itself :core/class]
        (def-instance! :language/en)
        (def-instance! :language/sp)
        (def-instance! :language/fr))))

(defn languages [onto]
  (select-by-classes onto [:language/-itself]))

(defn select-by-lang [onto lang objects]
  (s/assert ::core/onto onto)
  (s/assert ::core/objects objects)
  (s/assert ::core/abstract-object lang)
  (select-by-classes onto [lang] objects))

(deftest language-utils-test
  (let [onto (-> language
                 (def-instance! "in en" [:language/en])
                 (def-instance! "in sp" [:language/sp])
                 (def-instance! "in fr" [:language/fr]))
        objects (core/non-tuples onto)]
    (is (= #{:language/en :language/sp :language/fr}
           (languages onto)))
    (is (= #{"in en"}
           (select-by-lang onto :language/en objects)))
    (is (= {"in en" #{:language/en}}
           (->> objects
                (select-by-lang onto :language/en)
                (core/object-classification onto))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def code-system
  ^{:doc "Objects for code-system and related object representation."}
  (-> core/base
      (def-instance! :code-system/-itself [:core/class])
      (core/with-classes-> [:code-system/-itself :core/class]
        (def-instance! :code-system/code-system)
        (def-instance! :code-system/name)
        (def-instance! :code-system/title)

        (def-instance! :code-system/concept)
        (def-instance! :code-system/concept.code)
        (def-instance! :code-system/concept.display)
        (def-instance! :code-system/concept.designation))))

(def code-system:c
  ^{:doc "Objects for C code-system itself."}
  (-> (core/unify language code-system)
      (def-instance! :c/-itself [:code-system/code-system :core/class])
      (core/with-classes-> [:c/-itself :core/class]
        (def-instance! :c/concept.code        [] {:core/is-subclass [:code-system/concept.code]})
        (def-instance! :c/concept.display     [] {:core/is-subclass [:code-system/concept.display]})
        (def-instance! :c/concept.designation [] {:core/is-subclass [:code-system/concept.designation]}))
      (core/with-classes-> [:c/-itself]
        (def-instance! "C name"  [:code-system/name])
        (def-instance! "C title" [:code-system/title :language/en]))))

(defn- name->code-system! [onto]
  (s/assert ::core/onto onto)
  (let [names (->> (select-by-classes onto [:code-system/name])
                   (map (fn [name]
                          [name
                           (->> (core/object-classes onto name)
                                (select-by-classes onto [:code-system/code-system])
                                (misc/unwrap-singleton!))]))
                   (into {}))]
    names))

(defn resolve-code-system [onto name]
  (get (name->code-system! onto) name))

(deftest resolve-code-system-test
  (is (= {"C name" :c/-itself}
         (name->code-system! code-system:c)))
  (is (= :c/-itself
         (resolve-code-system code-system:c "C name"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def code-system:c:concepts
  ^{:doc "Object inside C code-system."}
  (-> (core/unify code-system:c language)
      (core/with-classes-> [:c/-itself]
        (def-instance! :c/concept->73211009 [:code-system/concept])
        (core/with-classes-> [:c/concept->73211009]
          (def-instance! "73211009" [:c/concept.code])
          (def-instance! "Diabetes mellitus (disorder)"
            [:c/concept.display
             :c/concept.designation :language/en])
          (def-instance! "Diabetes mellitus (trastorno)"
            [:c/concept.designation :language/sp]))

        (def-instance! :c/concept->126877002 [:code-system/concept]
          {:core/is-subclass [:c/concept->73211009]})
        (core/with-classes-> [:c/concept->126877002]
          (def-instance! "126877002" [:c/concept.code])
          (def-instance! "Disorder of glucose metabolism (disorder)" [:c/concept.display])
          (def-instance! "Disorder of glucose metabolism (disorder)" [:c/concept.designation :language/en])))
      ;; TODO: how not to forget?
      (core/infer-all)))

(defn resolve-code [onto code-system value]
  (s/assert ::core/onto onto)
  (s/assert ::core/abstract-object code-system)
  (s/assert ::core/information-object value)
  (let [result (select-by-classes onto
                                  [:code-system/concept.code code-system]
                                  [value])]
    (when-not (empty? result)
      (misc/unwrap-singleton! result))))

(defn- select-in-accordance-to-class-hierarchy [onto index objects]
  (->> objects
       (filter #(let [display-classes (core/object-classes onto %)
                      related-concepts (->> display-classes
                                            (select-by-classes onto [:code-system/concept]))]
                  (= index (count related-concepts))))
       doall))

(defn- describe-concept [onto index concept]
  (let [display (->> (select-by-classes onto [concept :code-system/concept.display])
                     (select-in-accordance-to-class-hierarchy onto index)
                     doall
                     (misc/unwrap-singleton!))
        designations (->> (select-by-classes onto [concept :code-system/concept.designation])
                          (select-in-accordance-to-class-hierarchy onto index))
        designation-by-lang
        (->> (languages onto)
             (map (fn [lang]
                    (let [txt (select-by-classes onto [lang] designations)]
                      (when (seq txt)
                        [lang (misc/unwrap-singleton! txt)]))))
             (into {}))]
    {:concept      concept
     :display      display
     :designations designation-by-lang}))

(defn describe-code [onto code-system value]
  (let [concepts (->> (core/object-classes onto value)
                      (select-by-classes onto [:code-system/concept code-system])
                      (subclasses-upward-hierarchy onto))

        concept-descriptions
        (->> concepts
             reverse
             (map-indexed (fn [index concept]
                            (describe-concept onto (inc index) concept)))
             reverse)]
    {:primary     (first concept-descriptions)
     :secondaries (into [] (rest concept-descriptions))}))

(deftest describe-code-test
  (let [onto code-system:c:concepts
        code-system (resolve-code-system onto "C name")]
    (is (= {:primary {:concept :c/concept->73211009,
                      :display "Diabetes mellitus (disorder)"
                      :designations
                      {:language/en "Diabetes mellitus (disorder)",
                       :language/sp "Diabetes mellitus (trastorno)"}},
            :secondaries []}
           (describe-code onto code-system "73211009")))
    (is (= {:primary {:concept :c/concept->126877002,
                      :display "Disorder of glucose metabolism (disorder)",
                      :designations
                      {:language/en "Disorder of glucose metabolism (disorder)"}},
            :secondaries
            [{:concept :c/concept->73211009,
              :display "Diabetes mellitus (disorder)",
              :designations
              {:language/en "Diabetes mellitus (disorder)",
               :language/sp "Diabetes mellitus (trastorno)"}}]}
           (describe-code onto code-system "126877002")))))

(defn validate-code [onto {code-system-name :system value-code :code}]
  (let [code-system (resolve-code-system onto code-system-name)
        code-value  (when code-system
                      (resolve-code onto code-system value-code))]
    {:result (some? code-value)}))

(deftest validate-code-test
  ;; FIXME: FHIR compliance
  (let [onto code-system:c:concepts]
    (is (= {:result false}
           (validate-code onto {:system "C name" :code "no"})))
    (is (= {:result false}
           (validate-code onto {:system "no" :code "73211009"})))
    (is (= {:result true}
           (validate-code onto {:system "C name" :code "73211009"})))
    (is (= {:result false}
           (validate-code onto {:system "73211009" :code "73211009"})))
    (is (= {:result false}
           (validate-code onto {:system "C name" :code "C name"})))))

(defn lookup [onto {code-system-name :system value-code :code}]
  (let [code-system (resolve-code-system onto code-system-name)
        code-description (when code-system
                           (describe-code onto code-system value-code))
        primary (:primary code-description)]
    {:display     (:display primary)
     :designation (:designations primary)}))

(deftest lookup-test
  ;; FIXME: FHIR compliance
  (let [onto code-system:c:concepts]
    (is (= {:display "Diabetes mellitus (disorder)",
            :designation
            #:language{:en "Diabetes mellitus (disorder)",
                       :sp "Diabetes mellitus (trastorno)"}}
           (lookup onto {:system "C name" :code "73211009"})))))

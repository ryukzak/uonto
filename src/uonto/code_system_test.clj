(ns uonto.code-system-test
  "
  Problem from here: https://github.com/HealthSamurai/termbox/discussions/44
  "
  (:require [uonto.core :as core]
            [uonto.misc :as misc]
            [clojure.spec.alpha :as s]
            [clojure.test :as t :refer [deftest is]]))

;; helpers

(defn def-instance! [onto object & classes]
  (core/def-object! onto object {:core/is-instance classes}))

(defn def-class [onto object & {:keys [instance-of subclass-of]}]
  (core/def-object! onto object {:core/is-instance (concat [:core/class] instance-of)
                                 :core/is-subclass subclass-of}))

(defn select-by-classes [onto classes & [objects]]
  (s/assert ::core/onto onto)
  (s/assert (s/coll-of ::core/abstract-object) classes)
  (s/assert (s/or :all-objects nil?
                  :selected-objects ::core/objects) objects)
  (->> (or objects (core/all-objects onto))
       (filter (fn [object]
                 (every? (fn [class]
                           (contains? (core/object-classes onto object) class))
                         classes)))
       (into #{})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def language
  ^{:doc "Objects for multilanguage support."}
  (-> core/base
      (def-class :language/_itself)
      (def-instance! :language/en :language/_itself :core/class)
      (def-instance! :language/sp :language/_itself :core/class)
      (def-instance! :language/fr :language/_itself :core/class)))

(defn languages [onto]
  (select-by-classes onto [:language/_itself]))

(defn select-by-lang [onto lang objects]
  (s/assert ::core/onto onto)
  (s/assert ::core/objects objects)
  (s/assert ::core/abstract-object lang)
  (select-by-classes onto [lang] objects))

(deftest language-utils-test
  (let [onto (-> language
                 (def-instance! "in en" :language/en)
                 (def-instance! "in sp" :language/sp)
                 (def-instance! "in fr" :language/fr))
        objects (core/non-tuples onto)]
    (is (= #{:language/en :language/sp :language/fr}
           (languages onto)))
    (is (= #{"in en"}
           (select-by-lang onto :language/en objects)))
    (is (= {"in en" #{:language/en}}
           (->> objects
                (select-by-lang onto :language/en)
                (core/object-classification onto))))))

(def code-system
  ^{:doc "Objects for code-system and related object representation."}
  (-> core/base
      (def-class :code-system/-itself)
      (assoc :with-default-classes [:code-system/-itself :core/class])
      (def-class :code-system/concept)
      (def-class :code-system/code-system)
      (def-class :code-system/name)
      (def-class :code-system/title)
      (def-class :code-system/concept-code)
      (def-class :code-system/concept:display)
      (def-class :code-system/concept:designation)
      (dissoc :with-default-classes)))

(defn select-codesystems [onto]
  (select-by-classes onto [:code-system/code-system]))

(def code-system:c
  ^{:doc "Objects for C code-system itself."}
  (-> (core/unify language code-system)
      (def-instance! :c/-itself :code-system/code-system :core/class)
      (core/with-classes-> [:c/-itself]
        (core/with-classes-> [:core/class]
          (def-class :c/concept:code        :subclass-of [:code-system/concept-code])
          (def-class :c/concept:display     :subclass-of [:code-system/concept:display])
          (def-class :c/concept:designation :subclass-of [:code-system/concept:designation]))
        (def-instance! "C name"  :code-system/name)
        (def-instance! "C title" :code-system/title :language/en))))

(deftest select-codesystems-test
  (is (= #{:c/-itself}
         (select-codesystems code-system:c)))
  (is (= #{}
         (select-codesystems code-system))))

(defn name->code-system! [onto]
  (s/assert ::core/onto onto)
  (let [names (->> (select-by-classes onto [:code-system/name])
                   (map (fn [name]
                          [name
                           (->> (core/object-classes onto name)
                                (select-by-classes onto [:code-system/code-system])
                                (misc/singleton-unwrap!))]))
                   (into {}))]
    names))

(defn resolve-code-system [onto name]
  (get (name->code-system! onto) name))

(deftest resolve-code-system-test
  (is (= {"C name" :c/-itself}
         (name->code-system! code-system:c)))
  (is (= :c/-itself
         (resolve-code-system code-system:c "C name"))))

(def code-system:c:concepts
  ^{:doc "Object inside C code-system."}
  (-> (core/unify code-system:c language)
      (core/with-classes-> [:c/-itself]
        (def-class :c:concept/-73211009 :instance-of [:code-system/concept])
        (core/with-classes-> [:c:concept/-73211009]
          (def-instance! "73211009" :c/concept:code)
          (def-instance! "Diabetes mellitus (disorder)"
            :c/concept:display
            :c/concept:designation :language/en)
          (def-instance! "Diabetes mellitus (trastorno)"
            :c/concept:designation :language/sp))

        (def-class :c:concept/-126877002
          :instance-of [:code-system/concept]
          :subclass-of [:c:concept/-73211009])
        (core/with-classes-> [:c:concept/-126877002]
          (def-instance! "126877002" :c/concept:code)
          (def-instance! "Disorder of glucose metabolism (disorder)" :c/concept:display)
          (def-instance! "Disorder of glucose metabolism (disorder)" :c/concept:designation :language/en)))

      (core/infer-all)))

;; TODO: add inferred? check for onto before any select.

(defn resolve-code [onto code-system value]
  (s/assert ::core/onto onto)
  (s/assert ::core/abstract-object code-system)
  (s/assert ::core/information-object value)
  (let [result (select-by-classes onto
                                  [:code-system/concept-code code-system]
                                  [value])]
    (when-not (empty? result)
      (misc/singleton-unwrap! result))))

;; TODO: how to show codes with subclasses?

;; TODO: move to core
(defn sort-subclasses-upward [onto concepts]
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
                       (misc/singleton-unwrap!))]
              (recur (remove #(-> % second (= concept)) tuples)
                     (remove #(= % concept) concepts)
                     (cons concept acc)))))]
    sorted-concepts))

(defn- select-in-accordance-to-class-hierarchy [onto index objects]
  (->> objects
       (filter #(let [display-classes (core/object-classes onto %)
                      related-concepts (->> display-classes
                                            (select-by-classes onto [:code-system/concept]))]
                  (= index (count related-concepts))))
       doall))

(defn- describe-concept [onto index concept]
  (let [display (->> (select-by-classes onto [concept :c/concept:display])
                     (select-in-accordance-to-class-hierarchy onto index)
                     doall
                     (misc/singleton-unwrap!))
        designations (->> (select-by-classes onto [concept :c/concept:designation])
                          (select-in-accordance-to-class-hierarchy onto index))
        designation-by-lang
        (->> (languages onto)
             (map (fn [lang]
                    (let [txt (select-by-classes onto [lang] designations)]
                      (when (seq txt)
                        [lang (misc/singleton-unwrap! txt)]))))
             (into {}))]
    {:concept      concept
     :display      display
     :designations designation-by-lang}))

(defn describe-code [onto code-system value]
  (let [concepts (->> (core/object-classes onto value)
                      (select-by-classes onto [:code-system/concept code-system])
                      (sort-subclasses-upward onto))

        concept-descs
        (->> concepts reverse
             (map-indexed (fn [index concept]
                            (describe-concept onto (inc index) concept)))
             reverse)]
    {:primary     (first concept-descs)
     :secondaries (into [] (rest concept-descs))}))

(deftest describe-code-test
  (let [onto code-system:c:concepts
        code-system (resolve-code-system onto "C name")]
    (is (= {:primary {:concept :c:concept/-73211009,
                      :display "Diabetes mellitus (disorder)"
                      :designations
                      {:language/en "Diabetes mellitus (disorder)",
                       :language/sp "Diabetes mellitus (trastorno)"}},
            :secondaries []}
           (describe-code onto code-system "73211009")))
    (is (= {:primary {:concept :c:concept/-126877002,
                      :display "Disorder of glucose metabolism (disorder)",
                      :designations
                      {:language/en "Disorder of glucose metabolism (disorder)"}},
            :secondaries
            [{:concept :c:concept/-73211009,
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

(comment

  (defn id-consistency [onto]
    (doseq [object (keys (:object->id onto))]
      (assert (= object (core/id->object! onto (core/object->id! onto object))))))

  (id-consistency core/base)

  (core/object->id! code-system:c :code-system/name)
  (core/id->object! code-system:c 10)

  (->> (:object->id code-system:c)
       (filter (fn [[_ i]] (= i 10)))
       first first)

  (core/object->id! code-system:c "C")
  (core/id->object! code-system:c 10)
  (core/id->object! code-system:c 22)
  (get (:classification code-system:c) 37)

  (core/object-classes code-system:c "C")
  #_(clojure.pprint/pprint (->> code-system:c
                                (core/non-tuples)
                                (core/object-classification code-system:c)
                                (core/remove-core-from-classification))))

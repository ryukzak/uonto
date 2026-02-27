(ns uonto.code-system-test
  "
  Problem from here: https://github.com/HealthSamurai/termbox/discussions/44
  "
  (:require [uonto.core :as core]
            [uonto.misc :as misc]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is]]))

(def language
  ^{:doc "Objects for multilanguage support."}
  (-> core/base
      (core/def-instance! :language/-itself [:core/class])
      (core/with-classes-> [:language/-itself :core/class]
        (core/def-instance! :language/en)
        (core/def-instance! :language/sp)
        (core/def-instance! :language/fr))))

(defn languages [onto]
  (core/select-by-classes onto [:language/-itself]))

(defn select-by-lang [onto lang objects]
  (s/assert ::core/onto onto)
  (s/assert ::core/objects objects)
  (s/assert ::core/abstract-object lang)
  (core/select-by-classes onto [lang] objects))

(deftest language-utils-test
  (let [onto (-> language
                 (core/def-instance! "in en" [:language/en])
                 (core/def-instance! "in sp" [:language/sp])
                 (core/def-instance! "in fr" [:language/fr]))
        objects (core/select-non-tuples onto)]
    (is (= #{:language/en :language/sp :language/fr}
           (languages onto)))
    (is (= #{"in en"}
           (select-by-lang onto :language/en objects)))
    (is (= {"in en" #{:language/en}}
           (->> objects
                (select-by-lang onto :language/en)
                (core/classification onto))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def code-system
  ^{:doc "Objects for code-system and related object representation."}
  (-> core/base
      (core/def-instance! :code-system/-itself [:core/class])
      (core/with-classes-> [:code-system/-itself :core/class]
        (core/def-instance! :code-system/code-system)
        (core/def-instance! :code-system/code-system.name)
        (core/def-instance! :code-system/code-system.title)

        (core/def-instance! :code-system/concept)
        (core/def-instance! :code-system/concept.code)
        (core/def-instance! :code-system/concept.display)
        (core/def-instance! :code-system/concept.designation))))

(def code-system:c
  ^{:doc "Objects for C code-system itself."}
  (-> (core/unify language code-system)
      (core/def-instance! :code-system.c/-itself [:code-system/code-system :core/class])
      (core/with-classes-> [:code-system.c/-itself :core/class]
        (core/def-instance! :code-system.c/concept.code        [] {:core/is-subclass [:code-system/concept.code]})
        (core/def-instance! :code-system.c/concept.display     [] {:core/is-subclass [:code-system/concept.display]})
        (core/def-instance! :code-system.c/concept.designation [] {:core/is-subclass [:code-system/concept.designation]}))
      (core/with-classes-> [:code-system.c/-itself]
        (core/def-instance! "C name"  [:code-system/code-system.name])
        (core/def-instance! "C title" [:code-system/code-system.title :language/en]))))

(defn- name->code-system! [onto]
  (s/assert ::core/onto onto)
  (let [names (->> (core/select-by-classes onto [:code-system/code-system.name])
                   (map (fn [name]
                          [name
                           (->> (core/object-classes onto name)
                                (core/select-by-classes onto [:code-system/code-system])
                                (misc/unwrap-singleton!))]))
                   (into {}))]
    names))

(defn resolve-code-system [onto name]
  (get (name->code-system! onto) name))

(deftest resolve-code-system-test
  (is (= {"C name" :code-system.c/-itself}
         (name->code-system! code-system:c)))
  (is (= :code-system.c/-itself
         (resolve-code-system code-system:c "C name"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def code-system:c:concepts
  ^{:doc "Object inside C code-system."}
  (-> (core/unify code-system:c language)
      (core/with-classes-> [:code-system.c/-itself]
        (core/def-instance! :code-system.c/concept->73211009 [:code-system/concept])
        (core/with-classes-> [:code-system.c/concept->73211009]
          (core/def-instance! "73211009" [:code-system.c/concept.code])
          (core/def-instance! "Diabetes mellitus (disorder)"
            [:code-system.c/concept.display
             :code-system.c/concept.designation :language/en])
          (core/def-instance! "Diabetes mellitus (trastorno)"
            [:code-system.c/concept.designation :language/sp]))

        (core/def-instance! :code-system.c/concept->126877002 [:code-system/concept]
          {:core/is-subclass [:code-system.c/concept->73211009]})
        (core/with-classes-> [:code-system.c/concept->126877002]
          (core/def-instance! "126877002" [:code-system.c/concept.code])
          (core/def-instance! "Disorder of glucose metabolism (disorder)" [:code-system.c/concept.display])
          (core/def-instance! "Disorder of glucose metabolism (disorder)" [:code-system.c/concept.designation :language/en])))
      ;; TODO: how not to forget?
      (core/infer-all)))

(defn resolve-code [onto code-system value]
  (s/assert ::core/onto onto)
  (s/assert ::core/abstract-object code-system)
  (s/assert ::core/information-object value)
  (let [result (core/select-by-classes onto
                                       [:code-system/concept.code code-system]
                                       [value])]
    (when-not (empty? result)
      (misc/unwrap-singleton! result))))

(defn- select-in-accordance-to-class-hierarchy [onto index objects]
  (->> objects
       (filter #(let [display-classes (core/object-classes onto %)
                      related-concepts (->> display-classes
                                            (core/select-by-classes onto [:code-system/concept]))]
                  (= index (count related-concepts))))
       doall))

(defn- describe-concept [onto index concept]
  (let [display (->> (core/select-by-classes onto [concept :code-system/concept.display])
                     (select-in-accordance-to-class-hierarchy onto index)
                     doall
                     (misc/unwrap-singleton!))
        designations (->> (core/select-by-classes onto [concept :code-system/concept.designation])
                          (select-in-accordance-to-class-hierarchy onto index))
        designation-by-lang
        (->> (languages onto)
             (map (fn [lang]
                    (let [txt (core/select-by-classes onto [lang] designations)]
                      (when (seq txt)
                        [lang (misc/unwrap-singleton! txt)]))))
             (into {}))]
    {:concept      concept
     :display      display
     :designations designation-by-lang}))

(defn describe-code [onto code-system value]
  (let [conceptss (->> (core/object-classes onto value)
                       (core/select-by-classes onto [:code-system/concept code-system])
                       (core/subclasses-upward-hierarchy onto))

        concept-descriptions
        (->> conceptss
             reverse
             (map-indexed (fn [index concepts]
                            (map #(describe-concept onto (inc index) %) concepts)))
             reverse
             (apply concat))]
    {:primary     (first concept-descriptions)
     :secondaries (into [] (rest concept-descriptions))}))

(deftest describe-code-test
  (let [onto code-system:c:concepts
        code-system (resolve-code-system onto "C name")]
    (is (= {:primary {:concept :code-system.c/concept->73211009,
                      :display "Diabetes mellitus (disorder)"
                      :designations
                      {:language/en "Diabetes mellitus (disorder)",
                       :language/sp "Diabetes mellitus (trastorno)"}},
            :secondaries []}
           (describe-code onto code-system "73211009")))
    (is (= {:primary {:concept :code-system.c/concept->126877002,
                      :display "Disorder of glucose metabolism (disorder)",
                      :designations
                      {:language/en "Disorder of glucose metabolism (disorder)"}},
            :secondaries
            [{:concept :code-system.c/concept->73211009,
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

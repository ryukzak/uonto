(ns uonto.core-test
  (:require [uonto.core :as core]
            [clojure.test :as t :refer [deftest is testing]]))

(deftest register-object-test
  (is (= {:object->id {:foo 0} :value 0}
         (core/register-object {} :foo)))

  (is (= {:object->id {:foo 0
                       :bar 1}
          :value 1}
         (-> {}
             (core/register-object :foo)
             (core/register-object :bar))))

  (is (= {:object->id {:foo 0
                       :bar 1
                       [:foo :bar] 2}
          :value 2}
         (-> {}
             (core/register-object :foo)
             (core/register-object :bar)
             (core/register-object [:foo :bar])))))

(deftest object->id-test
  (is (= 0
         (core/object->id! (core/register-object {} :foo) :foo)))

  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Can't resolve object to id"
                        (core/object->id! (core/register-object {} :foo) :bar)))

  (let [big-onto (->> (range (+ core/known-object-limit 5))
                      (reduce (fn [st i] (core/register-object st i))
                              {}))
        ex (try
             (core/object->id! big-onto :bar)
             (catch clojure.lang.ExceptionInfo e
               e))]
    (is (= core/known-object-limit
           (-> ex ex-data :some-known-objects count)))
    (is (= :bar
           (-> ex ex-data :object)))
    (is (= 5
           (-> ex ex-data :not-listed-object-count)))))

(deftest register-relations-test
  (let [onto (-> {}
                 (core/register-object :rel-class)
                 (core/register-object :bar)
                 (core/register-object :baz)
                 (core/register-relation :rel-class :bar :baz))]
    (is (= {:rel-class  #{},
            :bar        #{},
            :baz        #{},
            [:bar :baz] #{:rel-class}}
           (->> onto
                core/select-all-objects
                (core/classification onto)))))
  (let [onto (-> {}
                 (core/register-object :rel-class)
                 (core/register-object :bar)
                 (core/register-object :baz)
                 (core/register-relations :rel-class :bar [:baz]))]
    (is (= {:rel-class  #{},
            :bar        #{},
            :baz        #{},
            [:bar :baz] #{:rel-class}}
           (->> onto
                core/select-all-objects
                (core/classification onto))))))

(deftest classify-object-test
  (testing "classify object"
    (let [onto (-> {}
                   (core/register-object :core/is-instance)
                   (core/register-object :foo)
                   (core/register-object :bar)
                   (core/register-object [:foo :bar])
                   (core/classify-object! :foo [:bar]))]
      (is (= #{:bar} (core/object-classes onto :foo)))
      (is (= #{}     (core/object-classes onto :bar)))
      (is (= {:core/is-instance #{},
              :foo              #{:bar},
              :bar              #{},
              [:foo :bar]       #{:core/is-instance}}
             (->> onto
                  core/select-all-objects
                  (core/classification onto))))))

  (testing "classify relation"
    (let [onto (-> {}
                   (core/register-object :core/is-instance)
                   (core/register-object :foo)
                   (core/register-object :bar)
                   (core/register-object [:foo :bar])
                   (core/classify-object! [:foo :bar] [:bar]))]
      (is (= {:core/is-instance  #{},
              :foo               #{},
              :bar               #{},
              [:foo :bar]        #{:bar},
              [[:foo :bar] :bar] #{:core/is-instance}}
             (->> onto
                  core/select-all-objects
                  (core/classification onto)))))))

(deftest def-object-test
  (let [onto (-> {}
                 (core/register-object :core/class)
                 (core/register-object :core/is-instance)
                 (core/def-object! :core/class       {:core/is-instance []})
                 (core/def-object! :core/is-instance {:core/is-instance [:core/class]}))]
    (is (= {:core/class       #{},
            :core/is-instance #{:core/class}}
           (->> onto
                core/select-abstract-objects
                (core/classification onto))))
    (is (= {[:core/is-instance :core/class] #{:core/is-instance}}
           (->> onto
                core/select-tuples
                (core/classification onto)))))
  (testing "with default classes"
    (let [onto (-> core/base
                   (core/def-object! :foo {})
                   (assoc :with-default-classes [:core/class :foo])
                   (core/def-object! :bar {})
                   (dissoc :with-default-classes)
                   (core/def-object! :baz {}))]
      (is (= {:foo #{}
              :bar #{:core/class :foo}
              :baz #{}}
             (->> onto
                  core/select-non-tuples
                  (core/classification onto)
                  (core/remove-core))))))

  (testing "with default classes by with-default-classes"
    (let [onto (-> core/base
                   (core/def-object! :foo {})
                   (core/with-classes-> [:core/class]
                     (core/with-classes-> [:foo]
                       (core/def-object! :bar {}))
                     (core/def-object! :bas {}))
                   (core/def-object! :bat {}))]
      (is (= {:foo #{}
              :bar #{:core/class :foo}
              :bas #{:core/class}
              :bat #{}}
             (->> onto
                  core/select-non-tuples
                  (core/classification onto)
                  core/remove-core))))))

(deftest subclass-hierarchy-test
  "
  ```
    A
  B   C
    D
    E
  ```
  "
  (let [object->class {:a :A, :b :B, :c :C, :d :D, :e :E}
        onto (-> core/base
                 (core/->reduce (fn [st [object class]]
                                     (-> st
                                         (core/register-object class)
                                         (core/classify-object! class [:core/class])
                                         (core/register-object object)
                                         (core/classify-object! object [class])))
                                   object->class)
                 (core/def-object! :B {:core/is-subclass [:A]})
                 (core/def-object! :C {:core/is-subclass [:A]})
                 (core/def-object! :D {:core/is-subclass [:B :C]})
                 (core/def-object! :E {:core/is-subclass [:D]}))

        {infer-subclasses :value
         :as onto+transitive-subclasses} (core/infer-transitive-subclasses onto)

        {infer-instances :value
         :as onto+infer-instances} (core/infer-subclasses-instances onto+transitive-subclasses)]

    (is (= {[:D :A] #{:core/is-subclass},
            [:E :B] #{:core/is-subclass},
            [:E :A] #{:core/is-subclass},
            [:E :C] #{:core/is-subclass}}
           (core/classification onto+transitive-subclasses infer-subclasses)))

    (is (empty? (:value (core/infer-transitive-subclasses onto+transitive-subclasses))))

    (is (= {[:b :A] #{:core/is-instance}
            [:c :A] #{:core/is-instance}
            [:d :A] #{:core/is-instance}
            [:d :B] #{:core/is-instance}
            [:d :C] #{:core/is-instance}
            [:e :A] #{:core/is-instance}
            [:e :B] #{:core/is-instance}
            [:e :C] #{:core/is-instance}
            [:e :D] #{:core/is-instance}}
           (core/classification onto+infer-instances infer-instances)))

    (is (empty? (:value (core/infer-subclasses-instances onto+infer-instances))))

    (is (= #{:A}             (core/object-classes onto+infer-instances :a)))
    (is (= #{:A :B}          (core/object-classes onto+infer-instances :b)))
    (is (= #{:A :C}          (core/object-classes onto+infer-instances :c)))
    (is (= #{:A :B :C :D}    (core/object-classes onto+infer-instances :d)))
    (is (= #{:A :B :C :D :E} (core/object-classes onto+infer-instances :e)))

    (is (= [#{:E} #{:D} #{:B :C} #{:A}]
           (core/subclasses-upward-hierarchy onto #{:A :B :C :D :E})))))

(deftest unify-test
  (let [a-onto (-> core/base
                   (core/register-object :a1)
                   (core/register-object :a2)
                   (core/def-object! :a1 {:core/is-instance [:a2]}))
        b-onto (-> core/base
                   (core/register-object :b1)
                   (core/register-object :b2)
                   (core/def-object! :b1 {:core/is-instance [:b2]}))
        ab-onto (core/unify a-onto b-onto)]

    (is (= #{:core/class :core/is-instance :core/is-subclass
             :a1 :a2 :b1 :b2}
           (core/select-non-tuples ab-onto)))

    (is (= {[:a1 :a2] #{:core/is-instance}
            [:b1 :b2] #{:core/is-instance}
            [:core/is-instance :core/class] #{:core/is-instance}
            [:core/is-subclass :core/class] #{:core/is-instance}}
           (->> (core/select-tuples ab-onto)
                (core/classification ab-onto))))))

(ns uonto.core-test
  (:require [uonto.core :as core]
            [clojure.test :as t :refer [deftest is]]))

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

(deftest register-relatons-test
  (let [onto (-> {}
                 (core/register-object :foo)
                 (core/register-object :bar)
                 (core/register-object :baz)
                 (core/register-relations :foo :bar [:baz]))]
    (is (= {:foo #{},
            :bar #{},
            :baz #{},
            [:bar :baz] #{:foo}}
           (->> onto
                core/all-objects
                (core/object-classification onto))))))

(deftest classfy-object-test
  (let [onto (-> {}
                 (core/register-object :core/is-instance)
                 (core/register-object :foo)
                 (core/register-object :bar)
                 (core/register-object [:foo :bar])
                 (core/classify-object :foo [:bar]))]
    (is (= #{:bar} (core/object-classes onto :foo)))
    (is (= #{} (core/object-classes onto :bar)))
    (is (= {:core/is-instance #{},
            :foo              #{:bar},
            :bar              #{},
            [:foo :bar]       #{:core/is-instance}}
           (->> onto
                core/all-objects
                (core/object-classification onto)))))

  (let [onto (-> {}
                 (core/register-object :core/is-instance)
                 (core/register-object :foo)
                 (core/register-object :bar)
                 (core/register-object [:foo :bar])
                 (core/classify-object [:foo :bar] [:bar]))]
    (is (= {:core/is-instance  #{},
            :foo               #{},
            :bar               #{},
            [:foo :bar]        #{:bar},
            [[:foo :bar] :bar] #{:core/is-instance}}
           (->> onto
                core/all-objects
                (core/object-classification onto))))))

(deftest def-object-test
  (let [onto (-> {}
                 (core/register-object :core/class)
                 (core/register-object :core/is-instance)
                 (core/def-object :core/class       {:core/is-instance []})
                 (core/def-object :core/is-instance {:core/is-instance [:core/class]}))]
    (is (= {:core/class       #{},
            :core/is-instance #{:core/class}}
           (->> onto
                core/non-tuples
                (core/object-classification onto))))
    (is (= {[:core/is-instance :core/class] #{:core/is-instance}}
           (->> onto
                core/tuples
                (core/object-classification onto))))))

(deftest class-hierarchy-test
  "
     A
   B   C
     D
     E
  "
  (let [object->class {:a :A, :b :B, :c :C, :d :D, :e :E}
        onto (-> core/base
                 (core/flip-reduce (fn [st [object class]]
                                     (-> st
                                         (core/register-object class)
                                         (core/classify-object class [:core/class])
                                         (core/register-object object)
                                         (core/classify-object object [class])))
                                   object->class)
                 (core/def-object :B {:core/is-subclass [:A]})
                 (core/def-object :C {:core/is-subclass [:A]})
                 (core/def-object :D {:core/is-subclass [:B :C]})
                 (core/def-object :E {:core/is-subclass [:D]}))

        {infer-subclasses :value
         :as onto+transitive-subclasses} (core/infer-transitive-subclasses onto)

        {infer-instances :value
         :as onto+infer-instaces} (core/infer-subclasses-instances onto+transitive-subclasses)]

    (is (= {[:D :A] #{:core/is-subclass},
            [:E :B] #{:core/is-subclass},
            [:E :A] #{:core/is-subclass},
            [:E :C] #{:core/is-subclass}}
           (->> infer-subclasses
                (core/object-classification onto+transitive-subclasses))))

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
           (->> infer-instances
                (core/object-classification onto+infer-instaces))))

    (is (empty? (:value (core/infer-subclasses-instances onto+infer-instaces))))

    (is (= #{:A}             (core/object-classes onto+infer-instaces :a)))
    (is (= #{:A :B}          (core/object-classes onto+infer-instaces :b)))
    (is (= #{:A :C}          (core/object-classes onto+infer-instaces :c)))
    (is (= #{:A :B :C :D}    (core/object-classes onto+infer-instaces :d)))
    (is (= #{:A :B :C :D :E} (core/object-classes onto+infer-instaces :e)))))

(deftest unity-test
  (let [a-onto (-> core/base
                   (core/register-object :a1)
                   (core/register-object :a2)
                   (core/def-object :a1 {:core/is-instance [:a2]}))
        b-onto (-> core/base
                   (core/register-object :b1)
                   (core/register-object :b2)
                   (core/def-object :b1 {:core/is-instance [:b2]}))
        ab-onto (core/unify a-onto b-onto)]

    (is (= #{:core/class :core/is-instance :core/is-subclass
             :a1 :a2 :b1 :b2}
           (core/non-tuples ab-onto)))

    (is (= {[:a1 :a2] #{:core/is-instance}
            [:b1 :b2] #{:core/is-instance}
            [:core/is-instance :core/class] #{:core/is-instance}
            [:core/is-subclass :core/class] #{:core/is-instance}}
           (->> (core/tuples ab-onto)
                (core/object-classification ab-onto))))))

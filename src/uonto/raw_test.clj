(ns uonto.raw-test
  (:require
   [clojure.test :as t :refer [deftest is]]
   [uonto.raw :as raw]))

(deftest pretty-core-object-test
  (raw/with-onto {:isolate true}
    (is (= {:core/class #{:core/class},
            :core/is-instance #{:core/class},
            :core/is-subclass #{:core/class},
            [:core/class :core/class] #{:core/is-instance},
            [:core/is-instance :core/class] #{:core/is-instance},
            [:core/is-subclass :core/class] #{:core/is-instance}}
           (-> (raw/all-objects) raw/repr-verbose)))

    (is (= #:core{:class #{:core/class},
                  :is-instance #{:core/class},
                  :is-subclass #{:core/class}}
           (raw/repr-verbose [raw/core:class
                              raw/core:is-instance
                              raw/core:is-subclass])))

    (is (empty? (->> (raw/all-objects)
                     raw/remove-core)))))

(deftest with-onto-test
  (let [*onto (raw/with-onto {:isolate true :return :*onto}
                (raw/def-object! (raw/object->id :foo) {})
                (is (= [6] (->> (raw/all-objects) raw/remove-core))))]

    (is (= 42 (raw/with-onto {:isolate true :*onto *onto}
                (is (= [6] (->> (raw/all-objects) raw/remove-core)))
                42)))

    (raw/with-onto {}
      (is (= [] (->> (raw/all-objects) raw/remove-core))))

    (raw/with-onto {:isolate false :*onto *onto}
      (raw/def-object! (raw/object->id :bar) {})
      (is (= [6 7] (->> (raw/all-objects) raw/remove-core))))

    (raw/with-onto {:isolate false :*onto *onto}
      (is (= [6 7] (->> (raw/all-objects) raw/remove-core))))))

(deftest def-object-and-object-selector-test
  (raw/with-onto {:isolate true}
    (let [c (raw/object->id :c)]
      (is (= c
             (raw/def-object! c {raw/core:is-instance [raw/core:class]})))
      (is (= {:c                #{:core/class},
              [:c :core/class]  #{:core/is-instance}}
             (-> (raw/all-objects) raw/remove-core raw/repr-verbose)))

      (is (= {:c                #{:core/class}}
             (-> (raw/abstract-objects) raw/remove-core raw/repr-verbose)))

      (is (= {[:c :core/class]  #{:core/is-instance}}
             (-> (raw/tuples) raw/remove-core raw/repr-verbose)))

      (is (= {:c                #{:core/class}}
             (-> (raw/instances raw/core:class) raw/remove-core raw/repr-verbose))))))

(deftest class-hierarchy-test
  "
     A
   B   C
     D
     E
  "
  (raw/with-onto {:isolate true}
    (let [[A B C D E :as clss] (map raw/object->id [:A :B :C :D :E])
          [a b c d e :as objs] (map raw/object->id [:a :b :c :d :e])]
      (doall (map (fn [cls] (raw/def-object! cls {raw/core:is-instance [raw/core:class]}))
                  clss))
      (doall (map (fn [cls obj] (raw/def-object! obj {raw/core:is-instance [cls]}))
                  clss objs))
      (raw/def-object! B {raw/core:is-subclass [A]})
      (raw/def-object! C {raw/core:is-subclass [A]})
      (raw/def-object! D {raw/core:is-subclass [B C]})
      (raw/def-object! E {raw/core:is-subclass [D]})

      (is (= {[:E :C] #{:core/is-subclass}
              [:E :B] #{:core/is-subclass}
              [:D :A] #{:core/is-subclass}
              [:E :A] #{:core/is-subclass}}
             (->> (raw/infer-transitive-subclasses!)
                  raw/repr-verbose)))
      (is (empty? (raw/infer-transitive-subclasses!)))

      (is (= {[:b :A] #{:core/is-instance}
              [:c :A] #{:core/is-instance}
              [:d :A] #{:core/is-instance}
              [:d :B] #{:core/is-instance}
              [:d :C] #{:core/is-instance}
              [:e :A] #{:core/is-instance}
              [:e :B] #{:core/is-instance}
              [:e :C] #{:core/is-instance}
              [:e :D] #{:core/is-instance}}
             (->> (raw/infer-subclasses-instances!)
                  raw/repr-verbose)))
      (is (empty? (raw/infer-subclasses-instances!)))

      (is (= #{A} (raw/classes a)))
      (is (= #{A B} (raw/classes b)))
      (is (= #{A C} (raw/classes c)))
      (is (= #{A B C D} (raw/classes d)))
      (is (= #{A B C D E} (raw/classes e)))

      (is (raw/is-instance? A a))
      (is (raw/is-instance? A e))
      (is (not (raw/is-instance? B a)))

      (is (= #{:A} (->> (raw/classes a) raw/repr)))
      (is (= #{:A :B :C :D :E} (->> (raw/classes e) raw/repr))))))

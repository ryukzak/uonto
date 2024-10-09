(ns uonto.human-test
  (:require
   [clojure.spec.alpha :as s]
   [clojure.test :as t :refer [deftest is testing]]
   [uonto.human :as h]
   [uonto.misc :as misc]
   [uonto.raw :as raw]))

(s/check-asserts true)

(deftest class-hierarchy-test
  " raw-test/class-hierarchy-test in human format
     A
   B   C
     D
     E
  "
  (raw/with-onto
   (let [clss [:A :B :C :D :E]
         objs [:a :b :c :d :e]]

     (doall (map (fn [cls] (h/def-object! cls {:core/is-instance [:core/class]}))
                 clss))
     (doall (map (fn [cls obj] (h/def-object! obj {:core/is-instance [cls]}))
                 clss objs))
     (h/def-object! :B {:core/is-subclass [:A]})
     (h/def-object! :C {:core/is-subclass [:A]})
     (h/def-object! :D {:core/is-subclass [:B :C]})
     (h/def-object! :E {:core/is-subclass [:D]})

     (is (= #{[:E :A] [:E :B] [:E :C] [:D :A]}
            (->> (raw/infer-transitive-subclasses!) raw/repr*)))
     (is (empty? (raw/infer-transitive-subclasses!)))

     (is (= #{[:b :A] [:c :A] [:d :A] [:d :B] [:d :C] [:e :A] [:e :B] [:e :C] [:e :D]}
            (->> (raw/infer-subclasses-instances!) raw/repr*)))
     (is (empty? (raw/infer-subclasses-instances!)))

     (is (= #{:A} (h/classes :a)))
     (is (= #{:A :B} (h/classes :b)))
     (is (= #{:A :C} (h/classes :c)))
     (is (= #{:A :B :C :D} (h/classes :d)))
     (is (= #{:A :B :C :D :E} (h/classes :e)))

     (is (h/is-instance? :A :a))
     (is (h/is-instance? :A :e))
     (is (not (h/is-instance? :B :a)))

     (is (= #{:A}
            (-> (h/classes :a) h/repr)))
     (is (= {:A #{:core/class}}
            (-> (h/classes :a) h/repr-verbose))))))

(deftest multilanguage-example-test []
  (raw/with-onto {}
   (testing "Root classes"
     (h/def-object! :code-system    {:core/is-instance [:core/class]})
     (h/def-object! :code-system-id {:core/is-instance [:core/class]})

     (prn :classes :code-system 1 (h/classes :code-system))

     (h/def-object! :code-value   {:core/is-instance [:core/class]})
     (h/def-object! :display      {:core/is-instance [:core/class]})
     (h/def-object! :designation  {:core/is-instance [:core/class]})
     (h/def-object! :languages    {:core/is-instance [:core/class]}))

   (testing "Languages"
     (h/def-object! :languages/en
       {:core/is-instance         [:core/class :languages]})
     (h/def-object! :languages/sp
       {:core/is-instance         [:core/class :languages]}))

   (testing "Define code system 'C'"
     (h/def-object! :code-system/c
       {:core/is-instance         [:core/class :code-system]})
     (h/def-object! "C"
       {:core/is-instance         [:core/class :code-system/c :code-system-id]})
     (h/def-object! "C-CODE-SYSTEM"
       {:core/is-instance         [:core/class :code-system/c :display]}))

   (testing "Define concepts in 'C'"
     (h/def-object! :c/id-1
       {:core/is-instance         [:core/class :code-system/c]})
     (h/def-object! "73211009"
       {:core/is-instance [:c/id-1 :code-system/c :code-value]})
     (h/def-object! "Diabetes mellitus (disorder)"
       {:core/is-instance [:c/id-1    :display
                           :designation :languages/en]})
     (h/def-object! "Diabetes mellitus (trastorno)"
       {:core/is-instance [:c/id-1    :designation :languages/sp]})

     (h/def-object! :c/id-1.1
       {:core/is-instance         [:core/class :code-system/c]
        :core/is-subclass         [:c/id-1]})

     (h/def-object! "126877002"
       {:core/is-instance [:c/id-1.1 :code-system/c :code-value]})

     (h/def-object! "Disorder of glucose metabolism (disorder)"
       {:core/is-instance [:c/id-1.1 :display
                           :designation :languages/en]}))

   ;; (clojure.pprint/pprint @raw/*onto-state)
   ;; (h/def-object! :code-system    {:core/is-instance [:core/class]})


   (defn validate-code [{:keys [system code]}]
     (let [[code-system & ambiguous]
           (->> (h/classes system)
                (filter (h/is-instance? :code-system)))]
       {:result (and (h/is-instance? :code-value code)
                     (empty? ambiguous)
                     (some? code-system)
                     (h/is-instance? code-system code))}))

   (defn lookup [{system-id :system code-id :code}]
     (let [[code-system & ambiguous-system]
           (->> (h/classes system-id)
                (filter (h/is-instance? :code-system)))

           code-concept (->> (h/classes code-id)
                             (filter #(h/is-instance? code-system %))
                             first)

           display (->> (h/objects)
                        (filter #(h/is-instance? :display %))
                        (filter #(h/is-instance? code-concept %))
                        first)

           designations (->> (h/objects)
                             (filter #(h/is-instance? :designation %))
                             (filter #(h/is-instance? code-concept %))
                             (map (fn [design]
                                    [(->> (h/classes design)
                                          (filter #(h/is-instance? :languages %))
                                          first)
                                     design]))
                             (into {})
                             doall)]
       {:name system-id
        :display display
        :designation  designations}))

   (is (= {:result true} (validate-code {:system "C"
                                         :code "73211009"})))

   (is (= {:result false} (validate-code {:system "C"
                                          :code "bad code"})))

   (is (= {:result false} (validate-code {:system "bad system"
                                          :code "73211009"})))

   (is (= {:result true} (validate-code {:system "C"
                                         :code "126877002"})))

   (is (= {:name "C",
           :display "Diabetes mellitus (disorder)",
           :designation
           #:languages{:en "Diabetes mellitus (disorder)",
                       :sp "Diabetes mellitus (trastorno)"}}
          (lookup {:system "C"
                   :code "73211009"})))

   (is (= {:name "C",
           :display "Disorder of glucose metabolism (disorder)",
           :designation
           #:languages{:en "Disorder of glucose metabolism (disorder)"}}
          (lookup {:system "C"
                   :code "126877002"})))))

(ns uonto.code-system-test
  (:require [uonto.core :as core]
            [clojure.test :as t :refer [deftest is]]))

(defn def-instance [onto object & classes]
  (core/def-object onto object {:core/is-instance classes}))

(defn def-class [onto object & {:keys [instance-of subclass-of]}]
  (core/def-object onto object {:core/is-instance (concat [:core/class] instance-of)
                                :core/is-subclass subclass-of}))

(def language (-> core/base
                  (def-class :language)
                  (def-instance :language:en :language :core/class)
                  (def-instance :language:sp :language :core/class)))

(def code-system
  (-> core/base
      (def-class :code-system/_)

      (assoc :with-default-classes [:code-system/_ :core/class])
      (def-class :code-system/name)
      (def-class :code-system/title)
      (def-class :code-system/concept:code)
      (def-class :code-system/concept:display)
      (def-class :code-system/concept:designation)
      (dissoc :with-default-classes)))

(def code-system:c
  (-> (core/unify core/base code-system)
      (def-instance :c/_ :code-system/_ :core/class)

      (assoc :with-default-classes [:c/_ :core/class])
      (def-class :c/concept:code        :subclass-of [:code-system/concept:code])
      (def-class :c/concept:display     :subclass-of [:code-system/concept:display])
      (def-class :c/concept:designation :subclass-of [:code-system/concept:designation])
      (dissoc :with-default-classes)

      (assoc :with-default-classes [:c/_])
      (def-instance "C"       :code-system/name)
      (def-instance "C Title" :code-system/title)
      (dissoc :with-default-classes)))

(def code-system:c:concepts
  (-> (core/unify code-system:c language)
      (assoc :with-default-classes [:c/_])

      (def-class :c/concept:73211009)
      (def-instance "73211009" :c/concept:code :c/concept:73211009)
      (def-instance "Diabetes mellitus (disorder)"  :c/concept:display :c/concept:73211009)
      (def-instance "Diabetes mellitus (disorder)"  :c/concept:designation :language:en :c/concept:73211009)
      (def-instance "Diabetes mellitus (trastorno)" :c/concept:designation :language:sp :c/concept:73211009)

      (def-class :c/concept:126877002 :subclass-of [:c/concept:73211009])
      (def-instance "126877002" :c/concept:code :c/concept:126877002)
      (def-instance "Disorder of glucose metabolism (disorder)" :c/concept:display :c/concept:126877002)
      (def-instance "Disorder of glucose metabolism (disorder)" :c/concept:designation :language:en :c/concept:126877002)

      (dissoc :with-default-classes)))

#_(defn validate-code [{:keys [system code]}]
  (let [[code-system & ambiguous]
        (->> (h/classes system)
             (filter (h/is-instance? :code-system)))]
    {:result (and (h/is-instance? :code-value code)
                  (empty? ambiguous)
                  (some? code-system)
                  (h/is-instance? code-system code))}))

#_(defn lookup [{system-id :system code-id :code}]
  (let [code-system
        (->> (h/classes system-id)
             (filter #(h/is-instance? :code-system %))
             misc/singleton-unwrap)

        code-concept
        (->> (h/classes code-id)
             (filter #(h/is-instance? code-system %))
             misc/singleton-unwrap)

        display (->> (h/information-objects)
                     (filter #(h/is-instance? :display %))
                     (filter #(h/is-instance? code-concept %))
                     misc/singleton-unwrap)

        designations (->> (h/information-objects)
                          (filter #(h/is-instance? :designation %))
                          (filter #(h/is-instance? code-concept %))
                          (map (fn [design]
                                 [(->> (h/classes design)
                                       (filter #(h/is-instance? :languages %))
                                       misc/singleton-unwrap)
                                  design]))
                          (into {})
                          doall)]
    {:name        system-id
     :display     display
     :designation designations}))

(comment)

(deftest test (is true))

(ns uonto.table-test
  (:require [uonto.table :as table]
            [clojure.test :as t :refer [deftest is testing]]
            [uonto.core :as core]
                        [uonto.misc :as misc]))

(deftest table-test
  (let [onto (-> table/base-onto
                 (table/create "user" [{:name "id"   :type "int"    :nullable false}
                                       {:name "name" :type "string" :nullable true}]))]
    (testing "Get class for table itself"
      (is (= :table.user/-itself (table/table-itself onto "user")))
      (is (thrown? Exception (table/table-itself onto "unknown"))))

    (testing "Get classes for column description"
      (is (= :table.user/column.id (table/table-column onto (table/table-itself onto "user") "id")))
      (is (= {"id"   {:column :table.user/column.id,
                      :type "int",
                      :nullable? false,
                      :column-value :table.user/column.id.value},
              "name" {:column :table.user/column.name,
                      :type "string",
                      :nullable? true,
                      :column-value :table.user/column.name.value}}
             (table/table-columns onto (table/table-itself onto "user")))))))

(deftest insert-select-test
  (let [schema-onto (-> table/base-onto
                        (table/create "user" [{:name "id"   :type "int"    :nullable false}
                                              {:name "name" :type "string" :nullable true}]))

        onto (table/insert schema-onto "user" {:id 1 :name "John Doe"})]

    (testing "Try to work with not defined table"
      (is (thrown-with-msg? Exception #"Table not found"
                            (table/insert schema-onto "NOT_EXIST" {:id 1 :name "John Doe"})))
      (is (thrown-with-msg? Exception #"Table not found"
                            (table/select schema-onto "NOT_EXIST"))))

    (testing "Validation error"
      (is (thrown-with-msg? Exception
                            #"Unknown column in values"
                            (table/insert schema-onto "user" {:foo 1})))

      (is (thrown-with-msg? Exception
                            #"Unknown column in values"
                            (table/insert schema-onto "user" {:foo 1})))

      (is (thrown-with-msg? Exception
                            #"Invalid value type"
                            (table/insert schema-onto "user" {:id "incorrect text" :name "John Doe"}))))

    (is (= []
           (table/select schema-onto "user")))

    (is (= [{:id 1, :name "John Doe"}]
           (table/select onto "user")))))



(deftest staff-example
  (let [schema-onto
        (-> table/base-onto
            (table/create "staff"
                          [{:name "surname"        :type "string" :nullable false}
                           {:name "firstname"      :type "string" :nullable false}
                           {:name "accountmanager" :type "bool"   :nullable false}
                           {:name "salesperson"    :type "bool"   :nullable false}])
            (table/create "account_manager"
                          [{:name "surname"       :type "string" :nullable false}
                           {:name "firstname"     :type "string" :nullable false}])
            (table/create "sales_person"
                          [{:name "surname"       :type "string" :nullable false}
                           {:name "firstname"     :type "string" :nullable false}]))

        ;; comment out to check mapping of staff to account_manager and sales_person
        staff-init
        [{:surname "Beckett"   :firstname "Gordon"   :salesperson false :accountmanager true}
         {:surname "Blair"     :firstname "Margaret" :salesperson false :accountmanager true}
         {:surname "Bottomley" :firstname "Margaret" :salesperson true  :accountmanager false}
         {:surname "Brown"     :firstname "Claire"   :salesperson false :accountmanager true}
         {:surname "Clarke"    :firstname "Michael"  :salesperson true  :accountmanager false}
         #_{:surname "Heseltine" :firstname "Kenneth"  :salesperson true  :accountmanager false}
         #_{:surname "Howard"    :firstname "Cecil"    :salesperson true  :accountmanager false}
         #_{:surname "Short"     :firstname "Tony"     :salesperson false :accountmanager true}
         #_{:surname "Thatcher"  :firstname "Virginia" :salesperson true  :accountmanager true}]

        account-manager-init
        [#_{:surname "Beckett"   :firstname "Gordon"}
         #_{:surname "Blair"     :firstname "Margaret"}
         #_{:surname "Brown"     :firstname "Claire"}
         {:surname "Heseltine" :firstname "Kenneth"}
         {:surname "Short"     :firstname "Tony"}
         {:surname "Thatcher"  :firstname "Virginia"}]

        sales-person-init
        [#_{:surname "Bottomley" :firstname "Margaret"}
         #_{:surname "Clarke"    :firstname "Michael"}
         {:surname "Howard"    :firstname "Cecil"}
         {:surname "Thatcher"  :firstname "Virginia"}]

        onto
        (-> schema-onto
            (misc/->reduce (fn [onto value] (table/insert onto "staff" value)) staff-init)
            (misc/->reduce (fn [onto value] (table/insert onto "account_manager" value)) account-manager-init)
            (misc/->reduce (fn [onto value] (table/insert onto "sales_person" value)) sales-person-init))]

    (testing "Test correct table initialization"
      (is (= (set staff-init)
             (set (table/select onto "staff"))))
      (is (= (set account-manager-init)
             (set (table/select onto "account_manager"))))
      (is (= (set sales-person-init)
             (set (table/select onto "sales_person")))))

    (def onto onto)

    (testing "Let's try to resolve some simple problem: e.g. collect all persons.

              On the table level we can perform them in the following way:

              1. Syncronize tables, for that we need to write n! mapper and resolve
                 duplicate problem and source of true problem.

              2. We can create a new integrated table and write n mapper to it, but
                 how to select the right table structure for that?

              But we work with ontology, so we can use the following approach:")

    (table/table-column onto (table/table-itself onto "staff") "surname")

    (let [staff           (table/table-itself onto "staff")
          account-manager (table/table-itself onto "account_manager")
          sales-person    (table/table-itself onto "sales_person")

          ;; Let introduce some helper classes. We know, that staff.surname,
          ;; account_manager.surname, and sales_person.surname (the same for
          ;; firstnames) are the same from application domain. So we can define
          ;; the upclasses for them to simply select them.
          onto
          (-> onto
              ;; Define the helper class for surname
              (core/def-instance! ::surname [:core/class])
              ;; And mark the columns as subclasses of the helper class
              (core/def-instance! (table/table-column-value onto staff "surname")
                [] {:core/is-subclass [::surname]})
              (core/def-instance! (table/table-column-value onto account-manager "surname")
                [] {:core/is-subclass [::surname]})
              (core/def-instance! (table/table-column-value onto sales-person "surname")
                [] {:core/is-subclass [::surname]})
              ;; The same for firstname
              (core/def-instance! ::firstname [:core/class])
              (core/def-instance! (table/table-column-value onto staff "firstname")
                [] {:core/is-subclass [::firstname]})
              (core/def-instance! (table/table-column-value onto account-manager "firstname")
                [] {:core/is-subclass [::firstname]})
              (core/def-instance! (table/table-column-value onto sales-person "firstname")
                [] {:core/is-subclass [::firstname]})

              (core/infer-all))

          ;; Ok, we have a list of surnames and firstnames but how we can
          ;; intersect it with actual persons? Some names can be duplicated. How
          ;; surname related to firstname? That relation defined by the rows in
          ;; our tables. Let's hightlight the rows which define persons by
          ;; another helper class.
          onto
          (-> onto
              ;; Define helper class with rows with person
              (core/def-instance! ::person [:core/class])
              ;; And here we have options:
              ;;
              ;; 1. Make it on top of the row, and it can include additional
              ;;    information objects.
              ;; 2. Make it on top of related surname and first name.
              ;;
              ;; Let select the first, as more simple.
              (misc/->reduce
               (fn [o row]
                 (let [the-person (keyword (str "person-" (core/uniq-number o)))]
                   (-> o
                       (core/def-instance! the-person [::person])
                       (core/def-instance! row [] {:core/is-subclass [the-person]}))))
               (core/select-by-classes onto [:table/row staff]))
              (misc/->reduce
               (fn [o row]
                 (let [the-person (keyword (str "person-" (core/uniq-number o)))]
                   (-> o
                       (core/def-instance! the-person [::person])
                       (core/def-instance! row [] {:core/is-subclass [the-person]}))))
               (core/select-by-classes onto [:table/row account-manager]))
              (misc/->reduce
               (fn [o row]
                 (let [the-person (keyword (str "person-" (core/uniq-number o)))]
                   (-> o
                       (core/def-instance! the-person [::person])
                       (core/def-instance! row [] {:core/is-subclass [the-person]}))))
               (core/select-by-classes onto [:table/row sales-person]))
              (core/infer-all))

          persons
          (->> (core/select-by-classes onto [::person])
               (map (fn [person]
                      (let [surname   (core/select-by-classes onto [::surname   person])
                            firstname (core/select-by-classes onto [::firstname person])]
                        {:surname   (misc/unwrap-singleton! surname)
                         :firstname (misc/unwrap-singleton! firstname)}))))]

      (core/select-by-classes onto [::surname])
      (core/select-by-classes onto [::firstname])
      #_(->> (core/select-information-objects onto)
             (core/select-by-classes onto [::person]))
      (core/select-by-classes onto [::person])
      #_(:value onto)
      persons)

    (->> (core/select-by-classes onto [:table/row (table/table-itself onto "staff")])
         (core/classification onto))

    (->> (core/select-information-objects onto)
         (core/select-by-classes onto [:table.staff/row.242]))

    (->> (core/select-information-objects onto)
         (core/classification onto)
         (core/remove-core))
    #_(let [onto onto

            staff (table/table-itself onto "staff")
            account-manager (table/table-itself onto "account_manager")
            sales-person (table/table-itself onto "sales_person")]

        all-person-rows)

    (testing "")))

(deftest support-classes-builder-test
  (is (= :table.staff/column.firstname.value
         (table/column->column-value :table.staff/column.firstname)))
  (is (= :table.staff/row.193.column.accountmanager
         (table/row-value-class
          :table.staff/column.accountmanager
          :table.staff/row.193))))

(comment
  (table/select onto "staff")

  (->> (core/select-information-objects onto)
       (core/classification onto)
       (core/remove-core))

  (->> (core/select-abstract-objects onto)
       (core/classification onto)
       (core/remove-core))

  (core/classification onto [:table.account_manager/row.308])
  (core/select-by-classes onto [:table.account_manager/row.308])
  (core/select-by-classes onto [:table.staff/column.firstname])
  (core/select-by-classes onto [:table.staff/column.surname.value :table.staff/row.193])
  (table/select onto "staff")

  (->> (core/select-by-classes onto [:table.staff/row.193])
       (core/classification onto))

  (core/select-by-classes onto [:table.staff/column.accountmanager.value])

  (core/select-by-classes onto [:table.staff/row.228.column.accountmanager :table.staff/row.193]))

(ns uonto.table-test
  (:require [uonto.table :as table]
            [clojure.test :as t :refer [deftest is testing]]
            [uonto.core :as core]))

(deftest table-test
  (let [onto (-> table/table
                 (table/create "staff" [{:name "id" :type "int" :nullable false}
                                        {:name "name" :type "string" :nullable true}]))]
    (testing "table-itself"
      (is (= :table.staff/-itself (table/table-itself onto "staff")))
      (is (thrown? Exception (table/table-itself onto "unknown"))))

    (testing "table-columns"
      (is (= {"id" {:column :table.staff/column.id,
                    :type "int",
                    :nullable? false,
                    :column-value :table.staff/column.id.value},
              "name" {:column :table.staff/column.name,
                      :type "string",
                      :nullable? true,
                      :column-value :table.staff/column.name.value}}
             (table/table-columns onto (table/table-itself onto "staff")))))))

(deftest support-classes-test
  (is (= :table.staff/column.firstname.value
         (table/column->column-value :table.staff/column.firstname)))
  (is (= :table.staff/column.accountmanager.row.193
         (table/row-value-class
          :table.staff/column.accountmanager
          :table.staff/row.193))))

(deftest insert-select-test
  (let [onto (-> table/table
                 (table/create "staff" [{:name "id" :type "int" :nullable false}
                                        {:name "name" :type "string" :nullable true}]))
        onto' (table/insert onto "staff" {:id 1 :name "John Doe"})]

    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Unknown column in values"
                          (table/insert onto "staff" {:foo 1})))

    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Unknown column in values"
                          (table/insert onto "staff" {:foo 1})))

    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Invalid value type"
                          (table/insert onto "staff" {:id "incorrect text" :name "John Doe"})))

    (is (= []
           (table/select onto "staff")))

    (is (= [{"id" 1, "name" "John Doe"}]
           (table/select onto' "staff")))))

(deftest staff-test
  (let [tables
        (-> table/table
            (table/create "staff"
                          [{:name "surname"   :type "string" :nullable false}
                           {:name "firstname" :type "string" :nullable false}
                           {:name "middleinitial" :type "string" :nullable true}
                           {:name "accountmanager" :type "bool"   :nullable false}
                           {:name "salesperson" :type "bool"   :nullable false}])
            (table/create "accountmanager"
                          [{:name "surname"   :type "string" :nullable false}
                           {:name "firstname" :type "string" :nullable false}
                           {:name "middleinitial" :type "string" :nullable true}])
            (table/create "salesperson"
                          [{:name "surname"   :type "string" :nullable false}
                           {:name "firstname" :type "string" :nullable false}
                           {:name "middleinitial" :type "string" :nullable true}]))
        onto
        (reduce
         (fn [onto value] (table/insert onto "staff" value))
         tables
         [{:surname "Beckett" :firstname "Gordon" :salesperson false :accountmanager true}
          {:surname "Blair" :firstname "Margaret" :salesperson false :accountmanager true}
          {:surname "Bottomley" :firstname "Margaret" :salesperson true :accountmanager false}])]

    (is (= #{{"surname" "Beckett",
                "accountmanager" true,
                "firstname" "Gordon",
                "salesperson" false}
             {"surname" "Bottomley",
              "accountmanager" false,
              "firstname" "Margaret",
              "salesperson" true}
             {"surname" "Blair",
              "accountmanager" true,
              "firstname" "Margaret",
              "salesperson" false}}
           (set (table/select onto "staff"))))))

(comment
  (table/select onto "staff")

  (->> (core/select-all-objects onto)
       ;; (core/select-non-tuples onto)
       (core/select-information-objects onto)
       (core/classification onto)
       (core/remove-core))

  (core/select-by-classes onto [:table.staff/row.193])
  (core/select-by-classes onto [:table.staff/column.firstname])
  (core/select-by-classes onto [:table.staff/column.surname.value :table.staff/row.193])
  (table/select onto "staff")

  (->> (core/select-by-classes onto [:table.staff/row.193])
       (core/classification onto))

  (core/select-by-classes onto [:table.staff/column.accountmanager.value])

  (core/select-by-classes onto [:table.staff/row.228.column.accountmanager :table.staff/row.193]))

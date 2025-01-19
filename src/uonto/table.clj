(ns uonto.table
  "
  That namespace contains functions for table representation. The main problem -- how to represent



  It has two level of representation:

  1. For tables.
  2. For rows in it.

  ```text




           Users
          ------
  | id | name  |
  |----|-------|
  |  1 | Alice |
  |  2 | Bob   |






  ```



  collision:
  - between different rows;
  - between different cell in one row.
  "
  (:require [uonto.core :as core]
            [clojure.spec.alpha :as s]
            [clojure.set :as set]
            [uonto.misc :as misc]))

(comment
  "Table representation. Classes for that:"

  (core/select-by-classes base-onto [:table/-itself :core/class])
  #{:table/table      ;; for all defined tables
    :table/table.name ;; for their names

    :table/column     ;; for all defined column
    :table/column.nullable
    :table/column.type
    :table/column.name
    :table/column.value-class

    :table/row}

  (core/select-by-classes base-onto [:table/table])
  ;; => #{}

  " Define table:

           users
          ------
  | id | name  |
  |----|-------|
  "

  (def onto (-> base-onto
                (create "users" [{:name "id" :type "int" :nullable false}
                                 {:name "name" :type "string" :nullable true}])))

  (core/select-by-classes onto [:table/table])
  ;; => #{:table.users/-itself}

  (core/select-by-classes onto [:table.users/-itself])
  ;; => #{"users" :table.users/column.id :table.users/column.name}

  "Objects, which describe our table: name (information object), and
  columns (classes to recognize column settings)."

  (core/select-by-classes onto [:table.users/column.id])


  )

;; TODO: join

(s/def ::columns (s/every (s/keys :req-un [::name ::type ::nullable])))

(def base-onto
  ^{:doc "Objects for table and related object representation."}
  (-> core/base
      (core/def-instance! :table/-itself [:core/class])
      (core/with-classes-> [:table/-itself :core/class]
        (core/def-instance! :table/table)
        (core/def-instance! :table/table.name)

        (core/def-instance! :table/column)
        (core/def-instance! :table/column.name)
        (core/def-instance! :table/column.type)
        (core/def-instance! :table/column.nullable)
        (core/def-instance! :table/column.value-class)

        (core/def-instance! :table/row))))

(defn reduce-on [val f coll]
  (reduce f val coll))

(defn itself [class-ns]
  (keyword class-ns "-itself"))

(defn column->column-value [column]
  (keyword (namespace column) (str (name column) ".value")))

(defn row-value-class [column row]
  (keyword (namespace column)
           (str (name row) "." (name column))))

(defn create [onto name columns]
  (s/assert ::columns columns)
  (let [table-ns     (str "table." name)
        table-itself (itself table-ns)]
    (-> onto
        (core/def-instance! table-itself [:table/table])
        (core/def-instance! name [:table/table.name table-itself])
        (reduce-on
         (fn [onto {cname     :name
                    ctype     :type
                    cnullable :nullable :as _column}]
           (let [column-itself (keyword table-ns (str "column." cname))
                 column-value  (column->column-value column-itself)]
             (-> onto
                 (core/def-instance! column-itself [:table/column table-itself])
                 (core/with-classes-> [column-itself]
                   (core/def-instance! cname [:table/column.name])
                   (core/def-instance! ctype [:table/column.type])
                   (core/def-instance! cnullable [:table/column.nullable])
                   (core/def-instance! column-value [:table/column.value-class])))))
         columns))))

(defn table-itself [onto table-name]
  (let [table-name (-> (core/select-by-classes onto [:table/table.name] [table-name])
                       first)]
    (when (nil? table-name)
      (throw (ex-info "Table not found"
                      {:table-name table-name
                       :known-tables (core/object-classes onto [:table/table.name])})))
    (->> (core/object-classes onto table-name)
         (core/select-by-classes onto [:table/table])
         first)))

(defn table-columns [onto table]
  (let [columns (core/select-by-classes onto [table :table/column])]
    (->> columns
         (map (fn [column]
                (let [column-value (column->column-value column)]
                  [(->> (core/select-by-classes onto [column :table/column.name])
                        (misc/unwrap-singleton!))
                   {:column column
                    :type  (->> (core/select-by-classes onto [column :table/column.type])
                                first)
                    :nullable? (->> (core/select-by-classes onto [column :table/column.nullable])
                                    first)
                    :column-value column-value}])))
         (into {}))))

(defn table-column [onto table column-name]
   (get-in (table-columns onto table) [column-name :column]))

(defn table-column-value [onto table column-name]
  (column->column-value (table-column onto table column-name)))

(defn table-rows [onto table]
  (core/select-by-classes onto [table :table/row]))

(defn insert [onto table-name values]
  (let [table   (table-itself onto table-name)
        columns (table-columns onto table)
        values (->> values
                    (map (fn [[column-name value]]
                           [(if (keyword? column-name) (name column-name) column-name)
                            value]))
                    (into {}))]
    (when-not (set/subset? (-> values keys set)
                           (-> columns keys set))
      (throw (ex-info "Unknown column in values"
                      {:table-name table-name
                       :columns    (keys columns)
                       :values     values})))
    (when-not (set/subset? (->> columns
                                (keep (fn [[k v]] (when-not (:nullable? v) k)))
                                set)
                           (-> values keys set))
      (throw (ex-info "Not all required columns are present in values"
                      {:table-name       table-name
                       :required-columns (keys columns)
                       :values           values})))
    (doseq [[column-name value] values]
      (let [type     (get-in columns [column-name :type])
            is-valid (case type
                       "int" (number? value)
                       "string" (string? value)
                       "bool" (boolean? value))]
        (when-not is-valid
          (throw (ex-info "Invalid value type"
                          {:table-name  table-name
                           :column-name column-name
                           :value       value
                           :type    type})))))
    (let [row (keyword (namespace table) (str "row." (core/uniq-number onto)))]
      (-> onto
          (core/def-instance! row [:table/row table])
          (core/with-classes-> [row table]
            (reduce-on
             (fn [onto [column-name value]]
               (let [column       (get-in columns [column-name :column])
                     column-value (column->column-value column)
                     row-value    (row-value-class column row)]
                 (-> onto
                     (core/def-instance! row-value [:core/class])
                     (core/def-instance! value [column row-value column-value]))))
             values))))))

(defn select [onto table-name]
  (let [table   (table-itself onto table-name)
        columns (table-columns onto table)
        rows    (table-rows onto table)]
    (when (nil? table)
      (throw (ex-info "Table not found"
                      {:table-name table-name
                       :known-tables (core/object-classes onto [:table/table.name])})))
    (mapv
     (fn [row]
       (->> columns
            (keep (fn [[column-name {:keys [column]}]]
                    (let [row-value (row-value-class column row)
                          values    (core/select-by-classes onto [row-value row])]
                      (when-not (empty? values)
                        [(keyword column-name)
                         (-> (core/select-by-classes onto [row-value row])
                             (misc/unwrap-singleton!))]))))
            (into {})))
     rows)))

(comment
  (let [onto (-> base-onto
                 (create "staff" [{:name "id" :type "int" :nullable false}
                                  {:name "name" :type "string" :nullable true}]))]
    (->> (core/select-non-tuples onto)
         (core/classification onto)
         (core/remove-core))))

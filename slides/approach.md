# Высшие онтологии: идея подхода <br/>(Введение в BORO метод)

<div style="text-align: left">

<b>FHIR</b> — группа "резиновых" стандартов (R3, R4, R5, R6...), цель которых — обеспечить интероп по данным с возможностью расширения (extensions) и ограничений (profiles) на месте.

В докладе будет рассказано о том, как это делают действительно взрослые дяди, которым нужно интегрировать данные при проектировании, производстве и эксплуатации каких-нибудь вундервафель или плавучих нефтедобывающих платформ.

А именно: как моделировать без "сущностей", а исключительно на основе extensions и классификаций (по сути, пересказ основ BORO метода).

</div>

Пенской Александр, 2026

[text & source code](https://github.com/ryukzak/uonto)

---

## Fast Healthcare Interoperability Resources (FHIR)

### Задача

1. Обмен данными между разными системами (EHR, лаборатории, аптеки…)
1. Данные системы `A` должны быть **однозначно понятны** системе `B`
1. Нужна **специализация** под страну, регион, организацию

Это задача **моделирования предметной области**: формальная модель данных — универсальная и допускающая специализацию по месту.

К примеру: иерархия классов в ООП, схема БД, ER-диаграмма, спецификация API, протокол обмена данными.


<img src="/slides/fig/babel-fish.png" style="max-height: 250px;">


----

### Подход FHIR


1. **Базовые ресурсы** (Patient, Observation, MedicationRequest, …):
    - закрыть 80% use-case-ов;
    - но избыточные для большинства применений;
    - и недостаточные для особых случаев.
1. **Профили (Profiles)** — адаптируют базовые ресурсы по месту
    - Сделать SSN обязательным для US Core
    - Ограничить расы/этничности кодами CDC Race & Ethnicity
    - Запретить поле `animal` в ресурсе Patient (US Core — только люди)
1. **Расширения (Extensions)** — добавляют данные, не предусмотренные базовыми ресурсами
    - Поле «раса» в US Core Patient (в базовом ресурсе его нет)
    - Согласие на донорство органов в записи о пациенте

Базовый стандарт → профиль на уровне страны → профиль на уровне организации

----

### Проблемы FHIR

1. **Не удаётся стабилизировать** (R3 → R4 → R5 → R6 → ?)
    - R5 — не получилось. Почему должно получиться в R6?
1. **Профили ограничивают интероперабельность**
    - интероперабильность по FHIR
    - интероперабильность в рамках набора профилей
1. **Расширения — за пределами интероперабельности**
    - система `B` не обязана их понимать
1. **Базовые ресурсы избыточно сложны** для конкретного применения

---

## The Real-Scale of Interoperability Problem

1. **FHIR** — это "лёгкий" случай: один домен, стандартные сущности
1. А если нужно интегрировать данные через **весь жизненный цикл**?
    - Проектир. → Произв. → Поставка → Эксплуатация → Утилизация
    - Разные организации, инженерные дисциплины, системы

<img src="/slides/fig/se-domain.png" style="max-height: 350px;">

----

### Примеры «взрослых» решений

1. **ISO 15926** — нефтегаз, процессная (поточная) промышленность
    - Shell, BP, Statoil — интеграция данных между подрядчиками на всём жизненном цикле
    - Насос описан проектировщиком, изготовителем, эксплуатантом — это одна запись
1. **DoDAF / IDEA** — оборонка (США, NATO)
    - Интеграция данных о системах вооружений: от требований до эксплуатации
    - Разные рода войск, разные подрядчики, десятилетия жизненного цикла

Масштаб и длительность делают подход "договоримся о формате" **нежизнеспособным**

----

### Как они это делают?

Вместо «придумать базовые модели и расширять» — другая стратегия:

1. Определить **минимальный набор предельно общих категорий** (высшая онтология)
1. Всё доменное выражать через **классификацию** и **отношения**
    - без атрибутов, без «сущностей»
1. Расширение = добавление новых классов
    - нет новых полей → модель **не ломается**

<img src="/slides/fig/geek-and-poke-how-to-create-a-stable-data-model.png" style="max-height: 250px;">

----

### Пример: высшая онтология ISO 15926

<img src="/slides/fig/iso-15926-top-classes.png" style="max-height: 250px;">

- **Thing** — всё, о чём можно говорить
    - **PossibleIndividual** — существует в пространстве-времени (насос P-101, пациент Иванов)
    - **AbstractObject** — вне пространства-времени
        - **Class** — множество («Центробежный насос», «Диагноз J06»)
        - **Relationship** — связь («установлен на», «диагностирован с»)
        - **MultidimensionalObject** — костыль для реляционных БД

---

## Что мы моделируем?

### Конкретные вещи (Particular Things)

<img src="/slides/fig/a-particular-table-and-two-particular-chairs.png" style="max-height: 350px;">

1. Этот стол, эти два стула — каждый уникален
1. Насос P-101, пациент Иванов, операция №4217
1. Существует в пространстве и во времени

----

### Абстрактные вещи и классы

<img src="/slides/fig/general-types-and-particular-things.png" style="max-height: 250px;">

1. «Стол» — не вещь, а **класс**: множество всех столов
1. Три стола на картинке — экземпляры класса «Tables»

----

### Отношения: конкретные и абстрактные

![](/slides/fig/general-and-particular-relationships.png)

1. «Mothers → is the mother of → Children» — **тип связи**
1. «Elizabeth → is the mother of → Charles» — **конкретный факт**

----

### Изменения и события

![](/slides/fig/boro-substance-lepidopter.png)

1. Гусеница, куколка, бабочка — одно существо в трёх фазах
1. Процесс превращения — тоже вещь с началом и концом

---

## Business Objects Re-Engineering (BORO метод)

Метод пересборки доменных моделей. Лежит в основе ISO 15926.


1. **Traditional Entity Modelling**: при расширении охвата (новые домены, use-case-ы) сложность модели растёт **экспоненциально**
    - Новая «сущность» — новые атрибуты, связи, миграции
1. **Object Modelling** (подход BORO): сложность растёт **линейно**
    - Базовые категории (Thing, Class, Relationship) не меняются
    - Новый домен = новые классы и отношения (структура модели та же)

<img src="/slides/fig/boro-increases-in-scope.png" style="max-height: 250px;">

----

### Откуда это и как об это рассказать?

Chris Partridge, «Business Objects: Re-Engineering for Re-Use»

<img src="/slides/fig/business-objects-re-engineering-for-re-use.jpg" style="max-height: 200px;">

Рассказ как в книге — через эволюцию подходов к моделированию:

1. **Entity paradigm** (Платон, IV в. до н.э.) — реляционная модель
2. **Substance paradigm** (Аристотель, IV в. до н.э.) — ООП
3. **Logical paradigm** (Буль, Венн, Кантор, XIX в.) — классы без атрибутов
4. **4D extensionalism** (Chris Partridge, 1990-е) — высшие онтологии

---

### Entity paradigm (Платон)


1. Платон: реальны **идеальные формы**, а конкретные вещи — лишь их отражения
1. Entity paradigm: **тип первичен** (Entity Type + Attribute Type), экземпляр вторичен (Individual Entity)
1. Это **таблица в БД**: тип = заголовок таблицы, экземпляр = строка

<img src="/slides/fig/boro-entity-reuse-in-application.png" style="max-height: 350px;">

----

### Проблема выбора сущности и её атрибутов

<img src="/slides/fig/boro-entity-sales-account-table.png" style="max-height: 250px;">
<img src="/slides/fig/boro-entity-staff-table.png" style="max-height: 250px;">

Как смоделировать сотрудников, у которых несколько ролей?

- Отдельная таблица на роль (Salespersons, Account Managers) — но куда девать Virginia Thatcher, которая в обеих? Дублировать?
- Одна таблица Staff с колонками-флагами — но что делать, когда появится третья роль? `ALTER TABLE`?
- А если ролей станет двадцать?

---

### Substance paradigm (Аристотель)

<img src="/slides/fig/boro-substance-attributes.png" style="max-height: 250px;">

1. Аристотель переворачивает Платона: реальна **первичная субстанция** (конкретная вещь), а тип — **вторичная субстанция** (Car, Vehicle) — лишь способ классификации
1. Отсюда — **иерархия наследования**: Transport → Vehicle → Car, каждый уровень уточняет свойства родителя
1. Это **класс в ООП** и наследование

----

### Substance paradigm: сотрудники

<img src="/slides/fig/boro-substance-staff.png" style="max-height: 250px;">

1. Virginia Thatcher — и продавец, и менеджер. **Множественное наследование**
1. Почему «роль» — это подкласс, а не атрибут? Как хранить экземпляры разных классов?
1. Выбор зашит в код на этапе проектирования — сможем переделать?

Итого: стало лучше, но не сильно.

----

### Как моделируются отношения?

<img src="/slides/fig/boro-substance-relations-primary-level.png" style="max-height: 250px;">

1. Простые отношения кодируем **атрибутами** (foreign key), сложные — **промежуточными таблицами** (junction table)
1. Отношение — не самостоятельная вещь, а атрибут или клей между субстанциями
1. В ООП: **композиция и агрегация** — объект владеет другим или ссылается на него. Инварианты обеспечиваем вручную
1. Что из этого выбрать — решаем **заранее**, и опять **дорого менять**

----

### Как моделируются изменения?

<img src="/slides/fig/boro-substance-lepidopter-unchaniging.png" style="max-height: 140px;">
<img src="/slides/fig/boro-substance-arrow-changes.png" style="max-height: 140px;">

1. Гусеница → куколка → бабочка — это одна субстанция или три? Какие у неё атрибуты?
1. Стрела летит из P₁ в P₂ — это просто `UPDATE position`. Изменение — это объект или просто смена значения? Где хранить историю переходов?

----

Выводы:

1. «сущность», «субстанция», «атрибут» — **не определяются универсально**.
1. Они работают только внутри конкретной, узкой постановки задачи.
1. Менять постановку — плодить костыли или, иногда, менять всё.
1. От них нужно отказаться.

---

## Logical paradigm (новое время): <br/> от субстанции к протяжённости

<img src="/slides/fig/boro-logic-car-from-substance.png" style="max-height: 200px;">

1. Substance: начинаем с **идентичности объекта** и наращиваем его атрибутами (colour, type, etc.)
1. Logical: начинаем с **мира как протяжённости** и вычленяем объект из него классами, отделяя от других
1. «My car» — не субстанция с атрибутами, а **кусок пространства-времени**, попавший в пересечение классов Cars, Red Things, ...

----

### Атрибуты — это тоже классы

<img src="/slides/fig/boro-logic-color-things.png" style="max-height: 200px;">

1. Нет разделения на «тип» и «экземпляр». Есть **классы** и **принадлежность**
1. Красная машина — элемент пересечения классов «Cars» и «Red Things»
1. Никаких колонок, никаких схем — только множества и вхождение в них

----

### Множественная классификация

<img src="/slides/fig/boro-logic-multi-classification.png" style="max-height: 180px;">
<img src="/slides/fig/boro-logic-classes-minimisation.png" style="max-height: 180px;">

1. Porky принадлежит **одновременно** к Pigs, Male Animals, Boars, Animals
1. Нет атрибутов — **нет конфликтов** при множественной классификации
1. Определение класса через **правила вывода**: Boar ≡ Pig ∧ Male Animal
1. Всегда можно детализировать "протяжённое" через детальную классификацию. Без перепроектирования

----

### Отношения — тоже объекты

<img src="/slides/fig/boro-logic-relations-many.png" style="max-height: 180px;">
<img src="/slides/fig/boro-logic-relations-many-to-many.png" style="max-height: 180px;">

1. Отношение — **упорядоченная пара (tuple)** — самостоятельный объект
1. Пара ⟨Charles, William⟩ принадлежит классу «Is a Father of» и классу «Is Taller Than» одновременно
1. Нет «служебных» таблиц
1. Инварианты — через правила вывода
1. Новый тип отношения = новый класс, **без изменения структуры данных**

----

### Итого: substance vs logical

<img src="/slides/fig/boro-logic-summary.png" style="max-height: 200px;">

1. **Substance**: 1) General Substance, 2) Particular Substance, 3) Non-Relational Attribute, 4) Relational Attribute
1. **Logical**: 1) Objects (конкретные вещи), 2) Classes (множества), 3) Tuples (отношения)
1. Атрибуты исчезли — их роль взяли на себя классы. Отношения стали объектами
1. Нет неразрешимого вопроса «это атрибут или сущность?»
1. **Минимальный каркас**, который не ломается при расширении

---

### Осталась одна проблема: изменения

<img src="/slides/fig/boro-object-dynamic-extension.png" style="max-height: 200px;">

1. Помидор — сначала зелёный, потом красный. Он в Green Things или Red Things?
1. Логическая парадигма работает с «вечными» множествами — в ней нет понятия «когда»

----

### Решение: 4D extensionalism

<img src="/slides/fig/boro-object-butterfly-new.png" style="max-height: 160px;">
<img src="/slides/fig/boro-object-banck-chairman-change.png" style="max-height: 160px;">

1. Вещь — **четырёхмерный объект**, протяжённый в пространстве и во времени
1. Caterpillar и Butterfly — **части** (time slices) одного 4D-объекта, а не «состояния»
1. Chairman of Natland Bank — тоже 4D-объект, пересекающийся сначала с Mr Jones, потом с Mr Smith
1. Не `UPDATE` — а фиксация, какие 4D-объекты пересекаются в какой период

----

### 4D: отношения во времени

<img src="/slides/fig/boro-object-relation-in-time.png" style="max-height: 200px;">

1. Tuple ⟨Prince Charles, Prince William⟩ ∈ Father-Son — оба участника 4D-объекты
1. Отношение существует не «вообще», а в конкретном пространственно-временном контексте
1. **Вещи, классы, отношения — всё протяжено во времени**

----

### Итого: logical + 4D extensionalism

1. Logical + 4D: только **объекты** (кортежи — тоже объекты) и **классы**, протяжённые во времени
1. Нет атрибутов — нет вопроса «атрибут или сущность?». Нет иерархии — нет конфликтов наследования. Нет мутаций — нет вопроса «когда обновлять?»
1. Новый объект, класс, отношение, период, детализация — просто **новый класс или новый time slice**, без изменения структуры данных

Одна проблема:

----

<img src="/slides/fig/alien-tech.png">

1. Не для бизнеса.
1. Не для программистов.
1. Не для компьютеров.

---

## Практика: моделируем таблицы

Реализация на Clojure: [core.clj](https://github.com/ryukzak/uonto/blob/master/src/uonto/core.clj) + [table.clj](https://github.com/ryukzak/uonto/blob/master/src/uonto/table.clj)

----

### Онтологическая база: три примитива

```clojure
;; (def-object! obj {relation-class [targets]})
;; — зарегистрировать obj и создать кортежи [obj, target] ∈ relation-class

(def base
  (-> {}
      (register-object :core/class)        ;; класс всех классов
      (register-object :core/is-instance)  ;; класс отношения «является экземпляром»
      (register-object :core/is-subclass)  ;; класс отношения «является подклассом»
      ;; is-instance является экземпляром class:
      ;;   кортеж [:core/is-instance, :core/class] ∈ :core/is-instance
      (def-object! :core/class       {:core/is-instance []})
      (def-object! :core/is-instance {:core/is-instance [:core/class]})
      (def-object! :core/is-subclass {:core/is-instance [:core/class]})))
```

Всё остальное строится поверх этих трёх.

----

### База для представления таблиц

```clojure
(def base-onto
  (-> core/base
      (core/def-instance! :table/-itself [:core/class])
      (core/with-classes-> [:table/-itself :core/class]
        (core/def-instance! :table/table)       ;; класс всех таблиц
        (core/def-instance! :table/table.name)  ;; класс имён таблиц

        (core/def-instance! :table/column)            ;; класс колонок
        (core/def-instance! :table/column.name)       ;; класс имён колонок
        (core/def-instance! :table/column.type)       ;; класс типов колонок
        (core/def-instance! :table/column.nullable)   ;; класс nullable-флагов
        (core/def-instance! :table/column.value-class) ;; класс для значений

        (core/def-instance! :table/row))))  ;; класс строк
```

1. `with-classes->` — все объекты внутри автоматически классифицируются как `:table/-itself` и `:core/class`
1. Таблица, колонка, строка — не структуры данных, а **классы**
1. Метаданные (имя, тип, nullable) — тоже классы

----

### Пример: создаём таблицу

```clojure
(table/create onto "users"
  [{:name "name" :type "string" :nullable false}
   {:name "age"  :type "int"    :nullable false}])
```

Объект → его классы:

```edn
{:table.users/-itself              #{:table/table}
 "users"                           #{:table/table.name :table.users/-itself}
 ;; колонка name
 :table.users/column.name          #{:table/column :table.users/-itself}
 :table.users/column.name.value    #{:table/column.value-class :table.users/column.name}
 "name"                            #{:table/column.name :table.users/column.name}
 "string"                          #{:table/column.type :table.users/column.name}
 ;; колонка age
 :table.users/column.age           #{:table/column :table.users/-itself}
 :table.users/column.age.value     #{:table/column.value-class :table.users/column.age}
 "age"                             #{:table/column.name :table.users/column.age}
 "int"                             #{:table/column.type :table.users/column.age}
 ;; общий объект для обеих колонок
 false                             #{:table/column.nullable
                                     :table.users/column.name
                                     :table.users/column.age}}
```

----

### Добавляем строку

```clojure
(table/insert onto "users" {:name "Alice" :age 30})
```

Что добавилось:

```edn
{:table.users/row.0                  #{:table/row :table.users/-itself}
 :table.users/row.0.column.name      #{:core/class}
 :table.users/row.0.column.age       #{:core/class}
 "Alice"                              #{:table.users/column.name
                                        :table.users/row.0.column.name
                                        :table.users/column.name.value
                                        :table.users/row.0}
 30                                   #{:table.users/column.age
                                        :table.users/row.0.column.age
                                        :table.users/column.age.value
                                        :table.users/row.0}}
```

1. `"Alice"` и `30` — объекты на пересечении классов «колонка» и «строка»
1. `row.0.column.name` — вспомогательный класс для разрешения коллизий между строками

----

### Добавляем вторую строку

```clojure
(table/insert onto "users" {:name "Bob" :age 30})
```

Что добавилось:

```edn
{:table.users/row.1                  #{:table/row :table.users/-itself}
 :table.users/row.1.column.name      #{:core/class}
 :table.users/row.1.column.age       #{:core/class}
 "Bob"                                #{:table.users/column.name
                                        :table.users/row.1.column.name
                                        :table.users/column.name.value
                                        :table.users/row.1}
 ;; 30 уже существует — просто получает новые классы
 30                                   #{:table.users/column.age
                                        :table.users/row.0.column.age  ;; от Alice
                                        :table.users/row.1.column.age  ;; от Bob
                                        :table.users/column.age.value
                                        :table.users/row.0
                                        :table.users/row.1}}
```

1. `"Bob"` — новый объект. Но `30` — **тот же объект**, что и у Alice
1. Без `row.N.column.age` нельзя отличить «30 у Alice» от «30 у Bob» — для этого и нужны вспомогательные классы

----

### Выборка: кому 30 лет?

```clojure
;; SELECT * FROM users WHERE age = 30

;; 1. Находим объект 30 — он уже знает, в каких строках он есть:
(select-by-classes onto [:table.users/column.age.value] [30])
;; => #{30}

;; 2. Классы объекта 30 содержат строки:
(object-classes onto 30)
;; => #{... :table.users/row.0 :table.users/row.1 ...}

;; 3. Фильтруем только строки таблицы:
(select-by-classes onto [:table/row :table.users/-itself]
                        [:table.users/row.0 :table.users/row.1])
;; => #{:table.users/row.0 :table.users/row.1}
```

1. Нет сканирования таблицы — объект `30` **уже классифицирован** нужными строками
1. Выборка — это пересечение классов, а не обход структуры данных

----

### Что ещё показано в тестах

1. **Интеграция трёх таблиц** (staff, account_manager, sales_person) — через общий суперкласс `::surname`, без JOIN
1. **Inference** — транзитивный вывод подклассов и автоматическая пропагация классификации
1. **Мультиязычность** — объекты классифицируются по языку (`:language/en`, `:language/sp`), выборка — пересечение классов
1. **Медицинская терминология** — кодовая система с концептами, иерархией и обозначениями на разных языках

Смотрите: [table_test.clj](https://github.com/ryukzak/uonto/blob/master/src/uonto/table_test.clj), [code_system_test.clj](https://github.com/ryukzak/uonto/blob/master/src/uonto/code_system_test.clj)

----

### Итого

1. Только **объекты и классы** — создание, вставка, выборка, интеграция, расширение
1. Все объекты могут быть пронумерованы, все операции сводятся к **операциям на множествах**
1. Вроде работает...

Код: [core.clj](https://github.com/ryukzak/uonto/blob/master/src/uonto/core.clj), [table.clj](https://github.com/ryukzak/uonto/blob/master/src/uonto/table.clj)

Тесты: [core_test.clj](https://github.com/ryukzak/uonto/blob/master/src/uonto/core_test.clj), [table_test.clj](https://github.com/ryukzak/uonto/blob/master/src/uonto/table_test.clj), [code_system_test.clj](https://github.com/ryukzak/uonto/blob/master/src/uonto/code_system_test.clj)

---


## Что дальше?

1. Освоение промышленных инструментов (ISO 15926, DoDAF)?
1. Разобраться в медицинских терминологиях: SNOMED выглядит очень близким по духу.
1. Можно поиграться c uonto, внедрить мемоизацию и т.п.

# uonto

Ontology-based data modeling using the logical paradigm and 4D extensionalism. Implementation in Clojure.

## What is this

A practical exploration of modeling relational data through ontology — no entities, no attributes, only objects and classes. Inspired by BORO (Business Objects Re-Engineering) and ISO 15926.

- [core.clj](src/uonto/core.clj) — ontology engine: objects, classification, tuples, inference
- [table.clj](src/uonto/table.clj) — relational tables on top of the ontology
- [table_test.clj](src/uonto/table_test.clj) — table creation, insertion, integration of three tables without JOIN
- [code_system_test.clj](src/uonto/code_system_test.clj) — medical code systems with multilingual support

## Slides

Presentation explaining the approach — from Plato to 4D extensionalism:

```shell
pipx install mkslides
mkslides serve .
```

## Development

```shell
make test    # run tests
make repl    # start REPL
make format  # format code
make lint    # lint code
```

# levanzo [![CircleCI](https://circleci.com/gh/antoniogarrote/levanzo.svg?style=svg)](https://circleci.com/gh/antoniogarrote/levanzo)

Levanzo is Clojure library to build hypermedia driven RESTful APIs using W3C standards.
Levanzo supports the following features:

- Declarative declaration of resource classes
- Generation of machine consumable API documentation and meta-data
- Declarative support for validation constraints
- Generation of compatible HTTP middleware for the declared API and link generation functions
- Support for API indices generation that can be used in cross-resource API queries

The library is built on top of the following list of W3C standards:

- [JSON-LD](http://json-ld.org/spec/latest/json-ld/) as the preferred data format
- [Hydra](http://www.hydra-cg.com/spec/latest/core/) as the API vocabulary
- [SHACL](https://www.w3.org/TR/shacl/) as the declarative constraint language
- [Triple Pattern Fragments](https://www.hydra-cg.com/spec/latest/triple-pattern-fragments/) as the interface for API queries
- [SPARQL](https://www.w3.org/TR/sparql11-overview/) as the query language over the API resources

## Usage

FIXME

## License

Copyright © 2016 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

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

## Tutorial

### 1. Setting up your API namespace

One of the main problems with API specification languages like RAML or OpenAPI is that they use flat namespaces and plain strings as identifiers. This makes very difficult to achieve certain use cases like re-using API descriptions or extending the specification.

Levanzo on the other hand uses [Hydra](http://www.hydra-cg.com/spec/latest/core/) as its specification 'vocabulary' that has at its core the RDF data model. This means that identifiers for every single element in the API descriptions are namespaced and uniquely identified by an URI. This also means that you can re-use the terms and meaning of other vocabularies in your API.
For example, you could (and should if the meaning matches your application domain) use terms from the Schema.org vocabulary to build the resources of your API.

The trade-off of using URIs as identifiers instead of strings is that it they are more verbose and working with then can become quite cumbersome.

Levanzo includes some functions in the namespace `levanzo.namespaces` to make it easier to work with URIs in the API specification as well as to work with [compact URIs](https://www.w3.org/TR/curie/) or CURIEs that can reduce the complexity of dealing with URIs as identifiers.

Setting up the name-spaces for the vocabularies you are going to use in your API should be the first task you need to address when working with Levanzo.

The following snippet from the sample application shows how to declare a vocabulary and how to set-up the default vocabulary for our API:

``` clojure
(require '[levanzo.namespaces :as lns]
         '[clojure.test :refer [is]])

;; base URL where the API will be served
(def base (or (System/getenv "BASE_URL") "http://localhost:8080/"))

;; registering the namespace for our vocabulary at /vocab#
(lns/define-rdf-ns vocab (str base "vocab#"))

;; registering schema org vocabulary
(lns/define-rdf-ns sorg "http://schema.org/")
```

Now we can use the *sorg* and *vocab* functions to transform a string into a URI and to expand CURIEs:

``` clojure
;; tests
(is (= "http://localhost:8080/vocab#Test") (vocab "Test"))
(is (= "http://schema.org/Person") (sorg "Person"))
(is (= "http://schema.org/Person") (lns/resolve "sorg:Person"))
```
Certain namespaces are already declared in *levanzo.namespaces*, you don't need to re-declare them:

- rdf
- rdfs
- hydra
- xsd

### 2. Describing your API: declaring resources

Now that we have a namespace, we can start adding definitions to it. The namespace `levanzo.hydra` includes the functions required to declare the components of your API.

The simplest building block in Levanzo is a property.

We can declare properties using the `levanzo.hydra/property` function.

Properties must have an URI identifier and they can have a range, identified by another URI.
Scalar properties have a range over a XSD type like `xsd:float` or `xsd:string`.

The following snippet from the example API declares the [Schema.org streetAddress](https://schema.org/streetAddress) and [postalCode](https://schema.org/postalCode) properties.

``` clojure
(require '[levanzo.hydra :as hydra])
(require '[levanzo.xsd :as xsd])

;; declaring properties
(def street-address-property (hydra/property {::hydra/id (sorg "streetAddress")
                                              ::hydra/title "streetAddress"
                                              ::hydra/description "The street address"
                                              ::hydra/range xsd/string}))

(def postal-code-property (hydra/property {::hydra/id (sorg "postalCode")
                                           ::hydra/title "postalCode"
                                           ::hydra/description "The postal code"
                                           ::hydra/range xsd/string}))
```
In the example, other options for a property like the `title` and `description` are also provided. To see a complete list of arguments, check the documentation for the `hydra/property` function.
The `levanzo.hydra/id` function can be used to extract the URI ID of a component of the Hydra model.

Levanzo is built using [Clojure Spec](https://clojure.org/about/spec), that's the reason you will see so many namespaced keywords in the library.

The next important Hydra concept after properties is the notion of [Hydra classes](http://www.hydra-cg.com/spec/latest/core/#hydra:Class). Classes are collection of resources with shared semantics, meaning by shared semantics that they are described using the same set of properties.
To group the properties of a Hydra class we use the `levanzo.hydra/supported-property` function. `supported-property` allows us to describe some constraints about properties when they are used to describe instances of that Hydra class. Examples of constraints are [`::hydra/required`](http://www.hydra-cg.com/spec/latest/core/#hydra:required), [`::hydra/readonly`]((http://www.hydra-cg.com/spec/latest/core/#hydra:readonly)) and [`::hydra/writeonly`]((http://www.hydra-cg.com/spec/latest/core/#hydra:writeonly)).

This information, plus the range of the property will be used by Levanzo to perform constraint validations on incoming data.

Finally, the Hydra class can be described using the `levanzo.hydra/class` function.
The following snippet declares the [Schema.org PostalAddress](https://schema.org/PostalAddress) class.

``` clojure
(def sorg-PostalAddress (hydra/class {::hydra/id (sorg "PostalAddress")
                                      ::hydra/title "PostalAddress"
                                      ::hydra/description "The mailing address"
                                      ::hydra/supported-properties
                                      [(hydra/supported-property {::hydra/property (hydra/id sorg-street-address)
                                                                  ::hydra/required true})
                                       (hydra/supported-property {::hydra/property (hydra/id sorg-postal-code)})]}))
```

Properties can also have as values links to other resources. Links are declared using the `levanzo.hydra/link` function.

The following lines of code declare the [Schema.org address](https://schema.org/address) property that can be used to link People to PostalAddresses.
Other scalar properties to model a [Schema.org Person](https://schema.org/Person) are also defined.

``` clojure
;; People -> address -> PostalAddress link
(def sorg-address (hydra/link {::hydra/id (sorg "address")
                               ::hydra/title "address"
                               ::hydra/description "Physical address of the resource"
                               ::hydra/range (hydra/id sorg-PostalAddress)}))

;; Properties for People
(def sorg-name (hydra/property {::hydra/id (sorg "name")
                                ::hydra/title "name"
                                ::hydra/description "The name of the resource"
                                ::hydra/range xsd/string}))


(def sorg-email (hydra/property {::hydra/id (sorg "email")
                                 ::hydra/title "email"
                                 ::hydra/description "Email address"
                                 ::hydra/range xsd/string}))

```
Links are also connected to classes using the `levanzo.hydra/supported-property` function, but when connecting links, we can declare a collection of [Hydra operations](http://www.hydra-cg.com/spec/latest/core/#hydra:operation) specifying how links introduced by this property can be accessed using HTTP requests:

``` clojure
;; The Person class
(def person-address-link (hydra/supported-property {::hydra/id (vocab "address-link")
                                                    ::hydra/property (hydra/id sorg-address)
                                                    ::hydra/operation
                                                    [(hydra/get-operation {::hydra/returns (hydra/id sorg-PostalAddress)})
                                                     (hydra/post-operation {::hydra/expects (hydra/id sorg-PostalAddress)
                                                                            ::hydra/returns (hydra/id sorg-PostalAddress)})
                                                     (hydra/delete-operation {})]}))

(def sorg-Person (hydra/class {::hydra/id (sorg "Person")
                               ::hydra/title "Person"
                               ::hydra/description "A person"
                               ::hydra/supported-properties
                               [(hydra/supported-property {::hydra/property (hydra/id sorg-name)})
                                (hydra/supported-property {::hydra/property (hydra/id sorg-email)
                                                           ::hydra/required true})
                                person-address-link]}))
```
In the previous example we have defined three operations for the address link. The operations are bound to the HTTP GET, POST and DELETE HTTP methods.
For each operation we have also established the expected and return types. Finally, notice that we have given an optional URI ID to the supported property for the link.
This ID will be useful when declaring the HTTP bindings for this API as well as to generate link URIs programatically.

For more details about how to declare affordances for the resources of an API using Hydra and Levanzo, please read the [spec documentation](http://www.hydra-cg.com/spec/latest/core/#adding-affordances-to-representations) and the documentation for the Clojure functions.

## License

Copyright © 2017 Antonio Garrote

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

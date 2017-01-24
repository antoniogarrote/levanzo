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
;; The PostalAddress class
(def sorg-PostalAddress (hydra/class {::hydra/id (sorg "PostalAddress")
                                      ::hydra/title "PostalAddress"
                                      ::hydra/description "The mailing address"
                                      ::hydra/supported-properties
                                      [(hydra/supported-property
                                        {::hydra/property sorg-street-address
                                         ::hydra/required true})
                                       (hydra/supported-property
                                        {::hydra/property sorg-postal-code})]}))
```

Properties can also have as values links to other resources. Links are declared using the `levanzo.hydra/link` function.

The following lines of code declare the [Schema.org address](https://schema.org/address) property that can be used to link People to PostalAddresses.
Other scalar properties to model a [Schema.org Person](https://schema.org/Person) are also defined.
We also define a scalar property in our vocabulary namespace the `password` property. We mark this property as `required` and `writeonly`. That means that will be required required but only when we write data in the resource, for example in a POST operation.

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

(def vocab-password (hydra/property {::hydra/id (vocab "password")
                                     ::hydra/title "password"
                                     ::hydra/description "Secret passworld"
                                     ::hydra/range xsd/string}))
```
Links are also connected to classes using the `levanzo.hydra/supported-property` function, but when connecting links, we can declare a collection of [Hydra operations](http://www.hydra-cg.com/spec/latest/core/#hydra:operation) specifying how links introduced by this property can be accessed using HTTP requests:

``` clojure

;; The Person class
(def person-address-link (hydra/supported-property {::hydra/id (vocab "address-link")
                                                    ::hydra/property sorg-address
                                                    ::hydra/readonly true
                                                    ::hydra/operation
                                                    [(hydra/get-operation
                                                      {::hydra/returns (hydra/id sorg-PostalAddress)})
                                                     (hydra/post-operation
                                                      {::hydra/expects (hydra/id sorg-PostalAddress)
                                                       ::hydra/returns (hydra/id sorg-PostalAddress)})
                                                     (hydra/delete-operation {})]}))

(def sorg-Person (hydra/class {::hydra/id (sorg "Person")
                               ::hydra/title "Person"
                               ::hydra/description "A person"
                               ::hydra/supported-properties
                               [(hydra/supported-property
                                 {::hydra/property sorg-name})
                                (hydra/supported-property
                                 {::hydra/property sorg-email
                                  ::hydra/required true})
                                (hydra/supported-property
                                 {::hydra/property vocab-password
                                  ::hydra/required true
                                  ::hydra/writeonly true})
                                person-address-link]}))
```
In the previous example we have defined three operations for the address link. The operations are bound to the HTTP GET, POST and DELETE HTTP methods.
For each operation we have also established the expected and return types. Finally, notice that we have given an optional URI ID to the supported property for the link.
This ID will be useful when declaring the HTTP bindings for this API as well as to generate link URIs programatically.

For more details about how to declare affordances for the resources of an API using Hydra and Levanzo, please read the [spec documentation](http://www.hydra-cg.com/spec/latest/core/#adding-affordances-to-representations) and the documentation for the Clojure functions.

### 3. Instance creation, validation and generators

Once we have declared our API, we can create JSON-LD instances of the classes we have defined.

The functions to work with JSON-LD are defined in the namespace `levanzo.payload`.

To create a new JSON-LD document we can use the `levanzo.payload/jsonld` function. `jsonld` accepts pairs with a property and the value for the property and generates the JSON-LD document out of them.  We can also use other auxiliary functions in the namespace to declare properties and links.

``` clojure
;; Working with payloads

(require '[levanzo.payload :as payload])

(def address (payload/jsonld
              ["@type" (hydra/id sorg-PostalAddress)]
              [(hydra/id sorg-street-address) {"@value" "Finchley Road 523"}]
              [(hydra/id sorg-postal-code) {"@value" "NW3 7PB"}]))

(def address-alt (payload/instance
                  sorg-PostalAddress
                  (payload/supported-property {:property sorg-street-address
                                               :value "Finchley Road 523"})
                  (payload/supported-property {:property sorg-postal-code
                                               :value "NW3 7PB"})))
(is (= address address-alt))
```

JSON-LD documents can be validated using the information described in the API Hydra classes. Data ranges, required, readonly and writeonly flags will be used to check if the document is valid.

All the validation functionality is in the `levanzo.schema` namespace. The function `levanzo.schema/valid-instance?` can be used to check if one document is valid provided a certain access mode and a collection of classes.

The output of this function is a map, with classes URIs as keys and validation error descriptions as value if the instance is invalid or nil if the document is valid for that class. All the declared classes in the JSON-LD instance will be checked for constraints.

The following snippet shows the validation of different instances of the Person and PostalAddress classes:

``` clojure
(require '[levanzo.schema :as schema])

;; valid
(schema/valid-instance? :read
                        (payload/instance
                         sorg-PostalAddress
                         (payload/supported-property {:property sorg-street-address
                                                      :value "Finchley Road 523"})
                         (payload/supported-property {:property sorg-postal-code
                                                      :value "NW3 7PB"}))
                        {:supported-classes [sorg-PostalAddress]})

;; valid, postal code is optional on read
(schema/valid-instance? :read
                        (payload/instance
                         sorg-PostalAddress
                         (payload/supported-property {:property sorg-street-address
                                                      :value "Finchley Road 523"}))
                        {:supported-classes [sorg-PostalAddress]})

;; invalid, streeet addres has range string
(schema/valid-instance? :read
                        (payload/instance
                         sorg-PostalAddress
                         (payload/supported-property {:property sorg-street-address
                                                      :value 523}))
                        {:supported-classes [sorg-PostalAddress]})

;; invalid, streeet addres is mandatory on read
(schema/valid-instance? :read
                        (payload/instance
                         sorg-PostalAddress
                         (payload/supported-property {:property sorg-postal-code
                                                      :value "NW3 7PB"}))
                        {:supported-classes [sorg-PostalAddress]})

;; valid
(schema/valid-instance? :read
                        (payload/instance
                         sorg-Person
                         (payload/supported-property {:property sorg-name
                                                      :value "Tim"})
                         (payload/supported-property {:property sorg-email
                                                      :value "timbl@w3.org"}))
                        {:supported-classes [sorg-Person]})

;; invalid, password is mandatory on write
(schema/valid-instance? :write
                        (payload/instance
                         sorg-Person
                         (payload/supported-property {:property sorg-name
                                                      :value "Tim"})
                         (payload/supported-property {:property sorg-email
                                                      :value "timbl@w3.org"}))
                        {:supported-classes [sorg-Person]})


;; valid
(schema/valid-instance? :write
                        (payload/instance
                         sorg-Person
                         (payload/supported-property {:property sorg-name
                                                      :value "Tim"})
                         (payload/supported-property {:property sorg-email
                                                      :value "timbl@w3.org"})
                         (payload/supported-property {:property vocab-password
                                                      :value "~asd332fnxzz"}))
                        {:supported-classes [sorg-Person]})

;; invalid, password is writeonly
(schema/valid-instance? :read
                        (payload/instance
                         sorg-Person
                         (payload/supported-property {:property sorg-name
                                                      :value "Tim"})
                         (payload/supported-property {:property sorg-email
                                                      :value "timbl@w3.org"})
                         (payload/supported-property {:property vocab-password
                                                      :value "~asd332fnxzz"}))
                        {:supported-classes [sorg-Person]})
```

Future versions of the library will add support for more sophisticated declarative validations adding support for SHACL.

Another interesting features of Levanzo when working with payloads is the integraction with `clojure.spec` in order to sample generated instances of a particular class.
A Generator for any API class can be obtained using the functions in the `levanzo.spec.schema` namespace.

For example, to generate some instances of Person we can use the following code:

``` clojure
(clojure.pprint/pprint (last (gen/sample (schema-spec/make-payload-gen :read sorg-Person {:supported-classes [sorg-Person]}) 100)))
;; {"http://schema.org/email"
;;  [{"@value" "p6a2b11qHl4NH47On1xyf5KR4onN1zyb68",
;;    "@type" "http://www.w3.org/2001/XMLSchema#string"}],
;;  "http://schema.org/address"
;;  [{"@id"
;;    "https://15.165.15.23/1Xq35/zCm1b/70TBU/4EYLx/S3DB4/1aVcM/x29f5/n6844"}],
;;  "@id" "https://0.1.1.19/66xvB/x6tET/B90U9/U1sx0/cqjCc/RhLJg/h/3f2rP/8HfJ1",
;;  "@type" ["http://schema.org/Person"]}
```

We can also provide custom generators for certain properties (and the document @id) instead of allowing the code in `make-payload-gen` to pick the value based in the property range:

``` clojure
;; Overwriting generators
(require '[clojure.test.check.generators :as tg])

(clojure.pprint/pprint (last (gen/sample (schema-spec/make-payload-gen
                                          :read
                                          sorg-Person
                                          {:supported-classes [sorg-Person]}
                                          {(hydra/id sorg-email) (tg/return {"@value" "test@test.com"})
                                           (hydra/id sorg-name)  (tg/return {"@value" "Constant Name"})
                                           (hydra/id sorg-address) (tg/return {"@id" "http://test.com/constant_address"})
                                           "@id" (tg/return "http://test.com/generated")}) 100)))
;; {"http://schema.org/name" [{"@value" "Constant Name"}],
;; "http://schema.org/email" [{"@value" "test@test.com"}],
;; "http://schema.org/address" [{"@id" "http://test.com/constant_address"}],
;; "@id" "http://test.com/generated",
;; "@type" ["http://schema.org/Person"]}
```

## License

Copyright © 2017 Antonio Garrote

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

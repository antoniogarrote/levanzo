(ns levanzo.namespaces
  (:require [clojure.string :as string]))

(def ns-register (atom {}))
(def inverse-ns-register (atom {}))

(defn register [prefix uri]
  (swap! ns-register (fn [acc] (assoc acc prefix uri)))
  (swap! inverse-ns-register (fn [acc] (assoc acc uri prefix))))

(defmacro define-rdf-ns [ns url]
  `(do
     (levanzo.namespaces/register (name '~ns) ~url)
     (intern 'levanzo.namespaces '~ns (fn [x#] (str ~url x#)))))

(define-rdf-ns lvz "http://levanzo.org/vocab#")
(define-rdf-ns rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
(define-rdf-ns rdfs "http://www.w3.org/2000/01/rdf-schema#")
(define-rdf-ns hydra "http://www.w3.org/ns/hydra/core#")
(define-rdf-ns xsd "http://www.w3.org/2001/XMLSchema#")

(defn resolve [curie]
  (let [[p s] (string/split curie #"\:")]
    (if (some? (get @ns-register p))
      (str (get @ns-register p) s)
      (throw (Exception. (str "Impossible to resolve curie " curie))))))

(defn default-ns [prefix]
  (register "" prefix))

(defn prefix-for-ns [ns]
  (get @ns-register ns))

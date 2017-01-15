(defproject levanzo "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :monkeypatch-clojure-test false
  :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                 [bidi "2.0.16"]
                 [commons-validator "1.5.1"]
                 [cheshire "5.7.0"]
                 [com.cemerick/url "0.1.1"]
                 [com.taoensso/timbre "4.8.0"]
                 [com.github.jsonld-java/jsonld-java "0.9.0"]
                 [org.clojure/test.check "0.9.0" :scope "test"]
                 ;;; for testing
                 [com.novemberain/monger "3.1.0"]
                 [http-kit "2.2.0"]])

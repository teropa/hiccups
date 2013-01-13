(defproject hiccups "0.2.0"
  :description "A ClojureScript port of Hiccup - a fast library for rendering HTML in ClojureScript"
  :url "http://github.com/teropa/hiccups"
  :dependencies [[org.clojure/clojure "1.4.0"]]
  :plugins [[lein-cljsbuild "0.2.10"]]
  :cljsbuild {:test-commands {:unit ["phantomjs"
                                     "phantom/unit-test.js"
                                     "test-resources/unit-test.html"]}
              :builds {:test {:source-path "test"
                              :compiler {:output-to "target/unit-test.js"
                                         :optimizations :whitespace
                                         :pretty-print true}}}})

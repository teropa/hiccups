(defproject macchiato/hiccups "0.4.1"
  :description "A ClojureScript port of Hiccup - a fast library for rendering HTML in ClojureScript"
  :url "https://github.com/macchiato-framework/hiccups"
  :license {:name "Eclipse Public License - v 1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.2.0"
  :clojurescript? true
  :source-paths ["src/clj" "src/cljs"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.562"]]

  :plugins [[lein-doo "0.1.7"]
            [lein-cljsbuild "1.1.4"]]

  :profiles {:test
             {:cljsbuild
                   {:builds
                    {:test
                     {:source-paths ["src/clj" "src/cljs" "test/cljs"]
                      :compiler     {:main          hiccups.runner
                                     :output-to     "target/test/core.js"
                                     :target        :nodejs
                                     :optimizations :none
                                     :source-map    true
                                     :pretty-print  true}}}}
              :doo {:build "test"}}}

  :aliases
  {"test"
   ["do"
    ["clean"]
    ["with-profile" "test" "doo" "node" "once"]]
   "test-watch"
   ["do"
    ["clean"]
    ["with-profile" "test" "doo" "node"]]})

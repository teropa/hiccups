(def project 'hiccups)
(def version "0.3.1")

(set-env! :resource-paths #{"resources" "src/cljs" "src/clj"}
          :source-paths #{"test"}
          :asset-paths #{"html"}
          :dependencies '[[org.clojure/clojure "1.8.0"]
                          [org.clojure/clojurescript "1.8.40"]
                          [adzerk/boot-test "RELEASE" :scope "test"]
                          [adzerk/boot-cljs "1.7.228-1" :scope "test"]
                          [adzerk/boot-cljs-repl "0.3.0" :scope "test"]
                          [com.cemerick/piggieback "0.2.1" :scope "test"]
                          [weasel "0.7.0" :scope "test"]
                          [org.clojure/tools.nrepl "0.2.12" :scope "test"]
                          [adzerk/boot-reload "0.4.5" :scope "test"]
                          [pandeiro/boot-http "0.7.0" :scope "test"]
                          [cljsjs/boot-cljsjs "0.5.1" :scope "test"]])

(task-options!
  pom {:project     project
       :version     version
       :description "FIXME: write description"
       :url         "http://example/FIXME"
       :scm         {:url "https://github.com/yourname/tree-graph"}
       :license     {"Eclipse Public License"
                     "http://www.eclipse.org/legal/epl-v10.html"}})

(require
  '[adzerk.boot-cljs :refer :all]
  '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
  '[adzerk.boot-reload :refer :all]
  '[pandeiro.boot-http :refer :all]
  '[cljsjs.boot-cljsjs :refer :all])

(deftask web-dev
         "Developer workflow for web-component UX."
         []
         (comp
           (serve :dir "target/")
           (watch)
           (cljs-repl)
           (speak)
           (reload)
           (cljs)
           (target)))
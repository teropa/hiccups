(ns hiccups.test-runner
  (:use clojure.test)
  (:require [cljs.closure :as cljsc])
  (:import [java.awt Desktop]
           [java.io File]
           [java.net URI]
           [org.apache.commons.io FileUtils]))

(defn- compile-jstests []
  (println "Compiling...")
  (FileUtils/deleteDirectory (File. "out"))
  (let [compiled (cljsc/build
                   "test/hiccups/core_test.cljs"
                   {:optimizations :simple, :pretty-print true, :output-file "whatever"})
        out-file (File/createTempFile "hiccupstest" ".js")]
    (FileUtils/writeStringToFile out-file compiled "UTF-8")
    (.getCanonicalPath out-file)))


(defn- generate-html [js-file]
  (let [out-file (File/createTempFile "hiccupstest" ".html")]
    (FileUtils/writeStringToFile out-file
      (str "<!DOCTYPE html>"
           "<html>"
           "<head>"
           "<meta http-equiv='Content-Type' content='text/html; charset=utf-8'/>"
           "</head>"
           "<body>"
           "<script src='file:" js-file "'></script>"
           "</body>"
           "</html>"))
    (.getCanonicalPath out-file)))

(defn- launch-tests [html-file]
  (println "Launching...")
  (let [uri (URI. "file" html-file nil)
        desktop (Desktop/getDesktop)]
    (.browse desktop uri)))

(deftest all
  (-> (compile-jstests)
      (generate-html)
      (launch-tests)))

  
(ns hiccups.runtime
  (:require [clojure.string :as cstring]))

(def ^{:doc "Regular expression that parses a CSS-style id and class from a tag name." :private true}
  re-tag #"([^\s\.#]+)(?:#([^\s\.#]+))?(?:\.([^\s#]+))?")

(def ^{:doc "Characters to replace when escaping HTML" :private true}
  character-escapes {\& "&amp;", \< "&lt;", \> "&gt;", \" "&quot;"})

(def ^{:doc "A list of tags that need an explicit ending tag when rendered."}
  container-tags
  #{"a" "b" "body" "canvas" "dd" "div" "dl" "dt" "em" "fieldset" "form" "h1" "h2" "h3"
    "h4" "h5" "h6" "head" "html" "i" "iframe" "label" "li" "ol" "option" "pre"
    "script" "span" "strong" "style" "table" "textarea" "ul"})

(defn as-str [x]
  (if (or (keyword? x) (symbol? x))
    (name x)
    (str x)))

(def ^:dynamic *html-mode* :xml)

(defn- xml-mode? []
  (= *html-mode* :xml))

(defn in-mode [mode f]
  (binding [*html-mode* mode]
    (f)))

(defn escape-html
  "Change special characters into HTML character entities."
  [text]
  (-> (as-str text)
      (cstring/escape character-escapes)))

(def h escape-html) ; alias for escape-html

(defn end-tag []
  (if (xml-mode?) " />" ">"))

(defn xml-attribute [name value]
  (str " " (as-str name) "=\"" (escape-html value) "\""))

(defn render-attribute [[name value]]
  (cond
    (true? value)
      (if (xml-mode?)
          (xml-attribute name name)
          (str " " (as-str name)))
    (not value)
      ""
    :else
      (xml-attribute name value)))

(defn render-attr-map [attrs]
  (apply str
    (sort (map render-attribute attrs))))

(defn normalize-element
  "Ensure a tag vector is of the form [tag-name attrs content]."
  [[tag & content]]
  (when (not (or (keyword? tag) (symbol? tag) (string? tag)))
    (throw (str tag " is not a valid tag name")))
  (let [[_ tag id class] (re-matches re-tag (as-str tag))
        tag-attrs        {:id id
                          :class (if class (cstring/replace class "." " "))}
        map-attrs        (first content)]
    (if (map? map-attrs)
      [tag (merge tag-attrs map-attrs) (next content)]
      [tag tag-attrs content])))

(declare render-html)

(defn render-element
  "Render a tag vector as a HTML element."
  [element]
  (let [[tag attrs content] (normalize-element element)]
    (if (or content (container-tags tag))
      (str "<" tag (render-attr-map attrs) ">"
           (render-html content)
           "</" tag ">")
      (str "<" tag (render-attr-map attrs) (end-tag)))))

(defn render-html
  "Turn a Clojure data type into a string of HTML."
  [x]
  (cond
    (vector? x) (render-element x)
    (seq? x) (apply str (map render-html x))
    :else (as-str x)))

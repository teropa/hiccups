(ns hiccups.core
  "Library for rendering a tree of vectors into a string of HTML.
   Pre-compiles where possible for performance.
   Core macros and their (Clojure) helper functions."
  (:import [clojure.lang IPersistentVector ISeq]))

;; Pulled from old-contrib to avoid dependency
(defn- as-str
  ([] "")
  ([x] (if (instance? clojure.lang.Named x)
         (name x)
         (str x)))
  ([x & ys]
     ((fn [^StringBuilder sb more]
        (if more
          (recur (. sb  (append (as-str (first more)))) (next more))
          (str sb)))
      (new StringBuilder ^String (as-str x)) ys)))

(defn- escape-html
  "Change special characters into HTML character entities."
  [text]
  (.. ^String (as-str text)
    (replace "&"  "&amp;")
    (replace "<"  "&lt;")
    (replace ">"  "&gt;")
    (replace "\"" "&quot;")))

(defn- end-tag []
  ">")

(defn- xml-attribute [name value]
  (str " " (as-str name) "=\"" (escape-html value) "\""))

(defn- render-attribute [[name value]]
  (cond
    (true? value)
      (str " " (as-str name))
    (not value)
      ""
    :else
      (xml-attribute name value)))

(defn- render-attr-map [attrs]
  (apply str
    (sort (map render-attribute attrs))))

(def ^{:doc "Regular expression that parses a CSS-style id and class from a tag name." :private true}
  re-tag #"([^\s\.#]+)(?:#([^s\.#]+))?(?:\.([^\s#]+))?")

(def ^{:doc "A list of tags that need an explicit ending tag when rendered." :private true}
  container-tags
  #{"a" "b" "body" "canvas" "dd" "div" "dl" "dt" "em" "fieldset" "form" "h1" "h2" "h3"
    "h4" "h5" "h6" "head" "html" "i" "iframe" "label" "li" "ol" "option" "pre"
    "script" "span" "strong" "style" "table" "textarea" "ul"})

(defn- normalize-element 
  "Ensure a tag vector is of the form [tag-name attrs content]."
  [[tag & content]]
  (when (not (or (keyword? tag) (symbol? tag) (string? tag)))
    (throw (IllegalArgumentException. (str tag " is not a valid tag name"))))
  (let [[_ tag id class] (re-matches re-tag (as-str tag))
        tag-attrs        {:id id
                          :class (if class (.replace ^String class "." " "))}
        map-attrs        (first content)]
    (if (map? map-attrs)
      [tag (merge tag-attrs map-attrs) (next content)]
      [tag tag-attrs content])))

(defmulti render-html
  "Turn a Clojure data type into a string of HTML."
  {:private true}
  type)

(defn- render-element
  "Render a tag vector as a HTML element."
  [element]
  (let [[tag attrs content] (normalize-element element)]
    (if (or content (container-tags tag))
      (str "<" tag (render-attr-map attrs) ">"
           (render-html content)
           "</" tag ">")
      (str "<" tag (render-attr-map attrs) (end-tag)))))

(defmethod render-html IPersistentVector
  [element]
  (render-element element))

(defmethod render-html ISeq [coll]
  (apply str (map render-html coll)))

(defmethod render-html :default [x]
  (as-str x))

(defn- unevaluated?
  "True if the expression has not been evaluated."
  [expr]
  (or (symbol? expr)
      (and (seq? expr)
           (not= (first expr) `quote))))

(defn- compile-attr-map
  "Returns an unevaluated form that will render the supplied map as HTML
   attributes."
  [attrs]
  (if (some unevaluated? (mapcat identity attrs))
    `(hiccups.runtime/render-attr-map ~attrs)
    (render-attr-map attrs)))

(defn- form-name
  "Get the name of the supplied form."
  [form]
  (if (and (seq? form) (symbol? (first form)))
    (name (first form))))

(defmulti compile-form
  "Pre-compile certain standard forms, where possible."
  {:private true}
  form-name)

(defmethod compile-form "for"
  [[_ bindings body]]
  `(cljs.core/apply cljs.core/str (cljs.core/for ~bindings (html ~body))))

(defmethod compile-form "if"
  [[_ condition & body]]
 `(if ~condition ~@(for [x body] `(html ~x))))
      
(defmethod compile-form :default
  [expr]
  `(hiccups.runtime/render-html ~expr))

(defn- literal?
  "True if x is a literal value that can be rendered as-is."
  [x]
  (and (not (unevaluated? x))
       (or (not (or (vector? x) (map? x)))
           (every? literal? x))))

(defn- not-implicit-map?
  "True if we can infer that x is not a map."
  [x]
  (or (= (form-name x) "for")
      (not (unevaluated? x))))

(defn- element-compile-strategy 
  "Returns the compilation strategy to use for a given element."
  [[tag attrs & content :as element]]
  (cond
    (every? literal? element)
      ::all-literal                    ; e.g. [:span "foo"]
    (and (literal? tag) (map? attrs))
      ::literal-tag-and-attributes     ; e.g. [:span {} x]
    (and (literal? tag) (not-implicit-map? attrs))
      ::literal-tag-and-no-attributes  ; e.g. [:span ^String x]
    (literal? tag)
      ::literal-tag                    ; e.g. [:span x]
    :else
      ::default))                      ; e.g. [x]

(declare compile-html)

(defmulti compile-element
  "Returns an unevaluated form that will render the supplied vector as a HTML
   element."
  {:private true}
  element-compile-strategy)

(defmethod compile-element ::all-literal
  [element]
  (render-element (eval element)))

(defmethod compile-element ::literal-tag-and-attributes
  [[tag attrs & content]]
  (let [[tag attrs _] (normalize-element [tag attrs])]
    (if (or content (container-tags tag))
      `(cljs.core/str ~(str "<" tag) ~(compile-attr-map attrs) ">"
                      ~@(compile-html content)
                      ~(str "</" tag ">"))
      `(cljs.core/str "<" ~tag ~(compile-attr-map attrs) ~(end-tag)))))

(defmethod compile-element ::literal-tag-and-no-attributes
  [[tag & content]]
  (compile-element (apply vector tag {} content)))


(defmethod compile-element ::literal-tag
  [[tag attrs & content]]
  (let [[tag tag-attrs _] (normalize-element [tag])
        attrs-sym         (gensym "attrs")]
   `(let [~attrs-sym ~attrs]
      (if (map? ~attrs-sym)
         ~(if (or content (container-tags tag))
            `(cljs.core/str ~(str "<" tag)
                            (hiccups.runtime/render-attr-map (merge ~tag-attrs ~attrs-sym)) ">"
                            ~@(compile-html content)
                            ~(str "</" tag ">"))
            `(cljs.core/str ~(str "<" tag)
                            (hiccups.runtime/render-attr-map (merge ~tag-attrs ~attrs-sym))
                            ~(end-tag)))
         ~(if (or attrs (container-tags tag))
           `(cljs.core/str ~(str "<" tag (render-attr-map tag-attrs) ">")
                           ~@(compile-html (cons attrs-sym content))
                           ~(str "</" tag ">"))
           (str "<" tag (render-attr-map tag-attrs) (end-tag)))))))

(defmethod compile-element :default
  [element]
  `(hiccups.runtime/render-element
     [~(first element)
      ~@(for [x (rest element)]
          (if (vector? x)
            (compile-element x)
            x))]))

(defn- compile-html
  "Pre-compile data structures into HTML where possible"
  [content]
  (doall (for [expr content]
           (cond
             (vector? expr) (compile-element expr)
             (literal? expr) expr
             (seq? expr) (compile-form expr)
             :else `(hiccups.runtime/render-html ~expr)))))


(defn- collapse-strs
  "Collapse nested str expressions into one, where possible."
  [expr]
  (if (seq? expr)
    (cons
      (first expr)
      (mapcat
        #(if (and (seq? %) (symbol? (first %)) (= (first %) (first expr) `cljs.core/str))
            (rest (collapse-strs %))
            (list (collapse-strs %)))
        (rest expr)))
    expr))

(defmacro html
  "Render Clojure data structures to a string of HTML."
  [& content]
  (collapse-strs `(cljs.core/str ~@(compile-html content))))


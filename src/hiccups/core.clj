(ns hiccups.core
  "Library for rendering a tree of vectors into a string of HTML.
   Pre-compiles where possible for performance.
   Core macros and their (Clojure) helper functions."
  (:require [hiccups.runtime :as rt])
  (:import [clojure.lang IPersistentVector ISeq]))

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
    (rt/render-attr-map attrs)))

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
  (rt/render-element (eval element)))

(defmethod compile-element ::literal-tag-and-attributes
  [[tag attrs & content]]
  (let [[tag attrs _] (rt/normalize-element [tag attrs])]
    (if (or content (rt/container-tags tag))
      `(cljs.core/str ~(str "<" tag) ~(compile-attr-map attrs) ">"
                      ~@(compile-html content)
                      ~(str "</" tag ">"))
      `(cljs.core/str "<" ~tag ~(compile-attr-map attrs) ~(rt/end-tag)))))

(defmethod compile-element ::literal-tag-and-no-attributes
  [[tag & content]]
  (compile-element (apply vector tag {} content)))


(defmethod compile-element ::literal-tag
  [[tag attrs & content]]
  (let [[tag tag-attrs _] (rt/normalize-element [tag])
        attrs-sym         (gensym "attrs")]
   `(let [~attrs-sym ~attrs]
      (if (map? ~attrs-sym)
         ~(if (or content (rt/container-tags tag))
            `(cljs.core/str ~(str "<" tag)
                            (hiccups.runtime/render-attr-map (merge ~tag-attrs ~attrs-sym)) ">"
                            ~@(compile-html content)
                            ~(str "</" tag ">"))
            `(cljs.core/str ~(str "<" tag)
                            (hiccups.runtime/render-attr-map (merge ~tag-attrs ~attrs-sym))
                            ~(rt/end-tag)))
         ~(if (or attrs (rt/container-tags tag))
           `(cljs.core/str ~(str "<" tag (rt/render-attr-map tag-attrs) ">")
                           ~@(compile-html (cons attrs-sym content))
                           ~(str "</" tag ">"))
           (str "<" tag (rt/render-attr-map tag-attrs) (rt/end-tag)))))))

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



(ns hiccups.core
  "Library for rendering a tree of vectors into a string of HTML.
   Pre-compiles where possible for performance.
   Core macros and their (Clojure) helper functions."
  (:require [hiccups.runtime :as rt])
  (:import [clojure.lang IPersistentVector ISeq]))

(def doctype
  {:html4
   (str "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01//EN\" "
        "\"http://www.w3.org/TR/html4/strict.dtd\">\n")
   :xhtml-strict
   (str "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" "
        "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n")
   :xhtml-transitional
   (str "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" "
        "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">\n")
   :html5
   "<!DOCTYPE html>\n"})

(defn wrap-attrs
  "Add an optional attribute argument to a function that returns a element vector."
  [func]
  (fn [& args]
    (if (map? (first args))
      (let [[tag & body] (apply func (rest args))]
        (if (map? (first body))
          (apply vector tag (merge (first body) (first args)) (rest body))
          (apply vector tag (first args) body)))
      (apply func args))))

(defn- update-arglists [arglists]
  (for [args arglists]
    (vec (cons 'attr-map? args))))

(defmacro defelem
  "Defines a function that will return a element vector. If the first argument
  passed to the resulting function is a map, it merges it with the attribute
  map of the returned element value."
  [name & fdecl]
  `(do (defn ~name ~@fdecl)
       (alter-meta! (var ~name) update-in [:arglists] #'update-arglists)
       (alter-var-root (var ~name) wrap-attrs)))

(defelem xhtml-tag
  "Create an XHTML element for the specified language."
  [lang & contents]
  [:html {:xmlns "http://www.w3.org/1999/xhtml"
          "xml:lang" lang
          :lang lang}
    contents])

(defn xml-declaration
  "Create a standard XML declaration for the following encoding."
  [encoding]
  (str "<?xml version=\"1.0\" encoding=\"" encoding "\"?>\n"))

(defmacro html4
  "Create a HTML 4 document with the supplied contents. The first argument
  may be an optional attribute map."
  [& contents]
  `(html {:mode :sgml}
     ~(doctype :html4)
     [:html ~@contents]))

(defmacro xhtml
  "Create a XHTML 1.0 strict document with the supplied contents. The first
  argument may be an optional attribute may. The following attributes are
  treated specially:
    :lang     - The language of the document
    :encoding - The character encoding of the document, defaults to UTF-8."
  [options & contents]
  (if-not (map? options)
    `(xhtml {} ~options ~@contents)
    `(let [options# ~options]
       (html {:mode :xml}
         (xml-declaration (options# :encoding "UTF-8"))
         ~(doctype :xhtml-strict)
         (xhtml-tag (options# :lang) ~@contents)))))

(defmacro html5
  "Create a HTML5 document with the supplied contents."
  [options & contents]
  (if-not (map? options)
    `(html5 {} ~options ~@contents)
    (if (options :xml?)
      `(let [options# (dissoc ~options :xml?)]
         (html {:mode :xml}
           (xml-declaration (options# :encoding "UTF-8"))
           ~(doctype :html5)
           (xhtml-tag options# (options# :lang) ~@contents)))
      `(let [options# (dissoc ~options :xml?)]
         (html {:mode :html}
           ~(doctype :html5)
           [:html options# ~@contents])))))


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
  [options & content]
  (letfn [(make-html [content]
            (collapse-strs `(cljs.core/str ~@(compile-html content))))]
    (if-let [mode (and (map? options) (:mode options))]
      (binding [rt/*html-mode* mode]
        `(hiccups.runtime/in-mode ~mode
           (fn [] ~(make-html content))))
      (make-html (cons options content)))))

(defmacro defhtml
  "Define a function, but wrap its output in an implicit html macro."
  [name & fdecl]
  (let [[fhead fbody] (split-with #(not (or (list? %) (vector? %))) fdecl)
        wrap-html (fn [[args & body]] `(~args (html ~@body)))]
    `(defn ~name
       ~@fhead
       ~@(if (vector? (first fbody))
           (wrap-html fbody)
           (map wrap-html fbody)))))


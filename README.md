Hiccups
=======

Hiccups is a ClojureScript port of the [Hiccup](https://github.com/weavejester/hiccup) HTML generation library.
It uses vectors to represent tags, and maps to represent a tag's attributes.

The goal is to provide similar performance to Closure Templates with a much more Clojure friendly
syntax.

Differences from Hiccup
-----------------------

* In ClojureScript, macros need to be defined in separate Clojure namespaces. Because of this,
  core functionality is split into two files: `core.clj` contains the macros and compile-time only
  functions, and `runtime.cljs` contains functions that are also available at runtime. The contents
  of `runtime.cljs` are also used at compile-time, so the goal is to keep it portable between
  ClojureScript and Clojure.
* Unit tests are run in a PhantomJS browser using [lein-cljsbuild](https://github.com/emezeske/lein-cljsbuild/) and Closure's testing libs.
* Not everything has been ported yet. See ToDo.

Alternatives
------------

* [Crate](https://github.com/ibdknox/crate) is an alternative Hiccup style library for ClojureScript. The main difference
  between Crate and Hiccups is that Crate generates DOM nodes and Hiccups generates strings. There are a few reasons why you might consider Hiccups over Crate (YMMV, of course):
  * As with the original Hiccup, Hiccups tries to do as much as possible at compile time, with macro expansion.
  * Working with strings can be much more
  performant than working with DOM nodes, especially with large amounts of markup, and
  [especially with older browsers](http://www.quirksmode.org/dom/innerhtml.html).
  * Easier to use in headless environments like Node.js
* [Closure Templates](http://code.google.com/closure/templates/) is Google's Closure templating library.

Install
-------

Add the following dependency to your `project.clj` file:

```clojure
[hiccups "0.3.0"]
```

Usage
-----

Require both the core macros and the runtime functions in your namespace declaration:

```clojure
(ns myns
  (:require-macros [hiccups.core :as hiccups :refer [html]])
  (:require [hiccups.runtime :as hiccupsrt]))

(hiccups/defhtml my-template []
  [:div
    [:a {:href "https://github.com/weavejester/hiccup"}
      "Hiccup"]])
```

Syntax
------

Here is a basic example of Hiccups syntax:

```clojure
(html [:span {:class "foo"} "bar"])
"<span class=\"foo\">bar</span>"
```

The first element of the vector is used as the tag name. The second
attribute can optionally be a map, in which case it is used to supply
the tag's attributes. Every other element is considered part of the
tag's body.

Hiccups is intelligent enough to render different HTML tags in different
ways, in order to accommodate browser quirks:

```clojure
(html [:script])
"<script></script>"
(html [:p])
"<p />"
```

And provides a CSS-like shortcut for denoting `id` and `class`
attributes:

```clojure
(html [:div#foo.bar.baz "bang"])
"<div id=\"foo\" class=\"bar baz\">bang</div>"
```

If the body of the tag is a seq, its contents will be expanded out into
the tag body. This makes working with forms like `map` and `for` more
convenient:

```clojure
(html [:ul
        (for [x (range 1 4)]
          [:li x])])
"<ul><li>1</li><li>2</li><li>3</li></ul>"
```

Note that while lists are considered to be seqs in Clojure(Script), vectors and sets are not. As a consequence, Hiccups will bail out if a vector is passed in without a tag: `[[:div] [:div]]`.

See the [Hiccup wiki](https://github.com/weavejester/hiccup/wiki) for more information.

ToDo
----

* Catch up with recent changes in Hiccup.
* Form helpers
* Page helpers
* Figure out if the runtime can be pulled in without an explicit require by the user
* Explore potential performance improvements using Google's StringBuffer et al.


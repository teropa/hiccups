Hiccups
=======

Hiccups is a ClojureScript port of [Hiccup](https://github.com/weavejester/hiccup) -
an alternative to [Closure Templates](http://code.google.com/closure/templates/)
for representing HTML in ClojureScript. It uses vectors to represent tags,
and maps to represent a tag's attributes.

The goal is to provide similar performance to Closure Templates with a much more "Clojure friendly"
syntax. 

Install
-------

Hiccups needs to be in the classpath when the ClojureScript compiler is run. If you're developing
in a Leiningen project, you can just add a dependency to `[hiccups "0.1.0"]`. Otherwise, there are
at least two options:

1. Download the [Hiccups jar](http://clojars.org/repo/hiccups/hiccups/0.1/hiccups-0.1.jar)
   and drop it in your classpath.
2. Clone the Git repository and add `src/clj` and `src/cljs` to your classpath.

ClojureScript command line tools
--------------------------------

While best practices for how to include external ClojureScript libraries don't really exist yet, one 
option is to drop the [Hiccups jar](http://clojars.org/repo/hiccups/hiccups/0.1/hiccups-0.1.jar)
in `$CLOJURESCRIPT_HOME/lib`. This will make Hiccups available to the command line compiler script
as well as the REPL when launched via `script/repl` or `script/repljs`.
    
Usage
-----

Require both the core macros and the runtime functions in your namespace declaration:

    (ns myns
      (:require-macros [hiccups.core :as hiccups])
      (:require [hiccups.runtime :as hiccupsrt]))
      
    (defn ^:export my-template []      
      (hiccups/html 
        [:div
          [:a {:href "https://github.com/weavejester/hiccup"}
            "Hiccup"]]))

Syntax
------

Here is a basic example of Hiccups syntax:

    (html [:span {:class "foo"} "bar"])
    "<span class=\"foo\">bar</span>"

The first element of the vector is used as the tag name. The second
attribute can optionally be a map, in which case it is used to supply
the tag's attributes. Every other element is considered part of the
tag's body.

Hiccups is intelligent enough to render different HTML tags in different
ways, in order to accommodate browser quirks:

    (html [:script])
    "<script></script>"
    (html [:p])
    "<p />"

And provides a CSS-like shortcut for denoting `id` and `class`
attributes:

    (html [:div#foo.bar.baz "bang"])
    "<div id=\"foo\" class=\"bar baz\">bang</div>"

If the body of the tag is a seq, its contents will be expanded out into
the tag body. This makes working with forms like `map` and `for` more
convenient:

    (html [:ul
            (for [x (range 1 4)]
              [:li x])])
    "<ul><li>1</li><li>2</li><li>3</li></ul>"
    
See the [Hiccup wiki](https://github.com/weavejester/hiccup/wiki) for more information.

ToDo
----

* XML mode
* Form helpers
* Page helpers
* Figure out if the runtime can be pulled in without an explicit require by the user
* Explore potential performance improvements using Google's StringBuffer et al.
* Run tests in Rhino instead of a browser?


;;; This namespace is used for testing purpose. It use the
;;; clojurescript.test lib.
(ns hiccups.core-test
  (:require
    [cljs.test :refer-macros [is are deftest testing use-fixtures]]
    [hiccups.runtime :as hiccupsrt])
  (:require-macros
    [hiccups.core :as hiccups]))

(deftest runtime-escaped-chars
         (is (= (hiccupsrt/escape-html "\"") "&quot;"))
         (is (= (hiccupsrt/escape-html "<") "&lt;"))
         (is (= (hiccupsrt/escape-html ">") "&gt;"))
         (is (= (hiccupsrt/escape-html "&") "&amp;"))
         (is (= (hiccupsrt/escape-html "foo") "foo")))

(deftest tag-names
         (is (= (hiccups/html [:div]) "<div></div>"))
         (is (= (hiccups/html ["div"]) "<div></div>"))
         (is (= (hiccups/html ['div]) "<div></div>"))
         (is (= (hiccups/html [:div#foo]) "<div id=\"foo\"></div>"))
         (is (= (hiccups/html [:form#sign-in]) "<form id=\"sign-in\"></form>"))
         (is (= (hiccups/html [:input#sign-in-email]) "<input id=\"sign-in-email\" />"))
         (is (= (hiccups/html [:div.foo]) "<div class=\"foo\"></div>"))
         (is (= (hiccups/html [:div.foo (str "bar" "baz")])
                "<div class=\"foo\">barbaz</div>"))
         (is (= (hiccups/html [:div.a.b]) "<div class=\"a b\"></div>"))
         (is (= (hiccups/html [:div.a.b.c]) "<div class=\"a b c\"></div>"))
         (is (= (hiccups/html [:div#foo.bar.baz])
                "<div class=\"bar baz\" id=\"foo\"></div>")))

(deftest tag-contents
         ; empty tags
         (is (= (hiccups/html [:div]) "<div></div>"))
         (is (= (hiccups/html [:h1]) "<h1></h1>"))
         (is (= (hiccups/html [:script]) "<script></script>"))
         (is (= (hiccups/html [:text]) "<text />"))
         (is (= (hiccups/html [:a]) "<a></a>"))
         (is (= (hiccups/html [:iframe]) "<iframe></iframe>"))
         ; tags containing text
         (is (= (hiccups/html [:text "Lorem Ipsum"]) "<text>Lorem Ipsum</text>"))
         ; contents are concatenated
         (is (= (hiccups/html [:body "foo" "bar"]) "<body>foobar</body>"))
         (is (= (hiccups/html [:body [:p] [:br]]) "<body><p /><br /></body>"))
         ; seqs are expanded
         (is (= (hiccups/html [:body (list "foo" "bar")]) "<body>foobar</body>"))
         (is (= (hiccups/html (list [:p "a"] [:p "b"])) "<p>a</p><p>b</p>"))
         ; tags can contain tags
         (is (= (hiccups/html [:div [:p]]) "<div><p /></div>"))
         (is (= (hiccups/html [:div [:b]]) "<div><b></b></div>"))
         (is (= (hiccups/html [:p [:span [:a "foo"]]])
                "<p><span><a>foo</a></span></p>")))

(deftest tag-attributes
         ; tag with blank attribute map
         (is (= (hiccups/html [:xml {}]) "<xml />"))
         ; tag with populated attribute map
         (is (= (hiccups/html [:xml {:a "1", :b "2"}]) "<xml a=\"1\" b=\"2\" />"))
         (is (= (hiccups/html [:img {"id" "foo"}]) "<img id=\"foo\" />"))
         (is (= (hiccups/html [:img {'id "foo"}]) "<img id=\"foo\" />"))
         (is (= (hiccups/html [:xml {:a "1", 'b "2", "c" "3"}])
                "<xml a=\"1\" b=\"2\" c=\"3\" />"))
         ; attribute values are escaped
         (is (= (hiccups/html [:div {:id "\""}]) "<div id=\"&quot;\"></div>"))
         ; boolean attributes
         (is (= (hiccups/html [:input {:type "checkbox" :checked true}])
                "<input checked=\"checked\" type=\"checkbox\" />"))
         (is (= (hiccups/html [:input {:type "checkbox" :checked false}])
                "<input type=\"checkbox\" />"))
         ; nil attributes
         (is (= (hiccups/html [:span {:class nil} "foo"])
                "<span>foo</span>")))

(deftest compiled-tags
         ; tag content can be vars
         (is (= (let [x "foo"] (hiccups/html [:span x])) "<span>foo</span>"))
         ; tag content can be forms
         (is (= (hiccups/html [:span (str (+ 1 1))]) "<span>2</span>"))
         (is (= (hiccups/html [:span ({:foo "bar"} :foo)]) "<span>bar</span>"))
         ; attributes can contain vars
         (let [x "foo"]
           (is (= (hiccups/html [:xml {:x x}]) "<xml x=\"foo\" />"))
           (is (= (hiccups/html [:xml {x "x"}]) "<xml foo=\"x\" />"))
           (is (= (hiccups/html [:xml {:x x} "bar"]) "<xml x=\"foo\">bar</xml>")))
         ; attributes are evaluated
         (is (= (hiccups/html [:img {:src (str "/foo" "/bar")}])
                "<img src=\"/foo/bar\" />"))
         (is (= (hiccups/html [:div {:id (str "a" "b")} (str "foo")])
                "<div id=\"ab\">foo</div>"))
         ; optimized forms
         (is (= (hiccups/html [:ul (for [n (range 3)]
                                     [:li n])])
                "<ul><li>0</li><li>1</li><li>2</li></ul>"))
         (is (= (hiccups/html [:div (if true
                                      [:span "foo"]
                                      [:span "bar"])])
                "<div><span>foo</span></div>"))
         ; values are evaluated only once
         (let [times-called (atom 0)
               foo          #(do (swap! times-called inc) "foo")]
           (hiccups/html [:div (foo)])
           (is (= 1 @times-called))))

(deftest defhtml-macro
         ; basic html function
         (hiccups/defhtml basic-fn [x] [:span x])
         (is (= (basic-fn "foo") "<span>foo</span>"))
         ; html function with overloads
         (hiccups/defhtml overloaded-fn
                          ([x] [:span x])
                          ([x y] [:span x [:div y]]))
         (is (= (overloaded-fn "foo") "<span>foo</span>"))
         (is (= (overloaded-fn "foo" "bar")
                "<span>foo<div>bar</div></span>")))

(deftest render-modes
         ; "closed tag"
         (is (= (hiccups/html [:br]) "<br />"))
         (is (= (hiccups/html {:mode :xml} [:br]) "<br />"))
         (is (= (hiccups/html {:mode :sgml} [:br]) "<br>"))
         (is (= (hiccups/html {:mode :html} [:br]) "<br>"))
         ; boolean attributes
         (is (= (hiccups/html {:mode :xml} [:input {:type "checkbox" :checked true}])
                "<input checked=\"checked\" type=\"checkbox\" />"))
         (is (= (hiccups/html {:mode :sgml} [:input {:type "checkbox" :checked true}])
                "<input checked type=\"checkbox\">"))
         ; "laziness and binding scope"
         (is (= (hiccups/html {:mode :sgml} [:html [:link] (list [:link])])
                "<html><link><link></html>")))

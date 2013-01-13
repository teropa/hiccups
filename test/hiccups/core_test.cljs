(ns hiccups.core-test
  (:require-macros [hiccups.core :as hiccups]
                   [hiccups.test-macros :as t])
  (:require [goog.testing.jsunit :as jsunit]
            [hiccups.runtime :as hiccupsrt]))

(t/deftest runtime-escaped-chars
  (t/is (= (hiccupsrt/escape-html "\"") "&quot;"))
  (t/is (= (hiccupsrt/escape-html "<") "&lt;"))
  (t/is (= (hiccupsrt/escape-html ">") "&gt;"))
  (t/is (= (hiccupsrt/escape-html "&") "&amp;"))
  (t/is (= (hiccupsrt/escape-html "foo") "foo")))

(t/deftest tag-names
  (t/is (= (hiccups/html [:div]) "<div></div>"))
  (t/is (= (hiccups/html ["div"]) "<div></div>"))
  (t/is (= (hiccups/html ['div]) "<div></div>"))
  (t/is (= (hiccups/html [:div#foo]) "<div id=\"foo\"></div>"))
  (t/is (= (hiccups/html [:form#sign-in]) "<form id=\"sign-in\"></form>"))
  (t/is (= (hiccups/html [:input#sign-in-email]) "<input id=\"sign-in-email\" />"))
  (t/is (= (hiccups/html [:div.foo]) "<div class=\"foo\"></div>"))
  (t/is (= (hiccups/html [:div.foo (str "bar" "baz")])
           "<div class=\"foo\">barbaz</div>"))
  (t/is (= (hiccups/html [:div.a.b]) "<div class=\"a b\"></div>"))
  (t/is (= (hiccups/html [:div.a.b.c]) "<div class=\"a b c\"></div>"))
  (t/is (= (hiccups/html [:div#foo.bar.baz])
           "<div class=\"bar baz\" id=\"foo\"></div>")))

(t/deftest tag-contents
 ; empty tags
 (t/is (= (hiccups/html [:div]) "<div></div>"))
 (t/is (= (hiccups/html [:h1]) "<h1></h1>"))
 (t/is (= (hiccups/html [:script]) "<script></script>"))
 (t/is (= (hiccups/html [:text]) "<text />"))
 (t/is (= (hiccups/html [:a]) "<a></a>"))
 (t/is (= (hiccups/html [:iframe]) "<iframe></iframe>"))
 ; tags containing text
 (t/is (= (hiccups/html [:text "Lorem Ipsum"]) "<text>Lorem Ipsum</text>"))
 ; contents are concatenated
 (t/is (= (hiccups/html [:body "foo" "bar"]) "<body>foobar</body>"))
 (t/is (= (hiccups/html [:body [:p] [:br]]) "<body><p /><br /></body>"))
 ; seqs are expanded
 (t/is (= (hiccups/html [:body (list "foo" "bar")]) "<body>foobar</body>"))
 (t/is (= (hiccups/html (list [:p "a"] [:p "b"])) "<p>a</p><p>b</p>"))
 ; vecs don't expand - error if vec doesn't have tag name
 (t/is-thrown (hiccups/html (vector [:p "a"] [:p "b"])))
 ; tags can contain tags
 (t/is (= (hiccups/html [:div [:p]]) "<div><p /></div>"))
 (t/is (= (hiccups/html [:div [:b]]) "<div><b></b></div>"))
 (t/is (= (hiccups/html [:p [:span [:a "foo"]]])
          "<p><span><a>foo</a></span></p>")))

(t/deftest tag-attributes
  ; tag with blank attribute map
  (t/is (= (hiccups/html [:xml {}]) "<xml />"))
  ; tag with populated attribute map
  (t/is (= (hiccups/html [:xml {:a "1", :b "2"}]) "<xml a=\"1\" b=\"2\" />"))
  (t/is (= (hiccups/html [:img {"id" "foo"}]) "<img id=\"foo\" />"))
  (t/is (= (hiccups/html [:img {'id "foo"}]) "<img id=\"foo\" />"))
  (t/is (= (hiccups/html [:xml {:a "1", 'b "2", "c" "3"}])
           "<xml a=\"1\" b=\"2\" c=\"3\" />"))
  ; attribute values are escaped
  (t/is (= (hiccups/html [:div {:id "\""}]) "<div id=\"&quot;\"></div>"))
  ; boolean attributes
  (t/is (= (hiccups/html [:input {:type "checkbox" :checked true}])
           "<input checked=\"checked\" type=\"checkbox\" />"))
  (t/is (= (hiccups/html [:input {:type "checkbox" :checked false}])
           "<input type=\"checkbox\" />"))
  ; nil attributes
  (t/is (= (hiccups/html [:span {:class nil} "foo"])
           "<span>foo</span>")))

(t/deftest compiled-tags
  ; tag content can be vars
  (t/is (= (let [x "foo"] (hiccups/html [:span x])) "<span>foo</span>"))
  ; tag content can be forms
  (t/is (= (hiccups/html [:span (str (+ 1 1))]) "<span>2</span>"))
  (t/is (= (hiccups/html [:span ({:foo "bar"} :foo)]) "<span>bar</span>"))
  ; attributes can contain vars
  (let [x "foo"]
    (t/is (= (hiccups/html [:xml {:x x}]) "<xml x=\"foo\" />"))
    (t/is (= (hiccups/html [:xml {x "x"}]) "<xml foo=\"x\" />"))
    (t/is (= (hiccups/html [:xml {:x x} "bar"]) "<xml x=\"foo\">bar</xml>")))
  ; attributes are evaluated
    (t/is (= (hiccups/html [:img {:src (str "/foo" "/bar")}])
             "<img src=\"/foo/bar\" />"))
    (t/is (= (hiccups/html [:div {:id (str "a" "b")} (str "foo")])
             "<div id=\"ab\">foo</div>"))
  ; optimized forms
    (t/is (= (hiccups/html [:ul (for [n (range 3)]
                                  [:li n])])
             "<ul><li>0</li><li>1</li><li>2</li></ul>"))
    (t/is (= (hiccups/html [:div (if true
                                   [:span "foo"]
                                   [:span "bar"])])
             "<div><span>foo</span></div>"))
  ; values are evaluated only once
  (let [times-called (atom 0)
        foo #(do (swap! times-called inc) "foo")]
    (hiccups/html [:div (foo)])
    (t/is (= 1 @times-called))))


(t/deftest defhtml-macro
  ; basic html function
  (hiccups/defhtml basic-fn [x] [:span x])
  (t/is (= (basic-fn "foo") "<span>foo</span>"))
  ; html function with overloads
  (hiccups/defhtml overloaded-fn
    ([x] [:span x])
    ([x y] [:span x [:div y]]))
  (t/is (= (overloaded-fn "foo") "<span>foo</span>"))
  (t/is (= (overloaded-fn "foo" "bar")
           "<span>foo<div>bar</div></span>")))

(t/deftest render-modes
  ; "closed tag"
  (t/is (= (hiccups/html [:br]) "<br />"))
  (t/is (= (hiccups/html {:mode :xml} [:br]) "<br />"))
  (t/is (= (hiccups/html {:mode :sgml} [:br]) "<br>"))
  (t/is (= (hiccups/html {:mode :html} [:br]) "<br>"))
  ; boolean attributes
  (t/is (= (hiccups/html {:mode :xml} [:input {:type "checkbox" :checked true}])
           "<input checked=\"checked\" type=\"checkbox\" />"))
  (t/is (= (hiccups/html {:mode :sgml} [:input {:type "checkbox" :checked true}])
           "<input checked type=\"checkbox\">"))
  ; "laziness and binding scope"
  (t/is (= (hiccups/html {:mode :sgml} [:html [:link] (list [:link])])
           "<html><link><link></html>")))

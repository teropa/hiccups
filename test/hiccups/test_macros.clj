(ns hiccups.test-macros)

(defmacro deftest [nm & body]
  (let [testname (symbol (str "test-" (name nm)))]
    `(do
       (defn ~testname []
         ~@body)
       (set! (. goog.global ~testname) ~testname))))

(defmacro is [expr]
  `(window/assertTrue ~expr))

(defmacro is-thrown [& exprs]
  `(window/assertThrows
     (fn [] ~@exprs)))

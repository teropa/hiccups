(ns hiccups.test-macros)

(defmacro deftest [nm & body]
  (let [testname (str "test-" (name nm))]
    `(aset goog.global
           ~testname
           (fn [] ~@body))))

(defmacro is [expr]
  `(window/assertTrue ~expr))

(defmacro is-thrown [& exprs]
  `(window/assertThrows
     (fn [] ~@exprs)))

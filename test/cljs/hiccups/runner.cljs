(ns hiccups.runner
  (:require
    [doo.runner :refer-macros [doo-tests]]
    [hiccups.core-test]))

(doo-tests 'hiccups.core-test)

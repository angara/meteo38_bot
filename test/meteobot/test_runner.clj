(ns meteobot.test-runner
  "Minimal clojure.test runner: clojure -M:test"
  (:require
   [clojure.test :as t]
   [meteobot.app.command-test]
   [meteobot.app.fmt-test]
   [meteobot.data.store-test]))


(defn -main [& _]
  (let [summary (t/run-tests 'meteobot.app.command-test
                             'meteobot.app.fmt-test
                             'meteobot.data.store-test)]
    (System/exit (if (t/successful? summary) 0 1))))

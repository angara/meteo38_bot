(ns meteobot.test-runner
  "Minimal clojure.test runner: clojure -M:test"
  (:require
   [clojure.test :as t]
   [meteobot.app.fmt-test]))


(defn -main [& _]
  (let [summary (t/run-tests 'meteobot.app.fmt-test)]
    (System/exit (if (t/successful? summary) 0 1))))

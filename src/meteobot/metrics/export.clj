(ns meteobot.metrics.export
  (:require
   [iapetos.export :refer [text-format]]
   [iapetos.collector.ring :refer [metrics-response]]
   [meteobot.metrics.reg :as reg]
   ))

(comment
  
  (print
   (text-format reg/registry))

  ,)
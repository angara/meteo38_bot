(ns meteobot.metrics.core
  (:require
   [iapetos.core :as prom]
   [iapetos.export :as prom-exp]
   ,))


;; iexp/text-format registry


(def registry
  (->
   (prom/collector-registry)
   (prom/register
    
    (prom/counter :meteobot/internal-errors 
                  {:labels [:type :cause] :description "application internal error"})
    
    (prom/counter :meteobot/telegram-updates 
                  {:labels [:type] :description "telegram inbound updates"})
    
    (prom/counter :meteobot/telegram-api 
                  {:labels [:method :status] :description "telegram api-call status"})

    (prom/counter :meteobot/send-subs
              {:description "send subscription data"})
    
    (prom/counter :meteobot/meteo-data-api
              {:labels [:method] :description "meteo-data api"})
    
    ,)))



(defn inc-metric [metric labels]
  (try
    (prom/inc registry metric labels 1)
    (catch Exception _
      (prom/inc registry :meteobot/internal-errors {:type "wrong_metric" :cause metric} 1))
    ,))


(comment
  
  (prom/inc registry :meteobot/messages-in {:type "message"} 1)

  (prom/inc registry :meteobot/telegram-api {:method "sendMessage"} 1)

  (print (prom-exp/text-format registry))

  
  (inc-metric :meteobot/telegram-api nil)


  (inc-metric :meteobot/_non-existent {})

  ,)

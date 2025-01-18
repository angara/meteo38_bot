(ns meteobot.app.dispatch)



(defn message-priv [ctx message]
  
  (prn "priv message:" message)
  )

(defn message-group [ctx message]
  (prn "group-message")

  )

(defn void-handler [ctx data]
  (prn "void:" data)
  )


(def type-handler-map
  {
   :message [message-priv message-group]
   :edited_message []
  })



(defn router [ctx upd]
  (let [[u t] (keys upd)
        utype (if (= :update_id u) t u)
        data (get upd utype)
        [priv-h group-h] (type-handler-map utype)]
    (if (:is_private data)
      (if priv-h
        (priv-h ctx data)
        (do 
          ;; inc unhandled
          )
        )
      (if group-h
        (group-h ctx data)
        (do
          ;; inc unhandled
          )
        )
      )
    ))


(comment
  
  (router {} {:update_id 1 :message {}})

  ,)

(ns user
  (:require
   [mount.core :as mount]
    ;
   [meteobot.config :as cfg]
   [meteobot.main :refer [-main]]
   ; 
   [mlib.telegram.botapi :refer [get-me]]
   ))


(comment

  (try
    (-main)
    (catch Exception ex
      ex
      )
    )

  
  (-> (cfg/make-config)
      (mount/with-args)
      (mount/only #{#'cfg/config 
                    ;; #'meteobot.app.serv/bot-info 
                    #'meteobot.app.serv/poller
                    })
      (mount/start)
      )
      
  (try
    ;(cfg/validate-config (cfg/env-config))
    (get-me (-> cfg/config :telegram-apikey))
    (catch Exception ex ex)
    )
  ;;=> {:can_connect_to_business false,
  ;;    :first_name "meteo38 test bot",
  ;;    :is_bot true,
  ;;    :username "meteo38_bot",
  ;;    :can_read_all_group_messages false,
  ;;    :supports_inline_queries true,
  ;;    :id 178313410,
  ;;    :can_join_groups false,
  ;;    :has_main_web_app false}
  
  (mount/stop)
  
  )


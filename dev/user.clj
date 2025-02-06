(ns user
  (:require
   [mount.core :as mount]
   [portal.api :as portal]
   ;
   [meteobot.config :as cfg]
   [mlib.telegram.botapi :refer [get-me]]
   [meteobot.app.command :as cmd]
   [meteobot.main]
   ))


(set! *warn-on-reflection* true)


(comment
  
  (def cf {:apikey (:telegram-apikey cfg/config)})

  (try
    (cmd/setup-menu-commands cf)
    (catch Exception ex ex))
  
  
  (-> (cfg/make-config)
      (mount/with-args)
      (mount/only #{#'cfg/config 
                    #'meteobot.app.serv/poller
                    #'meteobot.data.pg/dbc
                    #'meteobot.app.sender/sender-proc
                    })
      (mount/start)
      )

  (mount/stop)

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
  
  (def p (portal/open))
  (add-tap #'portal/submit)

  ())


(import '[java.security SecureRandom])

(def ^ThreadLocal thread-local-secure-random
  (ThreadLocal/withInitial #(SecureRandom.)))

(defn random-bytes [size]
  (let [buffer (byte-array size)
        srnd ^SecureRandom (.get thread-local-secure-random)]
    (.nextBytes srnd buffer)
    buffer))

(comment
  
  (random-bytes 4)
  ;;=> [-109, 84, 26, 107]

  ,)

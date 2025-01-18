(ns mlib.telegram.botapi
  (:require
   [clojure.string :as str]
   [org.httpkit.client :as http]
   [jsonista.core :as json]
   ,))


(def CONNECTION_TIMEOUT 8000)
(def LONG_POLLING_TIMEOUT 8000)

(def GET_UPDATES_TIMEOUT 5)  ;; 5 seconds - less than long polling timeout

(def TELEGRAM_BOT_API_BASE_URL "https://api.telegram.org/bot")
(def TELEGRAM_BOT_FILE_URL "https://api.telegram.org/file/bot")

(def RETRY_HTTP_COUNT 5)
(def RETRY_HTTP_SLEEP 1000)


(defn api-url [token method]
  (str TELEGRAM_BOT_API_BASE_URL token "/" (name method)))


(defn file-url [token path]
  (str TELEGRAM_BOT_FILE_URL token "/" path))


(defn hesc [text]
  (str/escape text {\& "&amp;" \< "&lt;" \> "&gt;" \" "&quot;"}))


(defn user-lang [user]
  (-> user :language_code keyword))


;; https://core.telegram.org/bots/faq#broadcasting-to-users
(def ^:const THROTTLE_MS 30)


(defn throttle [atom-ms* period-ms]
  (let [now (System/currentTimeMillis)
        [_ t1] (swap-vals! atom-ms* #(max (+ (or % 0) period-ms) now))
        sleep-ms (- t1 now)]
    (when (< 0 sleep-ms)
      (Thread/sleep sleep-ms))
    sleep-ms))


(comment

  (let [t (atom 0)
        p 1000
        now #(System/currentTimeMillis)]
    (prn (now) @t)
    (prn ">" (throttle t p))
    (prn (now) @t)
    (prn ">" (throttle t p))
    (prn (now) @t)
    (prn ">" (throttle t p))
    (prn (now) @t)
    (prn "sleep 900") (Thread/sleep 900)
    (prn ">" (throttle t p))
    (prn (now) @t)
    (prn ">" (throttle t p))
    (prn (now) @t)
    (prn "sleep 100") (Thread/sleep 100)
    (prn ">" (throttle t p))
    (prn (now) @t)
    (prn "sleep 1000") (Thread/sleep 1000)
    (prn ">" (throttle t p))
    (prn (now) @t))
  
  ())


(defn api-call[token method params 
               {:keys [timeout retry retry-sleep]
                :or {timeout CONNECTION_TIMEOUT
                     retry RETRY_HTTP_COUNT
                     retry-sleep RETRY_HTTP_SLEEP}}]
  (let [req {:url (api-url token method)
             :method :post
             :headers {"Content-Type" "application/json"}
             :body (json/write-value-as-bytes params)
             :connect-timeout timeout 
             :timeout timeout
             }]
    (loop [n retry]
      (let [{:keys [status body error]} @(http/request req)]
        (if (= 200 status)
          (try
            (-> body
                (json/read-value json/keyword-keys-object-mapper)
                (:result))
            (catch Exception ex
              (throw 
               (ex-info "api-call: malformed body" {:method method :body body} ex))))
          (if (and (< 0 n)
                   (or
                    (not status)
                    (-> status (str) (first) #{\3 \5})))
            (do
              (Thread/sleep retry-sleep)
              (recur (dec n)))
            (throw
             (ex-info "api-call: retry failed" {:status status :method method :body body} error))))))
    ))


(defn send-text [token chat-id text]
  (api-call token :sendMessage {:chat_id chat-id :text text} nil))


(defn send-md [token chat-id text]
  (api-call token :sendMessage {:chat_id chat-id :text text :parse_mode "Markdown"} nil))


(defn send-html [token chat-id text]
  (api-call token :sendMessage {:chat_id chat-id :text text :parse_mode "HTML"} nil))


(defn send-message [token chat-id params]
  (api-call token :sendMessage 
            (assoc params :chat_id chat-id) nil))


(defn file-path [token file-id]
  ;; {:file_id "..." :file_size 999 :file_path "dir/file.ext"}
  (:file_path
    (api-call token :getFile {:file_id file-id} nil)))


(defn get-me [token]
  (api-call token :getMe {} nil))


;; (defn get-file [token file-id & {timeout :timeout}]
;;   (if-let [path (file-path token file-id)]
;;     (try
;;       (:body
;;         (http/get (file-url token path)
;;           { :as :byte-array
;;             :socket-timeout (or timeout SOCKET_TIMEOUT)
;;             :conn-timeout   (or timeout SOCKET_TIMEOUT)}))
;;       (catch Exception e
;;         (warn "get-file:" file-id (.getMessage e))))
;;     ;
;;     (debug "get-file - not path for file_id:" file-id)))
;; ;

;; (defn send-file
;;   "params should be stringable (json/generate-string)
;;     or File/InputStream/byte-array"
;;   [token method mpart & [{timeout :timeout}]]
;;   (try
;;     (let [tout (or timeout SOCKET_TIMEOUT)
;;           res (:body
;;                 (http/post (api-url token method)
;;                   { :multipart
;;                       (for [[k v] mpart]
;;                         {:name (name k) :content v :encoding "utf-8"})
;;                     :as :json
;;                     :throw-exceptions false
;;                     :socket-timeout tout
;;                     :conn-timeout tout}))]
;;           ;
;;       (if (:ok res)
;;         (:result res)
;;         (warn "send-file:" method res)))
;;     (catch Exception e
;;       (warn "send-file:" method (.getMessage e)))))
;; ;


;; (defn set-webhook-cert [token url cert-file]
;;   (http/post (api-url token :setWebhook)
;;     {:multipart [ {:name "url" :content url}
;;                   {:name "certificate" :content cert-file}]}))
;; ;


(defn get-updates [token offset opts]
  (api-call token :getUpdates {:offset offset :timeout GET_UPDATES_TIMEOUT} opts))


(defn seq-updates [token opts]
  (letfn [(safe-inc [n] (if (pos-int? n) (inc n) 0))
          (get-next [[u & rest] last-id]
                    (lazy-seq
                     (if u 
                       (cons u (get-next rest (safe-inc (:update_id u))))
                       (get-next (get-updates token last-id opts) last-id))))]
    (get-next [] 0)))


(comment

  (require '[meteobot.config :refer [config]])
  (require '[mount.core :as mnt])
  
  (mnt/start #'config)
  (mnt/stop #'config)

  (def token (:telegram-apikey config))

  
  (doseq [u (take 10 (seq-updates token nil))]
         (prn (:update_id u) (-> u :message :text))
         )

  (get-updates token 0 {})

  ;; https://t.me/meteo38bot?start=webauth_123123

  ,)


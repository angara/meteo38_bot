(ns mlib.telegram.botapi
  (:require
   [clojure.string :as str]
   [org.httpkit.client :as http]
   [jsonista.core :as json]
   ,)
  (:import
   [java.io ByteArrayOutputStream]
   [java.net InetSocketAddress ProxySelector Socket URI]
   [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
    HttpResponse$BodyHandlers]
   [java.nio.charset StandardCharsets]
   [javax.net.ssl SSLSocketFactory]
   [java.time Duration]))


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


(def ^:dynamic *metric-hook* nil)


(defn telegram-http-proxy-uri [proxy-url]
  (let [uri (try
              (URI. proxy-url)
              (catch Exception ex
                (throw (ex-info "invalid TELEGRAM_HTTP_PROXY: expected http://host:port"
                                {:telegram-http-proxy proxy-url}
                                ex))))]
    (when-not (and (.isAbsolute uri)
                   (= "http" (.getScheme uri))
                   (seq (.getHost uri))
                   (pos? (.getPort uri)))
      (throw (ex-info "invalid TELEGRAM_HTTP_PROXY: expected http://host:port"
                      {:telegram-http-proxy proxy-url})))
    uri))


(defn valid-telegram-http-proxy? [proxy-url]
  (try
    (telegram-http-proxy-uri proxy-url)
    true
    (catch Exception _
      false)))


(defn- basic-auth-header [userinfo]
  (str "Basic "
       (.encodeToString
        (java.util.Base64/getEncoder)
        (.getBytes userinfo StandardCharsets/UTF_8))))


(defn- java-http-client [^URI proxy-uri timeout]
  (cond->
   (doto (HttpClient/newBuilder)
     (.connectTimeout (Duration/ofMillis timeout))
     (.proxy (ProxySelector/of
              (InetSocketAddress. (.getHost proxy-uri) (.getPort proxy-uri)))))
    true (.build)))


(defn- execute-direct-request [req]
  @(http/request req))


(defn- read-until-headers-end [input]
  (let [out (ByteArrayOutputStream.)]
    (loop [state 0]
      (let [b (.read input)]
        (when (= -1 b)
          (throw (ex-info "api-call: unexpected EOF while reading headers" {})))
        (.write out b)
        (let [state' (case [state b]
                       [0 13] 1
                       [1 10] 2
                       [2 13] 3
                       [3 10] 4
                       (if (= 13 b) 1 0))]
          (if (= 4 state')
            (.toString out "UTF-8")
            (recur state')))))))


(defn- parse-response-headers [headers-text]
  (let [[status-line & header-lines] (str/split-lines headers-text)]
    {:status (some-> status-line (str/split #"\s+" 3) (second) (parse-long))
     :headers (into {}
                    (keep (fn [line]
                            (when-let [[_ k v] (re-matches #"(?i)^([^:]+):\s*(.*)$" line)]
                              [(str/lower-case k) v])))
                    header-lines)}))


(defn- read-fixed-body [input length]
  (let [buf (byte-array length)]
    (loop [offset 0]
      (when (< offset length)
        (let [n (.read input buf offset (- length offset))]
          (when (= -1 n)
            (throw (ex-info "api-call: unexpected EOF while reading body" {})))
          (recur (+ offset n)))))
    buf))


(defn- read-line-bytes [input]
  (let [out (ByteArrayOutputStream.)]
    (loop [prev -1]
      (let [b (.read input)]
        (cond
          (= -1 b) (when (pos? (.size out))
                     (.toString out "UTF-8"))
          (and (= 13 prev) (= 10 b)) (let [bytes (.toByteArray out)]
                                       (String. bytes 0 (max 0 (- (alength bytes) 1)) StandardCharsets/UTF_8))
          :else (do
                  (.write out b)
                  (recur b)))))))


(defn- read-chunked-body [input]
  (let [out (ByteArrayOutputStream.)]
    (loop []
      (let [line (read-line-bytes input)
            size (Long/parseLong (first (str/split line #";" 2)) 16)]
        (if (zero? size)
          (do
            (read-line-bytes input)
            (.toByteArray out))
          (do
            (.write out (read-fixed-body input size))
            (read-line-bytes input)
            (recur)))))))


(defn- read-response [input]
  (let [{:keys [status headers]} (parse-response-headers (read-until-headers-end input))
        body-bytes (cond
                     (some-> (get headers "transfer-encoding") (str/lower-case) (= "chunked"))
                     (read-chunked-body input)

                     (get headers "content-length")
                     (read-fixed-body input (parse-long (get headers "content-length")))

                     :else
                     (.readAllBytes input))]
    {:status status
     :body (String. body-bytes StandardCharsets/UTF_8)}))


(defn- write-ascii [output text]
  (.write output (.getBytes text StandardCharsets/US_ASCII)))


(defn- execute-proxied-request-with-basic-auth
  [{:keys [url headers body timeout telegram-http-proxy]}]
  (let [proxy-uri (telegram-http-proxy-uri telegram-http-proxy)
        target-uri (URI. url)
        target-host (.getHost target-uri)
        target-port (if (pos? (.getPort target-uri)) (.getPort target-uri) 443)
        path (str (.getRawPath target-uri)
                  (when-let [query (.getRawQuery target-uri)]
                    (str "?" query)))]
    (with-open [proxy-socket (Socket.)]
      (.connect proxy-socket
                (InetSocketAddress. (.getHost proxy-uri) (.getPort proxy-uri))
                timeout)
      (.setSoTimeout proxy-socket timeout)
      (let [proxy-input (.getInputStream proxy-socket)
            proxy-output (.getOutputStream proxy-socket)]
        (write-ascii
         proxy-output
         (str "CONNECT " target-host ":" target-port " HTTP/1.1\r\n"
              "Host: " target-host ":" target-port "\r\n"
              "Proxy-Authorization: " (basic-auth-header (.getUserInfo proxy-uri)) "\r\n"
              "Proxy-Connection: keep-alive\r\n"
              "\r\n"))
        (.flush proxy-output)
        (let [{connect-status :status} (parse-response-headers (read-until-headers-end proxy-input))]
          (if (not= 200 connect-status)
            {:status connect-status}
            (let [ssl-socket (.createSocket (SSLSocketFactory/getDefault)
                                            proxy-socket
                                            target-host
                                            target-port
                                            true)]
              (with-open [ssl-socket ssl-socket]
                (.setSoTimeout ssl-socket timeout)
                (.startHandshake ssl-socket)
                (let [ssl-input (.getInputStream ssl-socket)
                      ssl-output (.getOutputStream ssl-socket)]
                  (write-ascii
                   ssl-output
                   (str "POST " path " HTTP/1.1\r\n"
                        "Host: " target-host "\r\n"
                        "Content-Type: " (get headers "Content-Type") "\r\n"
                        "Accept: application/json\r\n"
                        "Accept-Encoding: identity\r\n"
                        "Connection: close\r\n"
                        "Content-Length: " (alength ^bytes body) "\r\n"
                        "\r\n"))
                  (.write ssl-output ^bytes body)
                  (.flush ssl-output)
                  (read-response ssl-input))))))))))


(defn- execute-proxied-request [{:keys [url headers body timeout telegram-http-proxy]}]
  (let [proxy-uri (telegram-http-proxy-uri telegram-http-proxy)
        request (cond->
                 (doto (HttpRequest/newBuilder (URI. url))
                   (.timeout (Duration/ofMillis timeout))
                   (.POST (HttpRequest$BodyPublishers/ofByteArray body)))
                  true (.header "Content-Type" (get headers "Content-Type")))]
    (try
      (let [client (java-http-client proxy-uri timeout)
            response (try
                       (.send client
                              (.build request)
                              (HttpResponse$BodyHandlers/ofString))
                       (finally
                         (.close client)))]
        {:status (.statusCode response)
         :body (.body response)})
      (catch InterruptedException ex
        (.interrupt (Thread/currentThread))
        (throw ex))
      (catch Exception ex
        {:error ex}))))


(defn- execute-request [{:keys [telegram-http-proxy] :as req}]
  (if telegram-http-proxy
    (if (.getUserInfo (telegram-http-proxy-uri telegram-http-proxy))
      (try
        (execute-proxied-request-with-basic-auth req)
        (catch InterruptedException ex
          (.interrupt (Thread/currentThread))
          (throw ex))
        (catch Exception ex
          {:error ex}))
      (execute-proxied-request req))
    (execute-direct-request (dissoc req :telegram-http-proxy))))


(defn api-call[method params
               {:keys [apikey timeout retry retry-sleep telegram-http-proxy]
                :or {timeout CONNECTION_TIMEOUT
                     retry RETRY_HTTP_COUNT
                     retry-sleep RETRY_HTTP_SLEEP}}]
  (let [req {:url (api-url apikey method)
             :method :post
             :headers {"Content-Type" "application/json"}
             :body (json/write-value-as-bytes params)
             :connect-timeout timeout
             :timeout timeout
             :telegram-http-proxy telegram-http-proxy}]
    (loop [n retry]
      (let [{:keys [status body error]} (execute-request req)]
        (when *metric-hook*
          (*metric-hook* {:method (name method) :status status}))
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


(defn send-text [cfg chat-id text]
  (api-call :sendMessage {:chat_id chat-id :text text} cfg))


(defn send-md [cfg chat-id text]
  (api-call :sendMessage {:chat_id chat-id :text text :parse_mode "Markdown"} cfg))


(defn send-html [cfg chat-id text]
  (api-call :sendMessage {:chat_id chat-id :text text :parse_mode "HTML"} cfg))


(defn send-message [cfg chat-id params]
  (api-call :sendMessage 
            (assoc params :chat_id chat-id) cfg))


(defn send-location [cfg chat-id params] ;; latitude, longitude
  (api-call :sendLocation
            (assoc params :chat_id chat-id) cfg))


(defn answer-callback-text [cfg cbk-id text]
  (api-call :answerCallbackQuery {:callback_query_id cbk-id :text text} cfg))


(defn edit-message [cfg chat-id msg-id params]
  (api-call :editMessageText (assoc params :chat_id chat-id :message_id msg-id) cfg))


(defn edit-reply-markup [cfg chat-id msg-id kbd]
  (api-call :editMessageReplyMarkup {:chat_id chat-id :message_id msg-id :reply_markup kbd} cfg))


(defn delete-message [cfg chat-id msg-id]
  (api-call :deleteMessage {:chat_id chat-id :message_id msg-id} cfg))


(defn file-path [cfg file-id]
  ;; {:file_id "..." :file_size 999 :file_path "dir/file.ext"}
  (:file_path
    (api-call :getFile {:file_id file-id} cfg)))


(defn get-me [cfg]
  (api-call :getMe {} cfg))


;; https://core.telegram.org/bots/api#setmycommands
(defn set-my-commands [cfg commands scope language_code]
  (api-call :setMyCommands 
            (cond-> {:commands commands} 
              scope (assoc :scope scope)
              language_code (assoc :language_code language_code)) 
            cfg))


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


(defn get-updates [offset cfg]
  (api-call :getUpdates {:offset offset :timeout GET_UPDATES_TIMEOUT} cfg))


;; (defn seq-updates [token opts]
;;   (letfn [(safe-inc [n] (if (pos-int? n) (inc n) 0))
;;           (get-next [[u & rest] last-id]
;;                     (lazy-seq
;;                      (if u 
;;                        (cons u (get-next rest (safe-inc (:update_id u))))
;;                        (get-next (get-updates token last-id opts) last-id))))]
;;     (get-next [] 0)))


(comment

  (require '[meteobot.config :refer [config]])
  (require '[mount.core :as mnt])
  
  (mnt/start #'config)
  (mnt/stop #'config)

  (def token (:telegram-apikey config))

  
  ;; (doseq [u (take 10 (seq-updates token nil))]
  ;;        (prn (:update_id u) (-> u :message :text))
  ;;        )

  (get-updates 0 {:apikey token})

  ;; https://t.me/meteo38bot?start=webauth_123123

  ,)

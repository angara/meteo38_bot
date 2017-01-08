
# mlib-clj: 0.6.0


project.clj:

    :url https://github.com/maxp/mlib-clj

    :dependencies [
            [org.clojure/clojure "1.8.0"]
;            [org.clojure/tools.nrepl "0.2.10"]
;            [org.clojure/tools.logging "0.3.1"] ; https://github.com/clojure/tools.logging

            [clj-time "0.10.0"]
            ; [clj-http "1.1.2"]

            [javax.servlet/servlet-api "2.5"]

            [http-kit "2.1.19"]
            [ring/ring-core "1.3.2"]        
            [ring/ring-json "0.3.1"]        
            [ring/ring-headers "0.1.3"]
            [ring/ring-devel "1.3.2"]       ;; wrap-reload

            [cheshire "5.5.0"]
            [compojure "1.3.4"]
            [hiccup "1.0.5"]

            ; [com.novemberain/monger "2.1.0"]
            ; [postgresql "9.3-1102.jdbc41"]
            ; [korma "0.4.2"]     ; http://sqlkorma.com/docs

            ;; [com.draines/postal "1.11.3"]
            ;; [enlive "1.1.5"]     ;; https://github.com/cgrand/enlive
    ]

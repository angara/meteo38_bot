.EXPORT_ALL_VARIABLES:

APP_NAME   = mmeteo38_bot
VER_MAJOR  = 2
VER_MINOR  = 0
MAIN_CLASS = meteobot-main

JAR_NAME = meteo38_bot.jar
UBER_JAR = target/${JAR_NAME}

# # #
PROD_HOST = angara
PROD_PATH = /app/meteo38_bot
# # #

.PHONY: build clean dev deploy
SHELL = bash


dev:
	set -a && CONFIG_EDN=../conf/dev.edn && clojure -M:dev:nrepl


build: clean
	@clj -T:build uberjar


deploy:
	chmod g+r ${UBER_JAR}
	scp ${UBER_JAR} ${PROD_HOST}:${PROD_PATH}


clean:
	clj -T:build clean


# https://github.com/liquidz/antq/blob/main/CHANGELOG.adoc
outdated:
	@(clojure -Sdeps '{:deps {antq/antq {:mvn/version "2.11.1264"}}}' -T antq.core/-main || exit 0)

#.

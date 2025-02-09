.EXPORT_ALL_VARIABLES:

APP_NAME   = mmeteo38_bot
VER_MAJOR  = 2
VER_MINOR  = 0
MAIN_CLASS = meteobot.main

JAR_NAME = meteo38_bot.jar
UBER_JAR = target/${JAR_NAME}

# # #
PROD_HOST = angara
PROD_PATH = /app/meteo38_bot
# # #

.PHONY: build clean dev deploy
SHELL = bash


all: clean build deploy restart


dev:
	set -a && source .env && clojure -M:dev:nrepl


run:
	set -a && source .env && java -jar ${UBER_JAR}


build: clean
	@clj -T:build uberjar


deploy:
	chmod g+r ${UBER_JAR}
	scp run.sh ${UBER_JAR} ${PROD_HOST}:${PROD_PATH}


restart:
	ssh ${PROD_HOST} "ps ax | grep 'java -jar ${JAR_NAME}' | grep -v grep | awk '{print \$$1}' | xargs kill "


clean:
	clj -T:build clean


# https://github.com/liquidz/antq/blob/main/CHANGELOG.adoc
outdated:
	@(clojure -Sdeps '{:deps {antq/antq {:mvn/version "2.11.1264"}}}' -T antq.core/-main || exit 0)

#.

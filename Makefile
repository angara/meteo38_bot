.EXPORT_ALL_VARIABLES:
SHELL = bash

.PHONY: build clean dev deploy version

# # #

dev:
#	bash -c "set -a && source .env && clj -M:dev:nrepl"
	bash -c "CONFIG_EDN=../conf/meteo-bot-dev.edn clj -M:dev:nrepl"

version:
	@clj -T:build write-version

build:
	@mkdir -p ./target/resources
	@clj -T:build uberjar

deploy:
	scp target/meteo38_bot.jar angara:/app/meteo38_bot/

clean:
	@clj -T:build clean

#.

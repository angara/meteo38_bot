#!/bin/bash

export CONFIG_EDN="../conf/bots.edn"
exec java -jar meteo38_bot.jar | tee -a ../log/meteo38_bot.log

#.

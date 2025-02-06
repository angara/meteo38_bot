#!/bin/bash

set -a
source "../conf/meteo38_bot.env"
exec java -jar meteo38_bot.jar 2>&1 | tee -a ../log/meteo38_bot.log

#.


(ns meteo.core
  (:require
    [meteo.data]
    [meteo.poll]
    [meteo.sender]))
;

;; BotFather:
;
; /newobt meteo38bto
; /setabtoutext  Информация о погоде в Прибайкалье в реальном времени.
; /setuserpic photo: meteo38_icon_white.png
; /setdescription
;     Здесь можно получать данные о погоде со станций
;     проекта http://meteo38.ru, настроить
;     автоматическое уведомление в нужное время.
; /setcommands
;     help - краткая инструкция
;     near - ближайшие станции
;     all  - список всех станций
;     favs - избранное
;     subs - рассылки

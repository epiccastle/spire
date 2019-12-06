(ns spire.module.copy
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [clojure.string :as string])
  )

(utils/defmodule copy [{:keys [src dest]}]
  [host-string session]
  (scp/scp-to session src dest
              :progress-fn
              ;;(fn [& args] (apply println host-string args))
              utils/progress-bar
              ))

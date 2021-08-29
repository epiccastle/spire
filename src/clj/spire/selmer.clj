(ns spire.selmer
  (:require [spire.utils :as utils]
            [clojure.java.io :as io]
            [selmer.parser :as parser]))

(defn selmer [source vars & options]
  (let [flags (into #{} options)
        cwd (utils/current-file-parent)
        pre-markup (if (:data flags)
                     source
                     (slurp (io/input-stream (io/file cwd source))))]
    (parser/render pre-markup vars)))

(defn render-string
  "Render a string template to a string.
  Accepts the same arguments as `selmer.parser/render`"
  [s context-map & [opts]]
  (parser/render s context-map opts))

(defn render-file
  "Render a template in a file to a string.
  `file-path` is the relative path to the file to render.
  `context-map` and `opts` are the same as those expected by `selmer.parser/render`"
  [file-path context-map & [opts]]
  (let [cwd (utils/current-file-parent)
        pre-markup (slurp (io/input-stream (io/file cwd file-path)))]
    (parser/render pre-markup context-map opts)))

(comment
  (selmer "foo {{ bar }}" {:bar "qux"} :data)
  ;; paths are relative to spire repo in dev
  (selmer  "local/test-template" {:bar "qux"})

  (render-string "foo {{ bar }}" {:bar "qux"})
  (render-file "local/test-template" {:bar "qux"})

  (render-string "foo [{ bar }]" {:bar "baz"} {:tag-open \[ :tag-close \]}))

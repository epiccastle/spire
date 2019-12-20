(ns spire.module.download
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.local :as local]
            [spire.nio :as nio]
            [spire.remote :as remote]
            [spire.module.attrs :as attrs]
            [spire.compare :as compare]
            [digest :as digest]
            [puget.printer :as puget]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defn preflight [{:keys [src dest recurse preserve flat dir-mode mode] :as opts}]
  (cond
    (not src)
    (assoc failed-result
           :exit 3
           :err ":src must be provided")

    (not dest)
    (assoc failed-result
           :exit 3
           :err ":dest must be provided")

    (and preserve (or mode dir-mode))
    (assoc failed-result
           :exit 3
           :err "when providing :preverse you cannot also specify :mode or :dir-mode")

    (and src (utils/content-recursive? (io/file src)) (not recurse))
    (assoc failed-result
           :exit 3
           :err ":recurse must be true when :src specifies a directory.")))

(defn process-result [opts copy-result attr-result]
  (let [result {:result :failed
                :attr-result attr-result
                :copy-result copy-result}]
    (cond
      (= :failed (:result copy-result))
      result

      (= :failed (:result attr-result))
      result

      (or (= :changed (:result copy-result))
          (= :changed (:result attr-result)))
      (assoc result :result :changed)

      (and (= :ok (:result copy-result))
           (= :ok (:result attr-result)))
      (assoc result :result :ok)

      :else
      (dissoc result :result)
      )))

(defmacro scp-result [& body]
  `(try
     (let [res# (do ~@body)]
       (if res#
         {:result :changed}
         {:result :ok}))
     (catch Exception e#
       {:result :failed :exception e#})))

(utils/defmodule download [{:keys [src dest recurse preserve flat
                                   dir-mode mode owner group attrs] :as opts}]
  [host-string session]
  (or
   (preflight opts)
   (let [run (fn [command]
               (let [{:keys [out exit]}
                     (ssh/ssh-exec session command "" "UTF-8" {})]
                 (when (zero? exit)
                   (string/trim out))))

         ;; analyse local and remote paths
         local-file? (local/is-file? dest)
         remote-file? (remote/is-file? run src)

         {:keys [remote-to-local identical-content remote] :as comparison}
         (compare/compare-full-info

          ;; scp from a directory will create that directory on local
          (if remote-file?
            dest
            (io/file dest (.getName (io/file src))))

          run src)

         {:keys [sizes total]} (compare/remote-to-local comparison)

         max-filename-length (->> remote-to-local
                                  (map count)
                                  (apply max 0))

         all-files-total total

         copy-result
         (if recurse
           (cond
             (and local-file? (not force))
             {:result :failed :err "Cannot copy remote `src` over local `dest`: destination is a file. Use :force to delete destination file and replace."}

             (and local-file? force)
             (do
               (.delete (io/file dest))
               (scp-result
                (scp/scp-from session src dest
                              :progress-fn (fn [file bytes total frac context]
                                             (output/print-progress
                                              host-string
                                              (utils/progress-stats
                                               file bytes total frac
                                               all-files-total
                                               max-filename-length
                                               context)
                                              ))
                              :preserve preserve
                              :dir-mode (or dir-mode 0755)
                              :mode (or mode 0644)
                              :recurse true
                              :skip-files #{})))

             (not local-file?)
             (do
               (scp-result
                (when (not=
                       (count identical-content)
                       (count (filter #(= :f (:type (second %))) remote)))
                  (scp/scp-from session src dest
                                :progress-fn (fn [file bytes total frac context]
                                               #_ (println file bytes total frac context all-files-total)
                                               (output/print-progress
                                                host-string
                                                (utils/progress-stats
                                                 file bytes total frac
                                                 all-files-total
                                                 max-filename-length
                                                 context)))
                                :preserve preserve
                                :dir-mode (or dir-mode 0755)
                                :mode (or mode 0644)
                                :recurse true
                                :skip-files identical-content
                                ))))
             ))

         passed-attrs? (or owner group dir-mode mode attrs)

         attrs? (cond
                  ;; generally we assume that if a copy happened, all attributes
                  ;; and modes are correctly setup.
                  (and (= :ok (:result copy-result)) passed-attrs?)
                  (nio/set-attrs
                   session
                   {:path dest
                    :owner owner
                    :group group
                    :mode mode
                    :dir-mode dir-mode
                    :attrs attrs
                    :recurse recurse})

                  preserve
                  (nio/set-attrs-preserve
                   remote
                   (if remote-file?
                     dest
                     (io/file dest (.getName (io/file src))))))]
     (process-result
      opts
      copy-result
      {:result (if attrs? :changed :ok)}))))

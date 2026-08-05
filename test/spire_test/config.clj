(ns spire-test.config
  (:require [babashka.fs :as fs])
  (:import [java.nio.file Paths FileSystems]))

(def host-ports
  {
   ;; qemu hosts
   :windows {:port     22001
             :username "Administrator"}

   ;; openbsd doesnt boot, WARNING: / was not properly unmounted
   #_#_:openbsd 22002

   :netbsd  {:port 22003}
   :freebsd {:port 22004}
   :macos   {:port 22005}

   ;; docker hosts
   :alpine      {:port 22020}
   :alpine-fish {:port     22020
                 :username "fish"}
   :alpine-zsh  {:port     22020
                 :username "zsh"}
   :alpine-dash {:port     22020
                 :username "dash"}
   :ubuntu      {:port 22021}
   :ubuntu-fish {:port     22021
                 :username "fish"}
   :ubuntu-zsh  {:port     22021
                 :username "zsh"}
   :ubuntu-dash {:port     22021
                 :username "dash"}
   :ubuntu-ksh {:port     22021
                :username "ksh"}
   :debian      {:port 22022}
   :debian-fish {:port     22022
                 :username "fish"}
   :debian-zsh  {:port     22022
                 :username "zsh"}
   :debian-dash {:port     22022
                 :username "dash"}
   :debian-ksh {:port     22022
                :username "ksh"}
   :fedora      {:port 22023}
   :fedora-fish {:port     22023
                 :username "fish"}
   :fedora-zsh  {:port     22023
                 :username "zsh"}
   :fedora-dash {:port     22023
                 :username "dash"}
   :fedora-ksh {:port     22023
                :username "ksh"}
   :fedora-nu {:port     22023
                :username "nu"}
   :archlinux   {:port 22024}
   :archlinux-fish {:port     22024
                    :username "fish"}
   :archlinux-zsh  {:port     22024
                    :username "zsh"}
   :archlinux-dash {:port     22024
                    :username "dash"}
   :archlinux-ksh {:port     22024
                   :username "ksh"}
   :archlinux-nu {:port     22024
                   :username "nu"}
   :amazonlinux {:port 22025}
   :amazonlinux-zsh  {:port     22025
                      :username "zsh"}
   :amazonlinux-ksh {:port     22025
                     :username "ksh"}
   :rockylinux  {:port 22026}
   :rockylinux-zsh  {:port     22026
                     :username "zsh"}
   :rockylinux-ksh {:port     22026
                    :username "ksh"}
   :oraclelinux {:port 22027}
   :oraclelinux-zsh  {:port     22027
                     :username "zsh"}
   :oraclelinux-ksh {:port     22027
                     :username "ksh"}
   })

(def selected-hosts
  (set (keys host-ports)))

;; list any systems you are not running in test env
;; supports wildcards
;; eg #{:rockylinux* :oraclelinux*}
(def extra-exclude #{
                     ;; by default no macos, user is unlikely to
                     ;; have set it up
                     ;;:macos

                     ;; ;; exclude all the docker hosts
                     ;; :alpine-* :ubuntu* :debian*
                     ;; :fedora* :archlinux* :amazonlinux*
                     ;; :rockylinux* :oraclelinux*

                     ;; ;; exclude the qemu vms
                     ;; :windows :freebsd :macos :netbsd
                     })

(defn make-matcher
  "Build a PathMatcher once for a given glob pattern."
  [pattern]
  (.getPathMatcher (FileSystems/getDefault) (str "glob:" pattern)))

(defn glob-match?
  "Test whether a single string matches a glob pattern.
   No filesystem access - pure string/path parsing."
  [matcher s]
  (.matches matcher (Paths/get s (into-array String []))))

(defn glob-filter
  "Filter a collection of strings against a glob pattern."
  [pattern coll]
  (let [matcher (make-matcher (name pattern))]
    (filter #(glob-match? matcher (name %)) coll)))

(defn glob-hosts [pattern]
  (set (glob-filter pattern selected-hosts)))

(defn select-hosts [{:keys [only exclude]}]
  (cond->>
      (if only
        (reduce into
                (for [pattern only]
                  (glob-hosts pattern)))
        selected-hosts)

      (or exclude extra-exclude)
      (remove (fn [host]
                (->> exclude
                     (into (or extra-exclude #{}))
                     (some #(glob-match? (make-matcher (name %)) (name host))))))))

#_ (select-hosts {:only #{:ubuntu :ubuntu-*sh}
                  :exclude #{:*sh}})
#_ (select-hosts {:only #{:ubuntu*}
                  :exclude #{:*-fish}})
#_ (select-hosts {:only #{:*linux*}})
#_ (select-hosts {})

(defn filter-hashmap [selector hm]
  (select-hosts selector)
  (select-keys hm (select-hosts selector)))

#_ (filter-hashmap {:exclude #{:windows}}
                   {:alpine :foo
                    :ubuntu :bar
                    :windows :baz})
#_ (filter-hashmap {:exclude #{:windows}
                    :only #{:alpine}}
                   {:alpine :foo
                    :ubuntu :bar
                    :windows :baz})

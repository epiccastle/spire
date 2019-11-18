(ns spire.namespaces
  (:require [spire.system :as system]
            [spire.transfer :as transfer])
  )

(def bindings
  {'apt system/apt
   'hostname system/hostname
   ;;'ln system/ln
   ;;'mkdir system/mkdir

   ;;'git vcs/git

   ;;'copy transfer/copy
   ;;'template transfer/template


   'ssh (with-meta @#'transfer/ssh {:sci/macro true})
   })

(def namespaces
  {
   'spire.transfer {'ssh-line transfer/ssh-line
                    'ssh (with-meta @#'transfer/ssh {:sci/macro true})}
   })

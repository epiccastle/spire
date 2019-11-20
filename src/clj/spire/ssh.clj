(ns spire.ssh)

(defn open-auth-socket []
  (let [ssh-auth-sock (System/getenv "SSH_AUTH_SOCK")]
    ssh-auth-sock
    )
  )

#_ (open-auth-socket)

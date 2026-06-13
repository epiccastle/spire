(ns spire-test.docker
  (:require [babashka.process :as process]
            [clojure.string :as string])
  (:import [java.lang.ref WeakReference]
           [java.net Socket InetSocketAddress]
           [java.io IOException]))

(defn port-open?
  "Returns true if a TCP connection can be established to host:port."
  [host port timeout-ms]
  (try
    (let [sock (Socket.)]
      (.connect sock (InetSocketAddress. host port) timeout-ms)
      (.close sock)
      true)
    (catch IOException _ false)
    (catch Exception _ false)))

(defn wait-for-port
  "Blocks until host:port accepts a TCP connection, or the deadline is reached.

   Options (map, all optional):
     :timeout-ms   Total time to wait in ms        (default: 30000)
     :interval-ms  Time between attempts in ms      (default: 100)
     :connect-ms   Per-attempt connection timeout   (default: 1000)
     :on-retry     0-arity fn called on each miss   (default: nil)

   Returns :ok on success, :timeout on failure."
  ([host port] (wait-for-port host port {}))
  ([host port {:keys [timeout-ms interval-ms connect-ms on-retry]
               :or   {timeout-ms  30000
                      interval-ms 100
                      connect-ms  1000}}]
   (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
     (loop []
       (cond
         (port-open? host port connect-ms)
         :ok

         (>= (System/currentTimeMillis) deadline)
         :timeout

         :else
         (do
           (when on-retry (on-retry))
           (Thread/sleep interval-ms)
           (recur)))))))

(defn wait-for-port!
  "Like wait-for-port but throws ex-info on timeout instead of returning :timeout."
  ([host port] (wait-for-port! host port {}))
  ([host port opts]
   (let [result (wait-for-port host port opts)]
     (when (= result :timeout)
       (throw (ex-info (str "Timed out waiting for " host ":" port)
                       {:host host :port port :opts opts})))
     result)))

(defn run [command error-message]
  (let [{:keys [exit err out]}
        (process/sh command)]
    (assert (zero? exit) (str error-message ": out:" out " err:" err))
    out))

(defn run! [command]
  (process/sh command))

(defn build [{:keys [root-password]}]
  (run
    (format
     "docker build -t spire/test-base --build-arg root_password=%s test"
     root-password)
    "docker build failed"))

(defn cleanup []
  (run! "docker container stop spire-test")
  (run! "docker container rm spire-test")
  nil)

(defn start [{:keys [ssh-port]}]
  (let [result (-> "docker run --name spire-test -d -p %d:22 spire/test-base"
                  (format ssh-port)
                  (run "docker run failed")
                  string/trim)]
    (wait-for-port! "localhost" ssh-port)
    result))

(defn stop []
  (run! "docker container stop spire-test"))

(defn exec [command]
  (run
    (str "docker exec spire-test " command)
    "docker exec failed"))

(defn exec! [command]
  (run!
    (str "docker exec spire-test " command)))

(defn cp-to [local-src remote-dest]
  (run
    (format "docker cp \"%s\" \"spire-test:%s\"" local-src remote-dest)
    "docker cp failed"))

(defn cp-from [remote-src local-dest]
  (run
    (format "docker cp \"spire-test:%s\" \"%s\"" remote-src local-dest)
    "docker cp failed"))

(defn put-file [contents remote-dest]
  (process/sh
   ["docker" "exec" "spire-test" "ash" "-c"
    (format "echo '%s' > '%s'"
            contents
            remote-dest)]))

(defn put-dir
  "transfer a complete local directory to the docker container"
  [src-dir src-path dest-path]
  (process/sh "rm /tmp/spire-tarball.tgz")
  (process/sh (format "tar -cvz -C '%s' -f /tmp/spire-tarball.tgz '%s'" src-dir src-path))
  (exec "rm -f /tmp/spire-tarball.tgz")
  (cp-to "/tmp/spire-tarball.tgz" "/tmp/spire-tarball.tgz")
  (exec
   (format "tar -xv -f /tmp/spire-tarball.tgz -C '%s'"
           dest-path)))

(defn md5 [path]
  (-> (exec (format "md5sum '%s'" path))
      (string/split #" ")
      first))

(defn get-container-ip
  []
  (-> (process/sh
        "docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' spire-test")
      :out
      (string/trim)))

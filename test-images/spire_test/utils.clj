(ns spire-test.utils
  (:import [java.net Socket InetSocketAddress]
           [java.io IOException BufferedReader InputStreamReader])
  (:require [babashka.process :as proc]))

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


(defn ssh-banner?
  "Returns true if host:port responds with an SSH protocol banner
   (a line starting with \"SSH-\") within timeout-ms.
   Unlike port-open?, this verifies a real SSH server is listening,
   not just that something accepted the TCP connection (important for
   QEMU hostfwd, which accepts immediately regardless of guest state)."
  [host port timeout-ms]
  (try
    (let [sock (Socket.)]
      (try
        (.connect sock (InetSocketAddress. host port) timeout-ms)
        (.setSoTimeout sock timeout-ms)
        (let [rdr  (BufferedReader. (InputStreamReader. (.getInputStream sock)))
              line (.readLine rdr)]
          (boolean (and line (.startsWith line "SSH-"))))
        (finally
          (.close sock))))
    (catch IOException _ false)
    (catch Exception _ false)))


(defn wait-for-ssh
  "Blocks until host:port responds with a real SSH protocol banner,
   or the deadline is reached. Use this instead of wait-for-port for
   guests behind QEMU hostfwd, where the TCP port accepts connections
   before sshd is actually listening in the guest.
   Options (map, all optional):
     :timeout-ms   Total time to wait in ms        (default: 60000)
     :interval-ms  Time between attempts in ms      (default: 500)
     :connect-ms   Per-attempt connect+read timeout (default: 2000)
     :on-retry     0-arity fn called on each miss   (default: nil)
   Returns :ok on success, :timeout on failure."
  ([host port] (wait-for-ssh host port {}))
  ([host port {:keys [timeout-ms interval-ms connect-ms on-retry]
               :or   {timeout-ms  120000
                      interval-ms 500
                      connect-ms  2000}}]
   (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
     (loop []
       (cond
         (ssh-banner? host port connect-ms)
         :ok
         (>= (System/currentTimeMillis) deadline)
         :timeout
         :else
         (do
           (when on-retry (on-retry))
           (Thread/sleep interval-ms)
           (recur)))))))

(defn wait-for-ssh!
  "Like wait-for-ssh but throws ex-info on timeout instead of returning :timeout."
  ([host port] (wait-for-ssh! host port {}))
  ([host port opts]
   (let [result (wait-for-ssh host port opts)]
     (when (= result :timeout)
       (throw (ex-info (str "Timed out waiting for SSH on " host ":" port)
                       {:host host :port port :opts opts})))
     result)))


(defn wait-for-file-missing
  "Blocks until file at path no longer exists, or deadline is reached.

   Options (map, all optional):
     :timeout-ms   Total time to wait in ms        (default: 30000)
     :interval-ms  Time between checks in ms       (default: 500)

   Returns :ok on success, :timeout on failure."
  ([path] (wait-for-file-missing path {}))
  ([path {:keys [timeout-ms interval-ms]
          :or   {timeout-ms  300000 ;; openbsd on qemu takes AGES to shutdown
                 interval-ms 500}}]
   (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
     (loop []
       (cond
         (not (.exists (java.io.File. path)))
         :ok

         (>= (System/currentTimeMillis) deadline)
         :timeout

         :else
         (do
           (Thread/sleep interval-ms)
           (recur)))))))

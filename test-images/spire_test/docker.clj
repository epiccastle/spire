(ns spire-test.docker
  (:refer-clojure :exclude [run!])
  (:require [babashka.process :as process]
            [clojure.string :as string]
            [spire-test.utils :as utils]))

(defn run [command error-message]
  (let [{:keys [exit err out]}
        (process/sh command)]
    (assert (zero? exit) (str error-message ": out:" out " err:" err))
    out))

(defn run! [command]
  (process/sh command))

(defn tag-name [opts]
  (-> (:base-image opts)
      (string/replace #":" "_")))

(defn build [{:keys [root-password
                     base-image]
              :as opts}]
  (run
    (format
      "docker build -t spire/%s-base --build-arg root_password=%s --build-arg base_image=%s test-images/docker"
      (tag-name opts)
      root-password
      base-image)
    "docker build failed"))

(defn clean [opts]
  (run! (format "docker container stop spire-%s" (tag-name opts)))
  (run! (format "docker container rm spire-%s" (tag-name opts)))
  nil)

(defn start [{:keys [ssh-port vnc-port]
              :as opts}]
  (let [result (-> (format "docker run --name spire-%s -d -p %d:22 spire/%s-base"
                           (tag-name opts)
                           ssh-port
                           (tag-name opts))
                   (run "docker run failed")
                   string/trim)]
    (utils/wait-for-port! "localhost" ssh-port)
    result))

(defn stop [opts]
  (run! (format "docker container stop spire-%s" (tag-name opts))))

(defn exec [opts command]
  (run
    (str (format "docker exec spire-%s " (tag-name opts)) command)
    "docker exec failed"))

(defn exec! [opts command]
  (run!
    (str (format "docker exec spire-%s " (tag-name opts)) command)))

(defn cp-to [opts local-src remote-dest]
  (run
    (format "docker cp \"%s\" \"spire-%s:%s\"" local-src (tag-name opts) remote-dest)
    "docker cp failed"))

(defn cp-from [opts remote-src local-dest]
  (run
    (format "docker cp \"spire-%s:%s\" \"%s\"" (tag-name opts) remote-src local-dest)
    "docker cp failed"))

(defn put-file [opts contents remote-dest]
  (process/sh
    ["docker" "exec" (format "spire-" (tag-name opts)) "ash" "-c"
     (format "echo '%s' > '%s'"
             contents
             remote-dest)]))

(defn put-dir
  "transfer a complete local directory to the docker container"
  [opts src-dir src-path dest-path]
  (process/sh "rm /tmp/spire-tarball.tgz")
  (process/sh (format "tar -cvz -C '%s' -f /tmp/spire-tarball.tgz '%s'" src-dir src-path))
  (exec opts "rm -f /tmp/spire-tarball.tgz")
  (cp-to opts "/tmp/spire-tarball.tgz" "/tmp/spire-tarball.tgz")
  (exec
    opts
    (format "tar -xv -f /tmp/spire-tarball.tgz -C '%s'"
            dest-path)))

(defn md5 [opts path]
  (-> (exec opts (format "md5sum '%s'" path))
      (string/split #" ")
      first))

(defn get-container-ip
  [opts]
  (-> (format "docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' spire-%s" opts)
      process/sh
      :out
      string/trim))

(def ^:private docker-instances
  [{:root-password "root-access-please"
    :base-image "alpine:3.16.2"
    :ssh-port 22020}
   {:root-password "root-access-please"
    :base-image "ubuntu:24.04"
    :ssh-port 22021}
   {:root-password "root-access-please"
    :base-image "debian:stable"
    :ssh-port 22022}
   {:root-password "root-access-please"
    :base-image "fedora:44"
    :ssh-port 22023}
   {:root-password "root-access-please"
    :base-image "archlinux:latest"
    :ssh-port 22024}
   {:root-password "root-access-please"
    :base-image "amazonlinux:2023"
    :ssh-port 22025}
   {:root-password "root-access-please"
    :base-image "rockylinux:9"
    :ssh-port 22026}
   {:root-password "root-access-please"
    :base-image "oraclelinux:10"
    :ssh-port 22027}])

(defn start-all-docker
  "Starts all docker test instances.  SSH ports begin at 22020.
   Returns a vector of option maps for the running containers."
  []
  (doseq [opts docker-instances]
    (clean opts)
    (build opts))
  (doseq [opts docker-instances]
    (start opts))
  docker-instances)

#_ (start-all-docker)

(defn stop-all-docker
  "Stops all docker test instances.  Containers are stopped but not removed."
  []
  (doseq [opts docker-instances]
    (stop opts)))

#_ (stop-all-docker)

(defn clean-all-docker
  "Stops all docker test instances.  Then containers are removed."
  []
  (doseq [opts docker-instances]
    (clean opts)))

#_ (clean-all-docker)

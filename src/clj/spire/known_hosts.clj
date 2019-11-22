(ns spire.known-hosts
  (:require [clojure.string :as string]
            [edamame.core :as edamame]))

(defn process-hosts [hosts]
  (->> (string/split hosts #",")
       (into #{})))

(defn parse-line [line]
  (when-not (string/starts-with? line "#")
    (let [parts (string/split line #"\s+" -1)
          revoked? (= "@revoked" (first parts))
          cert-authority? (= "@cert-authority" (first parts))
          non-marked-parts (if (or revoked? cert-authority?)
                             (rest parts)
                             parts)
          zos-key-ring-label? (.contains line "zos-key-ring-label")
          non-marked-parts (if zos-key-ring-label?
                             [(first non-marked-parts) (string/join " " (rest non-marked-parts))]
                             non-marked-parts)
          num (count non-marked-parts)
          host (case num
                 5 (let [[hosts bits exponent modulus comment] non-marked-parts]
                     {:hosts hosts
                      :bits (Integer/parseInt bits)
                      :exponent (edamame/parse-string exponent)
                      :modulus (edamame/parse-string modulus)
                      :comment comment})
                 4 (let [[hosts type key comment] non-marked-parts]
                     {:hosts hosts
                      :type type
                      :key key
                      :comment comment})
                 3 (let [[hosts type key] non-marked-parts]
                     {:hosts hosts
                      :type type
                      :key key})
                 2 (let [[hosts key-ring] non-marked-parts
                         [label value] (string/split key-ring #"=")]
                     {:hosts hosts
                      (keyword label) (edamame/parse-string value)})
                 (throw (ex-info "malformed hosts line" {:line line})))
          annotated (if (or revoked? cert-authority?)
                      (assoc host
                             :marker (cond revoked? :revoked cert-authority? :cert-authority)
                             :revoked? revoked?
                             :cert-authority? cert-authority?)
                      host)]
      (update annotated :hosts process-hosts))))

(comment
  (parse-line "# this is a comment")
  (parse-line "closenet,...,192.0.2.53 1024 37 1593433453453453454593 closenet.example.net")
  (parse-line "cvs.example.net,192.0.2.10 ssh-rsa AAAA1234.....=")
  (parse-line "|1|JfKTdBh7.....= ssh-rsa AAAA1234.....=")
  (parse-line "@revoked cvs.example.net,192.0.2.10 ssh-rsa AAAA1234.....=")
  (parse-line "@cert-authority |1|JfKTdBh7.....= ssh-rsa AAAA1234.....=")
  (parse-line "mvs* zos-key-ring-label=\"KeyRingOwner/SSHKnownHostsRing mvs1-ssh-rsa\"")
  (parse-line "[anga.funkfeuer.at]:2022,[78.41.115.130]:2022 ssh-rsa AAAAB...fgTHaojQ==")
  (parse-line "sdsdfsdf"))

(defn read-known-hosts-file [filename]
  (with-open [reader (clojure.java.io/reader filename)]
    (->>
     (for [line (line-seq reader)]
       (parse-line line))
     (filter identity)
     (into []))))

#_ (read-known-hosts-file "/home/crispin/.ssh/known_hosts")

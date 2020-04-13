(ns spire.module.curl
  (:require [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:import [java.net URLEncoder URI]
           [java.io File SequenceInputStream ByteArrayInputStream]))

(set! *warn-on-reflection* true)

(defn- accept-header [opts]
  (when-let [accept (:accept opts)]
    ["-H" (str "Accept: " (case accept
                            :json "application/json"
                            accept))]))

(defn- url-encode
  "Returns an UTF-8 URL encoded version of the given string."
  [^String unencoded]
  (URLEncoder/encode unencoded "UTF-8"))

(defn command [{:keys [method headers form cookies url]
                :or {method :GET}
                :as opts}]
  (let [method-arg (case method
                     :head "-I"
                     (str "-X" (-> method name string/upper-case)))
        header-set (some->> headers
                            (map (fn [[k v]] (format "%s: %s" (name k) v))))
        form-set (some->> form
                          (map (fn [[k v]] (format "%s=%s" (name k) v))))
        cookies-set (if (string? cookies)
                      cookies
                      (some->> cookies
                               (map (fn [[k v]] (format "%s=%s" (name k) v)))
                               (string/join "; ")))
        url-val (cond
                  (string? url)
                  url

                  (map? url)
                  (str (URI. ^String (:scheme url)
                             ^String (:user url)
                             ^String (:host url)
                             ^Integer (:port url (case (:scheme url)
                                                   "https" 443
                                                   80))
                             ^String (:path url)
                             ^String (:query url)
                             ^String (:fragment url))))
        ]
    [method-arg header-set form-set cookies-set url-val]))

#_ (command {:method :head
             :headers {:X-First-name "Joe"
                       :X-Second-name "Bloggs"}
             :form {:name "Joe Bloggs"
                    :upload "@foo.dat"}
             :cookies {:NAME1 "VALUE1"
                       :NAME2 "VALUE2"}
             :cookie-jar "my-cookie-jar-file"
             :url {:host   "epiccastle.io"
                   :scheme "https"
                   :path   "/"}

             })





#_ (defn curl-command [opts]
  (let [body (:body opts)
        opts (if body
               (cond-> opts
                 (string? body) (assoc :data-raw body)
                 (file? body) (assoc :in-file body))
               opts)
        method (when-let [method (:method opts)]
                 (case method
                   :head ["--head"]
                   ["-X" (-> method name string/upper-case)]))
        headers (:headers opts)
        headers (loop [headers* (transient [])
                       kvs (seq headers)]
                  (if kvs
                    (let [[k v] (first kvs)]
                      (recur (reduce conj! headers* ["-H" (str k ": " v)]) (next kvs)))
                    (persistent! headers*)))
        accept-header (accept-header opts)
        form-params (:form-params opts)
        form-params (loop [params* (transient [])
                           kvs (seq form-params)]
                      (if kvs
                        (let [[k v] (first kvs)]
                          (recur (reduce conj! params* ["-F" (str k "=" v)]) (next kvs)))
                        (persistent! params*)))
        query-params (when-let [qp (:query-params opts)]
                       (loop [params* (transient [])
                              kvs (seq qp)]
                         (if kvs
                           (let [[k v] (first kvs)]
                             (recur (conj! params* (str (url-encode k) "=" (url-encode v))) (next kvs)))
                           (string/join "&" (persistent! params*)))))
        data-raw (:data-raw opts)
        data-raw (when data-raw
                   ["--data-raw" data-raw])
        url (let [url* (:url opts)]
              (cond
                (string? url*)
                url*

                (map? url*)
                (str (URI. ^String (:scheme url*)
                           ^String (:user url*)
                           ^String (:host url*)
                           ^Integer (:port url*)
                           ^String (:path url*)
                           ^String (:query url*)
                           ^String (:fragment url*)))))
        in-file (:in-file opts)
        in-file (when in-file ["-d" (str "@" (.getCanonicalPath ^File in-file))])
        basic-auth (:basic-auth opts)
        basic-auth (if (sequential? basic-auth)
                     (string/join ":" basic-auth)
                     basic-auth)
        basic-auth (when basic-auth
                     ["--user" basic-auth])
        ;;header-file (.getPath ^File (:header-file opts))
        stream? (identical? :stream (:as opts))]
    (conj (reduce into ["curl" "--silent" "--show-error" "--location" "--dump-header" ;;header-file
                        ]
                  [method headers accept-header data-raw in-file basic-auth
                   form-params
                   ;; tested with SSE server, e.g. https://github.com/enkot/SSE-Fake-Server
                   (when stream? ["-N"])
                   (:raw-args opts)])
          (str url
               (when query-params
                 (str "?" query-params))))))

;;;; End utils

;;;; Response Parsing

#_ (defn- read-headers [^File header-file]
  (line-seq (io/reader header-file)))

#_ (defn- curl-response->map
  "Parses a curl response input stream into a map"
  [opts]
  (let [is ^java.io.InputStream (:out opts)
        c (.read is)
        bais (when-not (= -1 c)
               (ByteArrayInputStream. (byte-array [c])))
        is (if bais (SequenceInputStream. bais is)
               is)
        body (if (identical? :stream (:as opts))
               is
               (slurp is))
        headers (read-headers (:header-file opts))
        [status headers]
        (reduce (fn [[status parsed-headers :as acc] header-line]
                    (if (string/starts-with? header-line "HTTP/")
                      [(Integer/parseInt (second (string/split header-line  #" "))) parsed-headers]
                      (let [[k v] (string/split header-line #":" 2)]
                        (if (and k v)
                          [status (assoc parsed-headers (string/lower-case k) (string/trim v))]
                          acc))))
                  [nil {}]
                  headers)
        response {:status status
                  :headers headers
                  :body body
                  :process (:proc opts)}]
    response))

;;;; End Response Parsing
#_
(string/join " " (curl-command {:url      {:host   "epiccastle.io"
                                           :scheme "http"
                                           :port   80
                                           :path   "/"}
                                :raw-args ["-L"]
                                :method :get}))


(comment
  ;; after running a python server in the source repo with `python3 -m http.server`
  (request {:url      {:host   "localhost"
                       :scheme "http"
                       :port   8000
                       :path   "/src/babashka"}
            :raw-args ["-L"]
            :method :get})

  (request {:url      {:host   "localhost"
                       :scheme "http"
                       :port   8000
                       :path   "/src/babashka"}
            :response true})

  )

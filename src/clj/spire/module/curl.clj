(ns spire.module.curl
  (:require [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:import [java.net URLEncoder URI]
           [java.io File SequenceInputStream ByteArrayInputStream]))

(set! *warn-on-reflection* true)

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defn preflight [{:keys [method headers accept dump-header form cookies cookie-jar url auth query-params
                         data-raw data-binary http2 output user-agent]
                  :or {method :GET}
                  :as opts}]
  (or
   (facts/check-bins-present [:curl])
   (cond
     (not url)
     (assoc failed-result :err ":url must be specified")

     (and form data-raw)
     (assoc failed-result :err "Cannot both POST (:data-raw) and multipart form POST (:form)")

     (and form data-binary)
     (assoc failed-result :err "Cannot both POST (:data-binary) and multipart form POST (:form)")

     (and data-raw data-binary)
     (assoc failed-result :err "Cannot both both :data-binary and :data-raw"))))

(defn- url-encode
  "Returns an UTF-8 URL encoded version of the given string."
  [^String unencoded]
  (URLEncoder/encode unencoded "UTF-8"))

(defn make-script [{:keys [method headers accept dump-header form cookies cookie-jar url auth query-params
                           data-raw data-binary http2 output user-agent]
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
        {:keys [user password method]
         :or {method :any}} auth
        auth-method-val (when auth
                          (case method
                            :any "--anyauth"
                            :basic "--basic"
                            :digest "--digest"
                            :ntlm "--ntlm"
                            :negotiate "--negotiate"))
        user-val (when (and user password) (str user ":" password))
        query-val (some->> query-params
                           (map (fn [[k v]] (format "%s=%s" (url-encode (name k)) (url-encode (name v)))))
                           (string/join "&"))
        ]
    (->> ["curl" "-s" "-S" method-arg
          (for [h header-set] ["-H" h])
          (when accept ["-H" (str "Accept: " (case accept
                                               :json "application/json"
                                               accept))])
          (when dump-header ["-D" dump-header])
          (for [f form-set] ["-F" f])
          (when cookies-set ["-b" cookies-set])
          (when cookie-jar ["-c" cookie-jar])
          auth-method-val
          (when user-val ["-u" user-val])
          (when data-raw ["--data-raw" data-raw])
          (when data-binary ["--data-binary" data-binary])
          (when http2 "--http2")
          (when output ["-o" output])
          (when user-agent ["-A" user-agent])
          "-L" (str url-val (when query-val
                              (str "?" query-val)))
          ]
         flatten
         (filter identity)
         (map utils/string-quote)
         (string/join " "))))

#_ (-> (command {:method :get
                 :headers {:X-First-name "Joe"
                           :X-Second-name "Bloggs"}
                 ;; :form {:name "Joe Bloggs"
                 ;;        :upload "@foo.dat"}
                 :cookies {:NAME1 "VALUE1"
                           :NAME2 "VALUE2"}
                 :cookie-jar "my-cookie-jar-file"
                 :url {:host   "epiccastle.io"
                       :scheme "https"
                       :path   "/"}
                 :auth {:user "user"
                        :password "pass"
                        :method :basic}
                 :query-params {:foo "bar"
                                "full name" "Joe Bloggs"
                                }
                 :data-raw "foo"
                 :data-binary "bar"
                 :http2 true
                 :output "filename.dat"
                 :user-agent "Chrome/1.1"
                 })
       println)

(defn process-result [{:keys [method headers accept dump-header form cookies cookie-jar url auth query-params
                               data-raw data-binary http2 output user-agent]
                        :or {method :GET}
                       :as opts}
                      {:keys [out err exit] :as result}]
  (cond
    (zero? exit)
    (assoc result :exit 0 :result :changed)

    :else
    (assoc result
           :result :failed)))
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

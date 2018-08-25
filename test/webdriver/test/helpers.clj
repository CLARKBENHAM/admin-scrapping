(ns webdriver.test.helpers
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.tools.reader.edn :as edn]
            [ring.adapter.jetty :refer [run-jetty]]
            [com.stuartsierra.component :as component]
            [webdriver.test.example-app :as web-app])
  (:import java.io.File
           [org.apache.log4j PropertyConfigurator]))

(log/info "Clojure version is" *clojure-version*)

(let [prop-file (io/file "test/log4j.properties")]
  (when (.exists prop-file)
    (PropertyConfigurator/configure (.getPath prop-file))))

(def ^:const test-port 5744)

(def ^:dynamic *base-url* (str "http://127.0.0.1:" test-port "/"))
;(def ^:dynamic *base-url* (str "http://localhost:3000/locations/464FvMG6t04s0cU05g9krVMw/hotels?brands[]=263&brands[]=272&brands[]=285&brands[]=516&checkIn=2018-08-16&checkOut=2018-08-17&guests=2&lat=35.72566235899431&lng=139.83065643139855&rooms=1&sort=distance-asc&zoom=9"))
(def heroku-url "http://vast-brushlands-4998.herokuapp.com/")

;; Utilities
(defmacro thrown?
  "Return truthy if the exception in `klass` is thrown, otherwise return falsey (nil) (code adapted from clojure.test)"
  [klass & forms]
  `(try ~@forms
        false
        (catch ~klass e#
          true)))

(defmacro immortal
  "Run `body` regardless of any error or exception. Useful for wait-until, where you expect the first N invocations to produce some kind of error, especially if you're waiting for an Element to appear."
  [& body]
  `(try
     ~@body
     (catch Throwable _#)))

(defn exclusive-between
  "Ensure a number is between a min and a max, both exclusive"
  [n min max]
  (and (> n min)
       (< n max)))

(defrecord WebServerComponent [port]
  component/Lifecycle
  (start [component]
    (let [start-server (fn [] (run-jetty #'web-app/routes {:port port, :join? false}))]
      (if-let [server (:server component)]
        (if (.isRunning server)
          component
          (assoc component :server (start-server)))
        (assoc component :server (start-server)))))

  (stop [component]
    (when-let [server (:server component)]
      (.stop server))
    (dissoc component :server)))

(defn test-system
  "Return a system map that the component library can use."
  ([] (test-system nil))
  ([configuration-map]
   (component/map->SystemMap
    (merge {:web (WebServerComponent. test-port)}
           configuration-map))))

;; For things like Saucelabs credentials
(defn test-config []
  (let [contents (try
                   (slurp (io/as-file "test/settings.edn"))
                   (catch Throwable _
                     (log/warn "No test/settings.edn file found.")
                     nil))]
    (if (seq contents)
      (edn/read-string contents)
      (log/warn "The test/settings.edn file has no contents."))))

(def system (test-system (test-config)))

(defn start-system! [f]
  (alter-var-root #'system component/start)
  (f))

(defn stop-system! [f]
  (f)
  (alter-var-root #'system component/stop))

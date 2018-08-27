;;this file is just a hodge podge of functions which may prove useful
(ns webdriver.scrape
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.tools.reader.edn :as edn]
            [ring.adapter.jetty :refer [run-jetty]]
            [com.stuartsierra.component :as component]
            [webdriver.test.example-app :as web-app]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [webdriver.core :refer :all]
            [webdriver.helper :refer :all]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [webdriver.test.helpers :refer :all]
            [webdriver.util :refer :all]
            [webdriver.form :refer :all]
            [webdriver.test.common :as c]
            [webdriver.chrome-test]
            [net.cgrand.enlive-html :as html]
            [clojure.java.io :as io] 
            [clojure.data.csv :as csv]:verbose)
  
  (load "helper")
   (:import java.io.File
           [org.apache.log4j PropertyConfigurator]
           org.openqa.selenium.remote.DesiredCapabilities
           org.openqa.selenium.chrome.ChromeDriver))

(defn location-page "starts scrapper towards the results page"[location-tid check-in? check-out?]
  (def scrapper (atom (new-webdriver {:browser :chrome})))
  (let  [*rmky-url* (if check-in
                      (str "https://www.roomkey.com/locations/" location-tid "/hotels?checkIn=" check-in "&checkOut=" check-out "&guests=2&rooms=1&sort=distance-asc")
                      (str "https://www.roomkey.com/locations/" location-tid "/hotels?"))]
  (to @scrapper *rmky-url*)
  (Thread/sleep 10000)
  (to @scrapper *rmky-url*)))

;;below for on the search results page
(defn count-results [scrapper] "takes scrapper and returns number of hotels in search results"
  (count
   (core/find-elements @scrapper {:tag :div, :class "_6cf13685"})))

(defn num-unavailible-hotels  "number of unavailible hotels" [scrapper]
  (count (find-elements @scrapper {:tag :div, :class "f0fd0ac8"})))

(defn  roomkey-rates  "roomkey only rates" [scrapper]
  (map
   #(Integer/parseInt (re-find #"[0-9]+" (text %)))
   (find-elements-by @scrapper {:tag :div, :class "_035e28b7 _35e316dc false"})));;

(defn others-rate "other-sites-rates" [scrapper]
  (map
   #(Integer/parseInt (re-find #"[0-9]+" (text %)))
   (find-elements-by @scrapper {:tag :div, :class "_035e28b7 "})))

(defn lowest-rates "gets lowest rates for hotel" [scrapper]
  (map
   #(Integer/parseInt (re-find #"[0-9]+" (text %)))
   (find-elements-by @scrapper (by-xpath "//div[contains(@class, '_035e28b7 ') and contains(@class, ' false')]"))))

(defn hotel-names "gets hotel names as keys" [scrapper]  (map #(keyword (text %)) (core/find-elements-by @scrapper {:tag :div, :class "_0d5a0043"})))

(defn rate-identifier "gets rate identifer" [scrapper]
  (map
   #(attribute % :data-rate-id)
   (find-elements-by @scrapper {:tag :a, :class "lead-link dd61811f"})))


(println (concat  (map #(Integer/parseInt (re-find #"[0-9]+" (text %))) (find-elements-by @scrapper (by-xpath "//div[contains(@class, '_035e28b7 ') and contains(@class, ' false')]"))) (map #(text %) (core/find-elements @scrapper {:tag :div, :class "f0fd0ac8"}))))


(println (zipmap (map #(keyword (text %)) (core/find-elements-by @scrapper {:tag :div, :class "_0d5a0043"})) (concat  (map #(Integer/parseInt (re-find #"[0-9]+" (text %))) (find-elements-by @scrapper (by-xpath "//div[contains(@class, '_035e28b7 ') and contains(@class, ' false')]"))) (map #(text %) (core/find-elements @scrapper {:tag :div, :class "f0fd0ac8"})))))


;;parses the page of a given hotel
(defn parse-hotel-page
  "Scrappes Rates, description and corresponding room information from roomkey.com"
  [hotel-tid check-in check-out & [time]]
  (let [crawler (atom (new-webdriver {:browser :chrome}))]
    (parsing-setup crawler hotel-tid check-in check-out)
    (let [rate-id (mapv  #(attribute % :data-rate-id) (find-elements-by @crawler {:tag :a, :class "lead-link _823bb73c" })) ;;what will actually be matched on 
          room-type (map #(first (str/split % #":")) rate-id)
          rate-plan (map #(second (str/split % #":")) rate-id)
          room-name (map text (find-elements-by @crawler {:tag :h4, :class "_93b07216"}))
          other-rates (map
                       #(re-find #"\d+" (text %))
                       (find-elements-by @crawler {:tag :p, :class "_5783264d"}))
          roomkey-rates (map
                         #(re-find #"\d+" (text %))
                         (find-elements-by @crawler {:tag :p, :class "bf25e4e9"}))
          room-description (map
                            #(str/replace (text %) #"\n" "   ")
                            (find-elements-by @crawler {:tag :p, :class "_407ee109"}))]

      (out
       (conj
        (map vector room-type rate-plan other-rates roomkey-rates room-name room-description)
        ["room-type" "rate-plan" "other-rates" "roomkey-rates" "room-name" "room-description"])
       (str "Hotel_" hotel-tid ".csv")))
    (Thread/sleep 15000)
    (quit @crawler)))

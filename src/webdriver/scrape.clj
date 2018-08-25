;;#!/bin/bash lein-exec

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
            ;[webdriver.util :refer :all]
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
;;(to-array-2d [(mapv #(keyword (c/text %)) (c/find-elements-by @scrapper {:tag :div, :class "_0d5a0043"})) (concat (mapv #(Integer/parseInt  (re-find #"[0-9]+" (c/text %))) (c/find-elements-by @scrapper {:tag :div, :class "_035e28b7 _35e316dc false"})) (mapv #(c/text %) (c/find-elements @scrapper {:tag :div, :class "f0fd0ac8"})))])



(def ^:dynamic *rmky-url* "http://localhost:3000/locations/464FvMG6t04s0cU05g9krVMw/hotels?brands[]=263&brands[]=272&brands[]=285&brands[]=516&checkIn=2018-08-16&checkOut=2018-08-17&guests=2&lat=35.72566235899431&lng=139.83065643139855&rooms=1&sort=distance-asc&zoom=9")

;;Hotel links are of form https://www.roomkey.com/hotels/HOTEL_TID?checkIn=YYYY-MM-DD&checkOut=YYYY-MM-DD&currency=AZA&guests=#&rooms=#


;;below only needs to be run twice (as long as caching is not disabled) 
;;(def scrapper (atom (new-webdriver {:browser :chrome})))

;; (to @scrapper *rmky-url*)
;; (Thread/sleep 10000)
;; (to @scrapper *rmky-url*);;not sure how to get repeat working, later

;; ;;below for on the search results page




;; (println (count (core/find-elements @scrapper {:tag :div, :class "_6cf13685"})));;number of hotels in search results

;; (println (count (fixnd-elements @scrapper {:tag :div, :class "f0fd0ac8"})));; number of unavailible hotels

;; (println "rmky rate: "(map #(Integer/parseInt (re-find #"[0-9]+" (text %))) (find-elements-by @scrapper {:tag :div, :class "_035e28b7 _35e316dc false"})));;roomkey only rates

;; (println "others rate: " (map #(Integer/parseInt (re-find #"[0-9]+" (text %))) (find-elements-by @scrapper {:tag :div, :class "_035e28b7 "})));; other sites rates

;; (println (map #(Integer/parseInt (re-find #"[0-9]+" (text %))) (find-elements-by @scrapper (by-xpath "//div[contains(@class, '_035e28b7 ') and contains(@class, ' false')]"))));;lowest rates for hotel

;; (println (map #(keyword (text %)) (core/find-elements-by @scrapper {:tag :div, :class "_0d5a0043"})));;hotel names as keys

;;  (map #(attribute % :data-rate-id) (find-elements-by @scrapper {:tag :a, :class "lead-link dd61811f"}));; gets the rate identifier


;; (println (concat  (map #(Integer/parseInt (re-find #"[0-9]+" (text %))) (find-elements-by @scrapper (by-xpath "//div[contains(@class, '_035e28b7 ') and contains(@class, ' false')]"))) (map #(text %) (core/find-elements @scrapper {:tag :div, :class "f0fd0ac8"}))))

;; (println (zipmap (map #(keyword (text %)) (core/find-elements-by @scrapper {:tag :div, :class "_0d5a0043"})) (concat  (map #(Integer/parseInt (re-find #"[0-9]+" (text %))) (find-elements-by @scrapper (by-xpath "//div[contains(@class, '_035e28b7 ') and contains(@class, ' false')]"))) (map #(text %) (core/find-elements @scrapper {:tag :div, :class "f0fd0ac8"})))))


#_(defn parse-hotel-page
  "Scrappes Rates and corresponding room information from roomkey.com"
    [hotel-tid check-in check-out & [time]]
  (let [crawler (atom (new-webdriver {:browser :chrome}))]
    
    (to @crawler (str "https://www.roomkey.com/hotels/" hotel-tid "?checkIn=" check-in "&checkOut=" check-out "&currency=USD&guests=2&rooms=1"))
    
    ;;checks if URL/hotel_tid is correct
    (when (exists? (find-element @crawler {:tag :pre}))
      (println "Webpage for hotel-tid " hotel-tid " " (text (find-element @crawler {:tag :pre})))
      (throw
       (Exception.
        (str  "check hotel_tid. The url was:\n"
              "https://www.roomkey.com/hotels/" hotel-tid "?checkIn=" check-in "&checkOut=" check-out "&currency=USD&guests=2&rooms=1")))) 

    ;;waits till page is finished loading to begin parsing
    (loop [n (re-find #"Loading Rooms & Rates"
                      (text (find-element @crawler {:tag :section, :class "a7cec703"})))]
      (when n
        (do
          (Thread/sleep 50)
          (recur (re-find #"Loading Rooms & Rates"
                          (text (find-element @crawler {:tag :section, :class "a7cec703"})))))))

    ;;checks if bookings are availible at this time
    (when
        (re-find #"unavailable"
                 (text (find-element @crawler {:tag :section, :class "a7cec703"})))
      (throw
       (Exception. "Booking Unavailible. Try refreshing page/changing date/increasing time")))

    ;;clicks to show all rates only if button exists
    (when
        (exists?
         (find-element @crawler {:tag :button, :class "_953e1efe"}))
      (click (find-element @crawler {:tag :button, :class "_953e1efe"}))) 

    (prn (map click (find-elements @crawler {:tag :button, :class "edfa26e3"})));;expands all room details
;;#######################################################
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


(defn hotel-tid-rates-right? "takes in a  hotel_tid and returns it if the other rate is lower than the roomkey rate"
  [hotel-tid & [check-in check-out]]
  (let [crawler (atom (new-webdriver {:browser :chrome}))]
    (parsing-setup crawler hotel-tid check-in check-out)
    (if
        (some false?
              (map #(<= 
                          (Integer/parseInt (second %)) ;;roomkey rates
                          (Integer/parseInt (first %))) ;;other rates
                    (remove empty? ;;only gets rates for rooms where roomkey is not too low to show
                            (map
                             #(rest
                               (re-find
                                #"Expect to pay\n\$(\d+)\n\$\d+ w/ taxes & fees\nRoom Key Rate\n(?:Save \$\d+/night\n\$|Same Low Rate\n\$)(\d+)"
                                (text %)))
                             (find-elements-by  @crawler {:tag :li, :class "_93ad074e"})))))
      (do (quit @crawler)
        hotel-tid)
      (quit @crawler))))


(defn collection-rates-right?  "takes in a collection of hotel_tids and returns those with: other rates lower than roomkey rates or no webpage with No page for  prepended. check-in/out can be collections that vary with hotel-tid. Will retry with a later date up unless check-in is a collection."
  [hotel-tids & [check-in check-out]]
  (let [error (fn [e hotel & [[in out]]]
                (case (get (ex-data e) :type)
                  "bad-tid"  (str "No page for " hotel)
                  "unavailible" (str "No Rooms for " hotel " from "
                                     (or in check-in "60 days out") " to "
                                     (or out check-out "61 days out"))))
        new-date-error (fn [e hotel & [[in out]]]
                         (case (get (ex-data e) :type)
                           "bad-tid"  (str "No page for " hotel)
                           "unavailible" (try
                                           (hotel-tid-rates-right? hotel in out)
                                           (catch Exception e
                                             (str (error e hotel in out) " nor " check-in " to " check-out)))))]
    (cond 
     (coll? check-in) (map
                       #(try
                          (hotel-tid-rates-right? %1 %2 %3)
                          (catch Exception e
                            (error e %1 [%2 %3])))
                       hotel-tids check-in check-out)
     (string? check-in) (map
                         #(try
                            (hotel-tid-rates-right? % check-in check-out)
                            (catch Exception e
                              (new-date-error e % (later-date check-in check-out) )))
                         hotel-tids)
     (nil? check-in) (map
                      #(try
                         (hotel-tid-rates-right? %)
                         (catch Exception e
                           (new-date-error e % (later-date 0 1))))
                      hotel-tids))))


(defn file-rates-right? "Either takes either a text or csv file of  hotel_tids and returns the ones which have other rates lower than roomkey rates. Check-in/out dates should be writen 0,1, or for each hotel"
  ([read-file & [write-file]]
   (let [file (slurp read-file)
         hotel-tids (re-seq #"[0-9a-zA-Z]{5,}" file);;want bad hotel-tids to be found and flagged
         dates (re-seq #"[0-9]{4}\-[0-9]{2}\-[0-9]{2}" file )
         check-in (take-nth 2 dates)
         check-out (take-nth 2 (rest dates))]
      (if write-file
        (case (last (str/split write-file #"\."))
          "csv" (out [(collection-rates-right? hotel-tids check-in check-out)] write-file)
          "txt" (spit write-file (do (collection-rates-right? hotel-tids check-in check-out)))
          (spit (str write-file ".txt") (do (collection-rates-right? hotel-tids check-in check-out))))
        (collection-rates-right? hotel-tids check-in check-out)))))


(defn roomkey-rates-less?  "checks whether roomkey rates are less than or equal to the other rates on the website"
  [hotel-tid check-in check-out]
  (let [crawler (atom (new-webdriver {:browser :chrome}))]

    (parsing-setup crawler hotel-tid check-in check-out)

    (prn (map click (find-elements @crawler {:tag :button, :class "edfa26e3"}))) ;;expands all room details
    
    (let [room-html (find-elements-by  @crawler {:tag :li, :class "_93ad074e"}) ;html for each room card
          rates-regex  #"Expect to pay\n\$(\d+)\n\$\d+ w/ taxes & fees\nRoom Key Rate\n(?:Save \$\d+/night\n\$|Same Low Rate\n\$)(\d+)" 
          both-rates (remove empty?
                             (map
                              #(rest (re-find rates-regex (text %)))
                              room-html)) ; only gets rates where a roomkey rate is not too low to show
          other-rates (map
                       #(Integer/parseInt  (first %))
                       both-rates)
          roomkey-rates (map
                         #(Integer/parseInt  (second %))
                         both-rates)
          roomkey-less (mapv <= roomkey-rates other-rates)
          room-rate-id (mapv
                        #(attribute % :data-rate-id)
                        (find-elements-by @crawler {:tag :a, :class "lead-link _823bb73c" }))
          room-type (map
                     #(first (str/split % #":"))
                     room-rate-id)
          rate-plan (map
                     #(second (str/split % #":"))
                     room-rate-id)
          room-name (map
                     text
                     (find-elements-by @crawler {:tag :h4, :class "_93b07216"}))
          room-description (map
                            #(str/replace
                              (str/replace (text %) #"\n" " ")
                              #"Close Details" "")
                            (find-elements-by @crawler {:tag :p, :class "_407ee109"}))
          rooms-per-section (mapv
                             #(dec (count (re-seq rates-regex (text %)))) ;;dec as you're not inserting, you're overwriting the spot to put in the Catagory name
                             (find-elements-by @crawler {:tag :li, :class "f6497a18"}))
          section-names (mapv
                         text
                         (find-elements-by @crawler {:tag :h3, :class "_9ba39f73"}))
          spaces-for-section-name (apply str
                                         (repeat
                                          (apply max
                                                 (map #(count %) section-names))
                                          " "))
          category-types (intertwin-at
                          (into [] (repeat (count roomkey-rates) spaces-for-section-name))
                          section-names
                          rooms-per-section)] 


      (println "cat-type" category-types)
      (println "rm-types" room-type)
      (println "rat-pl" rate-plan)
      (println "o-r" other-rates)
      (println "rm-r" roomkey-rates)
      (println "name" room-name)
      (println "description" room-description)

      
      (if (every? true? roomkey-less)
        (do
          (out
           (conj (map vector
                      category-types
                      room-type
                      rate-plan
                      other-rates
                      roomkey-rates
                      room-name
                      room-description)
                 ["category-types" "room-type" "rate-plan" "other-rates" "roomkey-rates" "room-name" "room-description"] 
                 (str "Hotel_" hotel-tid ".csv"))
           (println "Everything is normal." "Rooms are availible: " (not (empty? roomkey-rates)))))
        (do
          (out
           (conj
            (map vector
                 (map #(if %
                         "      "
                         "---->>")
                      roomkey-less)
                 category-types
                 room-type
                 rate-plan
                 other-rates
                 roomkey-rates
                 room-name
                 room-description) ;;prints rate ids where other rate is lower
            ["Bad Rooms" "category-types" "room-type" "rate-plan" "other-rates" "roomkey-rates" "room-name" "room-description"]    
            [" "]
            ["Other Rates Lower?" "true" "THIS IS BAD"])
           (str "Hotel_" hotel-tid ".csv"))
          (println "\n\nTHIS IS AN ERROR! OTHER RATES SHOULD NOT BE LESS THAN ROOMKEY RATES")))
    
      (Thread/sleep 1500)
      (quit @crawler)))) ;;always want to close selenium window just to be safe 



;;######################For fetching 
;; (defn flatten-maps [x]
;;    (into {}
;;    (map #(if (instance? clojure.lang.PersistentArrayMap  (get x (key %)))
;;      (val %)
;;      %) x))) ;;brings key bindings in map in map into first map; { a{b c}}->{a b c}

;; (defn flatten-rates [x] (merge (let [[rmkey other] (get x :nightly_rate)] {:roomkey-rate rmkey, :other-sites-rate other}) (dissoc x :nightly_rate)));;brings rates from array to separate keys

;; (defn flatten-arrays [tester] (reduce #(update %1 %2 (fn [x] (first x))) tester (into [] (remove nil? (map #(when (and (instance? clojure.lang.PersistentVector (get tester (key %))) (= 1 (count (val %)))) (key %)) tester)))));;flattens all values which are single length vectors

;; (def process-fetch (comp flatten-maps flatten-rates flatten-arrays))

;; (defn to-csv [x] (out (conj (map #(into [] (vals (process-fetch %))) (first (vals x))) (keys (process-fetch (first (first (vals x)))))) (str "Hotel_" (reduce #(str/replace-first %1  #"::" %2) (first (keys x)) ["_" ""]) ".csv")))

;; (defn get-csv [x] (if (string? x) (to-csv (fetch [x])) (to-csv (fetch [(str x)]))))#_"will very likely not work with only an integer"

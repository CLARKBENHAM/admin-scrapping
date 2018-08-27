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


(defn roomkey-rates-less?  "checks whether roomkey rates are less than or equal to the other rates on the website and returns all information for that hotel"
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


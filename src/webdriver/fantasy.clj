(ns webdriver.scrape
  (:require [clojure.tools.logging :as log]
            [clojure.tools.reader.edn :as edn]
            [ring.adapter.jetty :refer [run-jetty]]
            [com.stuartsierra.component :as component]
            [webdriver.test.example-app :as web-app]
            
            [webdriver.core :refer :all]
  
          
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [webdriver.test.helpers :refer :all]
            [webdriver.core :refer :all]
            [webdriver.util :refer :all]
            [webdriver.form :refer :all]

            [webdriver.test.common :as c] ;;should be the only line actually neede
            [webdriver.chrome-test]
            [net.cgrand.enlive-html :as html]
            [webdriver.core :as core]
            [clojure.java.io :as io] 
            ;;updated, uncomment when reload repl project.clj [clojure.data.csv :as csv]
)
  (:import java.io.File
           [org.apache.log4j PropertyConfigurator]
           org.openqa.selenium.remote.DesiredCapabilities
           org.openqa.selenium.chrome.ChromeDriver))



(def scrapper (atom (new-webdriver {:browser :chrome})))

(to @scrapper "http://games.espn.com/ffl/freeagency?teamId=3&leagueId=2063112&seasonId=2018")

(click (find-element @scrapper {:tag :a, :class "flexpop fpop_open_ppc"}))

(Thread/sleep 10000)


(def open-info (find-elements @scrapper {:tag :a, :content "tabs#ppc", :class "flexpop", :fpopheight "357px"}));;expands every window

(click (find-element @scrapper {:tag :img, :class "fpop_closebtn"}));;close the open window

(def get-points  (remove nil? (mapv #(re-find #"[0-9]+" (text %)) (find-elements @scrapper {:tag :td, :align "right"}))))

(defn one-player [open-info]
  (click open-info)
(println (cons (text (find-element @scrapper {:tag :div, :class "player-name"})) (remove nil? (mapv #(re-find #"[0-9]+" (text %)) (find-elements @scrapper {:tag :td, :align "right"})))) )
  (click (find-element @scrapper {:tag :img, :class "fpop_closebtn"})))

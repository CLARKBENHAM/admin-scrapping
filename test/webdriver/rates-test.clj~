(ns rates-test
  (:require [webdriver.scrape :refer :all]
            [midje.sweet :refer :all]
            [webdriver.core :as core]
            [webdriver.helper :refer :all]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
)
  (:import org.openqa.selenium.remote.DesiredCapabilities
           org.openqa.selenium.chrome.ChromeDriver))


(fact "Can pass in a URL and check if other rates are lower than roomkey rates"
  (collection-rates-right? ["file:///Users/clarkbenham/startContent/admin-tool/clj-webdriver/resources/normal1.html" "file:///Users/clarkbenham/startContent/admin-tool/clj-webdriver/resources/normal2.html"]) => '(nil "file:///Users/clarkbenham/startContent/admin-tool/clj-webdriver/resources/normal2.html"))

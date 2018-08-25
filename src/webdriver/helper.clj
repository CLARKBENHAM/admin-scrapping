(ns webdriver.helper
  (:require [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [webdriver.core :refer :all]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv])
  (:import java.io.File
           [org.apache.log4j PropertyConfigurator]
           org.openqa.selenium.remote.DesiredCapabilities
           org.openqa.selenium.chrome.ChromeDriver))





(def date-format (f/formatter "yyyy-MM-dd"))

(defn min-vecs [q & rest]
  (apply min (map #(count %) (conj rest q)))) ;;minium length of vectors passed to function


(defn rotate [& vals]
  (apply map vector vals))


(defn out [out-data & [file-name]]
  (let [file-name (or file-name "out-file.csv")]
    (with-open [writer (io/writer file-name)]
      (csv/write-csv writer out-data))))


(defn remove-element [other element]
  (let [ n (count (take-while #(not= element %) other))]
    (vec (concat (take n other) (drop (inc n) other)))))
 

(defn iterative-sum [x]
  (when (<= (count x) 1)
    x)
  (loop [x x
         sums nil]
    (if (empty? x)
      sums
      (recur (drop-last x) (conj sums (apply + x))))))


(defn intertwin-at "Takes a given series and returns vector with the values inserted based on the number of elements which follow it as give by count-since-inserted."
        [series insert-vals  count-since-inserted]
        (when empty? count-since-inserted
              (concat insert-vals series))
        (let [index (iterative-sum count-since-inserted)]
          (flatten
           (interleave
            insert-vals
            (map
             #(subvec series %1 %2)
             (cons 0 (drop-last index))
             index)))))


(defn n-days-from-now "creates roomkey formated dates n days into future" ;use @~ to unsplice forms
   [n]
   (->> n
        t/days
        t/from-now
        (f/unparse date-format)))


(defn later-date  "returns date (in roomkey's YYYY-MM-dd format) 60 days the into future from given date or 60 + the number of dates given from current date"
  [check-in check-out]
  (try
    (map #(cond 
           (string? check-in) (as-> % x
                                    (f/parse date-format x)
                                    (t/plus x (t/days 60))
                                    (f/unparse date-format x))
           (integer? check-in) (n-days-from-now (+ % 60)))                           
         [check-in check-out])
    (catch Exception e (map n-days-from-now [60 61]))));;in instance of badly formated date


(defn booking-date  "checks if inputed date is before current date and if so sets to 2 weeks in to future"
  ([hotel-tid] nil) ;;if date not manually inputed will be correct
  ([hotel-tid check-in]
    (when
        (and check-in (t/before? (f/parse check-in) (t/today-at 12 00)))
      (println "Warning!: the dates being used for " hotel-tid " are actually: "
               (map n-days-from-now [14 15])))))


(defn to-hotel-tid "Sends webdriver to the URL of hotel-tid, with option parameters for date, or direcrlt to inputed URL"
  ([crawler hotel-tid-or-url check-in check-out]
  ;;  (to @crawler (str "https://www.roomkey.com/hotels/" hotel-tid "?checkIn=" check-in "&checkOut=" check-out "&currency=USD&guests=2&rooms=1")))
  ;; ([crawler hotel-tid]
   (cond
     (re-find #"\/" hotel-tid-or-url) (to @crawler hotel-tid-or-url)
     check-in (to @crawler
                  (str "https://www.roomkey.com/hotels/"
                       hotel-tid-or-url
                       "?checkIn=" check-in
                       "&checkOut=" check-out
                       "&currency=USD&guests=2&rooms=1"))
     :else (to @crawler (str "https://www.roomkey.com/hotels/" hotel-tid-or-url)))))


(defn valid-hotel-tid? "checks if crawler was directed to invalid page"
  [crawler hotel-tid & [check-in check-out]]
  ;;(println (exists? (find-element @crawler {:tag :pre})) "\n\n\n")
  (when (exists? (find-element @crawler {:tag :pre}))
    (println "Webpage for Hotel_tid " hotel-tid " " "Not Found")
    (throw
     (ex-info
      (str  "check hotel_tid. The url was:\n"
            "https://www.roomkey.com/hotels/" hotel-tid "?checkIn=" check-in "&checkOut=" check-out "&currency=USD&guests=2&rooms=1")
      {:type "bad-tid"}))))


(defn finish-loading "waits till page is loaded" [crawler]
  (loop [n (re-find #"Loading Rooms & Rates"
                    (text (find-element @crawler {:tag :section, :class "a7cec703"})))]
    (when n
      (do
        (Thread/sleep 50)
        (recur (re-find #"Loading Rooms & Rates"
                        (text (find-element @crawler {:tag :section, :class "a7cec703"}))))))))


(defn booking-unavailible? "Checks if rooms are availible for selected date. If date unavailable and not manually inputed, recurs on future date"
  [crawler hotel-tid & [check-in check-out]]
  (when (re-find #"unavailable"
                 (text (find-element @crawler {:tag :section, :class "a7cec703"})))
    (if (nil? check-in)
      (do
        (println hotel-tid " unavailible on " (map n-days-from-now [14 15]) ". Retrying.")
        true
        #_(recur crawler hotel-tid (n-days-from-now 60) (n-days-from-now 61)))
      (throw
       (ex-info (str "Booking Unavailible on: " check-in " to " check-out ". Try refreshing page/changing date/increasing time") {:type "unavailible"})))))


(defn show-all "clicks View all rates button if it exists"
  [crawler]
  (when
      (exists?
       (find-element @crawler {:tag :button, :class "_953e1efe"}))
    (click (find-element @crawler {:tag :button, :class "_953e1efe"}))))


(defn parsing-setup "checks hotel-tid correct, dates right, booking-availible, and clicks show more rooms. Quits on error" [crawler hotel-tid & [check-in check-out]]

    (to-hotel-tid crawler hotel-tid check-in check-out)

  (try  
    (valid-hotel-tid? crawler hotel-tid check-in check-out)

    (booking-date hotel-tid check-in) ;;to alert the user if incorrect dates were inputed. Not useful?    
    ;;waits till page is finished loading to begin parsing
    (finish-loading crawler)
    
    ;;checks if bookings are availible at this time
    (when
        (booking-unavailible? crawler hotel-tid check-in check-out)
      (parsing-setup crawler hotel-tid (n-days-from-now 60) (n-days-from-now 61)));;if booking unavailible, tries more likely date. 
    
    ;;clicks to show all rates only if button exists
    (show-all crawler)
  
    ;;quits on exception to prevent excess windows
    (catch Exception e (quit @crawler) (throw e))))

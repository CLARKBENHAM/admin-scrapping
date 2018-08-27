;;this file defines the functions nessisary to convert fetch to csv, but would have to be called in the repl of the same partner brand. I was unable to automat this. 
;;Steps to get CSV for rates which were called is as follows:
;;start lein repl; set emacs namespace to *partner*/test/roomkey/integration/partner/*brand*.clj with C-c M-n; load forms with C-c C-k;  load forms of revieved_info.clj with C-c C-k; then call get-csv on rates identifer which should output CSV.

(defn flatten-maps "brings key bindings in map in map into first map; { a{b c}}->{a b c}" [x] 
  (into {}
        (map #(if (instance? clojure.lang.PersistentArrayMap  (get x (key %)))
                (val %)
                %) x)))

(defn flatten-rates "brings rates from array to separate keys withen each map"[x]
  (merge
   (let [[rmkey other] (get x :nightly_rate)]
     {:roomkey-rate rmkey, :other-sites-rate other})
   (dissoc x :nightly_rate)))

(defn flatten-arrays "flattens all values which are single length vectors"[tester]
  (reduce
   #(update %1 %2 (fn [x] (first x)))
   tester
   (into []
         (remove nil?
                 (map #(when (and
                              (instance? clojure.lang.PersistentVector (get tester (key %)))
                              (= 1 (count (val %)))) (key %))
                      tester)))))

(def process-fetch (comp flatten-maps flatten-rates flatten-arrays));;combines processing functions

(defn to-csv "Convets output of fetch command to csv"[x]
  (out
   (conj
    (map
     #(into [] (vals (process-fetch %)))
     (first (vals x)))
    (keys (process-fetch (first (first (vals x))))))
   (str "Hotel_" (reduce #(str/replace-first %1  #"::" %2) (first (keys x)) ["_" ""]) ".csv")))


(defn get-csv "Calls fetch and returns all information as csv"[x] 
  (if (string? x)
    (to-csv (fetch [x]))
    (to-csv (fetch [(str x)]))))#_"will very likely not work with only an integer"

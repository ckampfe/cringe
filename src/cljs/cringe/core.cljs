(ns cringe.core
  (:require [cljs.nodejs :as nodejs]
            [cljs.core.async :refer [go chan >! alts!]]
            [cljs.tools.cli :refer [parse-opts]]
            [cljs.spec.alpha :as s]
            [xml])
  (:require-macros [cringe.macros :refer [or-wildcard string-range-set]]))

(nodejs/enable-util-print!)

(s/def ::minute (or-wildcard (string-range-set 0 60)))
(s/def ::hour (or-wildcard (string-range-set 0 24)))
(s/def ::day-of-month (or-wildcard (string-range-set 1 32)))
(s/def ::month-of-year (or-wildcard (string-range-set 1 13)))
(s/def ::day-of-week (or-wildcard (string-range-set 0 7)))

(s/def ::cron (s/cat :minute ::minute
                     :hour ::hour
                     :day-of-month ::day-of-month
                     :month-of-year ::month-of-year
                     :day-of-week ::day-of-week))

;; TODO: add these:
;; <key>StandardOutPath</key>
;; <string>/Users/xcxk066/code/yahoo.log</string>
;; 
;; <key>StandardErrorPath</key>
;; <string>/Users/xcxk066/code/yahoo.log</string>

(defn template [calendar-interval-vector program-args job-name]
  [{:plist [{:_attr {:version "1.0"}}
            {:dict [{:key "Label"}
                    {:string job-name}

                    {:key "ProgramArguments"} {:array [{:string program-args}]}
                    {:key "StartCalendarInterval"}
                    {:dict calendar-interval-vector}]}]}])

(defn render-xml [calendar-interval-vector program-args job-name]
  (xml (clj->js (template calendar-interval-vector
                          program-args
                          job-name))
       (clj->js {:indent "  "
                 :declaration true})))

(defn extract-if-numeric [parsed input-field-name output-field-name]
  (when (= :number (first (get parsed input-field-name [])))
    [{:key output-field-name}
     {:integer (js/parseInt (second (input-field-name parsed)) 10)}]))

(defn parsed-cron-to-template-format [parsed]
  (mapcat (fn [[i o]]
            (extract-if-numeric parsed i o))
          [[:minute "Minute"]
           [:hour "Hour"]
           [:day-of-month "Day"]
           [:month-of-year "Month"]
           [:day-of-week "Weekday"]]))

(def cli-options
  [["-h" "--help"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]

    (cond
      (= 0 (count args)) (do
                           (println summary)
                           (.exit nodejs/process 0))
      (:help options) (do
                        (println summary)
                        (.exit nodejs/process 0))
      errors (do
               (println "errors")
               (println errors)
               (.exit nodejs/process 1)))

    (let [cron-str (first arguments)
          cron-vector (clojure.string/split cron-str #" ")
          program-name (second arguments)
          job-name (nth arguments 2)]

      (when-not (s/valid? ::cron cron-vector)
        (do
          (println (s/explain-str ::cron cron-vector))
          (.exit nodejs/process 1)))

      (-> (s/conform ::cron cron-vector)
          parsed-cron-to-template-format
          (render-xml program-name job-name)
          print))))

(set! *main-cli-fn* -main)

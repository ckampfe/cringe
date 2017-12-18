(ns cringe.macros
  (:require [cljs.spec.alpha :as s]))

(defmacro or-wildcard [form]
  `(s/or :wildcard #(= "*" %)
         :number ~form))

(defmacro string-range-set [lower upper]
  (set (map str (range lower upper))))


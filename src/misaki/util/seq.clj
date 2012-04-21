(ns misaki.util.seq
  "misaki: sequence utility"
  (:use [clj-time.core :only [after?]]))

; =sort-by-date
(defn sort-by-date [posts]
  (sort #(after? (:date %) (:date %2)) posts))

; =sort-alphabetically
(defn sort-alphabetically
  "Sort list alphabetically."
  ([ls]   (sort-alphabetically identity ls))
  ([f ls] (sort #(neg? (.compareTo (f %) (f %2))) ls)))
(ns uonto.misc
  (:require
   [uonto.raw :as raw]))

(defmacro experiment
  "Experiment with ontology model and clean result on the exit."
  [& body]
  `(let [object-tmp#     @raw/*objects
         object->id-tmp# @raw/*object->id]
     (try ~@body
          (finally (reset! raw/*objects    object-tmp#)
                   (reset! raw/*object->id object->id-tmp#)))))

(ns app.views.helpers-test
  (:require [app.views.helpers :as helpers]
            [clojure.test :refer [are deftest is]]))

(deftest youtube-id-test
  (are [url] (= "dQw4w9WgXcQ" (helpers/youtube-id url))
    "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    "https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ"
    "https://youtu.be/dQw4w9WgXcQ?t=42"
    "https://www.youtube.com/live/dQw4w9WgXcQ")
  (is (nil? (helpers/youtube-id "https://vimeo.com/12345"))))

(deftest day-test
  (is (= "3 November 2026" (helpers/day "2026-11-03"))))

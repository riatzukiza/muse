(ns eta-mu.shape.review-context-delivery
  "Pure pinned OpenCode read rendering over boundary-decoded staged lines.
   No event admission, I/O, or model assessment. GPL-3.0-or-later."
  (:require [clojure.string :as str]))

(defn host-lines
  "Effect4 splitLines semantics: LF, CRLF or CR; no phantom final line."
  [text]
  (if (empty? text) []
    (let [lines (str/split text #"\r\n|\r|\n" -1)]
      (if (re-find #"[\r\n]$" text) (pop (vec lines)) (vec lines)))))

(defn read-rendering
  "Expected literal file output and metadata for a valid input range. The
   50KiB cap counts UTF8 content bytes/newlines, not numbered framing. Clipped
   individual lines remain explicitly unestablished, even with an EOF footer."
  [path lines line-bytes offset limit]
  (let [total (count lines)
        selection (subvec lines (min total (dec offset)) (min total (+ (dec offset) limit)))
        sizes (subvec line-bytes (min total (dec offset)) (min total (+ (dec offset) limit)))
        window (loop [remaining selection sizes sizes raw [] used 0]
                 (if-let [line (first remaining)]
                   (let [size (+ (first sizes) (if (seq raw) 1 0))]
                     (cond
                       (> (count line) 2000) {:clipped? true}
                       (> (+ used size) 51200) {:raw raw :capped? true}
                       :else (recur (next remaining) (next sizes) (conj raw line) (+ used size))))
                   {:raw raw :capped? false}))]
    (if (:clipped? window) window
      (let [raw (:raw window) end (+ offset (count raw) -1)
            capped? (:capped? window) more? (or capped? (< end total))
            footer (cond capped? (str "(Output capped at 50 KB. Showing lines " offset "-" end
                                      ". Use offset=" (inc end) " to continue.)")
                         more? (str "(Showing lines " offset "-" end " of " total
                                    ". Use offset=" (inc end) " to continue.)")
                         :else (str "(End of file - total " total " lines)"))]
        {:output (str "<path>" path "</path>\n<type>file</type>\n<content>\n"
                      (str/join "\n" (map-indexed #(str (+ offset %1) ": " %2) raw))
                      "\n\n" footer "\n</content>")
         :display {:type "file" :path path :text (str/join "\n" raw)
                   :lineStart offset :lineEnd end
                   :totalLines (if capped? (inc end) total) :truncated more?}
         :preview (str/join "\n" (take 20 raw)) :truncated more?
         :line-ids (vec (range offset (inc end))) :eof? (not more?)}))))

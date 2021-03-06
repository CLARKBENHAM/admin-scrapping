(ns webdriver.form
  "Utilities for filling out HTML forms."
  (:use [webdriver.core :only [input-text find-elements]])
  (:import org.openqa.selenium.WebDriver))

(defn- quick-fill*
  ([wd k v] (quick-fill* wd k v false))
  ([wd k v submit?]
   ;; shortcuts:
   ;; k as string => element's id attribute
   ;; v as string => text to input
   (let [query-map (if (string? k)
                     {:id k}
                     k)
         action (if (string? v)
                  #(input-text % v)
                  v)
         target-els (find-elements wd query-map)]
     (if submit?
       (doseq [el target-els]
         (action el))
       (apply action target-els)))))

(defprotocol IFormHelper
  "Useful functions for dealing with HTML forms"
  (quick-fill
    [wd query-action-maps]
    "`wd`              - WebDriver
    `query-action-maps`   - a seq of maps of queries to actions (queries find HTML elements, actions are fn's that act on them)

    Note that a \"query\" that is just a String will be interpreted as the id attribute of your target element.
    Note that an \"action\" that is just a String will be interpreted as a call to `input-text` with that String for the target text field.

    Example usage:
    (quick-fill wd
      [{\"first_name\" \"Rich\"}
       {{:class \"foobar\"} click}])")
  (quick-fill-submit
    [wd query-action-maps]
    "Same as `quick-fill`, but expects that the final step in your sequence will submit the form, and therefore webdriver will not return a value (since all page WebElement objects are lost in Selenium-WebDriver's cache after a new page loads)"))

(extend-type WebDriver
  IFormHelper
  (quick-fill
    [wd query-action-maps]
    (doseq [entries query-action-maps
            [k v] entries]
      (quick-fill* wd k v)))

  (quick-fill-submit
    [wd query-action-maps]
    (doseq [entries query-action-maps
            [k v] entries]
      (quick-fill* wd k v true))))

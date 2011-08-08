## Todo's for clj-webdriver ##

The following are features I would like to implement or limitations I would like to eliminate.

### Features ###

#### Form Filling ###

I'd like to put together a function that takes a map of form-fields-to-values and "intuitively" fills out a form. The first arg would be this map of fields and values, followed by an optional map including entries for specifying a form id (if a page contains multiple forms), a toggle for submitting after filling, amongst other things.

#### Data-Driven Testing ####

Ostensibly, this library is most useful as a web testing tool (that's certainly how I use it). In that vein, it would be good to support various kinds of data-driven testing, including reading from common formats (CSV, SQL databases, even Excel spreadsheets (using existing Java libraries)).

#### Wrappers/Middlewares ####

Beyond simply interacting with the page, this library should allow developers to gather information about the elements, the page or the browser at any given point. To help foster this, it would be nice to be able to "wrap" functionality around the means of interacting with the page, and allow developers to write middlewares that do things like custom reporting, extra auxiliary validation, or even things that might alter the DOM based on context.
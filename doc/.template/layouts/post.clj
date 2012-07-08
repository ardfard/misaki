; @layout  default
; @title   post default title

[:article
 ; page header
 [:div {:class "page-header"}
  ; post title
  (link (h1 (:title site)) (:index site))
  ; post tags
  (post-tags)
  ; post date
  (post-date)]

 ; contents
 [:div {:class "post"} contents
  [:p {:class "back"} "&raquo; " (link "Back to Top" (:index site))]]]


(defproject replikativ-demo "0.1.0-SNAPSHOT"
  :description "Example project for replikativ in clj."
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [io.replikativ/replikativ "0.1.4"]
                 ;; use whatever slf4j backend you want, timber is not bad...
                 [com.fzakaria/slf4j-timbre "0.3.1"]])

package fr.laas.fape.constraints.meta

class Configuration(
                     val enforceTpAfterStart: Boolean = true // if true, any timepoint add to the STN will enforced to be greater or equal than csp.temporalOrigin
                   )
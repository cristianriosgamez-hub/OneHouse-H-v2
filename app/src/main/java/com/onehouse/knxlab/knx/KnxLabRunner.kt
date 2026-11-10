package com.onehouse.knxlab.knx

/** Sólo el tipo de destino usado por el motor H. No incorpora otros métodos del Lab. */
object KnxLabRunner {
    data class Target(val host: String, val port: Int, val nat: Boolean)
}

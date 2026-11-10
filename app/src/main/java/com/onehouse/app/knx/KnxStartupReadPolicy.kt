package com.onehouse.app.knx

/** Installation 3.15: group actions have no aggregate feedback object. */
internal object KnxStartupReadPolicy {
    const val VALVE_STATE = "2/4/1"
    val commandOnly = setOf("1/1/50", "2/1/50", "2/1/53", "5/5/3", "5/5/13", "5/5/14", "2/3/1")

    // No current OneHouse state is excluded from GroupValueRead as event-only.
    // Terrace rain (15/0/15) has readable feedback in the validated installation,
    // so it must be queried at startup and still updated by spontaneous telegrams.
    fun isEvent(address: String): Boolean = false

    fun tracked(addresses: List<String>): List<String> = addresses
        .map(String::trim).filter(String::isNotEmpty).filterNot { it in commandOnly }.distinct()

    fun readable(addresses: List<String>): List<String> = tracked(addresses).filterNot(::isEvent)
}

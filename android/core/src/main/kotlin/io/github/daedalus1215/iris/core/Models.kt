package io.github.daedalus1215.iris.core

import kotlinx.serialization.Serializable

@Serializable
data class Health(val status: String, val env: String, val version: String)

@Serializable
data class Paired(val companion: Boolean, val airplay: Boolean)

@Serializable
data class Device(
    val id: String,
    val name: String,
    val address: String,
    val model: String,
    val os: String,
    val paired: Paired,
    val connected: Boolean,
) {
    /** Commands work once either protocol is paired. */
    val canControl: Boolean get() = paired.companion || paired.airplay
}

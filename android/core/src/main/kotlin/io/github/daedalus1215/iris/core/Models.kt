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

    fun isPaired(protocol: PairingProtocol): Boolean = when (protocol) {
        PairingProtocol.COMPANION -> paired.companion
        PairingProtocol.AIRPLAY -> paired.airplay
    }
}

/**
 * Each protocol is paired separately, with its own PIN shown on the TV. Companion alone
 * carries every command Iris sends; AirPlay adds nothing Iris uses yet.
 */
enum class PairingProtocol(val apiName: String) {
    COMPANION("companion"),
    AIRPLAY("airplay"),
}

/** A finger on the Siri Remote's touch surface: down, moving, up. */
enum class TouchPhase(val apiName: String) {
    PRESS("press"),
    MOVE("move"),
    RELEASE("release"),
}

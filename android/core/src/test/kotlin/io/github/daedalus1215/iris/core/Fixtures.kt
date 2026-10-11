package io.github.daedalus1215.iris.core

val BEDROOM = Device(
    id = "AA:BB:CC:00:00:01",
    name = "Bedroom",
    address = "192.0.2.10",
    model = "Apple TV 4K",
    os = "tvOS 26.6",
    paired = Paired(companion = true, airplay = true),
    connected = false,
)

val DEN_UNPAIRED = BEDROOM.copy(
    id = "DEN",
    name = "Den",
    address = "192.0.2.11",
    paired = Paired(companion = false, airplay = false),
)

const val DEVICES_JSON = """[{"id":"AA:BB:CC:00:00:01","name":"Bedroom","address":"192.0.2.10",
"model":"Apple TV 4K","os":"tvOS 26.6","paired":{"companion":true,"airplay":true},"connected":false,
"added_later":"ignored"}]"""

class FakeClient(
    var devices: List<Device> = listOf(BEDROOM),
    private val env: String = "local",
) : IrisClient {
    val sent = mutableListOf<Pair<String, String>>()
    var failWith: IrisException? = null

    override suspend fun health() = Health("ok", env, "0.1.0")

    override suspend fun devices(): List<Device> {
        failWith?.let { throw it }
        return devices
    }

    override suspend fun scan() = devices()

    override suspend fun send(deviceId: String, command: String) {
        failWith?.let { throw it }
        sent += deviceId to command
    }

    var goodPin = "1234"

    override suspend fun startPairing(deviceId: String, protocol: PairingProtocol): String {
        failWith?.let { throw it }
        return "session-${protocol.apiName}"
    }

    override suspend fun finishPairing(deviceId: String, protocol: PairingProtocol, session: String, pin: String) {
        if (pin != goodPin) throw IrisException("pairing failed, check the PIN", 400)
        devices = devices.map { device ->
            if (device.id != deviceId) {
                device
            } else when (protocol) {
                PairingProtocol.COMPANION -> device.copy(paired = device.paired.copy(companion = true))
                PairingProtocol.AIRPLAY -> device.copy(paired = device.paired.copy(airplay = true))
            }
        }
    }
}

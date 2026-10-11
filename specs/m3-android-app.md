# M3: Android app (native client)

**Status:** In progress: works on the phone against dev, including pairing; release signing and prod remain
**Depends on:** M1 API. Development can run against the local backend before M2 is done.
**Outcome:** An app on the phone that controls the Apple TV through the home-lab backend, with dev and prod builds installed side by side.

## Design

### Starting point

The scaffold in `android/` uses Kotlin 2.0, Compose (BOM 2024.04), Material 3, AGP 8.8, minSdk 24 and targetSdk 35. Bump these versions at the start.

### Networking

- **HTTP client:** Retrofit, OkHttp and kotlinx.serialization (or Ktor; decide at the start). The models mirror M1's API v1.
- **Plain HTTP:** Android blocks it by default since Android 9. Add a network security config that allows the home-lab host, or use HTTPS through a reverse proxy (an M2 open question).

### Build flavors

| Flavor | Application id | App name | Default server |
|---|---|---|---|
| `dev` | applicationId + `.dev` | "Iris Dev" | home-lab dev URL |
| `prod` | applicationId | "Iris" | home-lab prod URL |

Server URLs come from `gradle.properties` or `local.properties` and can be changed in Settings. The different application ids let both apps be installed at once.

### Screens

- **Remote:** a touchpad (D18), or the d-pad as a setting; menu, home, play/pause, next/previous and power. Haptic feedback on press. Hold-to-repeat follows M1's rules (one request in flight).
- **Devices:** the list from `/api/devices`; pick one and see whether it's paired and connected.
- **Settings:** server URL and auth token.

### Other behavior

- **Volume keys:** the phone's volume buttons send `volume_up` and `volume_down` while the app is open, if the M1 spike shows volume works.
- **Pairing:** in the app as well as the web remote. Use the link button in the header, or **Pair** under "isn't paired yet". Show PIN puts a PIN on the TV, and you type it into the phone. Companion is enough, so it's one PIN per TV per server; AirPlay is optional.
- **Architecture:** a single Compose activity, with ViewModel + StateFlow and a repository over the API client.

## Tasks

### Tooling

- [x] Decide where APKs get built: GitHub Actions, because this Mac can't run `aapt2` (D10)
- [x] CI: core tests and both APKs on every push to `android/`, published to the rolling [`android-dev` pre-release](https://github.com/daedalus1215/iris/releases/tag/android-dev)
- [ ] JDK 21 on this Mac for running core tests locally (you: `sudo pacman -S jdk21-openjdk android-tools`)
- [ ] Optional: install over `adb` (wireless debugging) instead of from the release page

### App

- [x] Application id `io.github.daedalus1215.iris`, with `.dev` for the dev flavor (D12)
- [x] AGP 9.4.1, Gradle 9.8, Kotlin 2.4.20, Compose BOM 2026.09; compileSdk 37, minSdk 26 (D11)
- [x] dev and prod flavors, and plain HTTP allowed (D13)
- [x] `core`: API client, models and error messages, with tests against MockWebServer
- [x] `core`: hold-to-repeat (400 ms delay, then every 150 ms, one request at a time), tested on virtual time
- [x] `core`: remote state (devices, selection, errors), tested against a fake client
- [x] Remote screen: power, d-pad, back, home, media and volume keys, with haptics
- [x] Device picker and scan in the header (instead of a separate devices screen)
- [x] Settings dialog: server address and token
- [x] The phone's volume keys control the TV while the app is open
- [x] Pairing in the app: core client and controller with tests, plus a pairing dialog with PIN entry
- [x] Try it on the phone: Iris Dev against the dev server. Paired Living Room over both protocols from the app, then controlled it (2026-10-04).
- [x] Touchpad (D18): a drag streams touches, a tap selects, and a long press holds select. The arrow buttons are a setting. The screen no longer scrolls, and the app is portrait only. Core: 11 new tests, 39 in total.
- [ ] Try the touchpad on the phone, and tune how far a swipe moves
- [x] Smoother swipes (D20): touches carry the finger's time, swipes keep to the axis they set out along, and the slide back at lift-off is ignored
- [x] Typing with the phone's keyboard (D19): the app watches the TV's text field while it's open, and a dialog opens when one gets focus. Core: 10 new tests, 49 in total.
- [ ] Try typing on the phone: a search on each TV

### Release

- [ ] Release signing key kept in a CI secret, not in git
- [ ] Signed release APK on tags
- [ ] Point the prod flavor at the home-lab prod server (after M2)

## Acceptance criteria

- [ ] The APK builds from the command line and installs on the phone.
- [ ] The prod app controls Bedroom through the home-lab prod backend, and the dev app through dev.
- [ ] Every button works, hold-to-repeat has no backlog, and the volume keys work if supported.
- [ ] When the backend or Apple TV is unreachable, the app shows a clear message instead of crashing, and recovers on its own.

## Later, or out of scope

- Standalone mode, where the phone talks to the Apple TV directly with no backend (see D4).
- A quick-settings tile and a home-screen widget.

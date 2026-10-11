# M3: Android app (native client)

**Status:** Not started
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

- **Remote:** d-pad (or a swipe touchpad), select, menu, home, play/pause, next/previous and power. Haptic feedback on press. Hold-to-repeat follows M1's rules (one request in flight).
- **Devices:** the list from `/api/devices`; pick one and see whether it's paired and connected.
- **Settings:** server URL and auth token.

### Other behavior

- **Volume keys:** the phone's volume buttons send `volume_up` and `volume_down` while the app is open, if the M1 spike shows volume works.
- **Pairing:** stays in the web UI for this version. It happens once per environment.
- **Architecture:** a single Compose activity, with ViewModel + StateFlow and a repository over the API client.

## Tasks

### Tooling (this PC)

- [ ] Install JDK 17 and the Android SDK (cmdline-tools, platform 35, build-tools), or Android Studio
- [ ] Build from the CLI with `./gradlew assembleDevDebug`, run from `android/`
- [ ] Phone: turn on developer options and USB or wireless debugging; confirm `adb install` works

### App

- [ ] Choose the applicationId, replacing `com.example.iris` before the first install on the phone
- [ ] Bump the AGP, Kotlin and Compose versions
- [ ] dev and prod flavors, and the network security config
- [ ] API client, models and error handling
- [ ] Remote screen
- [ ] Devices screen
- [ ] Settings screen
- [ ] Volume keys
- [ ] Unit tests for the API client and ViewModels (MockWebServer)

### Release

- [ ] Signed release APK, with the keystore kept out of git
- [ ] Install the prod and dev builds on the phone
- [ ] Optional: CI builds the APK on tags, filtered to changes in `android/`

## Acceptance criteria

- [ ] The APK builds from the command line and installs on the phone.
- [ ] The prod app controls Bedroom through the home-lab prod backend, and the dev app through dev.
- [ ] Every button works, hold-to-repeat has no backlog, and the volume keys work if supported.
- [ ] When the backend or Apple TV is unreachable, the app shows a clear message instead of crashing, and recovers on its own.

## Later, or out of scope

- Standalone mode, where the phone talks to the Apple TV directly with no backend (see D4).
- Pairing inside the app, a quick-settings tile, a home-screen widget, and keyboard text entry (pyatv has a keyboard interface).

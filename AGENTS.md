# kinoko-android: agent rules

The Android app for kinoko: Kotlin, Jetpack Compose, minSdk 26. It speaks kinoko's protocol
(`kinoko/docs/protocol.md`); change the protocol there first, then here.

- One branch per change off main; merge only when the user says so.
- Dependencies stay few: Compose, OkHttp (WebSocket), CameraX + ZXing (the QR), security-crypto.
  No Google Play services, no Firebase.
- The logic is plain Kotlin and tested on the JVM (`app/src/test`): `data/Protocol.kt`
  (parsing), `data/Transcript.kt` (history + events → what the chat draws). Keep Android out of it.
- `./gradlew testDebugUnitTest assembleDebug` passes with no warnings before a commit.
- Check UI changes on the emulator (`emulator -avd kinoko -no-window`, `adb reverse tcp:7878
  tcp:7878`, `kinoko serve --local`) with `adb exec-out screencap -p`.

## Map
data/Protocol.kt     the protocol's shapes, pairing links
data/Hub.kt          pairing, the encrypted store, the WebSocket client (reconnects by itself)
data/Transcript.kt   the conversation built from history and events (pure)
data/Sentences.kt    a reply cut into sentences to read aloud; data/Reading.kt: events → say/end/hush (pure)
Voice.kt             replies read aloud: each sentence from the hub's /v1/speech into one AudioTrack
HubViewModel.kt      state for the screens
ui/                  Pair (QR scanner), Agents, Chat, Markdown, Theme

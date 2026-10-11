# Kinoko for Android 🍄

Your [kinoko](https://github.com/LaraGuardiola/kinoko) agents, from your phone, anywhere. Not a
picture of a terminal: the app gets the agents' events as they happen (each token, each tool call)
and draws them natively, so the conversation reflows to the screen, code scrolls sideways, and the
buttons are real ones.

- **Agents**: every agent `kinoko serve` runs on your computer, live: working, waiting for you,
  what it was asked. Start a new one in any project kinoko has worked in, or resume a session.
- **Chat**: the reply streams in as markdown; thinking folds away; tool calls show what they ran,
  with a ✓ or ✗ and their output a tap away. Ask, steer it mid-run ("later" queues instead), or stop
  the run (Esc).
- **Notifications**: an agent that finishes, stops or fails while the app is not on screen says so,
  with how its reply began; tap to open its chat. A quiet "Listening" notification stays while the
  app keeps the connection open in the background (Disconnect ends it until you open the app).
- **Slash commands**: type `/` for the agent's commands (new, compact, thinking, models,
  extensions, skills, session, clear, fork, help); lists open in a panel above the input.
- **Pairing**: scan the QR code `kinoko serve` shows. The token stays in the Android Keystore.
- **Anywhere**: through Tailscale. With the Tailscale app on the phone, `kinoko serve` is reachable
  from anywhere and only by your devices; `kinoko serve --funnel` opens it to the internet for a
  phone without Tailscale (then only paired devices get in).

## Use it

On the computer:

```sh
kinoko serve          # shares itself with your tailnet; shows a QR code the first time
kinoko serve pair     # a new QR code for another device
```

On the phone: install the APK, open kinoko, *Scan the code*.

## Build

Needs JDK 21 and the Android SDK (platform 35). With [mise](https://mise.jdx.dev) the JDK comes from
`.mise.toml`:

```sh
mise install
./gradlew testDebugUnitTest     # the protocol and the transcript, on the JVM
./gradlew assembleRelease       # app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```

The release APK is signed with the debug key for now: installable by hand, not for a store.

## How it talks

The protocol is kinoko's [docs/protocol.md](https://github.com/LaraGuardiola/kinoko/blob/main/docs/protocol.md):
pairing over HTTPS, then one WebSocket carrying the agent list, history and live events.

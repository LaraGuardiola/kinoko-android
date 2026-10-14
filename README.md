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
- **Files and photos**: 📎 by the input attaches a photo from the gallery, one taken now, or any
  file. They travel with the message: the computer saves them in `~/.kinoko/uploads/` and tells the
  agent where; a photo (shrunk to 2048 px) is also shown to the model. Up to 20 MB a message.
- **Approvals**: when the agent wants to run something gated (`sudo`, `rm -rf`… with
  permission-gate), a card above the input shows the command and why: Allow, Deny or For this
  session. In the background it is a notification with the same three answers. The first answer
  wins (the terminal's too), and the card and the notification go when anyone answers.
- **Slash commands**: type `/` for the agent's commands (new, compact, thinking, models,
  extensions, skills, session, clear, fork, help); lists open in a panel above the input.
- **Blocks as in the terminal**: code with its colours, diffs (tinted rows, the changed words
  marked), directory trees, mermaid diagrams, tables and LaTeX math (`$$…$$` blocks, inline `$…$`,
  with the `latex` extension) are drawn by `kinoko serve` exactly as the TUI draws them (with your
  extensions' renderers), fitted to the phone's columns; prose stays native and reflows. A block still being written shows as plain code until it is complete.
- **Pairing**: scan the QR code `kinoko serve pair` shows. The token stays in the Android Keystore.
- **At home**: straight over the local network, encrypted; the app trusts the computer's own
  certificate only (its fingerprint comes in the QR). No Tailscale needed. The header says `· LAN`.
- **Anywhere**: through Tailscale. With the Tailscale app on the phone, `kinoko serve` is reachable
  from anywhere and only by your devices; `kinoko serve --funnel` opens it to the internet for a
  phone without Tailscale (then only paired devices get in).

## Use it

On the computer:

```sh
kinoko serve          # the local network and your tailnet; shows a QR code the first time
kinoko serve pair     # a new QR code for another device (starts kinoko serve if it is not running)
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

## Fonts

The monospace font is Noto Sans Mono (subset: Latin, punctuation, arrows, box drawing, blocks and
shapes), under the SIL Open Font License: `licenses/NotoSansMono-OFL.txt`.

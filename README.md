# Immersion Player

**A Japanese video player where the subtitle line is the interface.**

[![Licence: GPL-3.0-or-later](https://img.shields.io/badge/licence-GPL--3.0--or--later-blue)](LICENSE)
![Android 11+](https://img.shields.io/badge/Android-11%2B-3ddc84)
![arm64-v8a](https://img.shields.io/badge/abi-arm64--v8a-lightgrey)
![macOS 12+](https://img.shields.io/badge/macOS-12%2B%20Apple%20Silicon-lightgrey)

A video player for Android and macOS with a Japanese dictionary built in. The video plays on one side; the
current line sits on the other with a word already looked up. Tap another word, drag across a
phrase, hold for the English, swipe to step back a line and hear it again.

![Stepping through lines while the video plays](docs/loop.gif)

## Android: no buttons

Every action is a gesture. The app lists them once, on first run.

| Where | Gesture | Does |
|---|---|---|
| Video | tap | play / pause |
| Video | double-tap left or right edge | −5 s / +5 s, and keep tapping to stack |
| Video | drag sideways | scrub — move up or down mid-drag to scrub faster |
| Video | pinch | fill the area, or fit the whole picture |
| Video (paused) | tap or drag the bottom line | seek |
| Line | tap a word | look it up |
| Line | drag across the text | look up exactly what you selected |
| Line | hold | show the English translation |
| Panel | swipe sideways | previous / next line |
| Panel | tap | play / pause |
| Panel | double-tap | stop at the end of every line, on or off |
| Divider | drag | resize the video and the panel |
| Shoulder triggers | press | previous / next line (RedMagic, see below) |

![Portrait mode, with the same line shown as Japanese and then held to reveal the English](docs/portrait.png)

## Desktop: keys

| Key | Does |
|---|---|
| Space | play / pause |
| j / k | previous / next line |
| h / l | previous / next line, stop at its end |
| ; | replay this line, stop at its end |
| hold i, or hold the line | show the peek language |
| y / o | seek 5 s back / forward |
| n / m | shift subtitles 0.1 s earlier / later |
| u | stop at the end of every line, on or off |
| z, or ⌘ + scroll | fill the area, or fit the whole picture |
| f, or double-click | full screen |
| Esc | leave full screen, then back to the library |
| click / drag a word | look it up |

## Dictionaries

- Imports Yomitan/Rikaitan zips: term banks, frequency lists, pitch accent. Structured content is
  rendered, not flattened.
- JMdict is bundled and installs on first launch. Stored in SQLite, searched on device.
- Lookup takes the longest match at the point you touched and deinflects:
  食べさせられなかった → 食べる, with the chain shown in the entry.
- Every new line is looked up automatically — first kanji found, skipping speaker names and
  bracketed readings, falling through candidates until one hits.

## Playback

- libmpv, from [mpv-android](https://github.com/mpv-android/mpv-android)'s prebuilt binaries.
  Needed because much of what you'll play is 10-bit H.264, which Android's hardware decoders
  don't support and its software decoder refuses — a `MediaCodec` player shows a black rectangle.
- Desktop uses the same libmpv through its software renderer, drawing into the Compose window:
  about 5 ms per 1080p frame on Apple Silicon.
- Library thumbnails come from a second, headless mpv rather than a separate decoder.
- Subtitles are read from the container directly: Matroska EBML walked in-app, zlib-compressed
  tracks handled, SRT/ASS/SSA/WebVTT parsed. Sidecar files beat embedded tracks. Cached per file.
- Tracks are picked by language (desktop: set in settings). Tags are checked against the text:
  each line votes by script, so a Japanese track tagged `eng` is still found, and an English
  track with Japanese song lyrics stays English.

![The library, with thumbnails and resume positions](docs/library.png)

The library groups a folder of shows, sorts episodes in natural order, remembers where you
stopped, and offers the next episode when one ends. It also opens videos handed to it by other
apps.

## Shoulder triggers (RedMagic)

Maps the two capacitive triggers to previous/next line. Off by default, needs
[Shizuku](https://shizuku.rikka.app/), advanced settings.

They report `KEY_F7`/`KEY_F8` but the events never reach an app: the sensors are unpowered unless
Nubia's game settings are on, and Nubia's game service consumes the keys first. The app therefore
bypasses the input stack. While a video is open, a Shizuku user service (shell privilege) enables
the sensors, re-applies the setting every second because Nubia resets it, and reads the device
nodes with `getevent`. Settings are restored on exit.

Shizuku has to be started again after every reboot; the app's permission persists. Nothing else
in the app uses it. Approach from [RedTrigger](https://github.com/zampierilucas/RedTrigger).

## Install

Both are on [releases](https://github.com/finchett/Immersion-Player/releases/latest).

- **Android:** the APK.
- **macOS (Apple Silicon):** the `.dmg`. It isn't notarised, so the first launch needs
  right-click → Open, or `xattr -dr com.apple.quarantine "/Applications/Immersion Player.app"`.

### Build: Android

```sh
./scripts/fetch-libmpv.sh          # pinned mpv-android release, extracts its native libs
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17, Android SDK platform 36, and an arm64 device on Android 11 or newer.

The native libraries are not committed. `app/src/main/java/is/xyz/mpv/MPVLib.kt` is vendored
unchanged from the same mpv-android tag, because the prebuilt `libplayer.so` binds to that exact
class — keep the two in step when you update either.

### Build: desktop

```sh
brew install mpv                          # libmpv, found at run time
./gradlew :desktop:run
./gradlew :desktop:packageMacRelease      # desktop/build/release/*.dmg, libmpv bundled
```

Packaging copies Homebrew's libmpv and the libraries it loads into the app and relinks them, so
the result doesn't need Homebrew. Only macOS on Apple Silicon is packaged; the code has no
macOS-only parts besides the traffic-light placement, but Windows and Linux are untested.

## Tests

```sh
./gradlew :core:test
IMMERSION_TEST_VIDEO=any.mkv ./gradlew :desktop:test   # drives the real player offscreen
```

The Matroska test checks extraction against reference files produced by ffmpeg, and skips itself
unless you point it at a real episode:

```sh
IMMERSION_TEST_MKV=episode.mkv \
IMMERSION_TEST_JA_SRT=episode.ja.srt \  # ffmpeg -i episode.mkv -map 0:s:<ja> episode.ja.srt
IMMERSION_TEST_EN_ASS=episode.en.ass \  # ffmpeg -i episode.mkv -map 0:s:<en> -c:s copy episode.en.ass
./gradlew :core:test
```

## Debugging

Some phones quietly disable logcat for third-party apps, so crashes go to `files/last_crash.txt`
and lookup failures to `files/errors.log`:

```sh
adb shell run-as io.github.immersionplayer cat files/last_crash.txt
```

## Layout

```
core/       plain Kotlin/JVM, shared by every platform
  subs/        SRT/ASS parsers, Matroska extractor, embedded-track cache
  dictionary/  Yomitan importer, SQLite store (behind Sql), deinflector, lookup
  mining/      card model and store for Anki export
  library/     natural sort, video names
shared-ui/  Compose shared by both apps: dictionary panel, glossary renderer, themes
app/        Android
  player/      MpvView (libmpv surface), PlayerSession (playback state, line stepping)
  subs/        sidecar and embedded subtitle loading via SAF
  dictionary/  AndroidSql, bundled dictionaries
  triggers/    Shizuku user service for the RedMagic shoulder buttons
  ui/          library, player, settings
desktop/    Compose Desktop
  mpv/         libmpv over JNA, software-rendered into a Skia bitmap
               library, player, settings, macOS window chrome
```

## Limitations

- Anki export: card model and store exist, no UI. Nothing leaves the app yet.
- Sidecar subtitles can't be found for videos opened from another app (a content URI doesn't say
  what's next to it).
- Android: arm64 only, phone layouts only.
- Desktop: only macOS on Apple Silicon is packaged. Lookups deinflect Japanese only, whatever
  language is set to study.

## Licence

Immersion Player is free software under the **GNU General Public License v3.0 or later**
(see [LICENSE](LICENSE)). That follows from the mpv and FFmpeg binaries it bundles. You may use,
modify, sell and redistribute it, provided recipients get the same freedoms and the source.

- **JMdict** (bundled): © Electronic Dictionary Research and Development Group, under
  [CC BY-SA 4.0](https://www.edrdg.org/edrdg/licence.html). Yomitan conversion by
  [rikaitan-import](https://github.com/Ajatt-Tools/rikaitan-import).
- **mpv-android** (`MPVLib.kt`, JNI glue): MIT.
- **libmpv and FFmpeg** (prebuilt, fetched by `scripts/fetch-libmpv.sh`): GPL-2.0+/LGPL-2.1+ as
  built by mpv-android. These are why this app is GPL.
- **libmpv, FFmpeg and their libraries** (macOS build, copied from Homebrew): GPL, including
  x264 and x265.
- **Compose Multiplatform, JNA, sqlite-jdbc** (desktop): Apache-2.0 / Apache-2.0 or LGPL-2.1 /
  Apache-2.0.
- **Shizuku API** (optional, for the shoulder triggers): Apache-2.0.

The footage in the screenshots is
[Build your first WebAuthn app](https://www.youtube.com/watch?v=8ren54IMSf4) by Eiji Kitamura for
Chrome for Developers, used under CC BY 3.0, with its own Japanese and English subtitle tracks.

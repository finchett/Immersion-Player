# Immersion Player

An Android video player for Japanese immersion. The video sits on the left; the current
subtitle line sits on the right with its words already looked up. Tap or drag across any
word for its dictionary entry, swipe between lines, and hold to peek at the English.

Built for landscape phones (developed on a RedMagic 10 Pro).

## Features

- **Plays anything mpv plays**, including 10-bit H.264 anime encodes that Android's own
  decoders can't handle (playback is libmpv from [mpv-android](https://github.com/mpv-android/mpv-android)).
- **Subtitles straight from the file.** Text subtitle tracks (SRT, ASS/SSA, WebVTT) are read
  out of MKV files, including zlib-compressed tracks. Sidecar `.srt`/`.ass`/`.vtt` files with
  the same name as the video are used first when present. Results are cached per file.
- **Always a word defined.** Each new line looks up its first kanji automatically (skipping
  speaker names and readings in brackets), falling back to the next candidates until
  something matches.
- **Yomitan dictionaries.** Import the same zips Yomitan/Rikaitan use (Jitendex, JMdict,
  frequency lists, pitch accent). Lookup takes the longest match and undoes conjugations
  (食べさせられなかった → 食べる). JMdict is bundled and installs itself on first launch.
- **Library** of your shows with natural episode order, resume positions and a
  next-episode card at the end of each episode. Also opens videos from other apps.

## Gestures

| Where | Gesture | Does |
|---|---|---|
| Video | tap (middle) | play / pause |
| Video | double tap left / right edge | jump −5 s / +5 s (keep tapping to stack) |
| Video | drag sideways | scrub; move up or down while dragging to go up to 10× faster |
| Video | pinch out / in | fill the area / fit the whole picture |
| Video (paused) | tap or drag the bottom line | seek to that point |
| Video (paused) | ✕ / ⋯ | close / options (tracks, subtitle timing, fill, stop at end) |
| Line | tap a character | look up from there |
| Line | drag across text | look up the best match inside the selection |
| Right panel | swipe left / right | next / previous line |
| Right panel | hold | show the English line in place of the Japanese |
| Right panel | tap / double tap | play-pause / toggle stop-at-end-of-line |
| Divider | drag its middle | resize the panel (28–50 % of the width) |
| Shoulder triggers | press left / right | previous / next line (RedMagic, needs Shizuku — see below) |

## Shoulder triggers (RedMagic)

RedMagic's capacitive shoulder triggers report `KEY_F7`/`KEY_F8`, but the phone only powers them
while Nubia's game settings are on, and Nubia's game service swallows the key events before any
app sees them. With [Shizuku](https://shizuku.rikka.app/) running, the app starts a small user
service (shell privileges) while a video is open that switches the sensors on, keeps them on when
Nubia resets them, and reads the two device nodes with `getevent`. The triggers are switched off
again when you leave the player. Turn it on under Dictionaries & settings, where the same section
shows what Shizuku still needs and can test the triggers live.

Approach discovered by [RedTrigger](https://github.com/zampierilucas/RedTrigger) (MIT).

## Building

Requirements: JDK 17, the Android SDK (platform 36), and an arm64 Android device.

```sh
./scripts/fetch-libmpv.sh          # downloads the pinned mpv-android release and extracts its native libs
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The native libraries are not committed. `app/src/main/java/is/xyz/mpv/MPVLib.kt` is
vendored unchanged from the same mpv-android tag, because the prebuilt `libplayer.so`
binds to that class; keep the two in sync when updating.

## Tests

```sh
./gradlew :app:testDebugUnitTest
```

The Matroska test compares extraction against reference files from ffmpeg and only runs
when these are set (it is skipped otherwise):

```sh
IMMERSION_TEST_MKV=episode.mkv \
IMMERSION_TEST_JA_SRT=episode.ja.srt \  # ffmpeg -i episode.mkv -map 0:s:<ja> episode.ja.srt
IMMERSION_TEST_EN_ASS=episode.en.ass \  # ffmpeg -i episode.mkv -map 0:s:<en> -c:s copy episode.en.ass
./gradlew :app:testDebugUnitTest
```

## Debugging

The app writes crashes to `files/last_crash.txt` and lookup errors to `files/errors.log`
(some phones disable logcat). With the debug build:

```sh
adb shell run-as io.github.immersionplayer cat files/last_crash.txt
```

## Project layout

```
app/src/main/java/io/github/immersionplayer/
  player/      MpvView (libmpv surface), PlayerSession (playback state, line stepping)
  subs/        SRT/ASS parsers, Matroska subtitle extractor, subtitle loading and caching
  dictionary/  Yomitan importer, SQLite store, deinflector, lookup, bundled dictionaries
  mining/      card model and store for future Anki export (not wired to the UI yet)
  ui/          library, player, dictionary panel, settings
```

## Licences

- **JMdict** (bundled): © Electronic Dictionary Research and Development Group, used under
  [CC BY-SA 4.0](https://www.edrdg.org/edrdg/licence.html). Yomitan conversion by
  [rikaitan-import](https://github.com/Ajatt-Tools/rikaitan-import).
- **mpv-android** (`MPVLib.kt`, JNI glue): MIT.
- **libmpv and FFmpeg** (prebuilt binaries): GPL-2.0+/LGPL-2.1+ as built by mpv-android.
  Distributing APKs that include them means following those licences (including offering
  the corresponding source). No licence has been chosen for this app's own code yet.

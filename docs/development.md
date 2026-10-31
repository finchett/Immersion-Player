# Development

## Build: desktop

```sh
brew install mpv tesseract                # libmpv and libtesseract, found at run time
./gradlew :desktop:run
./gradlew :desktop:packageMacRelease      # desktop/build/release/*.dmg, libmpv bundled
```

Packaging copies Homebrew's libmpv and libtesseract, and the libraries they load, into the app
and relinks them, so the result doesn't need Homebrew.

Windows and Linux are packaged by the *Desktop builds* workflow on GitHub's runners, because
jpackage only packages for the system it runs on. It makes an `.msi` with shinchiro's libmpv
bundled, and a `.deb` that depends on the system's libmpv. `release.sh` takes them from the run
for the commit it releases.

To try a first launch without touching your own settings, run a packaged build with
`JAVA_TOOL_OPTIONS="-Duser.home=/tmp/fresh -Dmizune.prefs=app/mizune-fresh"`.

## Build: Android

```sh
./scripts/fetch-libmpv.sh          # mpv-android's native libs, built without GPL code (Android libmpv workflow)
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Needs JDK 17, Android SDK platform 36, and an arm64 device on Android 11 or newer.

The native libraries aren't committed. `app/src/main/java/is/xyz/mpv/MPVLib.kt` is copied
unchanged from the same mpv-android tag, because the prebuilt `libplayer.so` binds to that exact
class. Update the two together.

## Release

```sh
./scripts/release.sh --try notes.md   # same files in dist/, feed on localhost, to try the update first
./scripts/release.sh notes.md         # builds, signs and drafts the GitHub release
gh release edit v1.0.0 --draft=false  # publishing is what sends the update
```

The version is `appVersion` in `gradle.properties`, and Android's versionCode is derived from
it. The update feed is signed with `~/.keystores/mizune-update.pem`, whose public half is in
`core`'s `Updates`. Release APKs are signed with `~/.keystores/mizune.jks` (`-PmizuneKeystore`
points at `mizune.properties`). Back both up: installed apps accept no other feed key, and
Android won't update an app signed with a different key.

## Tests

```sh
./gradlew :core:test
MIZUNE_TEST_VIDEO=any.mkv ./gradlew :desktop:test   # drives the real player offscreen
MIZUNE_TEST_PGS=bluray.mkv ./gradlew :desktop:test  # reads a real episode's PGS tracks
```

The Matroska test compares extraction with reference files made by ffmpeg. It skips itself
unless you point it at a real episode:

```sh
MIZUNE_TEST_MKV=episode.mkv \
MIZUNE_TEST_JA_SRT=episode.ja.srt \  # ffmpeg -i episode.mkv -map 0:s:<ja> episode.ja.srt
MIZUNE_TEST_EN_ASS=episode.en.ass \  # ffmpeg -i episode.mkv -map 0:s:<en> -c:s copy episode.en.ass
./gradlew :core:test
```

The MP4 tests run on small files in `core/src/test/resources/mp4`. One more compares a real
file's first text track with ffmpeg's reading of it:

```sh
MIZUNE_TEST_MP4=episode.mp4 \
MIZUNE_TEST_MP4_SRT=episode.srt \  # ffmpeg -i episode.mp4 -map 0:s:0 episode.srt
./gradlew :core:test
```

The README's screenshots come from `ReadmeScreenshots`, which renders the real screens at twice
the size. Its header says what it needs.

On-device Android tests are in `app/src/androidTest` and are run by hand, as described there.

## Debugging

Some phones turn off logcat for third-party apps, so crashes go to `files/last_crash.txt` and
lookup failures to `files/errors.log`:

```sh
adb shell run-as app.mizune cat files/last_crash.txt
```

## Layout

```
core/       plain Kotlin/JVM, shared by every platform
  subs/        SRT/ASS parsers, Matroska and MP4 extractors, embedded-track cache, PGS decoding
               and line-by-line OCR (the engine is each platform's)
  media/       audio tracks as mpv lists them
  dictionary/  Yomitan importer, SQLite store (behind Sql), deinflector, lookup, catalogue
  mining/      card model and store for Anki export
  library/     natural sort, video names
  update/      the signed update feed
shared-ui/  Compose shared by both apps: dictionary panel, glossary renderer, themes
app/        Android
  player/      MpvView (libmpv surface), PlayerSession (playback state, line stepping)
  subs/        sidecar and embedded subtitle loading via SAF
  dictionary/  AndroidSql, bundled dictionaries
  triggers/    Shizuku user service for the RedMagic shoulder buttons
  ui/          library, player, settings
desktop/    Compose Desktop
  mpv/         libmpv over JNA, rendered offscreen into a Skia bitmap
               library, player, settings, welcome, window chrome for each OS
```

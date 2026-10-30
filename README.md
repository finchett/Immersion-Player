<div align="center">

<img src="desktop/src/main/resources/icon.png" width="120" alt="">

# Mizune

A video player for learning Japanese, with a dictionary in the subtitles.

[![Download](https://img.shields.io/badge/Download-macOS%20·%20Windows%20·%20Linux%20·%20Android-245D4E?style=for-the-badge)](https://github.com/finchett/Mizune/releases/latest)

[![Latest release](https://img.shields.io/github/v/release/finchett/Mizune?label=latest&color=8FCFB4)](https://github.com/finchett/Mizune/releases/latest)
[![Licence: MPL-2.0](https://img.shields.io/badge/licence-MPL--2.0-8FCFB4)](LICENSE)

</div>

![Sintel playing, with the current line beside it and 物心 looked up from it](docs/screenshots/player.webp)

Mizune shows the subtitle line being spoken next to the video. Click a word in it to look it
up, go back a line to hear it again, and send words to Anki with the sentence and its audio.

Mizune (水音, "the sound of water") is the girl in the icon. Before 1.0 the app was called
Immersion Player.

## What it does

- **Lookups.** Click a word or drag across a phrase. Mizune finds the longest word at that
  point and undoes the conjugation, so 食べさせられなかった finds 食べる and shows the steps.
  JMdict is built in. Jitendex, JPDB frequencies, pitch accent and any other Yomitan
  dictionary can be added.
- **Line by line.** Go back or forward a line, or replay the current one. The video can also
  pause at the end of every line.
- **Peek.** Hold **i**, or hold the line on Android, to see the line in a language you know:
  English or any of eighteen others. It can also be turned off.
- **Anki cards.** Press **a**, or tap **+** on Android, to add the word with its sentence, the
  line's audio, a short clip or a still, the reading as furigana and its pitch accent. Works
  with any note type.
- **Subtitles.** Files next to the video or tracks inside it: SRT, ASS and WebVTT, plus Blu-ray
  picture subtitles, which are read with OCR.
- **Library.** Choose the folder your shows are in. Each folder becomes a series, episodes sort
  in order, and Mizune remembers where you stopped and offers the next episode.
- **Themes.** Jade, Sakura, Nord, Paper, Adwaita, Breeze and OLED Black, in light and dark,
  following the system or not. Desktop has Anime4K upscaling built in.
- **Updates.** New versions download in the background and are checked against a signature
  before they install.

The first launch takes you through setup: theme, peek language, video folder, dictionaries and
Anki.

| | |
|---|---|
| ![The line over the video, with 探す looked up from 探しているんです](docs/screenshots/over-video.webp) | ![The library: Big Buck Bunny, Caminandes, Elephants Dream, Sintel and Tears of Steel](docs/screenshots/library.png) |
| The line over the video, with the panel put away (**s**) | The library |
| ![The welcome's dictionary step, with Jitendex and JPDB frequency ticked](docs/screenshots/welcome.png) | ![Settings › Appearance in the dark Jade theme](docs/screenshots/themes.png) |
| Choosing dictionaries during setup | The dark Jade theme |

## Install

Download from the [latest release](https://github.com/finchett/Mizune/releases/latest).

- **macOS** (Apple Silicon, macOS 12 or newer): open the `.dmg` and drag Mizune to
  Applications. It isn't notarised yet, so the first time, right-click it and choose **Open**,
  or run `xattr -dr com.apple.quarantine "/Applications/Mizune.app"`.
- **Windows** (x64, Windows 10 or newer): run the `.msi`. It installs for your user only, so it
  doesn't need an administrator. It isn't signed yet, so if SmartScreen stops it, choose
  **More info**, then **Run anyway**.
- **Linux** (x64): the `.AppImage` runs on distributions from about 2022 on (Ubuntu 22.04,
  Debian 12, Fedora 36). Make it executable and run it. Ubuntu 24.04, Debian 13 and newer can
  use the `.deb` instead, which uses the system's libmpv:
  `sudo apt install ./Mizune-*-linux-x64.deb`.
- **Android** (arm64, Android 11 or newer): install the `.apk`.

macOS and Android install updates themselves. Windows and Linux say when there's a new version
and link to it.

Found a bug, or want something changed? [Open an issue](https://github.com/finchett/Mizune/issues).

## Privacy

Once a day the app sends Google Analytics a random ID made for your install, your platform and
its version, and the app's version. Nothing about what you watch, look up or add to Anki, and
no account. It uses the Measurement Protocol, not Google's SDK, and on desktop it only starts
after setup. Turn it off during setup or under Settings › General › Privacy (Settings › Privacy
on Android). The code is in [`UsageStats`](core/src/main/kotlin/app/mizune/stats/UsageStats.kt).

## Keys (desktop)

| Key | Does |
|---|---|
| Space | play / pause |
| h / l | previous / next line, and keep playing |
| j / k | previous / next line, and stop at its end |
| ; | replay this line and stop at its end |
| hold i, or hold the line | show the line in the peek language |
| y / o | 5 s back / forward |
| n / m | subtitles 0.1 s earlier / later |
| u | stop at the end of every line, on or off |
| s | hide or show the panel; when it's hidden, the line goes over the video |
| z, or ⌘ + scroll | fill the area, or fit the whole picture |
| f, F11, or double-click | full screen |
| ⌘ (Ctrl) + 1–6 | Anime4K modes A, B, C, A+A, B+B, C+A |
| ⌘ (Ctrl) + 0 | Anime4K off |
| Esc | close a lookup, leave full screen, then back to the library |
| click / drag a word | look it up |
| a | add the looked-up word to Anki |
| Return | at the end of a video, play the next one |

Going back a line from a stop replays that line first. While paused, **l** and **k** start
playing instead of skipping ahead. **h** and **l** also skip the stops that **u** turns on, until
you next press **j**, **k** or **;**.

## Gestures (Android)

The app shows these once, on first run.

![Stepping through lines while the video plays, on Android](docs/loop.gif)

| Where | Gesture | Does |
|---|---|---|
| Video | tap | play / pause |
| Video | double-tap the left or right edge | 5 s back / forward; keep tapping to go further |
| Video | drag sideways | scrub; move up or down while dragging to scrub faster |
| Video | pinch | fill the area, or fit the whole picture |
| Video (paused) | tap or drag the bottom line | seek |
| Line | tap a word | look it up |
| Line | drag across the text | look up the selection |
| Line | hold | show the line in the peek language |
| Panel | swipe sideways | previous / next line |
| Panel | tap | play / pause |
| Panel | double-tap | stop at the end of every line, on or off |
| Divider | drag | resize the video and the panel; in landscape, drag it off the edge to watch with plain subtitles |
| Shoulder triggers | press | previous / next line on RedMagic phones ([details](docs/how-it-works.md#redmagic-shoulder-triggers)) |

## Limitations

- Picture subtitles can only be read in English and Japanese. Other peek languages need text
  subtitles.
- Subtitle files next to a video aren't found when the video is opened from another app.
- Lookups only undo Japanese conjugation.
- Android: arm64 and phone layouts only.
- Desktop: Apple Silicon Macs, and x64 Windows and Linux. OpenGL on Windows and Linux has only
  been tested without a real GPU, where the app uses software rendering, and Anime4K needs
  OpenGL.

## Development

Building, testing and releasing are in [docs/development.md](docs/development.md). How the
dictionaries, Anki cards, playback and updates work is in
[docs/how-it-works.md](docs/how-it-works.md).

## Licence

Mizune's code is under the [Mozilla Public License 2.0](LICENSE). You can use, change, sell and
redistribute it, and combine it with code under other licences. Changed files have to stay under
MPL-2.0 with their source available. Releases up to 0.4 were GPL-3.0-or-later and stay so.

The mpv and FFmpeg in every build are LGPL builds without their GPL parts. The bundled
libraries, dictionaries and shaders, and their licences, are listed in
[THIRD-PARTY.md](THIRD-PARTY.md).

The desktop screenshots show Blender Foundation open movies under Creative Commons Attribution:
[Sintel](https://durian.blender.org/) (CC BY 3.0, with its official Japanese subtitles),
[Big Buck Bunny](https://peach.blender.org/) (CC BY 3.0),
[Elephants Dream](https://orange.blender.org/) (CC BY 2.5),
[Tears of Steel](https://mango.blender.org/) (CC BY 3.0) and
[Caminandes: Gran Dillama](https://commons.wikimedia.org/wiki/File:Caminandes_-_Gran_Dillama_-_Blender_Foundation%27s_new_Open_Movie.webm)
(CC BY), all © Blender Foundation. The dictionary entries in them are from
[Jitendex](https://jitendex.org/) (CC BY-SA 4.0) and JMdict, with [JPDB](https://jpdb.io/)
frequencies. The Android recording shows
[Build your first WebAuthn app](https://www.youtube.com/watch?v=8ren54IMSf4) by Eiji Kitamura
for Chrome for Developers (CC BY 3.0), with its own Japanese and English subtitles.

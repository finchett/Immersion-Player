# How it works

## Dictionaries

- Mizune imports Yomitan and Rikaitan zips: term banks, frequency lists and pitch accent.
  Structured content is rendered as it is, not flattened to text.
- JMdict is bundled and installs on first launch. Jitendex, JPDB frequency and JMnedict are
  offered during setup and downloaded from where their makers publish them. Everything is stored
  in SQLite and searched on the device.
- A lookup takes the longest match at the point you touched and undoes the conjugation:
  食べさせられなかった → 食べる, with the steps shown in the entry.
- Each new line is looked up automatically. That's the first word with kanji, skipping speaker
  names and bracketed readings, trying the next one until something is found.

## Anki cards

Off by default. Turn it on under Settings › Anki or during setup. Tap or click the round **+** on
a dictionary entry (or press **a** on desktop) and the word goes to Anki with:

- the line, with the word in bold, and the peek-language line as its translation
- the word, its reading as Anki furigana, pitch accent graphs, and a short definition ("first
  day, opening day or premiere"); the full definition is an option
- the line's audio, with 0.3 s either side, as mono Opus (about 4 KB a second)
- the line as an animated AVIF at 360 px, or a still of the frame on screen
- the episode and time

The **+** then becomes a **−**, which removes the card. A card whose first field is already in
the deck is refused before anything is cut.

- **Desktop** adds cards through [AnkiConnect](https://ankiweb.net/shared/info/2055492159), so
  Anki has to be open. A separate headless mpv cuts the media from the file, so playback isn't
  interrupted.
- **Android** adds cards straight into AnkiDroid, which doesn't need to be open. The app asks for
  AnkiDroid's permission when cards are turned on. A second, headless libmpv (through JNA, since
  the player's JNI holds the first) decodes the line's frames, and the phone's AV1 encoder makes
  the AVIF: MediaMuxer writes an MP4, which is relabelled as an AVIF sequence. Phones without AV1
  get an animated WebP. The audio is cut with Android's own codecs.

Any note type works: pick it, then choose what fills each field. mpvacious's *Japanese
sentences* note type (often kept as *Japanese sentences+*) is filled in field by field. Get it
with its [example deck on AnkiWeb](https://ankiweb.net/shared/info/1557722832) or from
[Ajatt-Tools](https://github.com/Ajatt-Tools/AnkiNoteTypes/tree/main/templates/Japanese%20sentences).
Other note types are matched by their field names.

## Playback

- Android uses libmpv from [mpv-android](https://github.com/mpv-android/mpv-android). A lot of
  what people play is 10-bit H.264, which Android's hardware decoders can't decode and its
  software decoder refuses, so a `MediaCodec` player only shows a black rectangle.
- Desktop uses the same libmpv. mpv's GPU renderer runs in an offscreen OpenGL context (CGL on
  macOS, WGL on Windows, EGL on Linux), and each frame is read back into the Compose window.
  - If OpenGL can't be set up, mpv's software renderer draws into the window instead (about 5 ms
    per 1080p frame on Apple Silicon).
  - OpenGL emulated on the CPU (Mesa's llvmpipe and the like) is slower than mpv's software
    renderer, and drew seams on llvmpipe, so it's skipped.
  - If OpenGL is set up but its frames come back blank, the player switches to software
    mid-playback. It checks the first frames' alpha, since mpv's frames are opaque.
  - `-Prenderer=sw` forces the software renderer.
- [Anime4K](https://github.com/bloc97/Anime4K) v4 shaders are bundled. Choose Off, Auto, Fast or
  Best under Settings › Appearance › Shaders, or from the shader button on the player. They need
  the GPU renderer, so they're unavailable when the app falls back to software.
  - Fast runs Anime4K's smaller networks, and Best its largest (its own HQ setup).
  - Auto goes by the GPU's name as its OpenGL driver reports it. For NVIDIA, AMD and Intel it
    follows Anime4K's guidance: Best from about a GTX 1080, RTX 2060, RX 590, Vega 56, RX 5600
    or 6600, or Arc A580, and Fast below that and for integrated graphics. Apple GPUs are judged
    by core count against about 12 TFLOPS, so the larger Max chips (M2 Max 38-core and up) and
    the Ultras get Best. Anything it doesn't recognise gets Fast.
  - The mode follows the video's resolution, as Anime4K suggests: A for 1080p, B for 720p, C for
    480p. Any of its six modes can be picked under Advanced, or with ⌘ + 1–6.
- Library thumbnails come from a second, headless mpv rather than a separate decoder.
- Subtitles are read from the container directly. Matroska is parsed in the app, including
  zlib-compressed tracks, and SRT, ASS, SSA and WebVTT are parsed. MP4, M4V and QuickTime files
  are read too, fragmented or not, for their timed-text (mov_text) tracks; a QuickTime chapter
  track is left out. A subtitle file next to the video takes priority over embedded tracks in
  its language, and the embedded tracks in other languages still load, so English inside the
  video can sit under Japanese from a file. The tracks are cached per file.
- Subtitle files don't have to be UTF-8. A byte-order mark, UTF-16 or an old code page (Arabic
  windows-1256, Shift_JIS, GBK, Big5, windows-1251 and others) is recognised. The file's language
  tag and the languages in settings narrow the guess, and the reading that gives that script's
  commonest letters wins.
- Picture subtitles (Blu-ray PGS) are read with
  [Tesseract](https://github.com/tesseract-ocr/tesseract), with English and Japanese models
  bundled. Only tracks in the study or peek language that no text track covers are read. Their
  timing comes from the file, so they can be used a second after the video opens. Each line's
  text is read a few lines ahead of playback and the rest in the background, at about 50 ms a
  line on an M-series Mac. The text is kept, so a second viewing needs no OCR.
- Tracks, audio included, are picked by the languages in settings. A track chosen for one video
  (the cog in the player) overrides that for that video. A track's language tag is checked
  against its text: each line counts towards a language by its script, so a Japanese track
  tagged `eng` is still found, an English track with Japanese song lyrics stays English, and a
  track tagged `und` is judged by its text alone.

## Updates

Both apps check the latest release's `update.json` at launch and every six hours. When there's
a new version they download it in the background, check it against the feed's signature and
SHA-256, and offer **What's new**.

- **macOS** copies the new app out of the `.dmg` and swaps it in when the app quits, or straight
  away with **Restart now**. This only works where the app can write to its own folder, as in
  /Applications. Anywhere else it links to the release instead.
- **Android** installs through PackageInstaller. The first update asks for "Install unknown
  apps" and a confirmation. After that the app is its own installer, so on Android 12 and newer
  updates go in without asking when you leave the player.

## RedMagic shoulder triggers

On RedMagic phones the two capacitive shoulder triggers can step to the previous and next line.
It's off by default, and needs [Shizuku](https://shizuku.rikka.app/). Turn it on in the advanced
settings.

The triggers report `KEY_F7` and `KEY_F8`, but those events never reach an app. The sensors are
unpowered unless Nubia's game settings are on, and Nubia's game service takes the keys first. So
the app goes around the input stack. While a video is open, a Shizuku user service with shell
privileges turns the sensors on, sets the setting again every second because Nubia resets it,
and reads the device nodes with `getevent`. The settings are put back on exit.

Shizuku has to be started again after every reboot, but the app's permission stays. Nothing else
in the app uses it. The approach comes from [RedTrigger](https://github.com/zampierilucas/RedTrigger).

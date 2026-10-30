# Third-party software

Mizune's own code is under the [Mozilla Public License 2.0](LICENSE). The mpv and FFmpeg it
bundles are LGPL builds on every platform (Windows, macOS, Linux and Android), built without
their GPL parts. Everything else it bundles or downloads is listed here.

- **JMdict** (bundled): © Electronic Dictionary Research and Development Group, under
  [CC BY-SA 4.0](https://www.edrdg.org/edrdg/licence.html). Yomitan conversion by
  [rikaitan-import](https://github.com/Ajatt-Tools/rikaitan-import).
- **mpv-android** (`MPVLib.kt`, JNI glue): MIT, © Ilya Zhuravlev and sfan5; see
  `app/src/main/java/is/xyz/mpv/LICENSE`.
- **libmpv and FFmpeg** (Android, fetched by `scripts/fetch-libmpv.sh`): LGPL-2.1+, mpv-android's
  build run in CI without their GPL parts (`scripts/build-libmpv-lgpl-android.sh`: FFmpeg without
  `--enable-gpl`, `--enable-version3` or mbedtls, mpv with `-Dgpl=false`).
- **libmpv and FFmpeg** (macOS): LGPL-2.1+, built in CI without their GPL parts
  (`desktop/scripts/build-libmpv-lgpl-macos.sh`; mpv with `-Dgpl=false`, which CI checks), with
  Homebrew's dav1d, libass, libplacebo and the libraries they use, under their own LGPL or more
  permissive licences. `release.sh --try` builds with Homebrew's GPL libmpv instead.
- **libmpv and FFmpeg** (Windows, from
  [zhongfly/mpv-winbuild](https://github.com/zhongfly/mpv-winbuild)): LGPL-2.1+, built without
  their GPL parts (`-Dgpl=false`, which CI checks).
- **libmpv, FFmpeg and libplacebo** (Linux AppImage): LGPL-2.1+, built in CI without their GPL
  parts (`desktop/scripts/build-libmpv-lgpl-linux.sh`; mpv with `-Dgpl=false`, which CI checks).
  Tesseract and the other libraries they load come from Ubuntu 22.04's packages, gathered by
  [linuxdeploy](https://github.com/linuxdeploy/linuxdeploy), under their own licences. The Linux
  `.deb` uses the system's own libmpv.
- **Tesseract on Windows** (from [UB Mannheim's build](https://github.com/UB-Mannheim/tesseract)):
  Apache-2.0, with the libraries it loads: Leptonica (BSD-2-Clause), libarchive (BSD), OpenSSL's
  libcrypto (Apache-2.0), and the zlib, bzip2, xz, zstd, lz4, libjpeg, libpng, libtiff, libwebp,
  giflib, OpenJPEG, JBIG-KIT, LERC, libdeflate, libb2, expat and libiconv libraries under their
  own permissive or LGPL licences, and MinGW's runtime (libgcc, libstdc++, winpthreads).
- **Tesseract** and its `eng`/`jpn` models from
  [tessdata_best](https://github.com/tesseract-ocr/tessdata_best) (bundled, for picture
  subtitles): Apache-2.0. On Android through
  [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android), Apache-2.0; its
  Leptonica is BSD-2-Clause.
- **Compose Multiplatform, JNA, sqlite-jdbc** (desktop): Apache-2.0 / Apache-2.0 or LGPL-2.1 /
  Apache-2.0.
- **Anime4K** shaders (bundled, desktop): MIT, © bloc97; the AutoDownscalePre ones are public
  domain. See `desktop/src/main/resources/anime4k/LICENSE`.
- **Shizuku API** (optional, for the RedMagic shoulder triggers): Apache-2.0.

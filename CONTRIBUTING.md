# Contributing

Bug reports, ideas and fixes are all welcome.

## Issues

Report a bug or suggest something by [opening an issue](https://github.com/finchett/Mizune/issues).

For a bug, say:
- what you did, what happened and what you expected
- your platform and Mizune's version (it's in Settings)
- for subtitle problems, where the subtitles come from: a file next to the video or a track
  inside it, and its format and language. `ffprobe` output for the video helps more than the
  video itself.

For an idea, say what you want to do and why. The why helps more than a suggested design.

## Pull requests

Small fixes, such as a typo or an obvious bug, can go straight to a pull request. For anything
bigger, open an issue first, so we can agree on the change before you spend time on it. That
includes features, changes to how the app looks or behaves, and new dependencies.

In the pull request:
- keep to one change, on its own branch
- say what changed, why, and how you tested it
- link the issue it fixes with `Closes #N`
- run the tests ([docs/development.md](docs/development.md#tests) says how). The *Desktop builds*
  checks must pass too: they build the app and check that it plays video on Windows, Linux and
  macOS, but don't run the tests.

Write code the way the surrounding code is written, and comment the reasons rather than the
steps. Building, running and testing are in [docs/development.md](docs/development.md), and how
the main parts work is in [docs/how-it-works.md](docs/how-it-works.md).

## AI tools

You can use AI tools to write a contribution. It's reviewed the same way as any other.

Mizune itself is written with the help of AI tools. Every change is read line by line and tested
by hand before it's merged.

## Licence

Contributions are under the [Mozilla Public License 2.0](LICENSE), the same as the rest of
Mizune's code. Mizune ships without GPL code (its mpv and FFmpeg are LGPL builds), so new
dependencies need licences that allow that. [THIRD-PARTY.md](THIRD-PARTY.md) lists what's
bundled now.

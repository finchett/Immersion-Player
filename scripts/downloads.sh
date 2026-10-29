#!/bin/sh
# How many times each release's files have been downloaded from GitHub, newest release first, with
# a total for each. GitHub only gives these through its API, not on the release page. Leaves out
# update.json and its signature, which installed apps fetch every few hours, and the Android
# libmpv build, which isn't an app release. Needs gh, signed in.
set -eu
gh api repos/finchett/Mizune/releases --paginate --jq '
  .[] | select(.draft | not) | select(.tag_name | startswith("v")) |
  [.assets[] | select(.name | test("^update\\.json") | not)] as $files |
  "\n\(.tag_name)  \($files | map(.download_count) | add // 0) downloads",
  ($files[] | "  \(.download_count)\t\(.name)")'

#!/bin/bash
set -euo pipefail
IOS_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GUHEALTH_TARGET="${1:-simulator}"
case "$GUHEALTH_TARGET" in
  simulator) GUHEALTH_DESTINATION='generic/platform=iOS Simulator' ;;
  device) GUHEALTH_DESTINATION='generic/platform=iOS' ;;
  *) echo 'Usage: bash ios/scripts/build-ios.sh [simulator|device]' >&2; exit 2 ;;
esac
xcodebuild -project "$IOS_ROOT/GuHealth.xcodeproj" -scheme GuHealth -configuration Debug \
  -destination "$GUHEALTH_DESTINATION" -derivedDataPath "$IOS_ROOT/build/$GUHEALTH_TARGET" \
  CODE_SIGNING_ALLOWED=NO build

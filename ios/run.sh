#!/usr/bin/env bash
set -euo pipefail

# Navigate to the iOS project root (this script lives in /ios)
IOS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${IOS_DIR}"

SCHEME="Neighborly"
PROJECT_PATH="Neighborly/Neighborly.xcodeproj"
# Use an existing simulator from your xcodebuild output (iPhone 16)
DESTINATION='platform=iOS Simulator,name=iPhone 16,OS=18.2'

echo "Building and running scheme '${SCHEME}' from '${PROJECT_PATH}'..."
echo "Destination: ${DESTINATION}"
echo

if command -v xcpretty >/dev/null 2>&1; then
  xcodebuild \
    -project "${PROJECT_PATH}" \
    -scheme "${SCHEME}" \
    -destination "${DESTINATION}" \
    clean build \
    | xcpretty
else
  echo "xcpretty not found; showing raw xcodebuild output."
  xcodebuild \
    -project "${PROJECT_PATH}" \
    -scheme "${SCHEME}" \
    -destination "${DESTINATION}" \
    clean build
fi

# If you just want to build & run (no tests), you can instead do:
# xcodebuild -project "${PROJECT_PATH}" -scheme "${SCHEME}" -destination "${DESTINATION}" clean build
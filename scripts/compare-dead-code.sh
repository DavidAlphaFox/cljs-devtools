#!/usr/bin/env bash

set -e

pushd `dirname "${BASH_SOURCE[0]}"` > /dev/null
source "./config.sh"

cd "$ROOT"

if [[ -z "${SKIP_DCE_COMPILATION}" ]]; then
  ./scripts/compile-dead-code.sh "$@"
fi

cd "$DCE_CACHE_DIR"

TMP_DIR=test/resources/.compiled/dead-code-compare

BUILD_WITH_DEBUG="$DCE_CACHE_DIR/with-debug.js"
BUILD_NO_DEBUG="$DCE_CACHE_DIR/no-debug.js"
BUILD_NO_MENTION="$DCE_CACHE_DIR/no-mention.js"
BUILD_NO_REQUIRE="$DCE_CACHE_DIR/no-require.js"
BUILD_NO_SOURCES="$DCE_CACHE_DIR/no-sources.js"

WITH_DEBUG_SIZE=$(stat -f %z "$BUILD_WITH_DEBUG")
NO_DEBUG_SIZE=$(stat -f %z "$BUILD_NO_DEBUG")
NO_MENTION_SIZE=$(stat -f %z "$BUILD_NO_MENTION")
NO_REQUIRE_SIZE=$(stat -f %z "$BUILD_NO_REQUIRE")
NO_SOURCES_SIZE=$(stat -f %z "$BUILD_NO_SOURCES")

echo
echo "stats:"
echo "WITH_DEBUG: $WITH_DEBUG_SIZE bytes"
echo "NO_DEBUG:   $NO_DEBUG_SIZE bytes"
echo "NO_MENTION: $NO_MENTION_SIZE bytes"
echo "NO_REQUIRE: $NO_REQUIRE_SIZE bytes"
echo "NO_SOURCES: $NO_SOURCES_SIZE bytes"
echo

if [ -d "$TMP_DIR" ] ; then
  rm -rf "$TMP_DIR"
fi

mkdir -p "$TMP_DIR"

cd "$TMP_DIR"

js-beautify -f "$BUILD_WITH_DEBUG" -o "with-debug.js"
js-beautify -f "$BUILD_NO_DEBUG" -o "no-debug.js"
js-beautify -f "$BUILD_NO_MENTION" -o "no-mention.js"
js-beautify -f "$BUILD_NO_REQUIRE" -o "no-require.js"
js-beautify -f "$BUILD_NO_SOURCES" -o "no-sources.js"

echo
echo "beautified sources in $TMP_DIR"
echo

echo "see https://github.com/binaryage/cljs-devtools/issues/37"

popd

#!/usr/bin/env bash

set -e -o pipefail

# shellcheck source=_config.sh
source "$(dirname "${BASH_SOURCE[0]}")/_config.sh"

cd "$ROOT"

./scripts/check-versions.sh

LEIN_VERSION=$(read_lein_version "$PROJECT_FILE")

JAR_FILE="target/devtools-$LEIN_VERSION.jar"

echo "listing content of $JAR_FILE"
echo ""

unzip -l "$JAR_FILE"

echo ""
echo "----------------------------"
echo ""

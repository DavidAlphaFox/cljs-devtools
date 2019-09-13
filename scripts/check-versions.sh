#!/usr/bin/env bash

# checks if all version strings are consistent

set -e -o pipefail

# shellcheck source=_config.sh
source "$(dirname "${BASH_SOURCE[0]}")/_config.sh"

cd "$ROOT"

LEIN_VERSION=$(read_lein_version "$PROJECT_FILE")

# same version must be in src/version.clj

PROJECT_VERSION=$(read_project_version "$PROJECT_VERSION_FILE")
if [ -z "$PROJECT_VERSION" ] ; then
  echo "Unable to retrieve 'current-version' string from '$PROJECT_VERSION_FILE'"
  popd
  exit 1
fi

if [ ! "$LEIN_VERSION" = "$PROJECT_VERSION" ] ; then
  echo "Lein's project.clj version differs from version in '$PROJECT_VERSION_FILE': '$LEIN_VERSION' != '$PROJECT_VERSION'"
  popd
  exit 2
fi

echo "All version strings are consistent: '$LEIN_VERSION'"

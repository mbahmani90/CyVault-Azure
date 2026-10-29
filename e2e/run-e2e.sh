#!/usr/bin/env bash
# Runs the e2e tests. Needs a running emulator and Appium server (`appium`).
# The test account comes from e2e/.env (git-ignored) unless E2E_EMAIL/E2E_PASSWORD are already set,
# as they are on CI.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ -f e2e/.env ]; then
    set -a
    . e2e/.env
    set +a
fi

# Gradle needs a JDK; fall back to the one bundled with Android Studio.
if [ -z "${JAVA_HOME:-}" ] && [ -d "/c/Program Files/Android/Android Studio/jbr" ]; then
    export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
fi

./gradlew :e2e:e2eTest "$@"

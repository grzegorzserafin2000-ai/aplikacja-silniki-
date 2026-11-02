#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
classes=$(mktemp -d /tmp/climagold-tests.XXXXXX)
trap 'rm -rf "$classes"' EXIT
compiler=javac
runner=java
if [ -n "${JAVA_HOME:-}" ]; then
  compiler="$JAVA_HOME/bin/javac"
  runner="$JAVA_HOME/bin/java"
fi
sources=app/src/main/java/pl/cg/ziehlconfig
"$compiler" -d "$classes" "$sources/AddressInput.java" "$sources/GuidedSession.java" "$sources/ModbusRtu.java" "$sources/RtuSession.java" "$sources/CommunicationProgrammer.java" tests/AddressInputTest.java tests/CommunicationTest.java tests/GuidedSessionTest.java
"$runner" -cp "$classes" AddressInputTest
"$runner" -cp "$classes" pl.cg.ziehlconfig.CommunicationTest

"$runner" -cp "$classes" GuidedSessionTest

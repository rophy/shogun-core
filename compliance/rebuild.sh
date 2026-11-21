#!/usr/bin/env bash
# Rebuild shogun-core-main and the compliance webapp, then restart the shogun container.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
mvn() { mise exec java@temurin-8 maven@3 -- mvn "$@"; }
(cd "$here/../src" && mvn -B -q -pl shogun-core-main -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true install)
(cd "$here/webapp" && mvn -B -q -DskipTests -Djetty.skip=true package)
docker compose -p shogun-compliance -f "$here/compose.yml" restart shogun
until curl -sf -u user:shogun -o /dev/null http://127.0.0.1:18081/index.html; do sleep 3; done
echo "shogun ready"

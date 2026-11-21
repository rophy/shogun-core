#!/usr/bin/env bash
# Run one TEAM Engine suite against a capabilities URL and save the CTL log.
# Usage: run-suite.sh <suite> <out.xml> <query-string>
set -euo pipefail
suite=$1 out=$2 query=$3
curl -sS -u ogctest:ogctest -H 'Accept: application/xml' --max-time 3600 \
  -o "$out" "http://127.0.0.1:18083/teamengine/rest/suites/${suite}/run?${query}"
ls -la "$out"

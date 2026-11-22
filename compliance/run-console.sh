#!/usr/bin/env bash
# Run a CTL test with the TEAM Engine console, for suites that the REST API of
# TEAM Engine does not offer (WCS 1.0: ets-wcs10#43; CSW 2.0.2). The image is
# the pinned TEAM Engine production image, which has the CTL scripts. The log
# files of the session are joined into one file for summarize.py.
# Usage: run-console.sh <script> <test qname> <out.xml> [@param=value ...]
#   e.g. run-console.sh csw/2.0.2/ctl/main.xml csw:Main results/csw202-direct.xml @capabilities.url=...
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
cd "$here"
IMAGE=${TE_CONSOLE_IMAGE:-ogccite/teamengine-production@sha256:e4ee158edbeee1561778b2cd4031225041a07d721c2efebb136b3b7bcc710b52}
script=$1 test=$2 out=$3
shift 3
logs=.run/console
rm -rf "$logs"
mkdir -p "$logs"
args=()
for p in "$@"; do args+=("$p"); done
docker run --rm -u 0 --network shogun-compliance_default -v "$here/$logs:/logs" --entrypoint bash "$IMAGE" -c '
  TB=/usr/local/tomcat/te_base
  mkdir -p $TB/users/console
  CP=/usr/local/tomcat/webapps/teamengine/WEB-INF/classes:$(ls /usr/local/tomcat/webapps/teamengine/WEB-INF/lib/*.jar /usr/local/tomcat/lib/*.jar | tr "\n" ":")
  for d in $TB/scripts/*/*/resources; do CP=$CP:$d; done
  java -Xmx1g -DTE_BASE=$TB -cp "$CP" -Djava.protocol.handler.pkgs=com.occamlab.te.util.protocols com.occamlab.te.Test \
    -source=$TB/scripts/'"$script"' -test='"$test"' -logdir=$TB/users/console -session=s0001 "$@" \
    >/logs/console.txt 2>&1 || true
  # TEAM Engine accepts log dirs in TE_BASE only
  cp -r $TB/users/console/s0001 /logs/ 2>/dev/null || true
  chmod -R a+rX /logs' bash "${args[@]}"
python3 - "$logs/s0001" "$out" <<'PY'
import os, sys
import xml.etree.ElementTree as ET
root = ET.Element("execution")
for dirpath, dirs, files in sorted(os.walk(sys.argv[1])):
    dirs.sort()
    if "log.xml" in files:
        try:
            root.append(ET.parse(os.path.join(dirpath, "log.xml")).getroot())
        except ET.ParseError:
            pass
ET.ElementTree(root).write(sys.argv[2], encoding="utf-8", xml_declaration=True)
PY
ls -la "$out"

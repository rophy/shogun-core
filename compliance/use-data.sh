#!/usr/bin/env bash
# Start GeoServer with a fresh copy of one CITE data dir.
# Usage: use-data.sh <citewms-1.3|citewcs-1.1|wfs20/citewfs-2.0>
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
src="$here/.cache/geoserver-cite/$1"
[ -d "$src" ] || { echo "no data dir $src, run fetch-data.sh" >&2; exit 1; }
cd "$here"
docker compose stop geoserver
if [[ "$1" == wfs20* ]]; then
  # fresh WFS 2.0 database (transactions of earlier runs change it)
  docker compose rm -sf postgres
  docker compose up -d --wait postgres
fi
rm -rf .run/geoserver_data
mkdir -p .run
cp -r "$src" .run/geoserver_data
# WPS: geometry input for the Execute GET request, and a URL check that lets
# GeoServer read it (external URL checks refuse all other URLs)
mkdir -p .run/geoserver_data/www
cp wps/point.wkt .run/geoserver_data/www/
mkdir -p .run/geoserver_data/security
cp wps/urlchecks.xml .run/geoserver_data/security/
docker compose up -d --wait geoserver

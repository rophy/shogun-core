#!/usr/bin/env bash
# Start GeoServer with a fresh copy of one CITE data dir.
# Usage: use-data.sh <data dir>, e.g. citewms-1.1, citewms-1.3, citewcs-1.0,
# citewcs-1.1, wfs10/citewfs-1.0, wfs11/citewfs-1.1, wfs20/citewfs-2.0
# GEOSERVER_EXTENSIONS: comma-separated GeoServer extensions (e.g. wps,csw).
# Only the pinned zips of fetch-extensions.sh are used.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
src="$here/.cache/geoserver-cite/$1"
[ -d "$src" ] || { echo "no data dir $src, run fetch-data.sh" >&2; exit 1; }
cd "$here"
docker compose stop geoserver
if [[ "$1" == wfs* ]]; then
  # fresh WFS database (transactions of earlier runs change it), made by the
  # init scripts of the data dir
  set=${1%%/*}
  declare -A SQL=([wfs10]=citewfs-1.0/cite_data_postgis2.sql [wfs11]=citewfs-1.1/dataset-sf0-postgis2.sql
    [wfs20]=citewfs-2.0/dataset-sf0-postgis2.sql)
  docker compose rm -sf postgres
  rm -rf .run/pginit
  mkdir -p .run/pginit
  cp ".cache/geoserver-cite/$set/01-init-user.sh" .run/pginit/
  cp ".cache/geoserver-cite/$set/${SQL[$set]}" .run/pginit/02-init-db.sql
  docker compose up -d --wait postgres
fi
rm -rf .run/geoserver_data .run/libs
mkdir -p .run/libs
cp -r "$src" .run/geoserver_data
# the image installs the zips in its ADDITIONAL_LIBS_DIR and downloads only
# missing ones; give it the pinned zips, and refuse unpinned extensions
for e in $(echo "${GEOSERVER_EXTENSIONS:-}" | tr ',' ' '); do
  z=geoserver-3.0.2-$e-plugin.zip
  grep -q " $z\$" extensions.sha256 || { echo "extension $e is not pinned in extensions.sha256" >&2; exit 1; }
  [ -f .cache/extensions/$z ] || ./fetch-extensions.sh >/dev/null
  cp .cache/extensions/$z .run/libs/
done
# WPS: geometry input for the Execute GET request, and a URL check that lets
# GeoServer read it (external URL checks refuse all other URLs)
mkdir -p .run/geoserver_data/www
cp wps/point.wkt .run/geoserver_data/www/
mkdir -p .run/geoserver_data/security
cp wps/urlchecks.xml .run/geoserver_data/security/
if [ "${OWS_SETUP:-false}" = true ]; then
  # test data of check-ows.py (see ows/setup.sh): a time mosaic of two
  # copies of the CITE tazdem GeoTIFF, and SLD files for SLD URLs
  mosaic=.run/geoserver_data/coverages/tazmosaic
  mkdir -p "$mosaic" .run/geoserver_data/www/styles
  for t in 20200101T000000Z 20210101T000000Z; do
    cp .cache/geoserver-cite/citewcs-1.1/coverages/tazdem.tiff "$mosaic/tazdem_$t.tif"
  done
  cp ows/indexer.properties ows/timeregex.properties "$mosaic/"
  cp ows/styles/*.sld .run/geoserver_data/www/styles/
fi
docker compose up -d --wait geoserver
if [ "${OWS_SETUP:-false}" = true ]; then
  ows/setup.sh >.run/ows-setup.log 2>&1 || { cat .run/ows-setup.log >&2; exit 1; }
fi

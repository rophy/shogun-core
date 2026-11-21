#!/usr/bin/env bash
# Download the GeoServer CITE data directories into .cache/.
# GeoServer is GPL-2.0, so the data is fetched at setup time and not committed.
set -euo pipefail
cd "$(dirname "$0")"

GEOSERVER_REF="${GEOSERVER_REF:-3.0.2}"
DEST=.cache/geoserver-cite
if [ -f "$DEST/.ref" ] && [ "$(cat "$DEST/.ref")" = "$GEOSERVER_REF" ]; then
  echo "CITE data for $GEOSERVER_REF already in $DEST"; exit 0
fi

rm -rf "$DEST" .cache/gs-src
git -c advice.detachedHead=false clone --quiet --depth 1 --branch "$GEOSERVER_REF" --filter=blob:none --sparse \
  https://github.com/geoserver/geoserver.git .cache/gs-src
git -C .cache/gs-src sparse-checkout set build/cite/wms13 build/cite/wcs11 build/cite/wfs20
mkdir -p "$DEST"
cp -r .cache/gs-src/build/cite/wms13/citewms-1.3 "$DEST/"
cp -r .cache/gs-src/build/cite/wcs11/citewcs-1.1 "$DEST/"
cp -r .cache/gs-src/build/cite/wfs20 "$DEST/wfs20"
echo "$GEOSERVER_REF" > "$DEST/.ref"
rm -rf .cache/gs-src
echo "CITE data for $GEOSERVER_REF in $DEST"

#!/usr/bin/env bash
# Download the pinned GeoServer 3.0.2 extensions into .cache/extensions/ and
# check them against extensions.sha256. GeoServer is GPL-2.0, so the files are
# fetched at setup time and not committed. use-data.sh gives them to the
# GeoServer image, so the image does not download (unpinned) extensions.
set -euo pipefail
cd "$(dirname "$0")"
BASE_URL="${GEOSERVER_EXTENSIONS_URL:-https://downloads.sourceforge.net/project/geoserver/GeoServer/3.0.2/extensions}"
mkdir -p .cache/extensions
while read -r sum file; do
  if [ ! -f ".cache/extensions/$file" ]; then
    curl -sfL -o ".cache/extensions/$file.part" "$BASE_URL/$file"
    mv ".cache/extensions/$file.part" ".cache/extensions/$file"
  fi
done < extensions.sha256
(cd .cache/extensions && sha256sum -c --quiet ../../extensions.sha256)
echo "GeoServer extensions in .cache/extensions (checksums OK)"

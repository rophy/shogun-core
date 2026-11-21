#!/usr/bin/env bash
# Run each OGC suite directly against GeoServer and through SHOGun-Core, and
# print the difference. Usage: run-all.sh [suite ...]
# Suites: wms13 wmts10 wcs20 wfs20 wps10 (default: all)
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
cd "$here"
. wps/params.sh

enc() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$1"; }

# suite -> data dir, GeoServer extensions, ETS image, TEAM Engine suite name
declare -A DATA=([wms13]=citewms-1.3 [wmts10]=citewms-1.3 [wcs20]=citewcs-1.1 [wfs20]=wfs20/citewfs-2.0 [wps10]=citewms-1.3)
declare -A EXT=([wps10]=wps)
# WFS 2.0: the TEAM Engine production image (ets-wfs20 1.40), as in GeoServer's
# own CITE build. ets-wfs20 1.41-1.43 fail the After/Before period tests (see README).
declare -A IMAGE=([wms13]=ets-wms13 [wmts10]=ets-wmts10 [wcs20]=ets-wcs20 [wfs20]=teamengine-production [wps10]=ets-wps10)
declare -A NAME=([wms13]=wms13 [wmts10]=wmts [wcs20]=wcs [wfs20]=wfs20 [wps10]=wps10)

# query string for a suite; $2 = direct|shogun
query() {
  local gs=http://geoserver:8080/geoserver sg=http://nginx/geoserver.action
  case "$1/$2" in
    wms13/direct) echo "capabilities-url=$(enc "$gs/cite/ows?SERVICE=WMS&REQUEST=GetCapabilities&VERSION=1.3.0")&updatesequence=auto&low-updatesequence=0&high-updatesequence=100&queryable=true&recommended=true" ;;
    wms13/shogun) echo "capabilities-url=$(enc "$sg/cite?SERVICE=WMS&REQUEST=GetCapabilities&VERSION=1.3.0")&updatesequence=auto&low-updatesequence=0&high-updatesequence=100&queryable=true&recommended=true" ;;
    # the global GWC endpoint, as in GeoServer's own CITE setup (see README)
    wmts10/direct) echo "capabilities-url=$(enc "$gs/gwc/service/wmts?SERVICE=WMTS&REQUEST=GetCapabilities")" ;;
    wmts10/shogun) echo "capabilities-url=$(enc "$sg/cite?SERVICE=WMTS&REQUEST=GetCapabilities")" ;;
    wcs20/*) local u; [ "$2" = direct ] && u="$gs/topp/ows" || u="$sg/topp"
      echo "url=$(enc "$u?SERVICE=WCS&REQUEST=GetCapabilities&ACCEPTVERSIONS=2.0.1")&core=core&ext_post=post&ext_scal=scaling&ext_int=interpolation&ext_rsub=range%20subsetting&ext_crs=crs" ;;
    wfs20/*) local u; [ "$2" = direct ] && u="$gs/sf/ows" || u="$sg/sf"
      echo "wfs=$(enc "$u?SERVICE=WFS&REQUEST=GetCapabilities&VERSION=2.0.0")" ;;
    wps10/direct) wps_query "$gs/cite/ows" ;;
    wps10/shogun) wps_query "$sg/cite" ;;
  esac
}

suites=("$@")
[ ${#suites[@]} -gt 0 ] || suites=(wms13 wmts10 wcs20 wfs20 wps10)
mkdir -p results
for s in "${suites[@]}"; do
  echo "=== $s"
  ETS_IMAGE=${IMAGE[$s]} docker compose up -d --wait teamengine >/dev/null 2>&1
  for target in direct shogun; do
    GEOSERVER_EXTENSIONS=${EXT[$s]:-} ./use-data.sh "${DATA[$s]}" >/dev/null 2>&1
    ./run-suite.sh "${NAME[$s]}" "results/$s-$target.xml" "$(query "$s" "$target")" >/dev/null
  done
  python3 summarize.py "results/$s-direct.xml" "results/$s-shogun.xml"
done

#!/usr/bin/env bash
# Run each OGC suite directly against GeoServer and through SHOGun-Core, and
# print the difference. Usage: run-all.sh [suite ...]
# Suites: wms11 wms13 wmts10 wcs10 wcs11 wcs20 wfs10 wfs11 wfs20 wps10 csw202 (default: all)
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
cd "$here"
. wps/params.sh

enc() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$1"; }

# suite -> data dir, GeoServer extensions, ETS image, TEAM Engine suite name
declare -A DATA=([wms11]=citewms-1.1 [wms13]=citewms-1.3 [wmts10]=citewms-1.3 [wcs10]=citewcs-1.0 [wcs11]=citewcs-1.1
  [wcs20]=citewcs-1.1 [wfs10]=wfs10/citewfs-1.0 [wfs11]=wfs11/citewfs-1.1 [wfs20]=wfs20/citewfs-2.0 [wps10]=citewms-1.3
  [csw202]=citewms-1.3)
declare -A EXT=([wcs10]=wcs1_0 [wcs11]=wcs1_1 [wps10]=wps [csw202]=csw,csw-iso)
# suites that TEAM Engine runs on the console only (see run-console.sh): script and test
declare -A CONSOLE=([wcs10]="wcs/1.0.0/ctl/wcs.xml wcs1-0-0:main" [csw202]="csw/2.0.2/ctl/main.xml csw:Main")
# WFS 2.0: the TEAM Engine production image (ets-wfs20 1.40), as in GeoServer's
# own CITE build. ets-wfs20 1.41-1.43 fail the After/Before period tests (see README).
declare -A IMAGE=([wms11]=ets-wms11 [wms13]=ets-wms13 [wmts10]=ets-wmts10 [wcs11]=ets-wcs11 [wcs20]=ets-wcs20
  [wfs10]=ets-wfs10 [wfs11]=ets-wfs11 [wfs20]=teamengine-production [wps10]=ets-wps10)
declare -A NAME=([wms11]=wms11 [wms13]=wms13 [wmts10]=wmts [wcs10]=wcs10 [wcs11]=wcs11 [wcs20]=wcs [wfs10]=wfs10 [wfs11]=wfs
  [wfs20]=wfs20 [wps10]=wps10)

# query string for a suite; $2 = direct|shogun
query() {
  local gs=http://geoserver:8080/geoserver sg=http://nginx/geoserver.action
  case "$1/$2" in
    # as GeoServer's own CITE build (build/cite/run-test.sh), on the cite workspace
    wms11/*) local u; [ "$2" = direct ] && u="$gs/cite/ows" || u="$sg/cite"
      echo "capabilities-url=$(enc "$u?SERVICE=WMS&REQUEST=GetCapabilities&VERSION=1.1.1")&updatesequence=auto&low-updatesequence=0&high-updatesequence=100&profile=queryable&recommended=true&getfeatureinfo=true&feesconstraints=true&bboxconstraints=EITHER" ;;
    wms13/direct) echo "capabilities-url=$(enc "$gs/cite/ows?SERVICE=WMS&REQUEST=GetCapabilities&VERSION=1.3.0")&updatesequence=auto&low-updatesequence=0&high-updatesequence=100&queryable=true&recommended=true" ;;
    wms13/shogun) echo "capabilities-url=$(enc "$sg/cite?SERVICE=WMS&REQUEST=GetCapabilities&VERSION=1.3.0")&updatesequence=auto&low-updatesequence=0&high-updatesequence=100&queryable=true&recommended=true" ;;
    # the global GWC endpoint, as in GeoServer's own CITE setup (see README)
    wmts10/direct) echo "capabilities-url=$(enc "$gs/gwc/service/wmts?SERVICE=WMTS&REQUEST=GetCapabilities")" ;;
    wmts10/shogun) echo "capabilities-url=$(enc "$sg/cite?SERVICE=WMTS&REQUEST=GetCapabilities")" ;;
    wcs20/*) local u; [ "$2" = direct ] && u="$gs/topp/ows" || u="$sg/topp"
      echo "url=$(enc "$u?SERVICE=WCS&REQUEST=GetCapabilities&ACCEPTVERSIONS=2.0.1")&core=core&ext_post=post&ext_scal=scaling&ext_int=interpolation&ext_rsub=range%20subsetting&ext_crs=crs" ;;
    # two namespaces (cdf, cgf): the global endpoint
    wfs10/*) local u; [ "$2" = direct ] && u="$gs/ows" || u="$sg/ows"
      echo "capabilities-url=$(enc "$u?SERVICE=WFS&REQUEST=GetCapabilities&VERSION=1.0.0")&multiplenamenspaces=true" ;;
    # ets-wfs11 runs over REST now (ets-wfs11#109 is closed); the form values of
    # GeoServer's CITE build (build/cite/wfs11/wfs-1.1.0.xml)
    wfs11/*) local u; [ "$2" = direct ] && u="$gs/sf/ows" || u="$sg/sf"
      echo "capabilities-url=$(enc "$u?SERVICE=WFS&REQUEST=GetCapabilities&VERSION=1.1.0")&wfs-transaction=Transaction&profile=sf-0" ;;
    wcs11/*) local u; [ "$2" = direct ] && u="$gs/topp/ows" || u="$sg/topp"
      echo "url=$(enc "$u?SERVICE=WCS&REQUEST=GetCapabilities&VERSION=1.1.1")" ;;
    wfs20/*) local u; [ "$2" = direct ] && u="$gs/sf/ows" || u="$sg/sf"
      echo "wfs=$(enc "$u?SERVICE=WFS&REQUEST=GetCapabilities&VERSION=2.0.0")" ;;
    wps10/direct) wps_query "$gs/cite/ows" ;;
    wps10/shogun) wps_query "$sg/cite" ;;
  esac
}

# console parameters (@name=value) of a suite; $2 = direct|shogun
console_params() {
  local gs=http://geoserver:8080/geoserver sg=http://nginx/geoserver.action u
  [ "$2" = direct ] && u="$gs/ows" || u="$sg/ows"
  case "$1" in
    # the form values of GeoServer's CITE build (build/cite/wcs10/wcs-1.0.0.xml), global endpoint
    wcs10) echo "@VAR_WCS_CAPABILITIES_URL=$u?service=wcs&version=1.0.0&request=GetCapabilities" \
      "@VAR_WCS_FORMAT_1_HEADER=image/tiff" "@VAR_HIGH_UPDATESEQUENCE=100" "@VAR_LOW_UPDATESEQUENCE=0" \
      "@VAR_WCS_COVERAGE_1_RESX=0.1" "@VAR_WCS_COVERAGE_1_RESY=0.1" "@xml=xml" "@haveparameter=haveparameter" \
      "@VAR_MULTIFILE_FORMATS=" ;;
    csw202) echo "@capabilities.url=$u?service=CSW&version=2.0.2&request=GetCapabilities" ;;
  esac
}

suites=("$@")
[ ${#suites[@]} -gt 0 ] || suites=(wms11 wms13 wmts10 wcs10 wcs11 wcs20 wfs10 wfs11 wfs20 wps10 csw202)
mkdir -p results
for s in "${suites[@]}"; do
  echo "=== $s"
  [ -n "${CONSOLE[$s]:-}" ] || ETS_IMAGE=${IMAGE[$s]} docker compose up -d --wait teamengine >/dev/null 2>&1
  # TARGETS=direct runs only the direct run (e.g. to set up a suite)
  for target in ${TARGETS:-direct shogun}; do
    GEOSERVER_EXTENSIONS=${EXT[$s]:-} ./use-data.sh "${DATA[$s]}" >/dev/null 2>&1
    if [ -n "${CONSOLE[$s]:-}" ]; then
      read -r script test <<<"${CONSOLE[$s]}"
      # the parameters have no spaces
      # shellcheck disable=SC2046
      ./run-console.sh "$script" "$test" "results/$s-$target.xml" $(console_params "$s" "$target") >/dev/null
    else
      ./run-suite.sh "${NAME[$s]}" "results/$s-$target.xml" "$(query "$s" "$target")" >/dev/null
    fi
  done
  if [ -n "${TARGETS:-}" ]; then
    for target in $TARGETS; do python3 summarize.py "results/$s-$target.xml"; done
  else
    python3 summarize.py "results/$s-direct.xml" "results/$s-shogun.xml"
  fi
done

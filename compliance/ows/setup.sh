#!/usr/bin/env bash
# Test data of check-ows.py on the citewms-1.3 data dir (see use-data.sh):
# - cite:tazmosaic: an ImageMosaic with a time dimension (two granules of the
#   CITE tazdem GeoTIFF), a WCS 2.0 EO dataset series and a GWC layer with
#   the time dimension (WMTS multi-dimensional).
# - WCS EO is enabled in the WCS settings.
# - www/styles/*.sld: SLD files for the WMS SLD URL checks.
# The files are copied by use-data.sh; this script makes the REST calls.
set -euo pipefail
GS=${GS:-http://127.0.0.1:18080/geoserver}
AUTH=admin:geoserver
rest() { curl -sf -u "$AUTH" -H 'Content-Type: application/xml' "$@"; }

# WCS 2.0 EO
rest -XPUT -d '<wcs><metadata><entry key="wcseo.enabled">true</entry></metadata></wcs>' \
  "$GS/rest/services/wcs/settings" >/dev/null

# time mosaic (files in data dir coverages/tazmosaic)
rest -XPOST -d '<coverageStore><name>tazmosaic</name><type>ImageMosaic</type><enabled>true</enabled>
  <workspace>cite</workspace><url>file:coverages/tazmosaic</url></coverageStore>' \
  "$GS/rest/workspaces/cite/coveragestores" >/dev/null
rest -XPOST -d '<coverage><name>tazmosaic</name><nativeName>tazmosaic</nativeName><enabled>true</enabled>
  <metadata>
    <entry key="time"><dimensionInfo><enabled>true</enabled><presentation>LIST</presentation>
      <units>ISO8601</units><defaultValue><strategy>MAXIMUM</strategy></defaultValue></dimensionInfo></entry>
    <entry key="wcseo.dataset">true</entry>
  </metadata></coverage>' \
  "$GS/rest/workspaces/cite/coveragestores/tazmosaic/coverages" >/dev/null

# GWC layer with the time dimension (WMTS multi-dimensional)
rest -XPUT -d '<GeoServerLayer><name>cite:tazmosaic</name><enabled>true</enabled>
  <mimeFormats><string>image/png</string></mimeFormats>
  <gridSubsets><gridSubset><gridSetName>EPSG:4326</gridSetName></gridSubset></gridSubsets>
  <parameterFilters><regexParameterFilter><key>TIME</key><defaultValue></defaultValue><regex>.*</regex></regexParameterFilter></parameterFilters>
  </GeoServerLayer>' "$GS/gwc/rest/layers/cite:tazmosaic.xml" >/dev/null

echo "ows test data ready"

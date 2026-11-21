# Query string for the WPS 1.0 suite. Usage: wps_query <base URL as seen by TEAM Engine>
# - iut is the bare endpoint: the ETS appends "?service=wps&..." to it.
# - The ETS URL-decodes execute_http_get_uri once or two times before it
#   sends it, so the URL must not contain encoded characters: the geometry is
#   a reference to www/point.wkt (see use-data.sh). The ETS validates the
#   response as an ExecuteResponse, so the output is a ResponseDocument.
wps_query() {
  local base=$1 enc
  enc() { python3 -c 'import sys,urllib.parse;print(urllib.parse.quote(sys.argv[1],safe=""))' "$1"; }
  local get="$base?service=WPS&version=1.0.0&request=Execute&identifier=JTS:buffer&DataInputs=geom=@mimetype=application/wkt@xlink:href=http://localhost:8080/geoserver/www/point.wkt;distance=10&ResponseDocument=result=@mimetype=application/wkt"
  echo "iut=$(enc "$base")&ics=1&execute_http_get_uri=$(enc "$get")&execute_request_raw_data_uri=$(enc file:///root/wps/execute-raw-data-output.xml)&execute_request_response_document_uri=$(enc file:///root/wps/execute-response-document.xml)&execute_request_updating_response_document_uri=$(enc file:///root/wps/execute-updating-response-document.xml)"
}

#!/usr/bin/env python3
"""Checks of the GeoServer interceptor of SHOGun-Core that no ETS covers:

- WMS style documents (SLD_BODY, SLD, STYLE_BODY, STYLE_URL, GetMap XML):
  each layer of the style needs READ, also without LAYERS.
- WPS: process permissions (WpsProcess entities), the layers of Execute
  input references, filtered capabilities and descriptions.
- CSW 2.0.2: records of readable layers only, with correct counts and
  paging; GetRecordById, GetRepositoryItem, DirectDownload, GetDomain;
  SHOGun-Core URLs in the responses.
- WMTS multi-dimensional (DescribeDomains, GetDomainValues, GetHistogram,
  GetFeature) and WCS 2.0 EO (DescribeEOCoverageSet, EO capabilities).
- The global endpoint (/geoserver.action/ows) and the removed W3DS service.

Usage: check-ows.py

Needs the compliance setup with the citewms-1.3 data, the extensions and the
test data of ows/setup.sh:
  GEOSERVER_EXTENSIONS=wps,csw,csw-iso,wcs2_0-eo,wmts-multi-dimensional OWS_SETUP=true \\
    ./use-data.sh citewms-1.3
Prints one line per check and exits with 1 if a check fails.

- "user" may read all compliance layers and run all processes. Its requests
  are compared with GeoServer direct where the result depends on GeoServer.
- "limited" may read cite:Lakes and cite:Forests (and some layers of other
  data dirs) and run JTS:* and gs:Bounds (see webapp compliance.properties).
"""
import base64
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

TOMCAT = "http://127.0.0.1:18081/geoserver.action"
GEOSERVER = "http://127.0.0.1:18080/geoserver"
# the GeoServer URL that SHOGun-Core uses (compose service name)
INTERNAL = "http://geoserver:8080/geoserver"
USER = "Basic " + base64.b64encode(b"user:shogun").decode()
LIMITED = "Basic " + base64.b64encode(b"limited:limited").decode()
ADMIN = "Basic " + base64.b64encode(b"admin:geoserver").decode()
GEOSERVER_URL = re.compile(r"https?://(geoserver:8080|localhost:18080|127\.0\.0\.1:18080)/geoserver/"
                           r"(?!schemas/|www/)")

NS = {
    "ows": "http://www.opengis.net/ows/1.1",
    "ows2": "http://www.opengis.net/ows/2.0",
    "wps": "http://www.opengis.net/wps/1.0.0",
    "csw": "http://www.opengis.net/cat/csw/2.0.2",
    "dc": "http://purl.org/dc/elements/1.1/",
    "gmd": "http://www.isotc211.org/2005/gmd",
    "gco": "http://www.isotc211.org/2005/gco",
    "wcs": "http://www.opengis.net/wcs/2.0",
    "wcseo": "http://www.opengis.net/wcseo/1.0",
    "wmts": "http://www.opengis.net/wmts/1.0",
    "md": "http://demo.geo-solutions.it/share/wmts-multidim/wmts_multi_dimensional.xsd",
}

results = []


def check(name, ok, detail=""):
    results.append(ok)
    print("{} {}{}".format("PASS" if ok else "FAIL", name, "" if ok or not detail else "  -- " + str(detail)[:400]))


class Reply:
    def __init__(self, status, headers, body):
        self.status = status
        self.headers = headers
        self.body = body

    @property
    def type(self):
        return self.headers.get("Content-Type", "")

    @property
    def text(self):
        return self.body.decode("utf-8", "replace")

    def xml(self):
        try:
            return ET.fromstring(self.body)
        except ET.ParseError:
            return None

    def is_image(self):
        return self.type.startswith("image/") and len(self.body) > 0

    def exception(self):
        """(code, text) of an OGC exception report, or None"""
        root = self.xml()
        if root is None:
            return None
        local = root.tag.split("}")[-1]
        if local not in ("ExceptionReport", "ServiceExceptionReport"):
            return None
        for e in root.iter():
            name = e.tag.split("}")[-1]
            if name == "Exception":
                text = "".join(t.text or "" for t in e.iter() if t.tag.split("}")[-1] == "ExceptionText")
                return e.get("exceptionCode"), text.strip()
            if name == "ServiceException":
                return e.get("code"), (e.text or "").strip()
        return local, ""


def http(url, auth=None, data=None, content_type=None):
    headers = {}
    if auth:
        headers["Authorization"] = auth
    if content_type:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            return Reply(response.status, response.headers, response.read())
    except urllib.error.HTTPError as e:
        return Reply(e.code, e.headers, e.read())


def kvp(base, params, auth=None):
    return http(base + "?" + urllib.parse.urlencode(params), auth)


def shogun(params, auth=USER, endpoint="cite"):
    return kvp(TOMCAT + "/" + endpoint, params, auth)


def direct(params, endpoint="cite"):
    return kvp(GEOSERVER + "/" + endpoint + "/ows", params, ADMIN)


def shogun_xml(body, auth=USER, endpoint="cite"):
    return http(TOMCAT + "/" + endpoint, auth, body.encode("utf-8"), "application/xml")


def direct_xml(body, endpoint="cite"):
    return http(GEOSERVER + "/" + endpoint + "/ows", ADMIN, body.encode("utf-8"), "application/xml")


def same_xml(a, b):
    """True if two XML replies are equal as canonical XML (namespace
    declarations aside), with GeoServer hosts made equal"""
    def canon(reply):
        text = re.sub(r"https?://(geoserver:8080|localhost:18080|127\.0\.0\.1:18080)/", "GS/", reply.text)
        text = re.sub(r"<\?xml[^>]*\?>", "", text)
        try:
            return ET.canonicalize(text, strip_text=True)
        except ET.ParseError:
            return None
    ca, cb = canon(a), canon(b)
    return ca is not None and ca == cb


def no_geoserver_urls(reply):
    match = GEOSERVER_URL.search(reply.text)
    return match is None, match.group(0) if match else ""


# ---------------------------------------------------------------- WMS / SLD

def sld(layer):
    return ("<StyledLayerDescriptor version='1.0.0' xmlns='http://www.opengis.net/sld'"
            " xmlns:ogc='http://www.opengis.net/ogc'><NamedLayer><Name>{}</Name><UserStyle><FeatureTypeStyle><Rule>"
            "<PolygonSymbolizer><Fill><CssParameter name='fill'>#ff0000</CssParameter></Fill>"
            "</PolygonSymbolizer></Rule></FeatureTypeStyle></UserStyle></NamedLayer>"
            "</StyledLayerDescriptor>").format(layer)


GETMAP = {"SERVICE": "WMS", "VERSION": "1.1.1", "REQUEST": "GetMap", "SRS": "EPSG:4326",
          "BBOX": "-0.0042,-0.0024,0.0042,0.0024", "WIDTH": "64", "HEIGHT": "64", "FORMAT": "image/png"}


def getmap_xml(layer):
    return ("<GetMap xmlns='http://www.opengis.net/ows' xmlns:sld='http://www.opengis.net/sld'"
            " xmlns:gml='http://www.opengis.net/gml' xmlns:ogc='http://www.opengis.net/ogc'"
            " version='1.1.1' service='WMS'>" + sld(layer).replace("<StyledLayerDescriptor", "<sld:StyledLayerDescriptor")
            .replace("</StyledLayerDescriptor>", "</sld:StyledLayerDescriptor>")
            .replace("xmlns='http://www.opengis.net/sld'", "xmlns='http://www.opengis.net/sld'") +
            "<BoundingBox srsName='http://www.opengis.net/gml/srs/epsg.xml#4326'>"
            "<gml:coord><gml:X>-0.0042</gml:X><gml:Y>-0.0024</gml:Y></gml:coord>"
            "<gml:coord><gml:X>0.0042</gml:X><gml:Y>0.0024</gml:Y></gml:coord></BoundingBox>"
            "<Output><Format>image/png</Format><Size><Width>64</Width><Height>64</Height></Size></Output>"
            "</GetMap>")


def check_sld():
    print("== WMS style documents (SLD)")
    readable, refused = "cite:Lakes", "cite:Ponds"
    # GeoServer draws the layers of the style document: this is the bypass
    # that SHOGun-Core must close
    d = direct(dict(GETMAP, SLD_BODY=sld(refused)))
    check("direct: GetMap with SLD_BODY and no LAYERS draws the SLD layer", d.is_image(), d.type)
    for param in ("SLD_BODY", "STYLE_BODY"):
        r = shogun(dict(GETMAP, **{param: sld(refused)}), LIMITED)
        check("limited: GetMap {} of a refused layer, no LAYERS: LayerNotDefined".format(param),
              (r.exception() or ("",))[0] == "LayerNotDefined", r.exception() or r.type)
        r = shogun(dict(GETMAP, **{param: sld(readable)}), LIMITED)
        check("limited: GetMap {} of a readable layer: image".format(param), r.is_image(), r.exception() or r.type)
    r = shogun(dict(GETMAP, LAYERS=readable, STYLES="", SLD_BODY=sld(refused)), LIMITED)
    check("limited: GetMap readable LAYERS with SLD_BODY of a refused layer: LayerNotDefined",
          (r.exception() or ("",))[0] == "LayerNotDefined", r.exception())
    # style URLs: GeoServer's www folder serves the SLD files (ows/styles)
    for param in ("SLD", "STYLE_URL"):
        r = shogun(dict(GETMAP, **{param: INTERNAL + "/www/styles/ponds.sld"}), LIMITED)
        check("limited: GetMap {} URL of a refused layer: LayerNotDefined".format(param),
              (r.exception() or ("",))[0] == "LayerNotDefined", r.exception() or r.type)
        r = shogun(dict(GETMAP, **{param: INTERNAL + "/www/styles/lakes.sld"}), LIMITED)
        check("limited: GetMap {} URL of a readable layer: image".format(param), r.is_image(), r.exception() or r.type)
    r = shogun(dict(GETMAP, SLD="http://169.254.169.254/latest/meta-data"), LIMITED)
    check("limited: GetMap SLD URL of a host that is not allowed: InvalidParameterValue",
          (r.exception() or ("",))[0] == "InvalidParameterValue", r.exception())
    r = shogun(dict(GETMAP, SLD="file:///etc/passwd"), LIMITED)
    check("limited: GetMap SLD file URL: InvalidParameterValue",
          (r.exception() or ("",))[0] == "InvalidParameterValue", r.exception())
    # GetFeatureInfo and GetLegendGraphic
    info = dict(GETMAP, REQUEST="GetFeatureInfo", LAYERS=readable, QUERY_LAYERS=readable, STYLES="",
                X="32", Y="32", INFO_FORMAT="text/plain", SLD_BODY=sld(refused))
    r = shogun(info, LIMITED)
    check("limited: GetFeatureInfo with SLD_BODY of a refused layer: LayerNotDefined",
          (r.exception() or ("",))[0] == "LayerNotDefined", r.exception())
    legend = {"SERVICE": "WMS", "VERSION": "1.1.1", "REQUEST": "GetLegendGraphic", "FORMAT": "image/png"}
    r = shogun(dict(legend, SLD_BODY=sld(refused)), LIMITED)
    check("limited: GetLegendGraphic with SLD_BODY of a refused layer: LayerNotDefined",
          (r.exception() or ("",))[0] == "LayerNotDefined", r.exception() or r.type)
    r = shogun(dict(legend, LAYER=readable, SLD_BODY=sld(readable)), LIMITED)
    check("limited: GetLegendGraphic with SLD_BODY of a readable layer: image", r.is_image(),
          r.exception() or r.type)
    # GetMap XML (POST) with an embedded StyledLayerDescriptor
    d = direct_xml(getmap_xml(refused))
    check("direct: GetMap XML with the SLD of a layer draws it", d.is_image(), d.exception() or d.type)
    r = shogun_xml(getmap_xml(refused), LIMITED)
    check("limited: GetMap XML with the SLD of a refused layer: LayerNotDefined",
          (r.exception() or ("",))[0] == "LayerNotDefined", r.exception() or r.type)
    r = shogun_xml(getmap_xml(readable), LIMITED)
    check("limited: GetMap XML with the SLD of a readable layer: image", r.is_image(), r.exception() or r.type)
    r = shogun(dict(GETMAP, SLD_BODY="<StyledLayerDescriptor"), LIMITED)
    check("limited: GetMap with an invalid SLD_BODY: InvalidParameterValue",
          (r.exception() or ("",))[0] == "InvalidParameterValue", r.exception())


# ---------------------------------------------------------------- WPS

def process_ids(reply):
    root = reply.xml()
    if root is None:
        return None
    return sorted(e.text for e in root.iterfind(".//wps:Process/ows:Identifier", NS))


def execute(process, inputs):
    return ("<wps:Execute service='WPS' version='1.0.0' xmlns:wps='http://www.opengis.net/wps/1.0.0'"
            " xmlns:ows='http://www.opengis.net/ows/1.1' xmlns:xlink='http://www.w3.org/1999/xlink'"
            " xmlns:wfs='http://www.opengis.net/wfs' xmlns:ogc='http://www.opengis.net/ogc'>"
            "<ows:Identifier>{}</ows:Identifier><wps:DataInputs>{}</wps:DataInputs>"
            "<wps:ResponseForm><wps:RawDataOutput><ows:Identifier>{}</ows:Identifier></wps:RawDataOutput>"
            "</wps:ResponseForm></wps:Execute>").format(process, inputs, "bounds" if process == "gs:Bounds"
                                                       else "result")


def wfs_input(name, layer):
    return ("<wps:Input><ows:Identifier>{}</ows:Identifier><wps:Reference mimeType='text/xml'"
            " xlink:href='http://geoserver/wfs' method='POST'><wps:Body><wfs:GetFeature service='WFS'"
            " version='1.0.0' outputFormat='GML2'><wfs:Query typeName='{}'/></wfs:GetFeature></wps:Body>"
            "</wps:Reference></wps:Input>").format(name, layer)


def check_wps():
    print("== WPS process permissions")
    caps = {"SERVICE": "WPS", "VERSION": "1.0.0", "REQUEST": "GetCapabilities"}
    all_ids = process_ids(direct(caps))
    user_ids = process_ids(shogun(caps))
    limited_ids = process_ids(shogun(caps, LIMITED))
    check("user: the capabilities list all processes of GeoServer", all_ids and user_ids == all_ids,
          "{} vs {}".format(len(user_ids or []), len(all_ids or [])))
    expected = sorted(p for p in (all_ids or []) if p.startswith("JTS:") or p == "gs:Bounds")
    check("limited: the capabilities list JTS:* and gs:Bounds only", limited_ids == expected and len(expected) > 1,
          (limited_ids or [])[:5])
    check("limited: the capabilities have SHOGun-Core URLs", *no_geoserver_urls(shogun(caps, LIMITED)))
    describe = {"SERVICE": "WPS", "VERSION": "1.0.0", "REQUEST": "DescribeProcess"}
    d = direct(dict(describe, IDENTIFIER="gs:NoSuchProcess"))
    r = shogun(dict(describe, IDENTIFIER="gs:Reproject"), LIMITED)
    check("limited: DescribeProcess of a refused process gives the exception of an unknown one",
          r.exception() is not None and d.exception() is not None and r.exception()[0] == d.exception()[0]
          and r.exception()[1].startswith("No such process") and d.exception()[1].startswith("No such process")
          and r.status == d.status, "{} {} / direct {} {}".format(r.status, r.exception(), d.status, d.exception()))
    r = shogun(dict(describe, IDENTIFIER="JTS:buffer,gs:Bounds"), LIMITED)
    root = r.xml()
    check("limited: DescribeProcess of allowed processes", root is not None and
          len(root.findall(".//ProcessDescription")) == 2, r.exception() or r.text[:200])
    # GeoServer 3.0.2 has no IDENTIFIER=ALL: the same exception through SHOGun-Core
    d, r = direct(dict(describe, IDENTIFIER="ALL")), shogun(dict(describe, IDENTIFIER="ALL"), LIMITED)
    check("limited: DescribeProcess ALL gives the exception of GeoServer", r.exception() is not None
          and r.exception() == d.exception(), "{} / direct {}".format(r.exception(), d.exception()))
    # Execute
    execute_kvp = {"SERVICE": "WPS", "VERSION": "1.0.0", "REQUEST": "Execute"}
    r = shogun(dict(execute_kvp, IDENTIFIER="gs:Reproject"), LIMITED)
    check("limited: Execute (KVP) of a refused process: No such process",
          (r.exception() or ("", ""))[1].startswith("No such process"), r.exception())
    body = execute("gs:Bounds", wfs_input("features", "cite:Lakes"))
    d, r = direct_xml(body), shogun_xml(body, LIMITED)
    check("limited: Execute gs:Bounds of a readable layer (internal WFS reference) as direct",
          r.status == 200 and same_xml(r, d) and r.exception() is None, r.exception() or r.text[:200])
    r = shogun_xml(execute("gs:Bounds", wfs_input("features", "cite:Ponds")), LIMITED)
    check("limited: Execute with an internal WFS reference to a refused layer: refused",
          (r.exception() or ("", ""))[1].startswith("Unknown layer"), r.exception() or r.text[:200])
    href = INTERNAL + "/cite/ows?service=WFS&amp;version=1.0.0&amp;request=GetFeature&amp;typeName=cite:Ponds"
    r = shogun_xml(execute("gs:Bounds", "<wps:Input><ows:Identifier>features</ows:Identifier>"
                                        "<wps:Reference mimeType='text/xml' xlink:href='" + href + "'/></wps:Input>"),
                   LIMITED)
    check("limited: Execute with a GeoServer WFS URL of a refused layer: refused",
          (r.exception() or ("", ""))[1].startswith("Unknown layer"), r.exception() or r.text[:200])
    chained = execute("JTS:buffer", "<wps:Input><ows:Identifier>geom</ows:Identifier><wps:Reference"
                                    " mimeType='text/xml; subtype=gml/3.1.1' xlink:href='http://geoserver/wps'"
                                    " method='POST'><wps:Body>" + execute("gs:Reproject", "") + "</wps:Body>"
                                    "</wps:Reference></wps:Input><wps:Input><ows:Identifier>distance"
                                    "</ows:Identifier><wps:Data><wps:LiteralData>1</wps:LiteralData></wps:Data>"
                                    "</wps:Input>")
    r = shogun_xml(chained, LIMITED)
    check("limited: Execute of an allowed process that chains a refused one: No such process",
          (r.exception() or ("", ""))[1] == "No such process: gs:Reproject", r.exception() or r.text[:200])


# ---------------------------------------------------------------- CSW

CSW = {"SERVICE": "CSW", "VERSION": "2.0.2"}


def records(reply):
    root = reply.xml()
    if root is None:
        return None, None
    results = root.find(".//csw:SearchResults", NS)
    if results is None:
        return None, None
    ids = [e.text for e in results.iter("{http://purl.org/dc/elements/1.1/}identifier")]
    ids += [e.text for e in results.iterfind(".//gmd:fileIdentifier/gco:CharacterString", NS)]
    return ids, results.attrib


def check_csw():
    print("== CSW 2.0.2")
    get_records = dict(CSW, REQUEST="GetRecords", typeNames="csw:Record", resultType="results",
                       elementSetName="brief", maxRecords="100")
    d_ids, d_attrs = records(direct(get_records, "csw"))
    u_ids, u_attrs = records(shogun(get_records, USER, "ows"))
    check("user: GetRecords returns the records of GeoServer (all readable)", d_ids is not None and
          sorted(u_ids or []) == sorted([i for i in d_ids if i.startswith("cite:")]),
          "{} vs {}".format(len(u_ids or []), len(d_ids or [])))
    l_ids, l_attrs = records(shogun(get_records, LIMITED, "ows"))
    check("limited: GetRecords returns readable records only", sorted(l_ids or []) == ["cite:Forests", "cite:Lakes"],
          l_ids)
    check("limited: numberOfRecordsMatched/Returned count the readable records",
          l_attrs is not None and l_attrs.get("numberOfRecordsMatched") == "2"
          and l_attrs.get("numberOfRecordsReturned") == "2" and l_attrs.get("nextRecord") == "0", l_attrs)
    page = dict(get_records, maxRecords="1", startPosition="2")
    p_ids, p_attrs = records(shogun(page, LIMITED, "ows"))
    check("limited: GetRecords page 2 of size 1", p_ids is not None and len(p_ids) == 1 and p_ids[0] == sorted(l_ids)[1]
          if l_ids else False, (p_ids, p_attrs))
    p_ids, p_attrs = records(shogun(dict(page, startPosition="1"), LIMITED, "ows"))
    check("limited: GetRecords page 1 of size 1 has nextRecord 2",
          p_attrs is not None and p_attrs.get("nextRecord") == "2" and p_attrs.get("numberOfRecordsMatched") == "2",
          p_attrs)
    h_ids, h_attrs = records(shogun(dict(get_records, resultType="hits"), LIMITED, "ows"))
    check("limited: GetRecords hits counts the readable records",
          h_attrs is not None and h_attrs.get("numberOfRecordsMatched") == "2" and not h_ids, (h_ids, h_attrs))
    d_page_ids, d_page = records(direct(dict(get_records, maxRecords="3", startPosition="2"), "csw"))
    u_page_ids, u_page = records(shogun(dict(get_records, maxRecords="3", startPosition="2"), USER, "ows"))
    check("user: GetRecords paging as direct", d_page is not None and u_page_ids == d_page_ids
          and u_page.get("nextRecord") == d_page.get("nextRecord"), (u_page, d_page))
    post = ("<csw:GetRecords xmlns:csw='http://www.opengis.net/cat/csw/2.0.2' service='CSW' version='2.0.2'"
            " resultType='results' maxRecords='1' startPosition='1'><csw:Query typeNames='csw:Record'>"
            "<csw:ElementSetName>summary</csw:ElementSetName></csw:Query></csw:GetRecords>")
    x_ids, x_attrs = records(shogun_xml(post, LIMITED, "ows"))
    check("limited: GetRecords (XML POST) pages the readable records",
          x_attrs is not None and x_attrs.get("numberOfRecordsMatched") == "2" and len(x_ids) == 1, (x_ids, x_attrs))
    iso = dict(get_records, typeNames="gmd:MD_Metadata", outputSchema="http://www.isotc211.org/2005/gmd")
    i_ids, i_attrs = records(shogun(iso, LIMITED, "ows"))
    check("limited: GetRecords of ISO records returns readable records only",
          sorted(i_ids or []) == ["cite:Forests", "cite:Lakes"], (i_ids, i_attrs))
    full = shogun(dict(get_records, elementSetName="full"), LIMITED, "ows")
    check("limited: GetRecords full records have SHOGun-Core URLs", *no_geoserver_urls(full))
    by_id = dict(CSW, REQUEST="GetRecordById", elementSetName="full")
    r = shogun(dict(by_id, id="cite:Ponds"), LIMITED, "ows")
    root = r.xml()
    check("limited: GetRecordById of a refused record returns no record",
          root is not None and root.find(".//csw:Record", NS) is None and r.exception() is None, r.text[:200])
    r = shogun(dict(by_id, id="cite:Lakes"), LIMITED, "ows")
    root = r.xml()
    check("limited: GetRecordById of a readable record", root is not None and root.find(".//csw:Record", NS)
          is not None, r.text[:200])
    d = direct(dict(by_id, id="cite:Lakes"), "csw")
    u = shogun(dict(by_id, id="cite:Lakes"), USER, "ows")
    check("user: GetRecordById as direct (URLs aside)", GEOSERVER_URL.sub("X", d.text).replace(INTERNAL, "X")
          .count("<dc:") == u.text.count("<dc:"), "")
    item = dict(CSW, REQUEST="GetRepositoryItem")
    r = shogun(dict(item, id="cite:Ponds"), LIMITED, "ows")
    d = direct(dict(item, id="cite:NoSuchRecord"), "csw")
    check("limited: GetRepositoryItem of a refused record: HTTP 404 as for an unknown id",
          r.status == 404 and d.status == 404, "{} / direct {}".format(r.status, d.status))
    download = dict(CSW, REQUEST="DirectDownload")
    r = shogun(dict(download, resourceId="cite:Ponds"), LIMITED, "ows")
    check("limited: DirectDownload of a refused layer: InvalidParameterValue",
          (r.exception() or ("",))[0] == "InvalidParameterValue", r.exception())
    domain = dict(CSW, REQUEST="GetDomain")
    r = shogun(dict(domain, PropertyName="dc:title"), LIMITED, "ows")
    check("limited: GetDomain of a record property: refused", r.exception() is not None, r.text[:200])
    d = direct(dict(domain, PropertyName="dc:title"), "csw")
    u = shogun(dict(domain, PropertyName="dc:title"), USER, "ows")
    check("user (may read all records): GetDomain of a record property as direct",
          u.exception() is None and same_xml(u, d), u.exception() or u.text[:200])
    r = shogun(dict(domain, ParameterName="GetRecords.resultType"), LIMITED, "ows")
    check("limited: GetDomain of an operation parameter", r.exception() is None and r.status == 200, r.text[:200])
    harvest = ("<csw:Harvest xmlns:csw='http://www.opengis.net/cat/csw/2.0.2' service='CSW' version='2.0.2'>"
               "<csw:Source>http://example.com/csw</csw:Source><csw:ResourceType>"
               "http://www.opengis.net/cat/csw/2.0.2</csw:ResourceType></csw:Harvest>")
    r = shogun_xml(harvest, USER, "ows")
    check("user: Harvest is refused", (r.exception() or ("",))[0] == "OperationNotSupported", r.exception())
    caps = shogun(dict(CSW, REQUEST="GetCapabilities"), LIMITED, "ows")
    check("limited: the capabilities have SHOGun-Core URLs", *no_geoserver_urls(caps))
    r = shogun(dict(CSW, REQUEST="DescribeRecord"), LIMITED, "ows")
    check("limited: DescribeRecord", r.status == 200 and r.exception() is None, r.text[:200])


# ---------------------------------------------------------------- WMTS multi-dimensional

WMTS = {"SERVICE": "WMTS", "VERSION": "1.0.0", "LAYER": "cite:tazmosaic", "TILEMATRIXSET": "EPSG:4326"}


def check_wmts():
    print("== WMTS multi-dimensional")
    gwc = GEOSERVER + "/gwc/service/wmts"
    for operation, extra in (("DescribeDomains", {}), ("GetDomainValues", {"DOMAIN": "time"}),
                             ("GetHistogram", {"HISTOGRAM": "time", "RESOLUTION": "P1Y"}),
                             ("GetFeature", {"FORMAT": "application/gml+xml; version=3.1"})):
        params = dict(WMTS, REQUEST=operation, **extra)
        d = kvp(gwc, params, ADMIN)
        u = shogun(params)
        check("user: {} as direct".format(operation), d.status == 200 and u.status == 200 and u.body == d.body
              and d.exception() is None, "{} {} / direct {} {}".format(u.status, u.text[:150], d.status, d.text[:150]))
        r = shogun(params, LIMITED)
        check("limited: {} of a refused layer: InvalidParameterValue".format(operation),
              (r.exception() or ("",))[0] == "InvalidParameterValue", r.exception() or r.text[:150])
    params = dict(WMTS, REQUEST="DescribeDomains", LAYER="tazmosaic")
    check("user: DescribeDomains with the unqualified layer name of the endpoint", shogun(params).status == 200)
    caps = {"SERVICE": "WMTS", "REQUEST": "GetCapabilities"}
    u, l = shogun(caps), shogun(caps, LIMITED)
    check("user: the capabilities list the dimension layer and the extra operations",
          "tazmosaic" in u.text and "DescribeDomains" in u.text, u.text[:200])
    check("limited: the capabilities do not list the refused dimension layer", "tazmosaic" not in l.text, "")
    check("user: the capabilities have SHOGun-Core URLs", *no_geoserver_urls(u))


# ---------------------------------------------------------------- WCS 2.0 EO

WCS = {"SERVICE": "WCS", "VERSION": "2.0.1"}


def check_wcs_eo():
    print("== WCS 2.0 EO")
    caps = dict(WCS, REQUEST="GetCapabilities")
    d, u, l = direct(caps), shogun(caps), shogun(caps, LIMITED)
    dss = "cite__tazmosaic_dss"
    check("direct: the EO capabilities list the dataset series", dss in d.text, d.text[:200])
    check("user: the EO capabilities list the dataset series", dss in u.text, u.text[:200])
    check("limited: the EO capabilities do not list the refused dataset series", dss not in l.text
          and "tazmosaic" not in l.text, "")
    describe = dict(WCS, REQUEST="DescribeEOCoverageSet", eoId=dss)
    d, u = direct(describe), shogun(describe)
    check("user: DescribeEOCoverageSet as direct", d.status == 200 and u.status == 200 and
          GEOSERVER_URL.sub("", d.text).count("CoverageDescription") ==
          u.text.count("CoverageDescription") and d.exception() is None, u.text[:200])
    d = direct(dict(describe, eoId="cite__nosuch_dss"))
    r = shogun(describe, LIMITED)
    check("limited: DescribeEOCoverageSet of a refused series gives the exception of an unknown one",
          r.exception() is not None and d.exception() is not None and r.exception()[0] == d.exception()[0]
          and r.status == d.status, "{} {} / direct {} {}".format(r.status, r.exception(), d.status, d.exception()))
    root = shogun(describe).xml()
    granules = [e.text for e in root.iter("{http://www.opengis.net/wcs/2.0}CoverageId")] if root is not None else []
    check("user: the dataset series has granules", len(granules) > 0, granules)
    if granules:
        cov = dict(WCS, REQUEST="DescribeCoverage", CoverageId=granules[0])
        d, u, r = direct(cov), shogun(cov), shogun(cov, LIMITED)
        check("user: DescribeCoverage of a granule as direct", u.status == 200 and same_xml(u, d),
              u.exception() or u.text[:150])
        check("limited: DescribeCoverage of a granule of a refused coverage: NoSuchCoverage",
              (r.exception() or ("",))[0] == "NoSuchCoverage", r.exception())


# ---------------------------------------------------------------- other

def check_other():
    print("== global endpoint and W3DS")
    caps = {"SERVICE": "WMS", "VERSION": "1.3.0", "REQUEST": "GetCapabilities"}
    r = shogun(caps, LIMITED, "ows")
    root = r.xml()
    names = sorted(e.text for e in root.iter("{http://www.opengis.net/wms}Name")
                   if e.text and ":" in e.text) if root is not None else []
    check("limited: the global endpoint lists the readable layers with qualified names",
          "cite:Lakes" in names and "cite:Ponds" not in names, names)
    r = shogun(dict(GETMAP, LAYERS="cite:Lakes,cite:Forests", STYLES=","), LIMITED, "ows")
    check("limited: GetMap of two readable layers on the global endpoint", r.is_image(), r.exception())
    r = shogun(dict(GETMAP, LAYERS="Lakes", STYLES=""), LIMITED, "ows")
    check("limited: unqualified names on the global endpoint: LayerNotDefined",
          (r.exception() or ("",))[0] == "LayerNotDefined", r.exception())
    r = shogun({"SERVICE": "W3DS", "VERSION": "0.4.0", "REQUEST": "GetCapabilities"})
    check("W3DS is not a service of SHOGun-Core: InvalidParameterValue",
          (r.exception() or ("",))[0] == "InvalidParameterValue", r.exception())


def main():
    check_sld()
    check_wps()
    check_csw()
    check_wmts()
    check_wcs_eo()
    check_other()
    failed = results.count(False)
    print("\n{} checks: {} pass, {} fail".format(len(results), len(results) - failed, failed))
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()

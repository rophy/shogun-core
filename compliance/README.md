# OGC compliance tests for SHOGun-Core

This folder runs the OGC CITE test suites (TEAM Engine) against the GeoServer
interceptor of SHOGun-Core (`/geoserver.action`). Each suite runs two times:

1. **direct**: TEAM Engine → GeoServer.
2. **shogun**: TEAM Engine → nginx → SHOGun-Core → GeoServer.

A test that fails only in the second run is a gap in SHOGun-Core. GeoServer's
own failures are not SHOGun-Core gaps; they are shown for reference.

## Results

GeoServer 3.0.2, SHOGun-Core 5.3.14-SNAPSHOT (Java 8, Tomcat 9).

| Suite | ETS | direct | through SHOGun-Core | fail only through SHOGun-Core |
|---|---|---|---|---|
| WMS 1.1.1 | ets-wms11 1.23 | 135 pass | 135 pass | 0 |
| WMS 1.3.0 | ets-wms13 | 241 pass | 241 pass | 0 |
| WMTS 1.0.0 | ets-wmts10 1.11 | 35 pass, 8 fail, 14 skip | 45 pass, 0 fail, 17 skip | 0 |
| WCS 1.0.0 | ets-wcs10 1.16 (console, `teamengine-production`) | 47 pass, 5 fail, 1 inherited | 47 pass, 5 fail, 1 inherited | 0 |
| WCS 1.1.1 | ets-wcs11 1.18 | 150 pass | 150 pass | 0 |
| WCS 2.0.1 (core, post, scaling, interpolation, range subsetting, crs) | ets-wcs20 1.22 | 165 pass, 1 fail, 2 inherited | 165 pass, 1 fail, 2 inherited | 0 |
| WFS 1.0.0 | ets-wfs10 1.14 | 459 pass | 459 pass | 0 |
| WFS 1.1.0 (sf-0) | ets-wfs11 1.38 | 135 pass, 10 fail, 15 inherited | 135 pass, 10 fail, 15 inherited | 0 |
| WFS 2.0.0 | ets-wfs20 1.40 (`teamengine-production`) | 285 pass, 59 skip | 285 pass, 59 skip | 0 |
| WPS 1.0.0 (level 1) | ets-wps10 0.8 | 15 pass, 6 fail | 15 pass, 6 fail | 0 |
| CSW 2.0.2 | ets-csw202 1.18 (console, `teamengine-production`) | 46 pass, 30 fail, 13 inherited | 46 pass, 30 fail, 13 inherited | 0 |

`check-ows.py` (what no ETS covers, see [below](#check-owspy)): 75 checks,
75 pass.

All failures through SHOGun-Core also fail directly; see [Known failures](#known-failures).
WMTS through SHOGun-Core has no failures, because SHOGun-Core removes
GeoServer's `TileJSON` resource URLs (see below and
[Known failures](#known-failures)). Without that, the same
capabilities tests fail through SHOGun-Core as directly (checked: 36 pass,
7 fail).

Before the fixes (same setup): WMS 1.3.0 had 60 of 241 tests pass through
SHOGun-Core. For WCS and WPS, the capabilities documents sent clients directly
to GeoServer, so only the first request went through SHOGun-Core. For WMTS,
no KVP test ran. For WFS 2.0, the WFS 2.0 operations were refused.

Notes:

- The WFS 2.0 `LockFeatureTests` are not stable: `lockFeatureAndAttemptDelete`
  failed in 1 direct run and in 2 runs through SHOGun-Core (lock timing), with
  the same message. GeoServer's own CITE build retries WFS 2.0 up to 5 times
  for this reason. Run a suite again before you report a difference there.
- WFS 2.0 uses ets-wfs20 1.40 from the TEAM Engine production image, as
  GeoServer's own CITE build does. With the newest release (ets-wfs20 1.43:
  set `[wfs20]=ets-wfs20` in `IMAGE` of `run-all.sh`), 6 more tests fail in
  both runs (see below).
- WMTS: the direct run uses GeoServer's global GWC endpoint
  (`/geoserver/gwc/service/wmts`), as GeoServer's own CITE setup does. The
  workspace endpoint (`/geoserver/cite/gwc/service/wmts`) refuses
  `AcceptVersions=1.0.0` with `VersionNegotiationFailed` (GeoServer 3.0.2), so
  SHOGun-Core also uses the global endpoint (see below).

## Known failures

These tests fail directly against GeoServer (all but the WMTS ones also
through SHOGun-Core). The cause is in GeoServer or in the ETS, not in
SHOGun-Core. SHOGun-Core could only hide them by changing
GeoServer's answers (e.g. a false `CoverageSubtype`), so they are not fixed.

| Suite | Test | Cause |
|---|---|---|
| WCS 2.0 | `get-scal-req3-kvp` | ETS bug [ets-wcs20#141](https://github.com/opengeospatial/ets-wcs20/issues/141) (open): the test needs a `GridCoverage`, but one `GridCoverage` *or* `RectifiedGridCoverage` is enough. Fix PR #144 is not merged. GeoServer has rectified grids only. Also fails with ETS 1.20 (TEAM Engine production image). |
| WFS 2.0 (ets-wfs20 1.43 only) | `AfterTests.afterPeriod`, `BeforeTests.beforePeriod` (GET, POST, SOAP) | ETS regression after 1.40: `TimeUtils.periodAsGMLSubtractOneDay`/`AddOneDay` parse `Instant.toString()` with `yyyy-MM-dd'T'HH:mm:ss.SSSXX`, and `Instant.toString()` has no milliseconds if they are 0. The data (`2006-06-28T04:08:00Z`) is valid. With `ogccite/teamengine-production` (ets-wfs20 1.40, geomatics-geotk 1.16), all 285 tests pass, direct and through SHOGun-Core; GeoServer's own CITE build uses that image. ets-wfs20 1.43 has geomatics-geotk 1.18. No OGC issue found. |
| WMTS 1.0 (direct only) | 8 `Server.KVP.GET.GetCapabilities.*` tests | GeoServer writes `ResourceURL resourceType="TileJSON"` (28 times). The ETS validates against its copy of the WMTS 1.0.0 schema, which allows `tile` and `FeatureInfo` only; the schema now on schemas.opengis.net allows any string. Without the `TileJSON` elements, GeoServer's capabilities are valid against the ETS schema. SHOGun-Core removes them, so these tests pass through SHOGun-Core. |
| WPS 1.0 | `LanguageSelectionGetCapabilitiesValidation` (2 tests) | GeoServer advertises `en-US` but writes `xml:lang="en"` (`wps/GetCapabilities.java`, "TODO: make configurable"). |
| WPS 1.0 | `DescribeProcessResponseValidation` | ETS bug: the request has `&Identifier` + id without `=`. GeoServer then returns an empty `ProcessDescriptions`, which is not schema valid. |
| WPS 1.0 | `*HttpResponseStatusCodeValidation` (3 tests) | The ETS sends KVP requests with HTTP POST and an empty body, and expects HTTP 4xx/5xx with an exception report. GeoServer accepts the KVP request (HTTP 200), and sends its WPS exception reports with HTTP 200. |

| WFS 1.1 | 10 `ctl:SchematronValidatingParser` checks | The ETS requires `ows:ExceptionReport/@version = "1.2.0"`; GeoServer writes `1.0.0`. This is the error of [ets-wfs11#109](https://github.com/opengeospatial/ets-wfs11/issues/109) (REST runs only; the issue is closed, and the error still occurs with ets-wfs11 1.38). GeoServer's CITE build runs WFS 1.1 on the console for this reason (`build/cite/run-test.sh`: "Cannot run via REST API, see ets-wfs11#109"). |
| WCS 1.0 | `main-all`, 4 GetCoverage tests, `general_http_request_rules-6` | A `NullPointerException` in the ETS function `wcs:coords-from-envelope` stops the GetCoverage tests, so the suite runs 53 tests only. The same error occurs with GeoServer's own CITE TEAM Engine image (`geoserver-docker.osgeo.org/geoserver-cite:teamengine_latest`, 2021) against GeoServer direct; the log has the coverage CRS but no coverage name. Not fixed. |
| CSW 2.0.2 | 30 tests, 13 inherited | The ETS expects the CITE CSW sample records (e.g. `urn:uuid:94bc9c83-97f6-4b40-9eb8-a8e8787a5c63`); GeoServer's catalogue makes records of its layers only and cannot load them. Other failures are the OWS exception report version `1.2.0` (as WFS 1.1) and a spatial filter test that fails in the ETS (`Cannot convert string "6.9E-4" to xs:decimal`). GeoServer's CITE build has no CSW suite. |

GeoServer's own CITE build runs neither WCS 2.0 nor WPS 1.0. No ets-wps10
issues exist for the WPS causes above. The TEAM Engine production image has
no WPS 1.0 suite.

WPS test setup (`wps/params.sh`): the ETS appends `?service=wps&...` to `iut`,
so `iut` is the bare endpoint. It URL-decodes `execute_http_get_uri` one or
two times, so that URL has no encoded characters: the geometry is a
reference to `www/point.wkt`, which a GeoServer URL check
(`wps/urlchecks.xml`) allows. With the first setup, 5 more tests failed and 9
were skipped.

## Fixes in SHOGun-Core

All in `src/shogun-core-main`, Java 8 compatible. 805 unit tests passed
after the first round (29 new for the helpers below); after the second
round (below), 890 pass (`mvn install`: 0 checkstyle violations, 0 spotbugs
bugs).

General (`GeoServerInterceptorService`, `GeoServerInterceptorController`,
`MutableHttpServletRequest`, new `OgcException`):

- Errors are OGC exception reports (WMS `ServiceExceptionReport`, OWS 1.1 or
  2.0 `ExceptionReport`) with the HTTP status of the standard. Before, they
  were JSON with HTTP 200.
- WMS GetMap supports `EXCEPTIONS=INIMAGE` and `BLANK`.
- `SERVICE` is optional for WMS operations (e.g. GetMap), as on the GeoServer
  WMS endpoint, and for XML requests whose namespace names the service (see
  below).
- Unknown `REQUEST` values give `OperationNotSupported` (WMS) or
  `InvalidParameterValue` (OWS services).
- The endpoint of the URL path (`/geoserver.action/{endpoint}`) routes the
  request if the layer names do not name a known namespace (unqualified names).
- Repeated parameters (e.g. WCS 2.0 `SUBSET`) are sent as separate
  parameters, not joined with a comma.
- For XML POST requests, the body defines `SERVICE`, `REQUEST` and `VERSION`
  (as in GeoServer), also with query parameters on the URL. SOAP envelopes are
  supported. An invalid XML body falls back to the query parameters.
- A content type that Spring cannot parse (e.g.
  `application/vnd.ogc.gml/3.1.1`) does not cause HTTP 500.

Capabilities and URL rewriting (`secure/ProxyUrls`, `secure/CapabilitiesFilter`):

- `WmsResponseInterceptor` read `xlink:href` with the wrong local name, so
  every `OnlineResource` became the bare proxy URL (without
  `?SERVICE=WMS&`). That caused most WMS failures.
- Only GeoServer service URLs are rewritten. Metadata URLs (e.g.
  `/geoserver/www/...`) stay unchanged.
- The proxy URL keeps a non-default port and the first `X-Forwarded-Host`.
- Exception reports of GeoServer pass unchanged. If a document cannot be
  filtered, the client gets an exception, not the unfiltered document.

Per service:

- **WMS** (`secure/WmsRequestInterceptor`): each layer of `LAYERS`,
  `QUERY_LAYERS` and `LAYER` is checked (before, the whole comma-separated
  value was compared with one layer name). Refused or unknown layers give
  `LayerNotDefined`.
- **WMTS** (`secure/WmtsRequestInterceptor`, `secure/WmtsResponseInterceptor`):
  `OperationsMetadata` is kept (before, it was removed, so clients could not
  use KVP). KVP and RESTful requests
  (`/geoserver.action/{endpoint}/gwc/service/wmts/rest/...`) are checked and
  sent to the global GWC endpoint; the capabilities only list layers of the
  endpoint workspace. The old `/wmts/{layerId}/...` paths still work.
  `ResourceURL` elements other than `tile` and `FeatureInfo` (GeoServer's
  `TileJSON`) are removed: the WMTS 1.0.0 schema of the ETS does not allow
  them, and the interceptor does not support them.
- **WCS** (new `secure/WcsRequestInterceptor`, `secure/WcsResponseInterceptor`):
  before, there was no permission check and no URL rewrite. Now the
  capabilities list only readable coverages, with proxy URLs.
  `DescribeCoverage` and `GetCoverage` check each coverage (WCS 2.0 ids such as
  `topp__tazdem`, WCS 1.x identifiers, KVP and XML). A coverage is readable if
  the user may read a layer with an `ImageWmsLayerDataSource` of the same name
  (as for WFS).
- **WFS** (`secure/WfsRequestInterceptor`, `secure/WfsResponseInterceptor`):
  WFS 2.0 operations (`GetPropertyValue`, `GetFeatureWithLock`,
  `ListStoredQueries`, `DescribeStoredQueries`, `CreateStoredQuery`,
  `DropStoredQuery`) are supported (new default methods in the interceptor
  interfaces, so existing implementations still compile). Type names are read
  from lists, joins, XML attributes, inserts and replaces. Prefixes bound with
  `NAMESPACES` or `xmlns` are resolved in the endpoint workspace. Requests
  without type names (stored queries) are checked by the response: returned
  features need READ; locked features (`ResourceId/@rid`, GetFeatureWithLock
  results) need UPDATE. XML (also SOAP) and GeoJSON responses are checked;
  other formats are refused for such requests.
- **WPS** (new `secure/WpsResponseInterceptor`): URLs in capabilities and
  execute responses (`statusLocation`, output references) are rewritten.
  GeoServer's `GetExecutionStatus`, `GetExecutionResult` and `Dismiss` are
  supported.

Archetype (`src/shogun-core-webapp-archetype`):

- The generated pom depended on `shogun-core` (the parent pom), not
  `shogun-core-main`.
- Three classes had broken imports (`{package}.` instead of
  `import ${package}.`); two used `LOG` instead of the inherited `logger`.
- A Basic-auth security chain without CSRF for `/geoserver.action/**`. OGC
  clients cannot send CSRF tokens, so every POST (XML requests, WFS-T) got
  HTTP 403.

## Second round: all OGC Web Services of GeoServer 3.0.2

The goal of this round: close the gaps of the interceptor for every OGC Web
Service (not OGC API) that GeoServer 3.0.2 serves. All in
`src/shogun-core-main`, Java 8 and Spring 5.3.

| Service | GeoServer 3.0.2 | Before | Now |
|---|---|---|---|
| WMS 1.1.1 | core | not tested | ets-wms11, plus the fixes below |
| WFS 1.0.0, 1.1.0 | core | not tested | ets-wfs10, ets-wfs11 |
| WCS 1.0.0, 1.1.1 | extensions `wcs1_0`, `wcs1_1` | not tested | ets-wcs10 (console), ets-wcs11 |
| WMS style documents (SLD) | core | **not checked** (bypass, see below) | each layer of the style needs READ |
| WPS 1.0.0 | extension `wps` | no permission check | READ on a `WpsProcess` entity, input layers checked |
| CSW 2.0.2 (Dublin Core, ISO) | extensions `csw`, `csw-iso` | not supported | new CSW interceptors |
| WMTS multi-dimensional | extension `wmts-multi-dimensional` | not supported | DescribeDomains, GetDomainValues, GetHistogram, GetFeature |
| WCS 2.0 EO | extension `wcs2_0-eo` | not supported | DescribeEOCoverageSet, EO capabilities |
| W3DS | **no module** | enum values only | removed |

### Removed: W3DS

GeoServer 3.0.2 has no W3DS module: there is no `w3ds` folder in
`src/extension` or `src/community` of the 3.0.2 tag
(`gh api repos/geoserver/geoserver/contents/src/community?ref=3.0.2`). Its
`src/web/app/pom.xml` still has a Maven profile `w3ds` with the dependency
`gs-w3ds`, but that module is not in the tree. The W3DS
`ServiceType` and its operations `GetScene` and `GetLayerInfo` are removed from
`OgcEnum` and `OgcMessage`. A W3DS request now gets `InvalidParameterValue`
("service not supported"). Interceptor rules with service `W3DS` in a database
must be deleted before the update.

### WMS style documents (SLD)

GeoServer takes the layers of a request from the style document if LAYERS is
not given: `GetMap&SLD_BODY=<NamedLayer><Name>cite:Ponds</Name>...` draws
`cite:Ponds` (`GetMapKvpRequestReader.processSLDBody`). Before, the interceptor
checked only `LAYERS`, so a blank `LAYERS` skipped the check: any user could
draw any layer. Evidence before the fix: a unit test of
`secure/WmsRequestInterceptor` failed with "The request is not refused:
GeoServer would draw cite:Forests"; `check-ows.py` shows that GeoServer draws
the layer (`direct: GetMap with SLD_BODY and no LAYERS draws the SLD layer`).

Now (`secure/WmsRequestInterceptor`, new `secure/StyledLayers`,
`secure/StyleUrls`):

- GetMap, GetFeatureInfo and GetLegendGraphic check every layer that a style
  document names: `NamedLayer/Name`, `UserLayer/Name` (not inline features)
  and `FeatureTypeName`. Sources: `SLD_BODY`, `SLD` and GeoServer's aliases
  `STYLE_BODY` and `STYLE_URL`, and the `StyledLayerDescriptor` of a GetMap
  XML request. Refused or unknown layers give `LayerNotDefined`, also when
  `LAYERS` is blank. A style document that is not XML is refused
  (`InvalidParameterValue`).
- Style URLs: the interceptor reads the document (http/https, only the hosts
  of `geoserver.interceptor.styleUrlHosts`, no redirects, at most 1 MB),
  checks it and sends it to GeoServer as `SLD_BODY`/`STYLE_BODY`. So
  GeoServer uses the checked document, and clients cannot use the
  interceptor to read other URLs. No hosts are allowed by default.

### WPS process permissions

New entity `model/wps/WpsProcess` (identifier `JTS:buffer`, `JTS:*` or `*`)
with `WpsProcessDao`, `WpsProcessService`, `WpsProcessRestController`
(`/rest/wpsprocesses`) and `WpsProcessController`, as the existing WPS model.
`WpsProcessExecute` is not used for this: it describes one call of a process
for a client plugin (inputs, defaults), it can be the input of another call,
and one process can have many.

New `secure/WpsRequestInterceptor` (and new `secure/WpsRequests`):

- DescribeProcess and Execute need READ on a matching `WpsProcess`. A refused
  process gives GeoServer's exception of an unknown one (`No such process:
  ...`, `NoApplicableCode`, HTTP 200, as GeoServer). GeoServer 3.0.2 has no
  `IDENTIFIER=ALL`; it is an unknown process for both.
- Execute inputs that read GeoServer layers are checked as in WFS, WMS and
  WCS: internal references (`http://geoserver/wfs`, `/wcs`, `/wps`, which
  GeoServer resolves itself, `AbstractInputProvider`), references to the
  GeoServer URLs, KVP `DataInputs` (also with `%26`), and chained processes
  (their process and inputs). A GeoServer reference with `BodyReference`
  cannot be checked and is refused.
- `secure/WpsResponseInterceptor` lists only the allowed processes in the
  capabilities and DescribeProcess responses.

### CSW 2.0.2

New `CswRequestInterceptorInterface`, `CswResponseInterceptorInterface`,
`secure/CswRequestInterceptor`, `secure/CswResponseInterceptor`,
`secure/CswRecords` and `impl/CswRequestInterceptor` (credentials only);
`ServiceType.CSW` and its operations in `OgcEnum`.

- GeoServer makes one record per catalog resource; its identifier is the
  prefixed layer name (`Record.default.properties`,
  `MD_Metadata.default.properties`: `prefixedName`). A record is readable if
  the user may read the layer.
- GetRecords: the interceptor asks GeoServer for all matching records
  (`geoserver.interceptor.cswMaxRecords`, default 1000), removes the
  unreadable ones and makes the page of the client itself, so that
  `numberOfRecordsMatched`, `numberOfRecordsReturned` and `nextRecord` count
  the readable records (KVP and XML, `hits` and `results`). If GeoServer
  matches more records than the limit, the client gets an exception.
- GetRecordById returns readable records only. GetRepositoryItem (`id`) and
  DirectDownload (`resourceId`) need READ; a refused id gets GeoServer's
  answer for an unknown one (HTTP 404, and `InvalidParameterValue`).
- GetDomain of a record property (`PropertyName`) lists values of all
  records, so it needs READ on all records; GetDomain of an operation
  parameter is allowed. Harvest and Transaction are refused.
- All GeoServer service URLs and the GeoServer root URL (e.g.
  `ProviderSite`) in CSW responses point to the interceptor (the `/csw`
  service path is new in `ProxyUrls`).

### WMTS multi-dimensional and WCS 2.0 EO

- WMTS: DescribeDomains, GetDomainValues, GetHistogram and GetFeature read one
  layer (`LAYER`), so they get the check of GetTile (new default method
  `interceptMultiDimensional`). The capabilities list only readable layers.
- WCS 2.0 EO: DescribeEOCoverageSet checks each dataset series (`eoId`,
  GeoServer name `{coverageId}_dss`); a refused one gives GeoServer's
  `noSuchEODataset` with HTTP 404. Granule ids (`{coverageId}_granule_...`)
  need READ on their coverage. The EO capabilities list only readable
  `DatasetSeriesSummary` elements.

### Found by the new suites

- **Global endpoint** (new `OgcEndpoints`): `/geoserver.action/ows` uses the
  global GeoServer URL (`ows=...` in `geoServerNameSpaces.properties`) for
  layers of several workspaces (WFS 1.0: `cdf` and `cgf`). Names must be
  qualified there.
- **Exception reports** of GeoServer by version: WFS 1.0 and WCS 1.0 use the
  OGC `ServiceExceptionReport` 1.2.0 (WCS 1.0 with
  `application/vnd.ogc.se_xml`), WFS 1.1 and 1.x requests without a service
  use OWS 1.0 (not for `AcceptVersions`, the OWS 1.1 parameter of WMTS and
  WCS 1.1+), all with HTTP 200. For XML requests, the WCS namespace gives the
  version.
- **SERVICE**: GeoServer's `/ows` does not guess the service, so the
  interceptor guesses it only for WMS operations, and for XML requests from
  their namespace; then it sends `SERVICE` to GeoServer, as GeoServer's service
  paths (`/wfs`) of the capabilities do.
- WMS 1.1.1: the capabilities keep their `DOCTYPE`; external DTDs and
  entities are not loaded any more (`CapabilitiesFilter`). `REQUEST=map` (WMS
  1.0 name) is GetMap.
- WFS: the DescribeFeatureType filter of upstream removed complex types by
  name (`<layer>Type`), also the helper types of WFS 1.0 schemas
  (`AbstractSimpleFeatureType`, `PointFeatureType`), and its JSON branch
  checked `TileWmsLayerDataSource` and removed entries while it looped. Now
  only unreadable feature elements and their types are removed. WFS 1.1
  `NAMESPACE=xmlns(prefix=uri)` binds prefixes. The `Content-Type` of
  GeoServer is sent unchanged (e.g. `text/xml; subtype=gml/3.1.1`).
- **Permission tables** (`PersistentObject`): `USERPERMISSIONS` and
  `GROUPPERMISSIONS` hold the permissions of all entity types. With the new
  entity `WpsProcess`, Hibernate made a foreign key from `ENTITY_ID` to its
  table, and the layer permissions failed (`Referential integrity constraint
  violation ... WPSPROCESSES`). Before, there was no such key (checked on the
  base commit with the new test `PermissionTablesTest`). The key is now off
  for all types (`ConstraintMode.NO_CONSTRAINT`).

### Archetype

`src/shogun-core-webapp-archetype` wires the secure WCS (with EO), WPS and CSW
request and response interceptors, the CSW interceptor rules, and a
`WpsProcess` `*` with READ for the default user (before, every user could run
every process). New properties: `geoserver.interceptor.styleUrlHosts`,
`geoserver.interceptor.cswMaxRecords`; `geoServerNameSpaces.properties`
shows the global endpoint `ows`. A project generated from the archetype
(outside `src/`) builds (`mvn package -Djetty.skip=true`).

### check-ows.py

`check-ows.py` checks what no ETS covers: the SLD checks, WPS process and
input layer permissions, the CSW record filter (counts, paging, ISO, XML),
GetRecordById, GetRepositoryItem, DirectDownload, GetDomain, WMTS
multi-dimensional and WCS EO (refusals, lists, and the same answers as
GeoServer for allowed requests), the global endpoint and W3DS. Test data:
`ows/setup.sh` (a time mosaic `cite:tazmosaic` of two copies of the CITE
tazdem GeoTIFF as WCS EO dataset and WMTS dimension layer, WCS EO on, SLD
files in `www/styles`).

```bash
GEOSERVER_EXTENSIONS=wps,csw,csw-iso,wcs2_0-eo,wmts-multi-dimensional OWS_SETUP=true \
  ./use-data.sh citewms-1.3
./check-ows.py
```

Result: 75 checks, 75 pass (output: one `PASS`/`FAIL` line per check).

### Pinned extensions and suites

- `extensions.sha256` and `fetch-extensions.sh`: the GeoServer 3.0.2
  extension zips (from the GeoServer download site) with their SHA-256.
  `use-data.sh` gives them to the image (which then does not download), and
  refuses extensions that are not pinned.
- `run-console.sh`: WCS 1.0 (ets-wcs10#43: no REST API) and CSW 2.0.2 (no
  Docker image of ets-csw202; the production TEAM Engine image has its CTL
  scripts, version 1.18, but not in its REST API) run on the TEAM Engine console of
  `ogccite/teamengine-production`, pinned by digest
  (`sha256:e4ee158e...`).
- The WFS databases (`cite_wfs10`, `cite_wfs11`, `cite_wfs20`) come from the
  init scripts of each data dir (`.run/pginit`).


## Open points (not fixed)
- **CSRF**: the new chain for `/geoserver.action/**` has no CSRF protection.
  Browser sessions of the web login also work there.
- **Workspace-bound URLs**: the secure interceptors expect
  `geoServerNameSpaces.properties` entries such as
  `cite=http://host/geoserver/cite/ows`. For layers of several workspaces in
  one request, use the global endpoint (`ows=http://host/geoserver/ows`,
  `/geoserver.action/ows`), where names must be qualified.
- **DropStoredQuery** is allowed for any user; stored queries are global in
  GeoServer.
- **Locks by stored query** are checked after GeoServer made them. A lock of
  features without UPDATE stays until it expires (the client gets an
  exception, not the lock id).
- **Metadata URLs** (e.g. WMS `MetadataURL`) still point to GeoServer.
  Set the GeoServer proxy base URL if GeoServer is not reachable by clients.
- **WPS execution ids**: GetExecutionStatus, GetExecutionResult and Dismiss
  are not bound to the user of the Execute request; the execution id is a
  random id of GeoServer.
- **WPS literal inputs**: a process that takes a layer name as a literal
  input (not a reference) is not checked. Give READ only on processes you
  trust (e.g. `JTS:*`).
- **Style formats**: a style body that is not XML (e.g. GeoServer CSS or
  YSLD with `STYLE_FORMAT`) is refused, as its layers cannot be read.
- **CSW**: GetRecords reads at most `geoserver.interceptor.cswMaxRecords`
  records; a larger result gives an exception. GetDomain of a record property
  needs READ on all records.
- **Style URLs** are read by the interceptor, so GeoServer's own URL checks
  do not apply to them; `geoserver.interceptor.styleUrlHosts` is the list of
  allowed hosts.
- **Archetype `web.xml`** maps only `*.action`, `*.json` and `/rest/*`, so
  `/geoserver.action/{endpoint}/...` cannot be reached. The test webapp adds
  `/geoserver.action/*` and full-path matching; that breaks `/rest/*` in the
  test webapp (not used by the tests). This is not fixed in the archetype.

## Setup

Requirements: Docker with Compose, `mise` (JDK 8 and Maven, see
`../src/mise.toml`), Python 3, git.

```bash
./fetch-data.sh        # GeoServer CITE data dirs (GPL-2.0, not committed) into .cache/
./fetch-extensions.sh  # pinned GeoServer extensions (checked against extensions.sha256)
(cd ../src && mise exec -- mvn -B -DskipTests install)   # shogun-core 5.3.14-SNAPSHOT
(cd webapp && mise exec -- mvn -B -DskipTests -Djetty.skip=true package)
./use-data.sh citewms-1.3
docker compose up -d --wait
./run-all.sh           # all suites; or e.g. ./run-all.sh wms13 wfs20 (TARGETS=direct: direct only)
./rebuild.sh           # after a change in shogun-core-main or webapp/
```

`run-all.sh` resets the GeoServer data dir (and the WFS database) before each
run, because WFS-T tests change the data.

Ports (localhost only): 18080 GeoServer, 18081 SHOGun-Core (Tomcat), 18082
nginx, 18083 TEAM Engine (`ogctest`/`ogctest`).

## Files

| Path | Contents |
|---|---|
| `compose.yml` | GeoServer, PostGIS (WFS 1.0/1.1/2.0 data), SHOGun-Core on Tomcat 9 / Java 8, nginx, TEAM Engine (`ETS_IMAGE`) |
| `nginx/default.conf` | Adds Basic auth of the test user `user`/`shogun`; TEAM Engine cannot log in |
| `webapp/` | Test webapp, generated from the archetype (see below) |
| `wps/` | WPS suite parameters, Execute requests (`JTS:buffer`), input geometry and GeoServer URL check |
| `fetch-data.sh` | Gets the CITE data dirs (WMS 1.1/1.3, WFS 1.0/1.1/2.0, WCS 1.0/1.1) from GeoServer's `build/cite` |
| `extensions.sha256`, `fetch-extensions.sh` | Pinned GeoServer 3.0.2 extension zips (SHA-256) |
| `run-console.sh` | Runs a CTL suite on the TEAM Engine console (WCS 1.0, CSW 2.0.2) |
| `check-ows.py` | Checks that no ETS covers (SLD, WPS, CSW, WMTS multi-dimensional, WCS EO) |
| `ows/` | Test data of `check-ows.py` (`setup.sh`, mosaic properties, SLD files) |
| `use-data.sh` | Starts GeoServer with a fresh copy of one data dir |
| `rebuild.sh` | Builds `shogun-core-main` and the webapp, restarts SHOGun-Core |
| `run-suite.sh` | Runs one suite with the TEAM Engine REST API |
| `run-all.sh` | Runs suites direct and through SHOGun-Core, prints the difference |
| `summarize.py` | Compares two result files (CTL and TestNG formats) |
| `replay.py` | Sends a failed GET request of a CTL result file again |
| `results/` | Result files (not committed) |

## Test webapp

`webapp/` was generated from `shogun-core-webapp-archetype` (groupId
`de.terrestris.compliance`). Changes after generation:

- The archetype bugs above (fixed now in the archetype, too).
- `web.xml` and `shogun-compliance-servlet.xml`: `/geoserver.action/*`
  mapping and full-path matching (see open points).
- `ProjectContentInitializer`: one WMS and one WMTS layer entity per name in
  `compliance.layers` (`META-INF/compliance.properties`), with READ and
  UPDATE for the test user.
- `geoServerNameSpaces.properties`: `cite`, `topp`, `sf`, `cdf` and `cgf`
  workspaces, and the global endpoint `ows`.
- A user `limited` (password `limited`) with READ on `compliance.limitedLayers`
  and the WPS processes `compliance.limitedWpsProcesses`; the test user may
  run all processes (`compliance.wpsProcesses=*`).
- `shogun-compliance-context.xml`: the secure WMS, WMTS, WFS, WCS, WPS and CSW
  interceptors.
- H2 in-memory database.

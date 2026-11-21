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
| WMS 1.3.0 | ets-wms13 | 241 pass | 241 pass | 0 |
| WMTS 1.0.0 | ets-wmts10 1.11 | 35 pass, 8 fail, 14 skip | 45 pass, 0 fail, 17 skip | 0 |
| WCS 2.0.1 (core, post, scaling, interpolation, range subsetting, crs) | ets-wcs20 1.22 | 165 pass, 1 fail, 2 inherited | 165 pass, 1 fail, 2 inherited | 0 |
| WFS 2.0.0 | ets-wfs20 1.40 (`teamengine-production`) | 285 pass, 59 skip | 285 pass, 59 skip | 0 |
| WPS 1.0.0 (level 1) | ets-wps10 0.8 | 15 pass, 6 fail | 15 pass, 6 fail | 0 |

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

All in `src/shogun-core-main`, Java 8 compatible. 805 unit tests pass
(29 new for the helpers below).

General (`GeoServerInterceptorService`, `GeoServerInterceptorController`,
`MutableHttpServletRequest`, new `OgcException`):

- Errors are OGC exception reports (WMS `ServiceExceptionReport`, OWS 1.1 or
  2.0 `ExceptionReport`) with the HTTP status of the standard. Before, they
  were JSON with HTTP 200.
- WMS GetMap supports `EXCEPTIONS=INIMAGE` and `BLANK`.
- `SERVICE` is optional for operations that only one service has (e.g.
  GetMap), as in GeoServer.
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

## Open points (not fixed)

- **WPS has no permission check.** `impl/WpsRequestInterceptor` only adds the
  GeoServer credentials. Any user with `ROLE_USER` can run every process, and
  processes can read GeoServer layers through internal references.
- **CSRF**: the new chain for `/geoserver.action/**` has no CSRF protection.
  Browser sessions of the web login also work there.
- **Workspace-bound URLs**: the secure interceptors expect
  `geoServerNameSpaces.properties` entries such as
  `cite=http://host/geoserver/cite/ows`. GeoServer then only resolves names of
  that workspace. With a global URL, prefixed names of other workspaces are
  not scoped.
- **DropStoredQuery** is allowed for any user; stored queries are global in
  GeoServer.
- **Locks by stored query** are checked after GeoServer made them. A lock of
  features without UPDATE stays until it expires (the client gets an
  exception, not the lock id).
- **Metadata URLs** (e.g. WMS `MetadataURL`) still point to GeoServer.
  Set the GeoServer proxy base URL if GeoServer is not reachable by clients.
- **Archetype `web.xml`** maps only `*.action`, `*.json` and `/rest/*`, so
  `/geoserver.action/{endpoint}/...` cannot be reached. The test webapp adds
  `/geoserver.action/*` and full-path matching; that breaks `/rest/*` in the
  test webapp (not used by the tests). This is not fixed in the archetype.

## Setup

Requirements: Docker with Compose, `mise` (JDK 8 and Maven, see
`../src/mise.toml`), Python 3, git.

```bash
./fetch-data.sh        # GeoServer CITE data dirs (GPL-2.0, not committed) into .cache/
(cd ../src && mise exec -- mvn -B -DskipTests install)   # shogun-core 5.3.14-SNAPSHOT
(cd webapp && mise exec -- mvn -B -DskipTests -Djetty.skip=true package)
./use-data.sh citewms-1.3
docker compose up -d --wait
./run-all.sh           # all suites; or e.g. ./run-all.sh wms13 wfs20
./rebuild.sh           # after a change in shogun-core-main or webapp/
```

`run-all.sh` resets the GeoServer data dir (and the WFS database) before each
run, because WFS-T tests change the data.

Ports (localhost only): 18080 GeoServer, 18081 SHOGun-Core (Tomcat), 18082
nginx, 18083 TEAM Engine (`ogctest`/`ogctest`).

## Files

| Path | Contents |
|---|---|
| `compose.yml` | GeoServer, PostGIS (WFS 2.0 data), SHOGun-Core on Tomcat 9 / Java 8, nginx, TEAM Engine (`ETS_IMAGE`) |
| `nginx/default.conf` | Adds Basic auth of the test user `user`/`shogun`; TEAM Engine cannot log in |
| `webapp/` | Test webapp, generated from the archetype (see below) |
| `wps/` | WPS suite parameters, Execute requests (`JTS:buffer`), input geometry and GeoServer URL check |
| `fetch-data.sh` | Gets the CITE data dirs from GeoServer's `build/cite` |
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
- `geoServerNameSpaces.properties`: `cite`, `topp` and `sf` workspaces.
- `shogun-compliance-context.xml`: the secure WMS, WMTS, WFS, WCS and WPS
  interceptors.
- H2 in-memory database.

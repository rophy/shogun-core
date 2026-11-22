package de.terrestris.shoguncore.util.interceptor;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * An error that is sent to the client as an OGC exception report (WMS
 * ServiceExceptionReport or OWS ExceptionReport), as the OGC service
 * standards require, instead of a JSON message.
 */
public class OgcException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String NO_APPLICABLE_CODE = "NoApplicableCode";
    public static final String MISSING_PARAMETER_VALUE = "MissingParameterValue";
    public static final String INVALID_PARAMETER_VALUE = "InvalidParameterValue";
    public static final String OPERATION_NOT_SUPPORTED = "OperationNotSupported";
    public static final String LAYER_NOT_DEFINED = "LayerNotDefined";

    private final String code;

    private final String locator;

    private String service;

    private String version;

    private Map<String, String> params = new HashMap<>();

    /** The HTTP status, if not the one of the code (see {@link #getHttpStatus(String)}). */
    private HttpStatus httpStatus;

    /**
     * True if the version came from a VERSION parameter (not AcceptVersions
     * of OWS Common 1.1): for a request without a known service, GeoServer's
     * /ows then answers with an OWS 1.0 report.
     */
    private boolean versionParameter = true;

    public OgcException(String code, String locator, String message) {
        super(message);
        this.code = code;
        this.locator = locator;
    }

    public String getCode() {
        return code;
    }

    public String getLocator() {
        return locator;
    }

    public String getService() {
        return service;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Sets the service and version of the request, if not set yet.
     */
    public OgcException forRequest(String requestService, String requestVersion) {
        if (StringUtils.isEmpty(service)) {
            service = requestService;
        }
        if (StringUtils.isEmpty(version)) {
            version = requestVersion;
        }
        return this;
    }

    /**
     * @param fromVersionParameter true if the version of the request came from
     * a VERSION parameter, false for AcceptVersions
     */
    public OgcException withVersionParameter(boolean fromVersionParameter) {
        this.versionParameter = fromVersionParameter;
        return this;
    }

    /**
     * Sets the HTTP status of the report, e.g. to send an error with the
     * status that GeoServer uses for it.
     *
     * @param status The HTTP status
     */
    public OgcException withHttpStatus(HttpStatus status) {
        this.httpStatus = status;
        return this;
    }

    /**
     * Sets the request parameters, used for WMS image exceptions.
     *
     * @param requestParams The parameters
     */
    public OgcException withParameters(Map<String, String[]> requestParams) {
        if (requestParams != null) {
            for (Map.Entry<String, String[]> entry : requestParams.entrySet()) {
                params.put(entry.getKey().toUpperCase(Locale.ROOT), StringUtils.join(entry.getValue(), ","));
            }
        }
        return this;
    }

    /**
     * @return The HTTP response for this exception
     */
    public ResponseEntity<byte[]> toResponseEntity() {
        HttpHeaders headers = new HttpHeaders();
        if (isWms(service) && WmsExceptionImage.isRequested(params)) {
            try {
                byte[] image = WmsExceptionImage.render(params, getMessage());
                headers.setContentType(MediaType.parseMediaType(WmsExceptionImage.mediaType(params)));
                return new ResponseEntity<>(image, headers, HttpStatus.OK);
            } catch (IOException | IllegalArgumentException e) {
                // fall back to the XML report
            }
        }
        headers.setContentType(getMediaType());
        return new ResponseEntity<>(toReport(), headers, getHttpStatus());
    }

    public byte[] toReport() {
        return toReport(service, version);
    }

    public MediaType getMediaType() {
        return getMediaType(service, version);
    }

    public HttpStatus getHttpStatus() {
        return getHttpStatus(service);
    }

    /**
     * @param service The OGC service type of the request (may be empty)
     * @param version The requested version (may be empty)
     * @return The exception report document
     */
    public byte[] toReport(String service, String version) {
        String locatorAttr = StringUtils.isEmpty(locator) ? "" :
            " locator=\"" + StringEscapeUtils.escapeXml10(locator) + "\"";
        String text = StringEscapeUtils.escapeXml10(StringUtils.defaultString(getMessage()));
        String xml;
        if (isWms(service)) {
            String codeAttr = NO_APPLICABLE_CODE.equals(code) ? "" : " code=\"" + code + "\"";
            if (StringUtils.startsWith(version, "1.1")) {
                xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<ServiceExceptionReport version=\"1.1.1\">"
                    + "<ServiceException" + codeAttr + locatorAttr + ">" + text + "</ServiceException>"
                    + "</ServiceExceptionReport>";
            } else {
                xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<ServiceExceptionReport version=\"1.3.0\" xmlns=\"http://www.opengis.net/ogc\">"
                    + "<ServiceException" + codeAttr + locatorAttr + ">" + text + "</ServiceException>"
                    + "</ServiceExceptionReport>";
            }
        } else if (isOgcServiceException(service, version)) {
            // WFS 1.0 and WCS 1.0: the OGC ServiceExceptionReport, as in GeoServer
            String codeAttr = NO_APPLICABLE_CODE.equals(code) ? "" : " code=\"" + code + "\"";
            xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ServiceExceptionReport version=\"1.2.0\" xmlns=\"http://www.opengis.net/ogc\">"
                + "<ServiceException" + codeAttr + locatorAttr + ">" + text + "</ServiceException>"
                + "</ServiceExceptionReport>";
        } else if (isOws10(service, version)) {
            // WFS 1.1 uses OWS Common 1.0. GeoServer also sends this report
            // for 1.x requests without a service.
            xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ows:ExceptionReport xmlns:ows=\"http://www.opengis.net/ows\" version=\"1.0.0\">"
                + "<ows:Exception exceptionCode=\"" + code + "\"" + locatorAttr + ">"
                + "<ows:ExceptionText>" + text + "</ows:ExceptionText>"
                + "</ows:Exception></ows:ExceptionReport>";
        } else {
            // OWS Common 2.0: WCS 2.0. WFS 2.0, WMTS 1.0 and WPS 1.0 use OWS Common 1.1.
            boolean ows2 = "WCS".equalsIgnoreCase(service) && StringUtils.startsWith(version, "2");
            String ns = ows2 ? "http://www.opengis.net/ows/2.0" : "http://www.opengis.net/ows/1.1";
            // WFS 2.0 requires version 2.0.0, also if SERVICE is missing. A
            // request without service and version gets this version, too.
            boolean wfs2 = "WFS".equalsIgnoreCase(service) || (StringUtils.isEmpty(service)
                && (StringUtils.isEmpty(version) || StringUtils.startsWith(version, "2")));
            String reportVersion = ows2 || wfs2 ? "2.0.0" : "1.0.0";
            xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ows:ExceptionReport xmlns:ows=\"" + ns + "\" version=\"" + reportVersion + "\">"
                + "<ows:Exception exceptionCode=\"" + code + "\"" + locatorAttr + ">"
                + "<ows:ExceptionText>" + text + "</ows:ExceptionText>"
                + "</ows:Exception></ows:ExceptionReport>";
        }
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @return The media type of the report
     */
    public MediaType getMediaType(String service, String version) {
        if ((isWms(service) && StringUtils.startsWith(version, "1.1"))
            || ("WCS".equalsIgnoreCase(service) && StringUtils.startsWith(version, "1.0"))) {
            // WMS 1.1.1 and WCS 1.0 (as in GeoServer)
            return MediaType.parseMediaType("application/vnd.ogc.se_xml");
        }
        return MediaType.TEXT_XML;
    }

    /**
     * WMS reports exceptions with HTTP 200. OWS Common based services map the
     * exception code to an HTTP status.
     */
    public HttpStatus getHttpStatus(String service) {
        if (httpStatus != null) {
            return httpStatus;
        }
        if (isWms(service) || isOgcServiceException(service, version) || isOws10(service, version)) {
            // these versions have no HTTP status rules; GeoServer uses 200
            return HttpStatus.OK;
        }
        if (OPERATION_NOT_SUPPORTED.equals(code)) {
            return HttpStatus.NOT_IMPLEMENTED;
        }
        if ("NoSuchCoverage".equals(code) || "NotFound".equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if (!NO_APPLICABLE_CODE.equals(code)) {
            // MissingParameterValue, InvalidParameterValue, LayerNotDefined, ...
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * @return true for WFS 1.0 and WCS 1.0, which use the OGC
     * ServiceExceptionReport 1.2.0
     */
    private static boolean isOgcServiceException(String service, String version) {
        return ("WFS".equalsIgnoreCase(service) || "WCS".equalsIgnoreCase(service))
            && StringUtils.startsWith(version, "1.0");
    }

    /**
     * @return true for WFS 1.1, and for 1.x requests (VERSION parameter)
     * without a known service
     */
    private boolean isOws10(String service, String version) {
        if ("WFS".equalsIgnoreCase(service)) {
            return StringUtils.startsWith(version, "1.1");
        }
        return versionParameter && !isKnownService(service) && StringUtils.startsWith(version, "1.");
    }

    private static boolean isKnownService(String service) {
        return de.terrestris.shoguncore.util.enumeration.OgcEnum.ServiceType.fromString(service) != null;
    }

    /**
     * Requests without a known service get an OWS exception report, as in
     * GeoServer.
     */
    private static boolean isWms(String service) {
        return "WMS".equalsIgnoreCase(service);
    }
}

package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Builds the public URL of the interceptor and rewrites GeoServer service
 * URLs in capabilities documents to it.
 */
final class ProxyUrls {

    private static final String[] SERVICE_PATHS = {"/ows", "/wms", "/wfs", "/wcs", "/wps", "/wmts", "/csw"};

    private static final int HTTP_PORT = 80;

    private static final int HTTPS_PORT = 443;

    private ProxyUrls() {
    }

    /**
     * @param request The intercepted request
     * @param endpoint The endpoint of the URL path
     * @return e.g. https://example.com/app/geoserver.action/cite
     */
    static String baseUrl(MutableHttpServletRequest request, String endpoint) {
        String proto = request.getHeader("x-forwarded-proto");
        String host = request.getHeader("x-forwarded-host");
        if (StringUtils.isEmpty(proto)) {
            proto = request.getScheme();
        }
        if (StringUtils.isEmpty(host)) {
            host = request.getHeader("Host");
        }
        if (StringUtils.isEmpty(host)) {
            host = request.getServerName();
            int port = request.getServerPort();
            boolean defaultPort = ("http".equals(proto) && port == HTTP_PORT) || ("https".equals(proto) && port == HTTPS_PORT);
            if (port > 0 && !defaultPort) {
                host = host + ":" + port;
            }
        }
        // the first value if the header has a list of proxies
        host = host.split(",")[0].trim();
        String contextPath = StringUtils.defaultString(request.getContextPath());
        String url = proto + "://" + host + contextPath + "/geoserver.action";
        return StringUtils.isEmpty(endpoint) ? url : url + "/" + endpoint;
    }

    /**
     * @return true if the URL has the scheme, host and port of the GeoServer
     * URI. Works for URL templates with braces, too.
     */
    static boolean sameServer(String url, String geoServerUri) {
        try {
            URI geoServer = new URI(geoServerUri);
            String origin = geoServer.getScheme() + "://" + geoServer.getRawAuthority();
            return StringUtils.startsWithIgnoreCase(url, origin + "/");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Rewrites a GeoServer service URL (same host as the GeoServer base URI
     * and a service path, e.g. /ows) to the interceptor URL and keeps its
     * query. Other URLs (e.g. metadata documents) stay unchanged.
     *
     * @param url The URL in the document
     * @param geoServerUri The GeoServer URI that the request was sent to
     * @param baseUrl The interceptor URL
     * @return The new URL
     */
    static String rewrite(String url, String geoServerUri, String baseUrl) {
        if (StringUtils.isEmpty(url)) {
            return url;
        }
        try {
            URI target = new URI(url.trim());
            URI geoServer = new URI(geoServerUri);
            if (!StringUtils.equalsIgnoreCase(target.getHost(), geoServer.getHost())
                || target.getPort() != geoServer.getPort()) {
                return url;
            }
            String path = StringUtils.defaultString(target.getRawPath());
            boolean servicePath = path.contains("/gwc/service/");
            for (String suffix : SERVICE_PATHS) {
                servicePath |= StringUtils.endsWithIgnoreCase(path, suffix);
            }
            if (!servicePath) {
                return url;
            }
            int index = url.indexOf('?');
            return index > -1 ? baseUrl + url.substring(index) : baseUrl;
        } catch (URISyntaxException e) {
            return url;
        }
    }
}

package de.terrestris.shoguncore.util.interceptor;

/**
 * Endpoints of the URL path of the GeoServer interceptor
 * (/geoserver.action/{endpoint}).
 * <p>
 * An endpoint is a GeoServer namespace (a key of geoServerNameSpaces), for a
 * workspace bound service. The endpoint {@link #GLOBAL} is the global service
 * of GeoServer (/geoserver/ows), for requests with layers of more than one
 * workspace. It is used only if geoServerNameSpaces has a key "ows", e.g.
 * <code>ows=http://localhost:8080/geoserver/ows</code>. There, layer names
 * must be qualified ("workspace:name").
 */
public final class OgcEndpoints {

    /**
     * The endpoint of the global GeoServer services, as /geoserver/ows.
     */
    public static final String GLOBAL = "ows";

    private OgcEndpoints() {
    }

    /**
     * @param endpoint The endpoint of the URL path (may be null)
     * @return true for the global endpoint
     */
    public static boolean isGlobal(String endpoint) {
        return GLOBAL.equalsIgnoreCase(endpoint);
    }
}

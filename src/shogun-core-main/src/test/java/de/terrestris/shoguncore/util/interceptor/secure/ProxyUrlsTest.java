package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProxyUrlsTest {

    private static final String GEOSERVER = "http://geoserver:8080/geoserver/cite/ows";

    private static final String BASE = "https://example.com/app/geoserver.action/cite";

    @Test
    public void rewrite_keeps_the_query_of_service_urls() {
        assertEquals(BASE + "?SERVICE=WMS&",
            ProxyUrls.rewrite("http://geoserver:8080/geoserver/cite/ows?SERVICE=WMS&", GEOSERVER, BASE));
        assertEquals(BASE + "?service=WMS&request=GetLegendGraphic&layer=Lakes",
            ProxyUrls.rewrite("http://geoserver:8080/geoserver/cite/wms?service=WMS&request=GetLegendGraphic"
                + "&layer=Lakes", GEOSERVER, BASE));
        assertEquals(BASE, ProxyUrls.rewrite("http://geoserver:8080/geoserver/cite/wfs", GEOSERVER, BASE));
        assertEquals(BASE, ProxyUrls.rewrite("http://geoserver:8080/geoserver/cite/csw", GEOSERVER, BASE));
        assertEquals(BASE + "?", ProxyUrls.rewrite("http://geoserver:8080/geoserver/gwc/service/wmts?",
            GEOSERVER, BASE));
    }

    @Test
    public void rewrite_keeps_other_urls() {
        String metadata = "http://geoserver:8080/geoserver/www/Lakes.xml";
        assertEquals(metadata, ProxyUrls.rewrite(metadata, GEOSERVER, BASE));
        String external = "http://geoserver.org";
        assertEquals(external, ProxyUrls.rewrite(external, GEOSERVER, BASE));
        String otherPort = "http://geoserver:9090/geoserver/cite/ows?";
        assertEquals(otherPort, ProxyUrls.rewrite(otherPort, GEOSERVER, BASE));
        assertEquals("", ProxyUrls.rewrite("", GEOSERVER, BASE));
    }

    @Test
    public void sameServer_works_with_url_templates() {
        assertTrue(ProxyUrls.sameServer("http://geoserver:8080/geoserver/gwc/service/wmts/rest/a/{style}",
            GEOSERVER));
        assertFalse(ProxyUrls.sameServer("http://other/geoserver/gwc/service/wmts/rest/a/{style}", GEOSERVER));
    }

    @Test
    public void baseUrl_uses_forwarded_headers() {
        MockHttpServletRequest mock = new MockHttpServletRequest("GET", "/app/geoserver.action/cite");
        mock.setContextPath("/app");
        mock.addHeader("x-forwarded-proto", "https");
        mock.addHeader("x-forwarded-host", "example.com, proxy.local");
        assertEquals(BASE, ProxyUrls.baseUrl(new MutableHttpServletRequest(mock), "cite"));
    }

    @Test
    public void baseUrl_keeps_a_non_default_port() {
        MockHttpServletRequest mock = new MockHttpServletRequest("GET", "/geoserver.action");
        mock.setServerName("localhost");
        mock.setServerPort(8081);
        assertEquals("http://localhost:8081/geoserver.action",
            ProxyUrls.baseUrl(new MutableHttpServletRequest(mock), null));
    }
}

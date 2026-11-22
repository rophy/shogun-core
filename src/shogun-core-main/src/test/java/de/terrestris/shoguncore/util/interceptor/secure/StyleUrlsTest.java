package de.terrestris.shoguncore.util.interceptor.secure;

import com.sun.net.httpserver.HttpServer;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class StyleUrlsTest {

    private HttpServer server;

    private String base;

    @Before
    public void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/style.sld", exchange -> {
            byte[] body = "<StyledLayerDescriptor/>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void stopServer() {
        server.stop(0);
    }

    private static void assertRefused(Runnable call) {
        try {
            call.run();
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(OgcException.INVALID_PARAMETER_VALUE, e.getCode());
            assertEquals("SLD", e.getLocator());
        }
    }

    @Test
    public void only_listed_hosts_and_ports_are_allowed() {
        List<String> hosts = Arrays.asList("styles.example.com", " other.example.com:8080 ", "");
        assertEquals("styles.example.com", StyleUrls.check("http://styles.example.com/a.sld", hosts, "SLD").getHost());
        StyleUrls.check("https://styles.example.com:443/a.sld", hosts, "SLD");
        StyleUrls.check("HTTP://OTHER.example.com:8080/a.sld", hosts, "SLD");
        for (String url : new String[]{"http://styles.example.com:8080/a.sld", "http://other.example.com/a.sld",
            "http://evil-styles.example.com/a.sld", "http://styles.example.com.evil.com/a.sld",
            "file:///etc/passwd", "jar:http://styles.example.com/a!/b", "//styles.example.com/a.sld", "%"}) {
            assertRefused(() -> StyleUrls.check(url, hosts, "SLD"));
        }
    }

    @Test
    public void no_hosts_allow_no_url() {
        assertRefused(() -> StyleUrls.check("http://styles.example.com/a.sld", Collections.emptyList(), "SLD"));
        assertRefused(() -> StyleUrls.check("http://styles.example.com/a.sld", Collections.singletonList(""),
            "SLD"));
        assertRefused(() -> StyleUrls.check("http://styles.example.com/a.sld", null, "SLD"));
    }

    @Test
    public void reads_the_document() {
        assertEquals("<StyledLayerDescriptor/>", StyleUrls.read(URI.create(base + "/style.sld"), "SLD"));
    }

    @Test
    public void redirects_and_errors_are_not_followed() {
        assertRefused(() -> StyleUrls.read(URI.create(base + "/redirect"), "SLD"));
        assertRefused(() -> StyleUrls.read(URI.create(base + "/missing"), "SLD"));
    }

    @Test
    public void large_documents_are_refused() throws Exception {
        assertEquals("abc", StyleUrls.readLimited(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)),
            "SLD"));
        byte[] large = new byte[1024 * 1024 + 1];
        assertRefused(() -> {
            try {
                StyleUrls.readLimited(new ByteArrayInputStream(large), "SLD");
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }
}

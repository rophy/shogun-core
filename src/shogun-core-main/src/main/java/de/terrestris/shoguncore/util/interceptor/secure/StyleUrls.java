package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.OgcException;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Reads style documents of WMS style URLs (SLD, STYLE_URL), so that the
 * interceptor can check their layers.
 * <p>
 * Only http and https URLs of the configured hosts are read (exact host and
 * port), without redirects, as the interceptor sends the request: other URLs
 * would let clients use the interceptor to read internal URLs.
 */
final class StyleUrls {

    /** Connect and read timeout. */
    private static final int TIMEOUT_MILLIS = 10000;

    /** The largest style document that is read. */
    private static final int MAX_BYTES = 1024 * 1024;

    private static final int HTTP_PORT = 80;

    private static final int HTTPS_PORT = 443;

    private static final int HTTP_OK = 200;

    private static final int BUFFER_SIZE = 8192;

    private StyleUrls() {
    }

    /**
     * @param url A style URL of a request
     * @param hosts The allowed hosts ("host" or "host:port"); blank entries
     * are ignored
     * @param locator The parameter of the URL, for the exception
     * @return The parsed URL
     * @throws OgcException InvalidParameterValue if the URL is not allowed
     */
    static URI check(String url, List<String> hosts, String locator) {
        URI uri;
        try {
            uri = new URI(StringUtils.trimToEmpty(url));
        } catch (URISyntaxException e) {
            throw notAllowed(locator);
        }
        String scheme = StringUtils.lowerCase(uri.getScheme(), Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme) || uri.getHost() == null) {
            throw notAllowed(locator);
        }
        int port = uri.getPort() != -1 ? uri.getPort() : defaultPort(scheme);
        if (hosts != null) {
            for (String entry : hosts) {
                if (matches(entry, uri.getHost(), port, scheme)) {
                    return uri;
                }
            }
        }
        throw notAllowed(locator);
    }

    private static boolean matches(String entry, String host, int port, String scheme) {
        String trimmed = StringUtils.trimToEmpty(entry);
        if (trimmed.isEmpty()) {
            return false;
        }
        String entryHost = trimmed;
        int entryPort = defaultPort(scheme);
        int colon = trimmed.lastIndexOf(':');
        if (colon > 0) {
            entryHost = trimmed.substring(0, colon);
            try {
                entryPort = Integer.parseInt(trimmed.substring(colon + 1));
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return entryHost.equalsIgnoreCase(host) && entryPort == port;
    }

    private static int defaultPort(String scheme) {
        return "https".equals(scheme) ? HTTPS_PORT : HTTP_PORT;
    }

    private static OgcException notAllowed(String locator) {
        return new OgcException(OgcException.INVALID_PARAMETER_VALUE, locator,
            "The style URL is not allowed.");
    }

    /**
     * @param uri A checked style URL
     * @param locator The parameter of the URL, for the exception
     * @return The style document (UTF-8)
     * @throws OgcException InvalidParameterValue if it cannot be read
     */
    static String read(URI uri, String locator) {
        HttpURLConnection connection = null;
        try {
            URL url = uri.toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            if (connection.getResponseCode() != HTTP_OK) {
                throw cannotRead(locator);
            }
            try (InputStream in = connection.getInputStream()) {
                return readLimited(in, locator);
            }
        } catch (IOException | IllegalArgumentException e) {
            throw cannotRead(locator);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * @return The stream as UTF-8 text, refused if larger than MAX_BYTES
     */
    static String readLimited(InputStream in, String locator) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > MAX_BYTES) {
                throw new OgcException(OgcException.INVALID_PARAMETER_VALUE, locator,
                    "The style document is too large.");
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static OgcException cannotRead(String locator) {
        return new OgcException(OgcException.INVALID_PARAMETER_VALUE, locator,
            "The style URL cannot be read.");
    }
}

package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.OgcException;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the processes and the GeoServer layers of WPS 1.0 requests (KVP and
 * XML), so that the interceptor can check them.
 * <p>
 * An Execute request reads GeoServer layers through input references:
 * <ul>
 * <li>Internal references (http://geoserver/wfs, /wcs, /wps): GeoServer
 * resolves them itself (AbstractInputProvider of GeoServer).</li>
 * <li>References to the URL of the same GeoServer (same host and port).</li>
 * </ul>
 * A reference to /wps is a chained process: its process and inputs are read,
 * too. References to other servers are not GeoServer layers. A GeoServer
 * reference with a BodyReference cannot be checked (GeoServer reads the
 * request from another URL), so it is refused.
 */
final class WpsRequests {

    /** The host of GeoServer internal references. */
    private static final String INTERNAL_PREFIX = "http://geoserver/";

    private static final Pattern NAME_SEPARATORS = Pattern.compile("[\\s,()]+");

    private static final String XLINK = "http://www.w3.org/1999/xlink";

    private static final String WFS = "wfs";

    private static final String WCS = "wcs";

    private static final String WMS = "wms";

    private static final String WPS = "wps";

    private static final String[] WFS_PARAMS = {"typeName", "typeNames"};

    private static final String[] WCS_PARAMS = {"coverageId", "identifier", "identifiers", "coverage"};

    private static final String[] WMS_PARAMS = {"layers", "query_layers", "layer"};

    private static final String[] STYLE_PARAMS = {"sld_body", "style_body"};

    private static final int MAX_DEPTH = 10;

    private static final int HTTP_PORT = 80;

    private static final int HTTPS_PORT = 443;

    private WpsRequests() {
    }

    /**
     * The processes and layers of a request.
     */
    static final class Names {

        /** Process identifiers, e.g. "JTS:buffer". */
        private final Set<String> processes = new LinkedHashSet<>();

        /** Feature types and WMS layers, e.g. "cite:Lakes". */
        private final Set<String> layers = new LinkedHashSet<>();

        /** WCS coverage identifiers (also "workspace__name"). */
        private final Set<String> coverages = new LinkedHashSet<>();

        Set<String> getProcesses() {
            return processes;
        }

        Set<String> getLayers() {
            return layers;
        }

        Set<String> getCoverages() {
            return coverages;
        }
    }

    /**
     * @param identifier The KVP identifier parameter (may be null)
     * @param dataInputs The KVP DataInputs parameter (may be null)
     * @param geoServerOrigins "scheme://host:port" of the GeoServer URLs
     * @return The processes and layers of a KVP Execute request
     */
    static Names ofExecuteKvp(String identifier, String dataInputs, Collection<String> geoServerOrigins) {
        Names names = new Names();
        if (StringUtils.isNotBlank(identifier)) {
            names.processes.add(identifier.trim());
        }
        if (StringUtils.isNotBlank(dataInputs)) {
            // inputs are separated by ";", attributes by "@" (as in GeoServer)
            for (String input : dataInputs.split(";")) {
                String[] parts = input.split("@");
                for (int i = 1; i < parts.length; i++) {
                    int eq = parts[i].indexOf('=');
                    String attribute = eq > 0 ? parts[i].substring(0, eq).trim() : "";
                    if (eq > 0 && ("href".equalsIgnoreCase(attribute) || "xlink:href".equalsIgnoreCase(attribute))) {
                        reference(parts[i].substring(eq + 1), null, false, geoServerOrigins, names, 0);
                    }
                }
            }
        }
        return names;
    }

    /**
     * @param body An XML request (Execute or DescribeProcess)
     * @param geoServerOrigins "scheme://host:port" of the GeoServer URLs
     * @return The processes and layers of the request
     */
    static Names ofXml(String body, Collection<String> geoServerOrigins) {
        Names names = new Names();
        Element root = parse(body).getDocumentElement();
        if ("DescribeProcess".equals(localName(root))) {
            for (Element id : children(root, "Identifier")) {
                addAll(names.processes, id.getTextContent(), ",");
            }
        } else {
            execute(root, geoServerOrigins, names, 0);
        }
        return names;
    }

    /**
     * @param identifiers The KVP identifier parameter of DescribeProcess
     * @return The identifiers. GeoServer 3.0.2 has no "ALL" value (it
     * answers "No such process: ALL"), so ALL is a process name, too.
     */
    static Set<String> ofDescribeProcessKvp(String identifiers) {
        Set<String> names = new LinkedHashSet<>();
        addAll(names, identifiers, ",");
        return names;
    }

    private static void execute(Element execute, Collection<String> origins, Names names, int depth) {
        if (depth > MAX_DEPTH) {
            throw cannotCheck();
        }
        for (Element id : children(execute, "Identifier")) {
            if (StringUtils.isNotBlank(id.getTextContent())) {
                names.processes.add(id.getTextContent().trim());
            }
        }
        for (Element dataInputs : children(execute, "DataInputs")) {
            for (Element input : children(dataInputs, "Input")) {
                for (Element reference : children(input, "Reference")) {
                    String href = reference.getAttributeNS(XLINK, "href");
                    if (StringUtils.isEmpty(href)) {
                        href = reference.getAttribute("href");
                    }
                    Element body = firstChild(first(children(reference, "Body")));
                    boolean bodyReference = !children(reference, "BodyReference").isEmpty();
                    reference(href, body, bodyReference, origins, names, depth);
                }
            }
        }
    }

    /**
     * Reads a reference of an Execute input.
     */
    private static void reference(String href, Element body, boolean bodyReference, Collection<String> origins,
                                  Names names, int depth) {
        String service = geoServerService(href, origins);
        if (service == null) {
            // another server: not a GeoServer layer
            return;
        }
        if (bodyReference) {
            throw cannotCheck();
        }
        Map<String, Set<String>> params = queryParameters(href);
        if (service.isEmpty()) {
            Set<String> services = params.getOrDefault("service", new LinkedHashSet<>());
            service = services.size() == 1 ? services.iterator().next().toLowerCase(Locale.ROOT) : "";
        }
        if (body != null) {
            body(body, origins, names, depth);
        }
        if (WPS.equals(service)) {
            addAll(names.processes, params, "identifier", ",");
            for (String dataInputs : params.getOrDefault("datainputs", new LinkedHashSet<>())) {
                merge(ofExecuteKvp(null, dataInputs, origins), names);
            }
            return;
        }
        boolean wcs = WCS.equals(service);
        boolean wfs = WFS.equals(service);
        boolean wms = WMS.equals(service);
        if (!wfs && !wms) {
            for (String param : WCS_PARAMS) {
                addAll(names.coverages, params, param, ",");
            }
        }
        if (!wcs) {
            for (String param : WFS_PARAMS) {
                addAll(names.layers, params, param, null);
            }
        }
        if (!wcs && !wfs) {
            // WMS, or a service that is not known (e.g. /ows without SERVICE)
            for (String param : WMS_PARAMS) {
                addAll(names.layers, params, param, ",");
            }
            for (String param : STYLE_PARAMS) {
                for (String style : params.getOrDefault(param, new LinkedHashSet<>())) {
                    names.layers.addAll(StyledLayers.names(style, param.toUpperCase(Locale.ROOT)));
                }
            }
        }
    }

    /**
     * Reads the request in the Body of a reference.
     */
    private static void body(Element body, Collection<String> origins, Names names, int depth) {
        String root = localName(body);
        if ("Execute".equals(root)) {
            execute(body, origins, names, depth + 1);
            return;
        }
        if ("GetCoverage".equals(root) || "DescribeCoverage".equals(root)) {
            for (String element : new String[]{"Identifier", "CoverageId", "sourceCoverage", "Coverage"}) {
                NodeList ids = body.getElementsByTagNameNS("*", element);
                for (int i = 0; i < ids.getLength(); i++) {
                    addAll(names.coverages, ids.item(i).getTextContent(), ",");
                }
            }
            return;
        }
        // WFS (Query/@typeName(s)) and WMS (StyledLayerDescriptor) requests
        NodeList all = body.getElementsByTagNameNS("*", "*");
        for (int i = -1; i < all.getLength(); i++) {
            Element element = i < 0 ? body : (Element) all.item(i);
            for (String attribute : new String[]{"typeName", "typeNames"}) {
                if (element.hasAttribute(attribute)) {
                    addAll(names.layers, element.getAttribute(attribute), null);
                }
            }
        }
        names.layers.addAll(StyledLayers.names(serialize(body), "Body"));
    }

    /**
     * @return "wfs", "wcs", "wms" or "wps" for a GeoServer reference, "" for
     * a GeoServer reference with another path (e.g. /ows), null for other
     * servers
     */
    static String geoServerService(String href, Collection<String> origins) {
        String trimmed = StringUtils.trimToEmpty(href);
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            if (StringUtils.startsWithIgnoreCase(trimmed, INTERNAL_PREFIX)) {
                // GeoServer compares the prefix only
                throw cannotCheck();
            }
            return null;
        }
        boolean internal = StringUtils.startsWith(trimmed, INTERNAL_PREFIX);
        if (!internal && !origins.contains(origin(uri))) {
            return null;
        }
        String path = StringUtils.defaultString(uri.getPath()).toLowerCase(Locale.ROOT);
        for (String service : new String[]{WFS, WCS, WMS, WPS}) {
            if (path.endsWith("/" + service) || (internal && path.startsWith("/" + service))) {
                return service;
            }
        }
        return "";
    }

    /**
     * @return "scheme://host:port" of the URL (with the default port), or ""
     */
    static String origin(URI uri) {
        if (uri.getScheme() == null || uri.getHost() == null) {
            return "";
        }
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if (port == -1) {
            port = "https".equals(scheme) ? HTTPS_PORT : HTTP_PORT;
        }
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + ":" + port;
    }

    /**
     * @return The query parameters (lower case names, decoded values). A
     * reference may encode "&amp;" as "%26" (e.g. in KVP DataInputs), so the
     * parameters of the once decoded URL are added, too.
     */
    private static Map<String, Set<String>> queryParameters(String href) {
        Map<String, Set<String>> params = new LinkedHashMap<>();
        addQueryParameters(href, params);
        if (href.contains("%")) {
            addQueryParameters(decode(href), params);
        }
        return params;
    }

    private static void addQueryParameters(String href, Map<String, Set<String>> params) {
        int start = href.indexOf('?');
        if (start < 0) {
            return;
        }
        for (String pair : href.substring(start + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = decode(pair.substring(0, eq)).toLowerCase(Locale.ROOT);
            String value = decode(pair.substring(eq + 1));
            params.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(value);
        }
    }

    /**
     * Adds the values of a parameter, split at the separator (null: white
     * space, commas and parentheses).
     */
    private static void addAll(Set<String> target, Map<String, Set<String>> params, String key, String separator) {
        Set<String> values = params.get(key.toLowerCase(Locale.ROOT));
        if (values != null) {
            for (String value : values) {
                addAll(target, value, separator);
            }
        }
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            throw cannotCheck();
        }
    }

    private static void addAll(Set<String> target, String value, String separator) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        String[] parts = separator == null ? NAME_SEPARATORS.split(value) : value.split(Pattern.quote(separator));
        for (String part : parts) {
            if (StringUtils.isNotBlank(part)) {
                target.add(part.trim());
            }
        }
    }

    private static void merge(Names from, Names to) {
        to.processes.addAll(from.processes);
        to.layers.addAll(from.layers);
        to.coverages.addAll(from.coverages);
    }

    private static Document parse(String body) {
        try {
            return CapabilitiesFilter.parserFactory().newDocumentBuilder()
                .parse(new InputSource(new StringReader(StringUtils.defaultString(body))));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new OgcException(OgcException.NO_APPLICABLE_CODE, null,
                "The request is not a valid XML document.");
        }
    }

    private static String serialize(Element element) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(element), new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException e) {
            throw cannotCheck();
        }
    }

    private static String localName(Node node) {
        return node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        if (parent == null) {
            return result;
        }
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && localName.equals(localName(child))) {
                result.add((Element) child);
            }
        }
        return result;
    }

    private static Element first(List<Element> elements) {
        return elements.isEmpty() ? null : elements.get(0);
    }

    private static Element firstChild(Element parent) {
        if (parent == null) {
            return null;
        }
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                return (Element) child;
            }
        }
        return null;
    }

    private static OgcException cannotCheck() {
        return new OgcException(OgcException.NO_APPLICABLE_CODE, null,
            "An input reference of the request cannot be checked.");
    }
}

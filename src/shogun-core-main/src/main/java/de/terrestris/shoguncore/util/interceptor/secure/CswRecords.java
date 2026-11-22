package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Record permissions of the CSW interceptors. GeoServer makes one record per
 * catalog resource, and its identifier is the prefixed layer name
 * (Record.default.properties and MD_Metadata.default.properties of GeoServer:
 * identifier = prefixedName). A record is readable if the user may read the
 * layer.
 * <p>
 * GetRecords pages the readable records itself: the request interceptor asks
 * GeoServer for all matching records (up to a limit), and
 * {@link #page(Document, Set, String, Paging)} removes the other records and
 * returns the requested page with its counts.
 */
final class CswRecords {

    /** Request attribute with the {@link Paging} of the client. */
    static final String PAGING_ATTRIBUTE = CswRecords.class.getName() + ".paging";

    private static final String CSW = "http://www.opengis.net/cat/csw/2.0.2";

    private static final String DC = "http://purl.org/dc/elements/1.1/";

    private static final String GMD = "http://www.isotc211.org/2005/gmd";

    /** Record elements of the CSW 2.0.2 output schemas (Dublin Core and ISO). */
    private static final Set<String> RECORDS = new HashSet<>(Arrays.asList(
        "Record", "BriefRecord", "SummaryRecord", "MD_Metadata"));

    /** The CSW default of maxRecords. */
    static final int DEFAULT_MAX_RECORDS = 10;

    private CswRecords() {
    }

    /**
     * The paging of a GetRecords request of the client.
     */
    static final class Paging {

        private final int startPosition;

        private final int maxRecords;

        private final String resultType;

        Paging(int startPosition, int maxRecords, String resultType) {
            this.startPosition = startPosition;
            this.maxRecords = maxRecords;
            this.resultType = resultType;
        }

        int getStartPosition() {
            return startPosition;
        }

        int getMaxRecords() {
            return maxRecords;
        }

        String getResultType() {
            return resultType;
        }

        boolean isHits() {
            return "hits".equalsIgnoreCase(resultType);
        }

        boolean isValidate() {
            return "validate".equalsIgnoreCase(resultType);
        }
    }

    /**
     * @param value A startPosition or maxRecords value (may be null)
     * @param defaultValue The CSW default
     * @param locator The parameter, for the exception
     * @return The number
     */
    static int number(String value, int defaultValue, String locator) {
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            int number = Integer.parseInt(value.trim());
            if (number < 0) {
                throw new NumberFormatException(value);
            }
            return number;
        } catch (NumberFormatException e) {
            throw new OgcException(OgcException.INVALID_PARAMETER_VALUE, locator,
                "Invalid value for " + locator + ": " + value);
        }
    }

    /**
     * @return The identifier of a record element (dc:identifier, or
     * gmd:fileIdentifier/gco:CharacterString of ISO), or null
     */
    static String identifier(Element record) {
        for (Node child = record.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element)) {
                continue;
            }
            if ("identifier".equals(child.getLocalName()) && DC.equals(child.getNamespaceURI())) {
                return child.getTextContent().trim();
            }
            if ("fileIdentifier".equals(child.getLocalName()) && GMD.equals(child.getNamespaceURI())) {
                return child.getTextContent().trim();
            }
        }
        return null;
    }

    /**
     * @return true if the element is a record of a CSW response
     */
    static boolean isRecord(Node node) {
        return node instanceof Element && RECORDS.contains(node.getLocalName());
    }

    /**
     * @return true if the user may read the record
     */
    static boolean isReadable(Element record, Set<String> allowed, String namespace) {
        String id = identifier(record);
        return id != null && !id.isEmpty() && allowed.contains(LayerNames.key(id, namespace));
    }

    /**
     * Removes the records that the user may not read (e.g. of a
     * GetRecordById response).
     *
     * @return The number of records that stay
     */
    static int removeUnreadable(Element parent, Set<String> allowed, String namespace) {
        int kept = 0;
        List<Element> remove = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (isRecord(child)) {
                if (isReadable((Element) child, allowed, namespace)) {
                    kept++;
                } else {
                    remove.add((Element) child);
                }
            }
        }
        remove.forEach(parent::removeChild);
        return kept;
    }

    /**
     * Changes a GetRecordsResponse of all matching records (startPosition 1)
     * to the page that the client asked for, with readable records only:
     * numberOfRecordsMatched is the number of readable records,
     * numberOfRecordsReturned the number of records of the page, and
     * nextRecord the position after the page (0 if there are no more
     * records).
     */
    static void page(Document doc, Set<String> allowed, String namespace, Paging paging) {
        Element results = searchResults(doc);
        if (results == null) {
            return;
        }
        int matched = parse(results.getAttribute("numberOfRecordsMatched"));
        int returned = parse(results.getAttribute("numberOfRecordsReturned"));
        if (matched > returned) {
            // GeoServer did not return all matching records (limit)
            throw new OgcException(OgcException.NO_APPLICABLE_CODE, null,
                "The query matches too many records. Use a constraint to select fewer records.");
        }
        List<Element> readable = new ArrayList<>();
        List<Element> all = new ArrayList<>();
        for (Node child = results.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (isRecord(child)) {
                all.add((Element) child);
                if (isReadable((Element) child, allowed, namespace)) {
                    readable.add((Element) child);
                }
            }
        }
        int from = Math.max(paging.getStartPosition(), 1) - 1;
        int to = paging.isHits() ? from : (int) Math.min((long) from + paging.getMaxRecords(), readable.size());
        List<Element> page = from < to ? readable.subList(from, to) : new ArrayList<>();
        for (Element record : all) {
            if (!page.contains(record)) {
                results.removeChild(record);
            }
        }
        int next = 0;
        if (!paging.isHits() && from + page.size() < readable.size()) {
            next = from + page.size() + 1;
        }
        results.setAttribute("numberOfRecordsMatched", Integer.toString(readable.size()));
        results.setAttribute("numberOfRecordsReturned", Integer.toString(page.size()));
        results.setAttribute("nextRecord", Integer.toString(next));
    }

    private static Element searchResults(Document doc) {
        List<Element> stack = new ArrayList<>();
        stack.add(doc.getDocumentElement());
        while (!stack.isEmpty()) {
            Element element = stack.remove(0);
            if ("SearchResults".equals(element.getLocalName()) && CSW.equals(element.getNamespaceURI())) {
                return element;
            }
            for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child instanceof Element) {
                    stack.add((Element) child);
                }
            }
        }
        return null;
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Rewrites GeoServer service URLs in attributes and text (e.g.
     * dc:references, gmd:URL, OnlineResource) to the interceptor. The
     * GeoServer root URL (e.g. the ProviderSite of the capabilities) becomes
     * the interceptor URL, too.
     */
    static void rewriteUrls(Node node, String geoServerUri, String geoServerRoot, String baseUrl) {
        if (node instanceof Element) {
            NamedNodeMap attributes = node.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr attribute = (Attr) attributes.item(i);
                if (looksLikeUrl(attribute.getValue())) {
                    attribute.setValue(rewrite(attribute.getValue(), geoServerUri, geoServerRoot, baseUrl));
                }
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                String text = child.getNodeValue();
                if (looksLikeUrl(text)) {
                    String trimmed = text.trim();
                    child.setNodeValue(text.replace(trimmed, rewrite(trimmed, geoServerUri, geoServerRoot, baseUrl)));
                }
            } else {
                rewriteUrls(child, geoServerUri, geoServerRoot, baseUrl);
            }
        }
    }

    private static String rewrite(String url, String geoServerUri, String geoServerRoot, String baseUrl) {
        if (StringUtils.removeEnd(url, "/").equals(StringUtils.removeEnd(geoServerRoot, "/"))) {
            return baseUrl;
        }
        return ProxyUrls.rewrite(url, geoServerUri, baseUrl);
    }

    private static boolean looksLikeUrl(String value) {
        String trimmed = StringUtils.trimToEmpty(value).toLowerCase(Locale.ROOT);
        return (trimmed.startsWith("http://") || trimmed.startsWith("https://")) && !trimmed.contains(" ");
    }

    /**
     * @return The paging of the client, stored by the request interceptor
     */
    static Paging paging(MutableHttpServletRequest request) {
        Object paging = request.getAttribute(PAGING_ATTRIBUTE);
        return paging instanceof Paging ? (Paging) paging : null;
    }
}

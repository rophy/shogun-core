package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcXmlUtil;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the feature type names of WFS 1.x and 2.0 requests (KVP and XML).
 */
final class TypeNames {

    /** KVP parameters: WFS 2.0 (typeNames) and WFS 1.x (typeName). */
    private static final String[] PARAMS = {"typeNames", "typeName"};

    /** XML attributes with (space separated) type names. */
    private static final String[] ATTRIBUTES = {"typeNames", "typeName", "returnFeatureTypes"};

    /** KVP namespace bindings, e.g. xmlns(ns1,http://example.com). */
    private static final Pattern XMLNS = Pattern.compile("xmlns\\(([^,()]*),([^()]*)\\)");

    /**
     * Marks a name with a prefix that the request binds to a namespace URI
     * (NAMESPACES parameter or xmlns attribute).
     */
    static final String DECLARED = "{declared}";

    private TypeNames() {
    }

    /**
     * @param name A name from {@link #fromRequest}
     * @param namespace The endpoint namespace
     * @return The key to compare with the layer names. On a (workspace bound)
     * endpoint, GeoServer resolves a name with a declared prefix in the
     * workspace of the endpoint, so its local name is used.
     */
    static String key(String name, String namespace) {
        if (name.startsWith(DECLARED)) {
            String qualified = name.substring(DECLARED.length());
            if (StringUtils.isNotEmpty(namespace)) {
                return LayerNames.key(qualified.substring(qualified.indexOf(':') + 1), namespace);
            }
            return LayerNames.key(qualified, namespace);
        }
        return LayerNames.key(name, namespace);
    }

    /**
     * @return The name without the {@link #DECLARED} mark
     */
    static String display(String name) {
        return StringUtils.removeStart(name, DECLARED);
    }

    /**
     * @return The type names of the request, e.g. "sf:PrimitiveGeoFeature".
     * Empty if the request names no type (e.g. a stored query).
     */
    static Set<String> fromRequest(MutableHttpServletRequest request) {
        Set<String> names = new LinkedHashSet<>();
        for (String param : PARAMS) {
            String value = request.getParameterIgnoreCase(param);
            if (value != null) {
                Set<String> declared = new HashSet<>();
                String namespaces = StringUtils.defaultString(request.getParameterIgnoreCase("namespaces"),
                    request.getParameterIgnoreCase("namespace"));
                Matcher matcher = XMLNS.matcher(StringUtils.defaultString(namespaces));
                while (matcher.find()) {
                    declared.add(matcher.group(1).trim());
                }
                // e.g. "a,b" or "(a,b)(c)" (joins)
                Set<String> plain = new LinkedHashSet<>();
                addAll(plain, value.split("[,()\\s]+"));
                for (String name : plain) {
                    names.add(isDeclared(name, declared) ? DECLARED + name : name);
                }
                return names;
            }
        }
        String body = OgcXmlUtil.getRequestBody(request);
        if (StringUtils.isNotBlank(body) && body.trim().startsWith("<")) {
            fromXml(body, names);
        }
        return names;
    }

    private static void fromXml(String body, Set<String> names) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(body)));
            NodeList all = doc.getElementsByTagName("*");
            for (int i = 0; i < all.getLength(); i++) {
                Element element = (Element) all.item(i);
                for (String attribute : ATTRIBUTES) {
                    if (element.hasAttribute(attribute)) {
                        // e.g. "sf:A sf:B" or "schema-element(sf:A)"
                        Set<String> plain = new LinkedHashSet<>();
                        addAll(plain, element.getAttribute(attribute)
                            .replace("schema-element(", " ").split("[()\\s]+"));
                        for (String name : plain) {
                            String prefix = name.contains(":") ? name.substring(0, name.indexOf(':')) : null;
                            boolean declared = prefix != null && element.lookupNamespaceURI(prefix) != null;
                            names.add(declared ? DECLARED + name : name);
                        }
                    }
                }
                String localName = element.getLocalName();
                if ("TypeName".equals(localName)) {
                    // WFS 1.0 DescribeFeatureType
                    addAll(names, element.getTextContent().split("[,\\s]+"));
                }
                if ("Insert".equals(localName) || "Replace".equals(localName)) {
                    // WFS-T: the children are the features (Replace also has a filter)
                    NodeList children = element.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        if (children.item(j) instanceof Element
                            && !"Filter".equals(children.item(j).getLocalName())) {
                            Element feature = (Element) children.item(j);
                            String prefix = feature.getPrefix();
                            names.add(StringUtils.isEmpty(prefix) ? feature.getLocalName()
                                : DECLARED + prefix + ":" + feature.getLocalName());
                        }
                    }
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            // GeoServer reports the invalid request
            return;
        }
    }

    private static boolean isDeclared(String name, Set<String> prefixes) {
        int index = name.indexOf(':');
        return index > 0 && prefixes.contains(name.substring(0, index));
    }

    private static void addAll(Set<String> names, String[] values) {
        for (String value : values) {
            // ignore stored query parameter placeholders such as ${typeName}
            if (StringUtils.isNotBlank(value) && !value.contains("$")) {
                names.add(value.trim());
            }
        }
    }
}

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
import java.io.IOException;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Reads the layer names of a Styled Layer Descriptor (SLD 1.0 and 1.1), e.g.
 * of the WMS parameters SLD_BODY or SLD, or of a WMS GetMap XML request.
 * GeoServer takes the layers of a request from the SLD if LAYERS is not
 * given, so each name must be checked.
 * <p>
 * Names are read from:
 * <ul>
 * <li>NamedLayer: its Name (a layer of GeoServer).</li>
 * <li>UserLayer without InlineFeature: its Name (GeoServer uses it as the
 * layer name, or as the remote layer of a RemoteOWS).</li>
 * <li>FeatureTypeName of LayerFeatureConstraints in both (the feature type
 * GeoServer reads).</li>
 * </ul>
 * A UserLayer with InlineFeature has its data in the document, so its names
 * are not layer names.
 */
final class StyledLayers {

    private static final String NAMED_LAYER = "NamedLayer";

    private static final String USER_LAYER = "UserLayer";

    private StyledLayers() {
    }

    /**
     * @param xml An SLD document, or an XML document with one (e.g. a GetMap
     * XML request)
     * @param locator The parameter of the document, for the exception
     * @return The layer names, in document order
     * @throws OgcException InvalidParameterValue if the document is not XML
     */
    static Set<String> names(String xml, String locator) {
        Document doc;
        try {
            doc = CapabilitiesFilter.parserFactory().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new OgcException(OgcException.INVALID_PARAMETER_VALUE, locator,
                "The style document is not a valid XML document.");
        }
        Set<String> names = new LinkedHashSet<>();
        collect(doc.getDocumentElement(), names);
        return names;
    }

    private static void collect(Element element, Set<String> names) {
        String name = localName(element);
        if (NAMED_LAYER.equals(name) || USER_LAYER.equals(name)) {
            if (USER_LAYER.equals(name) && hasDescendant(element, "InlineFeature")) {
                return;
            }
            addText(childElement(element, "Name"), names);
            NodeList featureTypes = element.getElementsByTagNameNS("*", "FeatureTypeName");
            for (int i = 0; i < featureTypes.getLength(); i++) {
                addText(featureTypes.item(i), names);
            }
            return;
        }
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                collect((Element) child, names);
            }
        }
    }

    private static String localName(Node node) {
        return node.getLocalName() != null ? node.getLocalName() : node.getNodeName();
    }

    private static Element childElement(Element parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && localName.equals(localName(child))) {
                return (Element) child;
            }
        }
        return null;
    }

    private static boolean hasDescendant(Element element, String localName) {
        return element.getElementsByTagNameNS("*", localName).getLength() > 0;
    }

    private static void addText(Node node, Set<String> names) {
        if (node != null && StringUtils.isNotBlank(node.getTextContent())) {
            names.add(node.getTextContent().trim());
        }
    }
}

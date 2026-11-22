package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.OgcException;
import de.terrestris.shoguncore.util.model.Response;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathExpressionException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

import static org.apache.logging.log4j.LogManager.getLogger;

/**
 * Parses, changes and writes a capabilities document. Exception reports of
 * GeoServer are sent to the client unchanged. If the document cannot be
 * changed, the client gets an exception, not the unfiltered document.
 */
final class CapabilitiesFilter {

    private static final Logger LOG = getLogger(CapabilitiesFilter.class);

    /**
     * Changes the document.
     */
    interface Change {
        void apply(Document doc) throws XPathExpressionException;
    }

    private CapabilitiesFilter() {
    }

    /**
     * @return The first element in the SOAP body, or null
     */
    private static Element soapContent(Element envelope) {
        for (Node node = envelope.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element && "Body".equals(node.getLocalName())) {
                for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child instanceof Element) {
                        return (Element) child;
                    }
                }
            }
        }
        return null;
    }

    /**
     * @return true if the response body is an XML document
     */
    static boolean isXml(Response response) {
        return isXml(response.getBody());
    }

    /**
     * @return true if the body is an XML document
     */
    static boolean isXml(byte[] body) {
        if (body == null) {
            return false;
        }
        int i = 0;
        // skip white space and a UTF-8 byte order mark (negative bytes)
        while (i < body.length && (Character.isWhitespace(body[i]) || body[i] < 0)) {
            i++;
        }
        return i < body.length && body[i] == '<';
    }

    /**
     * A namespace aware parser that keeps a DOCTYPE (e.g. of WMS 1.1.1
     * capabilities), but does not load external DTDs or entities.
     */
    static DocumentBuilderFactory parserFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    /**
     * Writes the DOCTYPE of the document again (a DOM transform drops it).
     */
    static void keepDoctype(Document doc, Transformer transformer) {
        DocumentType doctype = doc.getDoctype();
        if (doctype == null || doctype.getSystemId() == null) {
            return;
        }
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, doctype.getSystemId());
        if (doctype.getPublicId() != null) {
            transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC, doctype.getPublicId());
        }
    }

    static Response filter(Response response, Change change) {
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            return response;
        }
        try {
            DocumentBuilderFactory factory = parserFactory();
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(body));
            Element root = doc.getDocumentElement();
            Element soapContent = "Envelope".equals(root.getLocalName()) ? soapContent(root) : null;
            Element content = soapContent != null ? soapContent : root;
            String rootName = content.getLocalName() == null ? content.getNodeName() : content.getLocalName();
            if (rootName.toLowerCase(Locale.ROOT).contains("exception") || "Fault".equals(rootName)) {
                return response;
            }
            if (soapContent != null) {
                // change a copy of the SOAP body content, then put it back
                Document inner = factory.newDocumentBuilder().newDocument();
                inner.appendChild(inner.importNode(soapContent, true));
                change.apply(inner);
                soapContent.getParentNode().replaceChild(doc.importNode(inner.getDocumentElement(), true),
                    soapContent);
            } else {
                change.apply(doc);
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            keepDoctype(doc, transformer);
            transformer.transform(new DOMSource(doc), new StreamResult(bos));
            response.setBody(bos.toByteArray());
            return response;
        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException
            | TransformerException | RuntimeException e) {
            LOG.warn("Could not filter the capabilities document: " + e.getMessage());
            LOG.trace("Stack trace", e);
            throw new OgcException(OgcException.NO_APPLICABLE_CODE, null,
                "Could not filter the capabilities document.");
        }
    }
}

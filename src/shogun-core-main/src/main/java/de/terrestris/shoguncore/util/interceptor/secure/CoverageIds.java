package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcXmlUtil;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the coverage identifiers of WCS 1.0, 1.1 and 2.0 requests (KVP and
 * XML).
 */
final class CoverageIds {

    /** KVP parameters: 2.0 (coverageId), 1.1 (identifiers, identifier), 1.0 (coverage). */
    private static final String[] PARAMS = {"coverageId", "identifiers", "identifier", "coverage"};

    private static final String XPATH = "//*[local-name()='CoverageId']"
        + " | /*[local-name()='DescribeCoverage' or local-name()='GetCoverage']/*[local-name()='Identifier']"
        + " | /*[local-name()='DescribeCoverage']/*[local-name()='Coverage']"
        + " | /*[local-name()='GetCoverage']/*[local-name()='sourceCoverage']";

    private CoverageIds() {
    }

    /**
     * @return The parameter name of the identifiers, for exception locators
     */
    static String locator(MutableHttpServletRequest request) {
        for (String param : PARAMS) {
            if (request.getParameterIgnoreCase(param) != null) {
                return param;
            }
        }
        return PARAMS[0];
    }

    /**
     * @return true for a WCS 2.0 request (coverageId parameter or WCS 2.0 XML)
     */
    static boolean isWcs2(MutableHttpServletRequest request) {
        if (request.getParameterIgnoreCase("coverageId") != null) {
            return true;
        }
        String body = OgcXmlUtil.getRequestBody(request);
        return body != null && body.contains("http://www.opengis.net/wcs/2.0");
    }

    /**
     * @return The requested coverage identifiers. Empty if there are none.
     */
    static List<String> fromRequest(MutableHttpServletRequest request) {
        List<String> ids = new ArrayList<>();
        for (String param : PARAMS) {
            String value = request.getParameterIgnoreCase(param);
            if (value != null) {
                for (String id : value.split(",")) {
                    if (StringUtils.isNotBlank(id)) {
                        ids.add(id.trim());
                    }
                }
                return ids;
            }
        }
        String body = OgcXmlUtil.getRequestBody(request);
        if (StringUtils.isBlank(body)) {
            return ids;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(body)));
            NodeList nodes = (NodeList) XPathFactory.newInstance().newXPath()
                .evaluate(XPATH, doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                String id = nodes.item(i).getTextContent();
                if (StringUtils.isNotBlank(id)) {
                    ids.add(id.trim());
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException | XPathExpressionException e) {
            // GeoServer reports the invalid request
            return ids;
        }
        return ids;
    }
}

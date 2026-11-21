package de.terrestris.shoguncore.util.interceptor.secure;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;

public class WmtsResponseInterceptorTest {

    private static final String WMTS = "http://www.opengis.net/wmts/1.0";

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    @Test
    public void only_standard_resource_urls_are_kept() throws Exception {
        Document doc = parse("<Capabilities xmlns='" + WMTS + "' xmlns:ows='http://www.opengis.net/ows/1.1'>"
            + "<Contents><Layer>"
            + "<ResourceURL resourceType='tile' format='image/png' template='a'/>"
            + "<ResourceURL resourceType='FeatureInfo' format='text/xml' template='b'/>"
            + "<ResourceURL resourceType='TileJSON' format='application/json' template='c'/>"
            + "</Layer></Contents></Capabilities>");

        WmtsResponseInterceptor.removeNonStandardResourceUrls(doc);

        NodeList urls = doc.getElementsByTagNameNS(WMTS, "ResourceURL");
        assertEquals(2, urls.getLength());
        assertEquals("tile", urls.item(0).getAttributes().getNamedItem("resourceType").getNodeValue());
        assertEquals("FeatureInfo", urls.item(1).getAttributes().getNamedItem("resourceType").getNodeValue());
    }
}

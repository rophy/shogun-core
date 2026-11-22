package de.terrestris.shoguncore.util.interceptor.secure;

import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class WfsDescribeFeatureTypeFilterTest {

    private static final String XSD = "http://www.w3.org/2001/XMLSchema";

    /** A WFS 1.0 schema of GeoServer: helper types that are not feature types. */
    private static final String SCHEMA = "<xsd:schema xmlns:xsd='" + XSD + "' xmlns:cgf='http://www.opengis.net/cite/geometry'"
        + " xmlns:gml='http://www.opengis.net/gml' targetNamespace='http://www.opengis.net/cite/geometry'>"
        + "<xsd:import namespace='http://www.opengis.net/gml'/>"
        + "<xsd:element name='id' type='xsd:string'/>"
        + "<xsd:complexType abstract='true' name='AbstractSimpleFeatureType'/>"
        + "<xsd:element abstract='true' name='_SimpleFeature' substitutionGroup='gml:_Feature'"
        + " type='cgf:AbstractSimpleFeatureType'/>"
        + "<xsd:complexType name='PointFeatureType'/>"
        + "<xsd:complexType name='LineFeatureType'/>"
        + "<xsd:element name='Points' substitutionGroup='cgf:_SimpleFeature' type='cgf:PointFeatureType'/>"
        + "<xsd:element name='Lines' substitutionGroup='cgf:_SimpleFeature' type='cgf:LineFeatureType'/>"
        + "</xsd:schema>";

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static int count(Document doc, String localName) {
        return doc.getElementsByTagNameNS(XSD, localName).getLength();
    }

    @Test
    public void readable_types_keep_their_helper_types() throws Exception {
        Document doc = parse(SCHEMA);
        WfsResponseInterceptor.removeFeatureTypes(doc, new HashSet<>(Collections.singletonList("cgf:points")), "");
        // id, _SimpleFeature, Points
        assertEquals(3, count(doc, "element"));
        // AbstractSimpleFeatureType, PointFeatureType
        assertEquals(2, count(doc, "complexType"));
        assertEquals(1, count(doc, "import"));
    }

    @Test
    public void unreadable_types_are_removed() throws Exception {
        Document doc = parse(SCHEMA);
        WfsResponseInterceptor.removeFeatureTypes(doc, Collections.emptySet(), "cgf");
        // id, _SimpleFeature
        assertEquals(2, count(doc, "element"));
        assertEquals(1, count(doc, "complexType"));
    }

    @Test
    public void unqualified_layer_names_use_the_endpoint_namespace() throws Exception {
        Document doc = parse(SCHEMA);
        WfsResponseInterceptor.removeFeatureTypes(doc, new HashSet<>(Collections.singletonList("cgf:lines")), "cgf");
        assertEquals(3, count(doc, "element"));
        assertEquals("Lines", doc.getElementsByTagNameNS(XSD, "element").item(2).getAttributes()
            .getNamedItem("name").getNodeValue());
    }
}

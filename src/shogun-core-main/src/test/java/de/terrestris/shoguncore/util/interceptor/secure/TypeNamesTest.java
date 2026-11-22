package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TypeNamesTest {

    private static MutableHttpServletRequest post(String xml) {
        MockHttpServletRequest mock = new MockHttpServletRequest("POST", "/geoserver.action/sf");
        mock.setContentType("text/xml");
        mock.setContent(xml.getBytes(StandardCharsets.UTF_8));
        return new MutableHttpServletRequest(mock);
    }

    @Test
    public void kvp_lists_and_joins_are_split() {
        MockHttpServletRequest mock = new MockHttpServletRequest("GET", "/geoserver.action/sf");
        mock.addParameter("TYPENAMES", "(sf:A,sf:B)(sf:C)");
        assertEquals(new LinkedHashSet<>(Arrays.asList("sf:A", "sf:B", "sf:C")),
            TypeNames.fromRequest(new MutableHttpServletRequest(mock)));
    }

    @Test
    public void kvp_prefixes_bound_by_namespaces_use_the_endpoint_workspace() {
        MockHttpServletRequest mock = new MockHttpServletRequest("GET", "/geoserver.action/sf");
        mock.addParameter("typenames", "ns1:PrimitiveGeoFeature");
        mock.addParameter("namespaces", "xmlns(ns1,http://cite.opengeospatial.org/gmlsf)");
        Set<String> names = TypeNames.fromRequest(new MutableHttpServletRequest(mock));
        assertEquals(1, names.size());
        assertEquals("sf:primitivegeofeature", TypeNames.key(names.iterator().next(), "sf"));
        assertEquals("ns1:PrimitiveGeoFeature", TypeNames.display(names.iterator().next()));
    }

    @Test
    public void kvp_prefixes_bound_by_the_wfs_11_namespace_parameter() {
        MockHttpServletRequest mock = new MockHttpServletRequest("GET", "/geoserver.action/sf");
        mock.addParameter("typename", "app:EntitéGénérique");
        mock.addParameter("namespace", "xmlns(app=http://cite.opengeospatial.org/gmlsf)");
        Set<String> names = TypeNames.fromRequest(new MutableHttpServletRequest(mock));
        assertEquals("sf:entitégénérique", TypeNames.key(names.iterator().next(), "sf"));
    }

    @Test
    public void xml_type_names_inserts_and_replaces_are_read() {
        Set<String> names = TypeNames.fromRequest(post(
            "<wfs:Transaction xmlns:wfs='http://www.opengis.net/wfs/2.0' xmlns:fes='http://www.opengis.net/fes/2.0'"
                + " xmlns:t='http://example.org'>"
                + "<wfs:Insert><t:Airport/></wfs:Insert>"
                + "<wfs:Replace><t:Road/><fes:Filter/></wfs:Replace>"
                + "<wfs:Delete typeName='t:River'/></wfs:Transaction>"));
        assertEquals(3, names.size());
        for (String name : names) {
            assertTrue(name, name.startsWith(TypeNames.DECLARED));
        }
    }

    @Test
    public void soap_requests_are_read() {
        Set<String> names = TypeNames.fromRequest(post(
            "<soap:Envelope xmlns:soap='http://www.w3.org/2003/05/soap-envelope'><soap:Body>"
                + "<wfs:GetFeature xmlns:wfs='http://www.opengis.net/wfs/2.0'><wfs:Query typeNames='sf:A'/>"
                + "</wfs:GetFeature></soap:Body></soap:Envelope>"));
        assertEquals(1, names.size());
    }

    @Test
    public void stored_query_placeholders_are_ignored() {
        Set<String> names = TypeNames.fromRequest(post(
            "<wfs:CreateStoredQuery xmlns:wfs='http://www.opengis.net/wfs/2.0'><wfs:StoredQueryDefinition>"
                + "<wfs:QueryExpressionText returnFeatureTypes='${typeName}'/></wfs:StoredQueryDefinition>"
                + "</wfs:CreateStoredQuery>"));
        assertTrue(names.isEmpty());
    }
}

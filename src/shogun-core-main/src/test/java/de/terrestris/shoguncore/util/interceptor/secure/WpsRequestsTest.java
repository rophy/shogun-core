package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.OgcException;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WpsRequestsTest {

    private static final List<String> ORIGINS = Collections.singletonList("http://gs.example.com:8080");

    private static LinkedHashSet<String> set(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    private static String execute(String identifier, String inputs) {
        return "<wps:Execute service='WPS' version='1.0.0' xmlns:wps='http://www.opengis.net/wps/1.0.0'"
            + " xmlns:ows='http://www.opengis.net/ows/1.1' xmlns:xlink='http://www.w3.org/1999/xlink'"
            + " xmlns:wfs='http://www.opengis.net/wfs' xmlns:wcs='http://www.opengis.net/wcs/1.1.1'>"
            + "<ows:Identifier>" + identifier + "</ows:Identifier><wps:DataInputs>" + inputs
            + "</wps:DataInputs></wps:Execute>";
    }

    private static String input(String reference) {
        return "<wps:Input><ows:Identifier>features</ows:Identifier>" + reference + "</wps:Input>";
    }

    @Test
    public void xml_execute_with_internal_wfs_and_wcs_references() {
        String xml = execute("gs:Bounds", input("<wps:Reference xlink:href='http://geoserver/wfs' method='POST'>"
            + "<wps:Body><wfs:GetFeature service='WFS' version='1.0.0'><wfs:Query typeName='cite:Lakes'/>"
            + "</wfs:GetFeature></wps:Body></wps:Reference>")
            + input("<wps:Reference xlink:href='http://geoserver/wcs' method='POST'><wps:Body>"
            + "<wcs:GetCoverage service='WCS' version='1.1.1'><ows:Identifier>topp:tazdem</ows:Identifier>"
            + "</wcs:GetCoverage></wps:Body></wps:Reference>"));
        WpsRequests.Names names = WpsRequests.ofXml(xml, ORIGINS);
        assertEquals(set("gs:Bounds"), names.getProcesses());
        assertEquals(set("cite:Lakes"), names.getLayers());
        assertEquals(set("topp:tazdem"), names.getCoverages());
    }

    @Test
    public void xml_execute_with_chained_process() {
        String inner = execute("JTS:centroid", input("<wps:Reference xlink:href='http://geoserver/wfs?"
            + "service=WFS&amp;request=GetFeature&amp;typeName=cite:Forests'/>"));
        String xml = execute("JTS:buffer", input("<wps:Reference xlink:href='http://geoserver/wps' method='POST'>"
            + "<wps:Body>" + inner + "</wps:Body></wps:Reference>"));
        WpsRequests.Names names = WpsRequests.ofXml(xml, ORIGINS);
        assertEquals(set("JTS:buffer", "JTS:centroid"), names.getProcesses());
        assertEquals(set("cite:Forests"), names.getLayers());
    }

    @Test
    public void references_to_the_geoserver_url_are_layers_others_are_not() {
        String xml = execute("gs:Bounds",
            input("<wps:Reference xlink:href='http://gs.example.com:8080/geoserver/cite/ows?service=WFS"
                + "&amp;request=GetFeature&amp;typeNames=cite:Ponds,cite:Streams'/>")
            + input("<wps:Reference xlink:href='http://gs.example.com:8080/geoserver/topp/wcs?"
                + "service=WCS&amp;request=GetCoverage&amp;coverageId=topp__tazbm'/>")
            + input("<wps:Reference xlink:href='http://gs.example.com:8080/geoserver/wms?"
                + "request=GetMap&amp;layers=cite:Bridges'/>")
            + input("<wps:Reference xlink:href='http://other.example.com/wfs?typeName=cite:Secret'/>"));
        WpsRequests.Names names = WpsRequests.ofXml(xml, ORIGINS);
        assertEquals(set("cite:Ponds", "cite:Streams", "cite:Bridges"), names.getLayers());
        assertEquals(set("topp__tazbm"), names.getCoverages());
    }

    @Test
    public void wfs_body_with_typenames_list_and_sld_body_of_wms_reference() {
        String xml = execute("gs:Bounds", input("<wps:Reference xlink:href='http://geoserver/wfs'><wps:Body>"
            + "<wfs:GetFeature xmlns:wfs='http://www.opengis.net/wfs/2.0' service='WFS' version='2.0.0'>"
            + "<wfs:Query typeNames='sf:PrimitiveGeoFeature sf:AggregateGeoFeature'/></wfs:GetFeature>"
            + "</wps:Body></wps:Reference>")
            + input("<wps:Reference xlink:href='http://geoserver/ows?service=WMS&amp;request=GetMap&amp;"
            + "sld_body=%3CStyledLayerDescriptor%3E%3CNamedLayer%3E%3CName%3Ecite%3ALakes%3C%2FName%3E"
            + "%3C%2FNamedLayer%3E%3C%2FStyledLayerDescriptor%3E'/>"));
        assertEquals(set("sf:PrimitiveGeoFeature", "sf:AggregateGeoFeature", "cite:Lakes"),
            WpsRequests.ofXml(xml, ORIGINS).getLayers());
    }

    @Test
    public void body_references_to_geoserver_are_refused() {
        String xml = execute("gs:Bounds", input("<wps:Reference xlink:href='http://geoserver/wfs'>"
            + "<wps:BodyReference xlink:href='http://other.example.com/getfeature.xml'/></wps:Reference>"));
        try {
            WpsRequests.ofXml(xml, ORIGINS);
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(OgcException.NO_APPLICABLE_CODE, e.getCode());
        }
    }

    @Test
    public void kvp_execute_data_inputs() {
        WpsRequests.Names names = WpsRequests.ofExecuteKvp("gs:Bounds",
            "features=@mimetype=text/xml@xlink:href=http://geoserver/wfs?request=GetFeature&typeName=cite:Lakes;"
                + "distance=10;geom=@href=http://other.example.com/a.wkt", ORIGINS);
        assertEquals(set("gs:Bounds"), names.getProcesses());
        assertEquals(set("cite:Lakes"), names.getLayers());
    }

    @Test
    public void kvp_reference_to_wps_is_a_chained_process() {
        WpsRequests.Names names = WpsRequests.ofExecuteKvp("JTS:buffer",
            "geom=@href=http://geoserver/wps?service=WPS%26request=Execute%26identifier=gs:Secret", ORIGINS);
        assertEquals(set("JTS:buffer", "gs:Secret"), names.getProcesses());
    }

    @Test
    public void describe_process() {
        assertEquals(set("JTS:buffer", "gs:Bounds"), WpsRequests.ofDescribeProcessKvp("JTS:buffer, gs:Bounds"));
        assertEquals(set("ALL"), WpsRequests.ofDescribeProcessKvp("ALL"));
        assertEquals(set(), WpsRequests.ofDescribeProcessKvp(null));
        String xml = "<wps:DescribeProcess service='WPS' version='1.0.0' xmlns:wps='http://www.opengis.net/wps/1.0.0'"
            + " xmlns:ows='http://www.opengis.net/ows/1.1'><ows:Identifier>JTS:buffer</ows:Identifier>"
            + "<ows:Identifier>gs:Bounds</ows:Identifier></wps:DescribeProcess>";
        assertEquals(set("JTS:buffer", "gs:Bounds"), WpsRequests.ofXml(xml, ORIGINS).getProcesses());
    }

    @Test
    public void geoserver_services_of_references() {
        assertEquals("wfs", WpsRequests.geoServerService("http://geoserver/wfs", ORIGINS));
        assertEquals("wps", WpsRequests.geoServerService("http://geoserver/wps?x=1", ORIGINS));
        assertEquals("wcs", WpsRequests.geoServerService("http://gs.example.com:8080/geoserver/topp/wcs", ORIGINS));
        assertEquals("", WpsRequests.geoServerService("http://gs.example.com:8080/geoserver/ows", ORIGINS));
        assertNull(WpsRequests.geoServerService("http://gs.example.com/geoserver/wfs", ORIGINS));
        assertNull(WpsRequests.geoServerService("http://other.example.com/wfs", ORIGINS));
        assertEquals("http://gs.example.com:80", WpsRequests.origin(URI.create("HTTP://GS.example.com/x")));
        assertEquals("", WpsRequests.origin(URI.create("/relative")));
        try {
            WpsRequests.geoServerService("http://geoserver/wfs?a=b c", ORIGINS);
            fail("Not refused");
        } catch (OgcException e) {
            assertTrue(e.getMessage().contains("cannot be checked"));
        }
    }

    @Test
    public void invalid_xml_is_refused() {
        try {
            WpsRequests.ofXml("<wps:Execute", ORIGINS);
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(OgcException.NO_APPLICABLE_CODE, e.getCode());
        }
    }
}

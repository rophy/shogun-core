package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.dao.WpsProcessDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.ImageWmsLayerDataSource;
import de.terrestris.shoguncore.model.wps.WpsProcess;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.service.WpsProcessService;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import de.terrestris.shoguncore.util.model.Response;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The user may run JTS:* and gs:Bounds, and read cite:Lakes and topp:tazdem.
 */
public class WpsInterceptorsTest {

    private WpsRequestInterceptor requestInterceptor;

    private WpsResponseInterceptor responseInterceptor;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        WpsProcessService<WpsProcess, WpsProcessDao<WpsProcess>> processService = mock(WpsProcessService.class);
        when(processService.findAll()).thenReturn(Arrays.asList(new WpsProcess("JTS:*"), new WpsProcess("gs:Bounds")));
        ImageWmsLayerDataSource source = new ImageWmsLayerDataSource();
        source.setUrl("/geoserver.action");
        source.setLayerNames("cite:Lakes,topp:tazdem");
        Layer layer = new Layer();
        layer.setSource(source);
        LayerService<Layer, LayerDao<Layer>> layerService = mock(LayerService.class);
        when(layerService.findAll()).thenReturn(Collections.singletonList(layer));

        requestInterceptor = new WpsRequestInterceptor();
        requestInterceptor.wpsProcessService = processService;
        requestInterceptor.layerService = layerService;
        Properties namespaces = new Properties();
        namespaces.setProperty("cite", "http://gs.example.com:8080/geoserver/cite/ows");
        requestInterceptor.geoServerNameSpaces = namespaces;
        ReflectionTestUtils.setField(requestInterceptor, "gsUser", "admin");
        ReflectionTestUtils.setField(requestInterceptor, "gsPass", "geoserver");

        responseInterceptor = new WpsResponseInterceptor();
        responseInterceptor.wpsProcessService = processService;
    }

    private static MutableHttpServletRequest kvp(String... params) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/geoserver.action/cite");
        for (int i = 0; i < params.length; i += 2) {
            request.addParameter(params[i], params[i + 1]);
        }
        MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
        mutable.addParameter("CUSTOM_ENDPOINT", "cite");
        mutable.setRequestURI("http://gs.example.com:8080/geoserver/cite/ows");
        return mutable;
    }

    private static MutableHttpServletRequest xml(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/geoserver.action/cite");
        request.setContentType("text/xml");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
        mutable.addParameter("CUSTOM_ENDPOINT", "cite");
        mutable.setRequestURI("http://gs.example.com:8080/geoserver/cite/ows");
        return mutable;
    }

    private static void assertUnknownProcess(String identifier, Runnable call) {
        try {
            call.run();
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(OgcException.NO_APPLICABLE_CODE, e.getCode());
            assertEquals("No such process: " + identifier, e.getMessage());
            assertEquals(HttpStatus.OK, e.getHttpStatus("WPS"));
        }
    }

    private static void assertUnknownLayer(String name, Runnable call) {
        try {
            call.run();
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(OgcException.INVALID_PARAMETER_VALUE, e.getCode());
            assertEquals("DataInputs", e.getLocator());
            assertEquals("Unknown layer: " + name, e.getMessage());
        }
    }

    @Test
    public void describe_process_of_allowed_and_refused_processes() {
        requestInterceptor.interceptDescribeProcess(kvp("REQUEST", "DescribeProcess", "identifier", "JTS:buffer,gs:Bounds"));
        assertUnknownProcess("ALL", () -> requestInterceptor.interceptDescribeProcess(
            kvp("REQUEST", "DescribeProcess", "identifier", "ALL")));
        assertUnknownProcess("gs:Import", () -> requestInterceptor.interceptDescribeProcess(
            kvp("REQUEST", "DescribeProcess", "identifier", "JTS:buffer,gs:Import")));
        assertUnknownProcess("gs:Import", () -> requestInterceptor.interceptDescribeProcess(xml(
            "<wps:DescribeProcess xmlns:wps='http://www.opengis.net/wps/1.0.0' xmlns:ows='http://www.opengis.net/ows/1.1'"
                + " service='WPS' version='1.0.0'><ows:Identifier>gs:Import</ows:Identifier></wps:DescribeProcess>")));
    }

    @Test
    public void execute_checks_the_process_and_the_input_layers() {
        requestInterceptor.interceptExecute(kvp("REQUEST", "Execute", "identifier", "gs:Bounds", "DataInputs",
            "features=@href=http://geoserver/wfs?request=GetFeature&typeName=cite:Lakes"));
        assertUnknownProcess("gs:Import", () -> requestInterceptor.interceptExecute(
            kvp("REQUEST", "Execute", "identifier", "gs:Import")));
        assertUnknownLayer("Forests", () -> requestInterceptor.interceptExecute(kvp("REQUEST", "Execute",
            "identifier", "gs:Bounds", "DataInputs",
            "features=@href=http://gs.example.com:8080/geoserver/wfs?request=GetFeature&typeName=Forests")));
        assertUnknownLayer("topp__tazbm", () -> requestInterceptor.interceptExecute(kvp("REQUEST", "Execute",
            "identifier", "gs:Bounds", "DataInputs",
            "c=@href=http://geoserver/wcs?request=GetCoverage&coverageId=topp__tazbm")));
        requestInterceptor.interceptExecute(kvp("REQUEST", "Execute", "identifier", "gs:Bounds", "DataInputs",
            "c=@href=http://geoserver/wcs?request=GetCoverage&coverageId=topp__tazdem"));
    }

    @Test
    public void execute_xml_checks_chained_processes() {
        String chained = "<wps:Execute xmlns:wps='http://www.opengis.net/wps/1.0.0'"
            + " xmlns:ows='http://www.opengis.net/ows/1.1' xmlns:xlink='http://www.w3.org/1999/xlink'"
            + " service='WPS' version='1.0.0'><ows:Identifier>JTS:buffer</ows:Identifier><wps:DataInputs>"
            + "<wps:Input><ows:Identifier>geom</ows:Identifier><wps:Reference xlink:href='http://geoserver/wps'"
            + " method='POST'><wps:Body><wps:Execute service='WPS' version='1.0.0'>"
            + "<ows:Identifier>%s</ows:Identifier></wps:Execute></wps:Body></wps:Reference></wps:Input>"
            + "</wps:DataInputs></wps:Execute>";
        requestInterceptor.interceptExecute(xml(String.format(chained, "JTS:centroid")));
        assertUnknownProcess("gs:Import", () -> requestInterceptor.interceptExecute(
            xml(String.format(chained, "gs:Import"))));
    }

    @Test
    public void execution_operations_are_sent() {
        requestInterceptor.interceptExecute(kvp("REQUEST", "GetExecutionStatus", "executionId", "abc"));
        requestInterceptor.interceptExecute(kvp("REQUEST", "Dismiss", "executionId", "abc"));
        requestInterceptor.interceptGetCapabilities(kvp("REQUEST", "GetCapabilities"));
    }

    private static Response xmlResponse(String xml) {
        Response response = new Response(HttpStatus.OK, new HttpHeaders(), xml.getBytes(StandardCharsets.UTF_8));
        return response;
    }

    @Test
    public void capabilities_and_descriptions_list_the_allowed_processes() {
        String caps = "<wps:Capabilities xmlns:wps='http://www.opengis.net/wps/1.0.0'"
            + " xmlns:ows='http://www.opengis.net/ows/1.1'><wps:ProcessOfferings>"
            + "<wps:Process><ows:Identifier>JTS:buffer</ows:Identifier></wps:Process>"
            + "<wps:Process><ows:Identifier>gs:Import</ows:Identifier></wps:Process>"
            + "<wps:Process><ows:Identifier>gs:Bounds</ows:Identifier></wps:Process>"
            + "</wps:ProcessOfferings></wps:Capabilities>";
        String filtered = new String(responseInterceptor.interceptGetCapabilities(kvp("REQUEST", "GetCapabilities"),
            xmlResponse(caps)).getBody(), StandardCharsets.UTF_8);
        assertTrue(filtered.contains("JTS:buffer"));
        assertTrue(filtered.contains("gs:Bounds"));
        assertFalse(filtered.contains("gs:Import"));

        String descriptions = "<wps:ProcessDescriptions xmlns:wps='http://www.opengis.net/wps/1.0.0'"
            + " xmlns:ows='http://www.opengis.net/ows/1.1'>"
            + "<ProcessDescription><ows:Identifier>gs:Import</ows:Identifier></ProcessDescription>"
            + "<ProcessDescription><ows:Identifier>JTS:area</ows:Identifier></ProcessDescription>"
            + "</wps:ProcessDescriptions>";
        filtered = new String(responseInterceptor.interceptDescribeProcess(kvp("REQUEST", "DescribeProcess"),
            xmlResponse(descriptions)).getBody(), StandardCharsets.UTF_8);
        assertTrue(filtered.contains("JTS:area"));
        assertFalse(filtered.contains("gs:Import"));
    }
}

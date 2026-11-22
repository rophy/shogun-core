package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.ImageWmsLayerDataSource;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import de.terrestris.shoguncore.util.interceptor.OgcXmlUtil;
import de.terrestris.shoguncore.util.model.Response;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The user may read cite:Lakes and cite:Forests; the catalogue has cite:Lakes,
 * cite:Ponds and cite:Forests.
 */
public class CswInterceptorsTest {

    private static final String CSW = "http://www.opengis.net/cat/csw/2.0.2";

    private CswRequestInterceptor requestInterceptor;

    private CswResponseInterceptor responseInterceptor;

    /** The records that GeoServer returns for the GetDomain check. */
    private String catalogue;

    private static String response(String... ids) {
        StringBuilder xml = new StringBuilder("<csw:GetRecordsResponse xmlns:csw='" + CSW + "'"
            + " xmlns:dc='http://purl.org/dc/elements/1.1/'><csw:SearchResults numberOfRecordsMatched='"
            + ids.length + "' numberOfRecordsReturned='" + ids.length + "' nextRecord='0'>");
        for (String id : ids) {
            xml.append("<csw:Record><dc:identifier>").append(id).append("</dc:identifier>")
                .append("<dc:references>http://gs:8080/geoserver/wms?layers=").append(id)
                .append("</dc:references></csw:Record>");
        }
        return xml.append("</csw:SearchResults></csw:GetRecordsResponse>").toString();
    }

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        ImageWmsLayerDataSource source = new ImageWmsLayerDataSource();
        source.setUrl("/geoserver.action");
        source.setLayerNames("cite:Lakes,cite:Forests");
        Layer layer = new Layer();
        layer.setSource(source);
        LayerService<Layer, LayerDao<Layer>> layerService = mock(LayerService.class);
        when(layerService.findAll()).thenReturn(Collections.singletonList(layer));
        catalogue = response("cite:Lakes", "cite:Ponds", "cite:Forests");
        requestInterceptor = new CswRequestInterceptor() {
            @Override
            protected Response fetch(URI uri) {
                return new Response(HttpStatus.OK, new HttpHeaders(), catalogue.getBytes(StandardCharsets.UTF_8));
            }
        };
        requestInterceptor.layerService = layerService;
        ReflectionTestUtils.setField(requestInterceptor, "gsUser", "admin");
        ReflectionTestUtils.setField(requestInterceptor, "gsPass", "geoserver");
        responseInterceptor = new CswResponseInterceptor();
        responseInterceptor.layerService = layerService;
    }

    private static MutableHttpServletRequest kvp(String... params) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/geoserver.action/ows");
        for (int i = 0; i < params.length; i += 2) {
            request.addParameter(params[i], params[i + 1]);
        }
        MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
        mutable.addParameter("CUSTOM_ENDPOINT", "ows");
        mutable.setRequestURI("http://gs:8080/geoserver/ows");
        return mutable;
    }

    private static MutableHttpServletRequest xml(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/geoserver.action/ows");
        request.setContentType("application/xml");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
        mutable.addParameter("CUSTOM_ENDPOINT", "ows");
        mutable.setRequestURI("http://gs:8080/geoserver/ows");
        return mutable;
    }

    private static Response xmlResponse(String xml) {
        return new Response(HttpStatus.OK, new HttpHeaders(), xml.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertRefused(String code, Runnable call) {
        try {
            call.run();
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(code, e.getCode());
        }
    }

    @Test
    public void get_records_asks_geoserver_for_all_records_and_pages_the_readable_ones() {
        MutableHttpServletRequest request = kvp("REQUEST", "GetRecords", "maxRecords", "1", "startPosition", "2",
            "resultType", "results");
        MutableHttpServletRequest forwarded = requestInterceptor.interceptGetRecords(request);
        assertEquals("1", forwarded.getParameterIgnoreCase("startPosition"));
        assertEquals("1000", forwarded.getParameterIgnoreCase("maxRecords"));
        assertEquals("results", forwarded.getParameterIgnoreCase("resultType"));

        Response response = responseInterceptor.interceptGetRecords(request, xmlResponse(catalogue));
        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(body, body.contains("numberOfRecordsMatched=\"2\""));
        assertTrue(body.contains("numberOfRecordsReturned=\"1\""));
        assertTrue(body.contains("cite:Forests"));
        assertFalse(body.contains("cite:Ponds"));
        assertFalse(body.contains("<dc:identifier>cite:Lakes"));
        // record references point to the interceptor
        assertTrue(body, body.contains("http://localhost/geoserver.action/ows?layers=cite:Forests"));
    }

    @Test
    public void get_records_xml_paging_is_rewritten() {
        MutableHttpServletRequest request = xml("<csw:GetRecords xmlns:csw='" + CSW + "' service='CSW'"
            + " version='2.0.2' maxRecords='5'><csw:Query typeNames='csw:Record'/></csw:GetRecords>");
        MutableHttpServletRequest forwarded = requestInterceptor.interceptGetRecords(request);
        String body = OgcXmlUtil.getRequestBody(forwarded);
        assertTrue(body, body.contains("maxRecords=\"1000\""));
        assertTrue(body.contains("startPosition=\"1\""));
        assertTrue(body.contains("resultType=\"results\""));
        CswRecords.Paging paging = CswRecords.paging(request);
        assertEquals(5, paging.getMaxRecords());
        assertTrue(paging.isHits());
    }

    @Test
    public void get_records_validate_is_not_changed() {
        MutableHttpServletRequest request = kvp("REQUEST", "GetRecords", "resultType", "validate");
        requestInterceptor.interceptGetRecords(request);
        Response response = xmlResponse("<csw:Acknowledgement xmlns:csw='" + CSW + "'/>");
        assertEquals(response, responseInterceptor.interceptGetRecords(request, response));
    }

    @Test
    public void get_record_by_id_removes_unreadable_records() {
        MutableHttpServletRequest request = kvp("REQUEST", "GetRecordById", "id", "cite:Ponds,cite:Lakes");
        requestInterceptor.interceptGetRecordById(request);
        String byId = "<csw:GetRecordByIdResponse xmlns:csw='" + CSW + "' xmlns:dc='http://purl.org/dc/elements/1.1/'>"
            + "<csw:Record><dc:identifier>cite:Ponds</dc:identifier></csw:Record>"
            + "<csw:Record><dc:identifier>cite:Lakes</dc:identifier></csw:Record></csw:GetRecordByIdResponse>";
        String body = new String(responseInterceptor.interceptGetRecordById(request, xmlResponse(byId)).getBody(),
            StandardCharsets.UTF_8);
        assertTrue(body.contains("cite:Lakes"));
        assertFalse(body.contains("cite:Ponds"));
    }

    @Test
    public void repository_items_and_downloads_need_read() {
        requestInterceptor.interceptGetRepositoryItem(kvp("REQUEST", "GetRepositoryItem", "id", "cite:Lakes"));
        try {
            requestInterceptor.interceptGetRepositoryItem(kvp("REQUEST", "GetRepositoryItem", "id", "cite:Ponds"));
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus("CSW"));
        }
        requestInterceptor.interceptDirectDownload(kvp("REQUEST", "DirectDownload", "resourceId", "cite:Lakes"));
        assertRefused(OgcException.INVALID_PARAMETER_VALUE, () -> requestInterceptor.interceptDirectDownload(
            kvp("REQUEST", "DirectDownload", "resourceId", "cite:Ponds")));
    }

    @Test
    public void get_domain_of_record_properties_needs_read_on_all_records() {
        requestInterceptor.interceptGetDomain(kvp("REQUEST", "GetDomain", "ParameterName", "GetRecords.resultType"));
        assertRefused(OgcException.OPERATION_NOT_SUPPORTED, () -> requestInterceptor.interceptGetDomain(
            kvp("REQUEST", "GetDomain", "PropertyName", "dc:title")));
        catalogue = response("cite:Lakes", "cite:Forests");
        requestInterceptor.interceptGetDomain(kvp("REQUEST", "GetDomain", "PropertyName", "dc:title"));
    }

    @Test
    public void catalogue_changes_are_refused() {
        assertRefused(OgcException.OPERATION_NOT_SUPPORTED,
            () -> requestInterceptor.interceptCatalogChange(kvp("REQUEST", "Harvest")));
    }

    @Test
    public void capabilities_urls_point_to_the_interceptor() {
        String caps = "<csw:Capabilities xmlns:csw='" + CSW + "' xmlns:ows='http://www.opengis.net/ows'"
            + " xmlns:xlink='http://www.w3.org/1999/xlink'><ows:OperationsMetadata><ows:Operation name='GetRecords'>"
            + "<ows:DCP><ows:HTTP><ows:Get xlink:href='http://gs:8080/geoserver/ows?service=csw'/></ows:HTTP>"
            + "</ows:DCP></ows:Operation></ows:OperationsMetadata></csw:Capabilities>";
        MutableHttpServletRequest request = kvp("REQUEST", "GetCapabilities");
        String body = new String(responseInterceptor.interceptGetCapabilities(request, xmlResponse(caps)).getBody(),
            StandardCharsets.UTF_8);
        assertFalse(body, body.contains("gs:8080"));
        assertTrue(body.contains("/geoserver.action/ows?service=csw"));
    }

    @Test
    public void the_impl_interceptor_adds_credentials_only() {
        de.terrestris.shoguncore.util.interceptor.impl.CswRequestInterceptor impl =
            new de.terrestris.shoguncore.util.interceptor.impl.CswRequestInterceptor();
        ReflectionTestUtils.setField(impl, "gsUser", "admin");
        ReflectionTestUtils.setField(impl, "gsPass", "geoserver");
        MutableHttpServletRequest request = kvp("REQUEST", "GetRecords");
        for (MutableHttpServletRequest forwarded : new MutableHttpServletRequest[]{
            impl.interceptGetCapabilities(request), impl.interceptDescribeRecord(request),
            impl.interceptGetRecords(request), impl.interceptGetRecordById(request), impl.interceptGetDomain(request),
            impl.interceptGetRepositoryItem(request), impl.interceptDirectDownload(request),
            impl.interceptCatalogChange(request)}) {
            assertTrue(forwarded.getHeader("x-geoserver-credentials").startsWith("Basic "));
        }
    }
}

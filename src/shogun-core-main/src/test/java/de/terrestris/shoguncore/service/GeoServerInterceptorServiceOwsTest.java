package de.terrestris.shoguncore.service;

import de.terrestris.shoguncore.dao.InterceptorRuleDao;
import de.terrestris.shoguncore.model.interceptor.InterceptorRule;
import de.terrestris.shoguncore.util.enumeration.HttpEnum;
import de.terrestris.shoguncore.util.enumeration.InterceptorEnum;
import de.terrestris.shoguncore.util.enumeration.OgcEnum;
import de.terrestris.shoguncore.util.http.HttpUtil;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcMessage;
import de.terrestris.shoguncore.util.interceptor.OgcMessageDistributor;
import de.terrestris.shoguncore.util.model.Response;
import org.apache.http.Header;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.URI;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Routing of the global endpoint (/geoserver.action/ows) and the WMS 1.0
 * operation name "map".
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(HttpUtil.class)
@PowerMockIgnore({"javax.management.*", "org.mockito.*", "org.powermock.*", "org.apache.commons.*", "org.junit.*",
    "javax.xml.*", "org.xml.*", "org.apache.logging.log4j.*"})
public class GeoServerInterceptorServiceOwsTest {

    @InjectMocks
    GeoServerInterceptorService service;

    @Mock(name = "interceptorRuleService")
    InterceptorRuleService<InterceptorRule, InterceptorRuleDao<InterceptorRule>> ruleService;

    @Mock
    OgcMessageDistributor distributor;

    private final Properties namespaces = new Properties();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(service, "namespaceBoundUrl", true);
        namespaces.setProperty("cdf", "http://gs:8080/geoserver/cdf/ows");
        namespaces.setProperty("cgf", "http://gs:8080/geoserver/cgf/ows");
        service.setGeoServerNameSpaces(namespaces);

        InterceptorRule rule = new InterceptorRule(HttpEnum.EventType.REQUEST, InterceptorEnum.RuleType.ALLOW,
            OgcEnum.ServiceType.WFS, null, null);
        when(ruleService.findAllRulesForServiceAndEvent(any(String.class), any(String.class)))
            .thenReturn(Collections.singletonList(rule));
        PowerMockito.mockStatic(HttpUtil.class);
        when(HttpUtil.get(any(URI.class), any(Header[].class))).thenReturn(new Response());
        when(distributor.distributeToRequestInterceptor(any(MutableHttpServletRequest.class), any(OgcMessage.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(distributor.distributeToResponseInterceptor(any(MutableHttpServletRequest.class), any(Response.class),
            any(OgcMessage.class))).thenReturn(new Response());
    }

    private OgcMessage send(String endpoint, String... params) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/geoserver.action/" + endpoint);
        for (int i = 0; i < params.length; i += 2) {
            request.setParameter(params[i], params[i + 1]);
        }
        service.interceptGeoServerRequest(request, Optional.of(endpoint));
        ArgumentCaptor<MutableHttpServletRequest> forwarded = ArgumentCaptor.forClass(MutableHttpServletRequest.class);
        ArgumentCaptor<OgcMessage> message = ArgumentCaptor.forClass(OgcMessage.class);
        verify(distributor).distributeToRequestInterceptor(forwarded.capture(), message.capture());
        lastUri = forwarded.getValue().getRequestURI();
        return message.getValue();
    }

    private String lastUri;

    @Test
    public void global_endpoint_uses_the_global_geoserver_url() throws Exception {
        namespaces.setProperty("ows", "http://gs:8080/geoserver/ows");
        OgcMessage message = send("ows", "SERVICE", "WFS", "VERSION", "1.0.0", "REQUEST", "GetFeature",
            "TYPENAME", "cdf:Seven,cgf:Lines");
        assertEquals("ows", message.getEndPoint());
        assertEquals("http://gs:8080/geoserver/ows", lastUri);
    }

    @Test
    public void without_global_url_the_layer_namespace_routes() throws Exception {
        send("ows", "SERVICE", "WFS", "VERSION", "1.0.0", "REQUEST", "GetFeature", "TYPENAME", "cgf:Lines");
        assertEquals("http://gs:8080/geoserver/cgf/ows", lastUri);
    }

    @Test
    public void workspace_endpoints_are_unchanged() throws Exception {
        namespaces.setProperty("ows", "http://gs:8080/geoserver/ows");
        send("cdf", "SERVICE", "WFS", "VERSION", "1.0.0", "REQUEST", "GetFeature", "TYPENAME", "Seven");
        assertEquals("http://gs:8080/geoserver/cdf/ows", lastUri);
    }

    @Test
    public void wms_10_name_map_is_get_map() throws Exception {
        InterceptorRule rule = new InterceptorRule(HttpEnum.EventType.REQUEST, InterceptorEnum.RuleType.ALLOW,
            OgcEnum.ServiceType.WMS, null, null);
        when(ruleService.findAllRulesForServiceAndEvent(any(String.class), any(String.class)))
            .thenReturn(Collections.singletonList(rule));
        OgcMessage message = send("cdf", "SERVICE", "WMS", "VERSION", "1.1.1", "REQUEST", "map",
            "LAYERS", "Seven");
        assertEquals(OgcEnum.OperationType.GET_MAP, message.getOperation());
    }

    @Test
    public void the_wcs_version_of_an_xml_request_without_version_is_the_one_of_its_namespace() {
        assertEquals("1.0.0", GeoServerInterceptorService.versionOfNamespace(
            "<DescribeCoverage xmlns=\"http://www.opengis.net/wcs\" service=\"WCS\"/>"));
        assertEquals("1.1.1", GeoServerInterceptorService.versionOfNamespace(
            "<wcs:GetCoverage xmlns:wcs=\"http://www.opengis.net/wcs/1.1.1\"/>"));
        assertEquals("2.0.1", GeoServerInterceptorService.versionOfNamespace(
            "<wcs:GetCoverage xmlns:wcs=\"http://www.opengis.net/wcs/2.0\"/>"));
        assertEquals(null, GeoServerInterceptorService.versionOfNamespace("<GetMap/>"));
    }

    @Test
    public void the_service_of_an_xml_request_is_the_one_of_its_namespace() {
        assertEquals("WFS", GeoServerInterceptorService.serviceOfNamespace(
            "<?xml version='1.0'?><wfs:DescribeFeatureType xmlns:wfs='http://www.opengis.net/wfs' version='1.1.0'/>"));
        assertEquals("WCS", GeoServerInterceptorService.serviceOfNamespace(
            "<DescribeCoverage xmlns=\"http://www.opengis.net/wcs\"/>"));
        assertEquals("CSW", GeoServerInterceptorService.serviceOfNamespace(
            "<csw:GetRecords xmlns:csw=\"http://www.opengis.net/cat/csw/2.0.2\"/>"));
        assertEquals(null, GeoServerInterceptorService.serviceOfNamespace("<GetMap xmlns='http://www.opengis.net/ows'/>"));
        assertEquals(null, GeoServerInterceptorService.serviceOfNamespace("not xml"));
    }
}

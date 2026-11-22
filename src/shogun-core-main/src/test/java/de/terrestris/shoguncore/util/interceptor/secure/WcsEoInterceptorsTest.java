package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.ImageWmsLayerDataSource;
import de.terrestris.shoguncore.service.LayerService;
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
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * WCS 2.0 EO: the user may read cite:tazmosaic, not cite:secret.
 */
public class WcsEoInterceptorsTest {

    private WcsRequestInterceptor requestInterceptor;

    private WcsResponseInterceptor responseInterceptor;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        ImageWmsLayerDataSource source = new ImageWmsLayerDataSource();
        source.setUrl("/geoserver.action");
        source.setLayerNames("cite:tazmosaic");
        Layer layer = new Layer();
        layer.setSource(source);
        LayerService<Layer, LayerDao<Layer>> layerService = mock(LayerService.class);
        when(layerService.findAll()).thenReturn(Collections.singletonList(layer));
        requestInterceptor = new WcsRequestInterceptor();
        requestInterceptor.layerService = layerService;
        ReflectionTestUtils.setField(requestInterceptor, "gsUser", "admin");
        ReflectionTestUtils.setField(requestInterceptor, "gsPass", "geoserver");
        responseInterceptor = new WcsResponseInterceptor();
        responseInterceptor.layerService = layerService;
    }

    private static MutableHttpServletRequest kvp(String... params) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/geoserver.action/cite");
        for (int i = 0; i < params.length; i += 2) {
            request.addParameter(params[i], params[i + 1]);
        }
        MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
        mutable.addParameter("CUSTOM_ENDPOINT", "cite");
        mutable.setRequestURI("http://gs:8080/geoserver/cite/ows");
        return mutable;
    }

    @Test
    public void describe_eo_coverage_set_needs_read_on_each_dataset_series() {
        requestInterceptor.interceptDescribeEOCoverageSet(kvp("REQUEST", "DescribeEOCoverageSet",
            "eoId", "cite__tazmosaic_dss"));
        try {
            requestInterceptor.interceptDescribeEOCoverageSet(kvp("REQUEST", "DescribeEOCoverageSet",
                "eoId", "cite__tazmosaic_dss,cite__secret_dss"));
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals("noSuchEODataset", e.getCode());
            assertEquals("eoid", e.getLocator());
            assertEquals(HttpStatus.NOT_FOUND, e.getHttpStatus("WCS"));
            assertTrue(e.getMessage().contains("cite__secret_dss"));
            assertFalse(e.getMessage().contains("tazmosaic"));
        }
    }

    @Test
    public void describe_eo_coverage_set_xml() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/geoserver.action/cite");
        request.setContentType("application/xml");
        request.setContent(("<wcseo:DescribeEOCoverageSet xmlns:wcseo='http://www.opengis.net/wcseo/1.0'"
            + " service='WCS' version='2.0.1'><wcseo:eoId>cite__secret_dss</wcseo:eoId>"
            + "</wcseo:DescribeEOCoverageSet>").getBytes(StandardCharsets.UTF_8));
        MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
        mutable.addParameter("CUSTOM_ENDPOINT", "cite");
        try {
            requestInterceptor.interceptDescribeEOCoverageSet(mutable);
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals("noSuchEODataset", e.getCode());
        }
    }

    @Test
    public void granules_need_read_on_their_coverage() {
        requestInterceptor.interceptDescribeCoverage(kvp("REQUEST", "DescribeCoverage", "VERSION", "2.0.1",
            "coverageId", "cite__tazmosaic_granule_tazmosaic.1"));
        try {
            requestInterceptor.interceptGetCoverage(kvp("REQUEST", "GetCoverage", "VERSION", "2.0.1",
                "coverageId", "cite__secret_granule_secret.1"));
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(WcsRequestInterceptor.NO_SUCH_COVERAGE, e.getCode());
        }
    }

    @Test
    public void eo_capabilities_list_readable_dataset_series_only() {
        String caps = "<wcs:Capabilities xmlns:wcs='http://www.opengis.net/wcs/2.0'"
            + " xmlns:wcseo='http://www.opengis.net/wcseo/1.0'><wcs:Contents>"
            + "<wcs:CoverageSummary><wcs:CoverageId>cite__tazmosaic</wcs:CoverageId></wcs:CoverageSummary>"
            + "<wcs:CoverageSummary><wcs:CoverageId>cite__secret</wcs:CoverageId></wcs:CoverageSummary>"
            + "<wcs:Extension><wcseo:DatasetSeriesSummary><wcseo:DatasetSeriesId>cite__tazmosaic_dss"
            + "</wcseo:DatasetSeriesId></wcseo:DatasetSeriesSummary><wcseo:DatasetSeriesSummary>"
            + "<wcseo:DatasetSeriesId>cite__secret_dss</wcseo:DatasetSeriesId></wcseo:DatasetSeriesSummary>"
            + "</wcs:Extension></wcs:Contents></wcs:Capabilities>";
        Response response = new Response(HttpStatus.OK, new HttpHeaders(), caps.getBytes(StandardCharsets.UTF_8));
        String body = new String(responseInterceptor.interceptGetCapabilities(kvp("REQUEST", "GetCapabilities"),
            response).getBody(), StandardCharsets.UTF_8);
        assertTrue(body.contains("cite__tazmosaic_dss"));
        assertFalse(body, body.contains("secret"));
    }
}

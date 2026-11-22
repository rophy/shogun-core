package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.WmtsLayerDataSource;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DescribeDomains, GetDomainValues, GetHistogram and GetFeature of the WMTS
 * multi-dimensional extension need READ on the WMTS layer, as GetTile.
 */
public class WmtsMultiDimensionalTest {

    private WmtsRequestInterceptor interceptor;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        WmtsLayerDataSource source = new WmtsLayerDataSource();
        source.setUrl("/geoserver.action");
        source.setWmtsLayer("cite:tazmosaic");
        Layer layer = new Layer();
        layer.setSource(source);
        LayerService<Layer, LayerDao<Layer>> layerService = mock(LayerService.class);
        when(layerService.findAll()).thenReturn(Collections.singletonList(layer));
        interceptor = new WmtsRequestInterceptor();
        ReflectionTestUtils.setField(interceptor, "layerService", layerService);
        ReflectionTestUtils.setField(interceptor, "gsUser", "admin");
        ReflectionTestUtils.setField(interceptor, "gsPass", "geoserver");
    }

    private static MutableHttpServletRequest request(String operation, String layer) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/geoserver.action/cite");
        request.addParameter("SERVICE", "WMTS");
        request.addParameter("REQUEST", operation);
        request.addParameter("LAYER", layer);
        MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
        mutable.addParameter("CUSTOM_ENDPOINT", "cite");
        mutable.setRequestURI("http://gs:8080/geoserver/cite/ows");
        return mutable;
    }

    @Test
    public void readable_layers_go_to_the_global_gwc_endpoint() {
        for (String operation : new String[]{"DescribeDomains", "GetDomainValues", "GetHistogram", "GetFeature"}) {
            MutableHttpServletRequest forwarded = interceptor.interceptMultiDimensional(request(operation, "tazmosaic"));
            assertEquals("cite:tazmosaic", forwarded.getParameterIgnoreCase("LAYER"));
            assertEquals("http://gs:8080/geoserver/gwc/service/wmts", forwarded.getRequestURI());
        }
    }

    @Test
    public void refused_layers_give_invalid_parameter_value() {
        try {
            interceptor.interceptMultiDimensional(request("DescribeDomains", "cite:secret"));
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(OgcException.INVALID_PARAMETER_VALUE, e.getCode());
            assertEquals("layer", e.getLocator());
        }
    }
}

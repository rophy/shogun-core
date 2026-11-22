package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.ImageWmsLayerDataSource;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GeoServer takes the layers of a WMS request from an SLD (SLD_BODY, SLD,
 * STYLE_BODY, STYLE_URL, or a GetMap XML request) if LAYERS is not given.
 * Each layer of the SLD needs READ.
 */
public class WmsRequestInterceptorSldTest {

    /** The user may read cite:Lakes only. */
    private WmsRequestInterceptor interceptor;

    private static final String SLD_FORESTS = "<StyledLayerDescriptor version='1.0.0'"
        + " xmlns='http://www.opengis.net/sld'><NamedLayer><Name>cite:Forests</Name>"
        + "<NamedStyle><Name>polygon</Name></NamedStyle></NamedLayer></StyledLayerDescriptor>";

    private static final String SLD_LAKES = "<StyledLayerDescriptor version='1.0.0'"
        + " xmlns='http://www.opengis.net/sld'><NamedLayer><Name>Lakes</Name>"
        + "<NamedStyle><Name>polygon</Name></NamedStyle></NamedLayer></StyledLayerDescriptor>";

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        ImageWmsLayerDataSource source = new ImageWmsLayerDataSource();
        source.setUrl("/geoserver.action");
        source.setLayerNames("cite:Lakes");
        Layer layer = new Layer();
        layer.setSource(source);
        LayerService<Layer, LayerDao<Layer>> layerService = mock(LayerService.class);
        when(layerService.findAll()).thenReturn(Collections.singletonList(layer));

        interceptor = new WmsRequestInterceptor() {
            @Override
            protected String fetchStyle(String url) {
                if ("http://styles.example.com/forests.sld".equals(url)) {
                    return SLD_FORESTS;
                }
                return SLD_LAKES;
            }
        };
        interceptor.layerService = layerService;
        ReflectionTestUtils.setField(interceptor, "gsUser", "admin");
        ReflectionTestUtils.setField(interceptor, "gsPass", "geoserver");
        ReflectionTestUtils.setField(interceptor, "styleUrlHosts",
            Arrays.asList("styles.example.com", "other.example.com:8080"));
    }

    private static MutableHttpServletRequest request(String... params) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/geoserver.action/cite");
        for (int i = 0; i < params.length; i += 2) {
            request.addParameter(params[i], params[i + 1]);
        }
        MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
        mutable.addParameter("CUSTOM_ENDPOINT", "cite");
        return mutable;
    }

    private static MutableHttpServletRequest xmlRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/geoserver.action/cite");
        request.setContentType("application/xml");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        MutableHttpServletRequest mutable = new MutableHttpServletRequest(request);
        mutable.addParameter("CUSTOM_ENDPOINT", "cite");
        return mutable;
    }

    private static void assertLayerNotDefined(Runnable call) {
        try {
            call.run();
            fail("The request is not refused");
        } catch (OgcException e) {
            assertEquals(OgcException.LAYER_NOT_DEFINED, e.getCode());
        }
    }

    @Test
    public void get_map_with_sld_body_and_no_layers_is_refused_for_unreadable_layers() {
        MutableHttpServletRequest request = request("REQUEST", "GetMap", "SLD_BODY", SLD_FORESTS);
        assertLayerNotDefined(() -> interceptor.interceptGetMap(request));
    }

    @Test
    public void get_map_with_sld_body_of_readable_layers_is_sent() {
        interceptor.interceptGetMap(request("REQUEST", "GetMap", "SLD_BODY", SLD_LAKES));
    }

    @Test
    public void style_body_alias_is_checked() {
        MutableHttpServletRequest request = request("REQUEST", "GetMap", "STYLE_BODY", SLD_FORESTS);
        assertLayerNotDefined(() -> interceptor.interceptGetMap(request));
    }

    @Test
    public void get_feature_info_and_get_legend_graphic_check_the_sld() {
        MutableHttpServletRequest info = request("REQUEST", "GetFeatureInfo", "SLD_BODY", SLD_FORESTS,
            "QUERY_LAYERS", "Lakes");
        assertLayerNotDefined(() -> interceptor.interceptGetFeatureInfo(info));
        MutableHttpServletRequest legend = request("REQUEST", "GetLegendGraphic", "SLD_BODY", SLD_FORESTS);
        assertLayerNotDefined(() -> interceptor.interceptGetLegendGraphic(legend));
    }

    @Test
    public void sld_url_is_read_checked_and_sent_as_body() {
        MutableHttpServletRequest refused = request("REQUEST", "GetMap", "SLD", "http://styles.example.com/forests.sld");
        assertLayerNotDefined(() -> interceptor.interceptGetMap(refused));

        MutableHttpServletRequest allowed = request("REQUEST", "GetMap", "sld", "http://styles.example.com/lakes.sld");
        interceptor.interceptGetMap(allowed);
        // GeoServer gets the checked document, not the URL
        assertNull(allowed.getParameterIgnoreCase("SLD"));
        assertEquals(SLD_LAKES, allowed.getParameterMap().get("SLD_BODY")[0]);
        assertEquals(1, allowed.getParameterMap().get("SLD_BODY").length);
    }

    @Test
    public void style_url_alias_is_sent_as_style_body() {
        MutableHttpServletRequest allowed = request("REQUEST", "GetMap", "STYLE_URL",
            "http://other.example.com:8080/lakes.sld");
        interceptor.interceptGetMap(allowed);
        assertNull(allowed.getParameterIgnoreCase("STYLE_URL"));
        assertEquals(SLD_LAKES, allowed.getParameterIgnoreCase("STYLE_BODY"));
    }

    @Test
    public void sld_urls_of_other_hosts_are_refused() {
        for (String url : new String[]{"http://internal.example.com/a.sld", "http://other.example.com/a.sld",
            "http://styles.example.com.evil.com/a.sld", "file:///etc/passwd", "ftp://styles.example.com/a.sld",
            "not a url"}) {
            try {
                interceptor.interceptGetMap(request("REQUEST", "GetMap", "SLD", url));
                fail("Not refused: " + url);
            } catch (OgcException e) {
                assertEquals(url, OgcException.INVALID_PARAMETER_VALUE, e.getCode());
            }
        }
    }

    @Test
    public void get_map_xml_request_checks_the_embedded_sld() {
        String getMap = "<GetMap xmlns='http://www.opengis.net/ows' xmlns:sld='http://www.opengis.net/sld'"
            + " version='1.1.1' service='WMS'><sld:StyledLayerDescriptor version='1.0.0'>"
            + "<sld:NamedLayer><sld:Name>%s</sld:Name></sld:NamedLayer></sld:StyledLayerDescriptor>"
            + "<BoundingBox srsName='EPSG:4326'/><Output><Format>image/png</Format>"
            + "<Size><Width>10</Width><Height>10</Height></Size></Output></GetMap>";
        MutableHttpServletRequest refused = xmlRequest(String.format(getMap, "cite:Forests"));
        assertLayerNotDefined(() -> interceptor.interceptGetMap(refused));
        interceptor.interceptGetMap(xmlRequest(String.format(getMap, "cite:Lakes")));
    }

    @Test
    public void invalid_sld_is_refused() {
        try {
            interceptor.interceptGetMap(request("REQUEST", "GetMap", "SLD_BODY", "<StyledLayerDescriptor>"));
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(OgcException.INVALID_PARAMETER_VALUE, e.getCode());
        }
    }
}

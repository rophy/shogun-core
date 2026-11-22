package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.interceptor.GeoserverAuthHeaderRequest;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.WmsRequestInterceptorInterface;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import static org.apache.logging.log4j.LogManager.getLogger;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import de.terrestris.shoguncore.util.interceptor.OgcXmlUtil;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Interceptor class for WMS requests. Adds basic auth headers based on the GS
 * properties by default and checks for permission on requested layer.
 * <p>
 * GetMap, GetFeatureInfo and GetLegendGraphic also check each layer of a
 * style document (SLD_BODY, SLD, and the GeoServer aliases STYLE_BODY and
 * STYLE_URL, or the StyledLayerDescriptor of a GetMap XML request), because
 * GeoServer takes the layers from it if LAYERS is not given. Style URLs are
 * read by the interceptor (only of the hosts in
 * geoserver.interceptor.styleUrlHosts) and sent to GeoServer as SLD_BODY or
 * STYLE_BODY, so GeoServer uses the checked document.
 *
 * @author Nils Bühner
 * @author Johannes Weskamm
 *
 */
public class WmsRequestInterceptor extends BaseInterceptor implements WmsRequestInterceptorInterface {

    /**
     *
     */
    private static final Logger LOG = getLogger(WmsRequestInterceptor.class);

    /**
     *
     */
    @Autowired
    @Qualifier("layerService")
    protected LayerService<Layer, LayerDao<Layer>> layerService;

    /**
     *
     */
    @Value("${geoserver.username:}")
    private String gsUser;

    /**
     *
     */
    @Value("${geoserver.password:}")
    private String gsPass;

    /**
     * The hosts ("host" or "host:port") whose style URLs may be read; none by
     * default.
     */
    @Value("#{'${geoserver.interceptor.styleUrlHosts:}'.split(',')}")
    private List<String> styleUrlHosts = Collections.emptyList();

    /** Style document parameters: {URL parameter, body parameter}. */
    private static final String[][] STYLE_PARAMETERS = {{"SLD", "SLD_BODY"}, {"STYLE_URL", "STYLE_BODY"}};

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptGetMap(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WMS GetMap request");
        checkLayers(request, "layers");
        checkStyleLayers(request);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptGetFeatureInfo(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WMS GetFeatureInfo request");
        checkLayers(request, "layers");
        checkLayers(request, "query_layers");
        checkStyleLayers(request);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptDescribeLayer(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WMS DescribeLayer request");
        checkLayers(request, "layers");
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptGetLegendGraphic(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WMS GetLegendGraphic request");
        checkLayers(request, "layer");
        checkStyleLayers(request);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptGetStyles(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WMS GetStyles request");
        checkLayers(request, "layers");
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptGetCapabilities(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WMS GetCapabilities request");
        // response will be intercepted
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     * Checks the read permission for each layer in the (comma separated)
     * parameter. A layer that the user may not read gives the same
     * LayerNotDefined exception as a layer that does not exist. An empty
     * parameter is sent to GeoServer, which reports the missing value.
     *
     * @param request The request
     * @param paramName The parameter name for the layer parameter
     */
    private void checkLayers(MutableHttpServletRequest request, String paramName) {
        String layersParam = request.getParameterIgnoreCase(paramName);
        if (StringUtils.isBlank(layersParam)) {
            return;
        }
        String namespace = LayerNames.endpointNamespace(request);
        Set<String> allowed = getAllowedLayerNames(request, namespace);
        for (String name : layersParam.split(",")) {
            if (StringUtils.isBlank(name)) {
                continue;
            }
            if (!allowed.contains(LayerNames.key(name, namespace))) {
                forbidLayer(OgcException.LAYER_NOT_DEFINED, paramName, name.trim());
            }
        }
    }

    /**
     * Checks each layer of the style documents of the request. Style URLs are
     * replaced by the documents they point to.
     */
    private void checkStyleLayers(MutableHttpServletRequest request) {
        for (String[] parameter : STYLE_PARAMETERS) {
            String urlKey = parameterKey(request, parameter[0]);
            if (urlKey == null) {
                continue;
            }
            String url = request.getParameter(urlKey);
            request.removeParameter(urlKey);
            if (parameterKey(request, parameter[1]) != null) {
                // GeoServer uses the body and ignores the URL
                continue;
            }
            String body = fetchStyle(StyleUrls.check(url, styleUrlHosts, parameter[0]).toString());
            request.setParameter(parameter[1], new String[]{body});
        }
        String namespace = LayerNames.endpointNamespace(request);
        Set<String> allowed = null;
        for (String[] parameter : STYLE_PARAMETERS) {
            String bodyKey = parameterKey(request, parameter[1]);
            if (bodyKey != null) {
                allowed = checkStyleDocument(request, request.getParameter(bodyKey), parameter[1], namespace, allowed);
            }
        }
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String body = OgcXmlUtil.getRequestBody(request);
            if (StringUtils.contains(body, "StyledLayerDescriptor")) {
                checkStyleDocument(request, body, "StyledLayerDescriptor", namespace, allowed);
            }
        }
    }

    private Set<String> checkStyleDocument(MutableHttpServletRequest request, String document, String locator,
                                           String namespace, Set<String> allowedLayers) {
        Set<String> names = StyledLayers.names(StringUtils.defaultString(document), locator);
        if (names.isEmpty()) {
            return allowedLayers;
        }
        Set<String> allowed = allowedLayers != null ? allowedLayers : getAllowedLayerNames(request, namespace);
        for (String name : names) {
            if (!allowed.contains(LayerNames.key(name, namespace))) {
                forbidLayer(OgcException.LAYER_NOT_DEFINED, locator, name);
            }
        }
        return allowed;
    }

    /**
     * @return The parameter name of the request that equals the name
     * (ignoring case), or null
     */
    private static String parameterKey(MutableHttpServletRequest request, String name) {
        for (String key : request.getParameterMap().keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    /**
     * @param url A checked style URL (see {@link StyleUrls#check})
     * @return The style document
     */
    protected String fetchStyle(String url) {
        return StyleUrls.read(java.net.URI.create(url), "SLD");
    }

    private Set<String> getAllowedLayerNames(MutableHttpServletRequest request, String namespace) {
        return LayerNames.wmsLayerKeys(layerService.findAll(), request.getContextPath() + "/geoserver.action",
            namespace);
    }

}

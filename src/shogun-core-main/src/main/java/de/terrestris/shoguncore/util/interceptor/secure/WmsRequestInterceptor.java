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
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

/**
 * Interceptor class for WMS requests. Adds basic auth headers based on the GS
 * properties by default and checks for permission on requested layer
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
     *
     */
    @Override
    public MutableHttpServletRequest interceptGetMap(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WMS GetMap request");
        checkLayers(request, "layers");
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

    private Set<String> getAllowedLayerNames(MutableHttpServletRequest request, String namespace) {
        return LayerNames.wmsLayerKeys(layerService.findAll(), request.getContextPath() + "/geoserver.action",
            namespace);
    }

}

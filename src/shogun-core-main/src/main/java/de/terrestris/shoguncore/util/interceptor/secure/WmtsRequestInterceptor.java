package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.WmtsLayerDataSource;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.interceptor.GeoserverAuthHeaderRequest;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import de.terrestris.shoguncore.util.interceptor.WmtsRequestInterceptorInterface;
import de.terrestris.shoguncore.util.interceptor.WmtsUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import static org.apache.logging.log4j.LogManager.getLogger;

/**
 * Interceptor class for WMTS requests. Checks the read permission on the
 * requested layer and sends KVP and RESTful requests to the GeoServer
 * (GeoWebCache) WMTS of the endpoint.
 *
 * @author Nils Bühner
 * @author Johannes Weskamm
 *
 */
public class WmtsRequestInterceptor extends BaseInterceptor implements WmtsRequestInterceptorInterface {

    /**
     *
     */
    private static final Logger LOG = getLogger(WmtsRequestInterceptor.class);

    /**
     * The KVP path of GeoServer's WMTS.
     */
    private static final String KVP_PATH = "/gwc/service/wmts";

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
    public MutableHttpServletRequest interceptGetTile(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WMTS GetTile request");
        return intercept(request);
    }

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptGetFeatureInfo(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WMTS GetFeatureInfo request");
        return intercept(request);
    }

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptGetCapabilities(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WMTS GetCapabilities request");
        // response will be intercepted, we reroute to GeoServers WMTS endpoint
        request.setRequestURI(geoServerRoot(request) + KVP_PATH);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    private MutableHttpServletRequest intercept(MutableHttpServletRequest request) {
        String restPath = WmtsUtil.getProxiedRestPath(request);
        if (restPath != null) {
            // RESTful request: /geoserver.action/{endpoint}/gwc/service/wmts/rest/{layer}/...
            String layerName = WmtsUtil.getProxiedRestLayer(request);
            checkLayer(request, layerName);
            String layerPath = restPath.contains("/") ? restPath.substring(restPath.indexOf('/')) : "";
            request.setRequestURI(geoServerRoot(request) + WmtsUtil.REST_PATH
                + qualify(request, layerName) + layerPath);
            request.removeParameter("CUSTOM_ENDPOINT");
            request.removeParameter("CONTEXT_PATH");
            return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
        }
        String layerId = WmtsUtil.getLayerId(request);
        if (layerId != null && StringUtils.isNumeric(layerId)) {
            // RESTful request by layer id: /geoserver.action/{endpoint}/wmts/{id}/...
            return interceptByLayerId(request, layerId);
        }
        // KVP request
        String layerName = request.getParameterIgnoreCase("layer");
        checkLayer(request, layerName);
        if (StringUtils.isNotBlank(layerName)) {
            removeParameterIgnoreCase(request, "layer");
            request.setParameter("LAYER", qualify(request, layerName));
        }
        request.setRequestURI(geoServerRoot(request) + KVP_PATH);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    private MutableHttpServletRequest interceptByLayerId(MutableHttpServletRequest request, String layerId) {
        Layer layer = layerService.findById(Integer.parseInt(layerId));
        if (layer == null) {
            return forbidRequest(request);
        }
        String url = layer.getSource().getUrl();
        url += request.getRequestURL().toString().split("/wmts/" + layerId)[1];
        request.setRequestURI(url);
        return request;
    }

    /**
     * Refuses the request if the user may not read the WMTS layer. An empty
     * layer name is sent to GeoServer, which reports the missing value.
     */
    private void checkLayer(MutableHttpServletRequest request, String name) {
        if (StringUtils.isBlank(name)) {
            return;
        }
        String namespace = LayerNames.endpointNamespace(request);
        String key = LayerNames.key(name, namespace);
        for (Layer layer : layerService.findAll()) {
            if (layer.getSource() instanceof WmtsLayerDataSource) {
                WmtsLayerDataSource source = (WmtsLayerDataSource) layer.getSource();
                if (source.getWmtsLayer() != null && StringUtils.contains(source.getUrl(), "geoserver.action")
                    && key.equals(LayerNames.key(source.getWmtsLayer(), namespace))) {
                    return;
                }
            }
        }
        forbidLayer(OgcException.INVALID_PARAMETER_VALUE, "layer", name);
    }

    /**
     * @return The layer name with the namespace of the endpoint, as the
     * global GeoServer WMTS needs it
     */
    private static String qualify(MutableHttpServletRequest request, String name) {
        String namespace = LayerNames.endpointNamespace(request);
        String trimmed = StringUtils.trimToEmpty(name);
        if (trimmed.contains(":") || StringUtils.isEmpty(namespace)) {
            return trimmed;
        }
        return namespace + ":" + trimmed;
    }

    private static void removeParameterIgnoreCase(MutableHttpServletRequest request, String name) {
        for (String key : request.getParameterMap().keySet().toArray(new String[0])) {
            if (key.equalsIgnoreCase(name)) {
                request.removeParameter(key);
            }
        }
    }

    /**
     * The global GeoServer WMTS is used, because the workspace WMTS of
     * GeoServer (3.0) refuses the AcceptVersions parameter
     * (VersionNegotiationFailed). The response interceptor removes the layers
     * of other workspaces.
     *
     * @return The GeoServer root URL, e.g. http://localhost:8080/geoserver for
     * http://localhost:8080/geoserver/cite/ows and the endpoint "cite"
     */
    static String geoServerRoot(MutableHttpServletRequest request) {
        String uri = request.getRequestURI();
        int query = uri.indexOf('?');
        if (query > -1) {
            uri = uri.substring(0, query);
        }
        uri = StringUtils.removeEnd(uri, "/");
        // remove the service path, e.g. /ows
        uri = uri.substring(0, Math.max(uri.lastIndexOf('/'), 0));
        String namespace = LayerNames.endpointNamespace(request);
        if (StringUtils.isNotEmpty(namespace) && uri.endsWith("/" + namespace)) {
            uri = uri.substring(0, uri.length() - namespace.length() - 1);
        }
        return uri;
    }

}

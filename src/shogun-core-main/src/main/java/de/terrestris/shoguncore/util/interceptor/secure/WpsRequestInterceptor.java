package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.dao.WpsProcessDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.wps.WpsProcess;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.service.WpsProcessService;
import de.terrestris.shoguncore.util.interceptor.GeoserverAuthHeaderRequest;
import de.terrestris.shoguncore.util.interceptor.InterceptorException;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import de.terrestris.shoguncore.util.interceptor.OgcXmlUtil;
import de.terrestris.shoguncore.util.interceptor.WpsRequestInterceptorInterface;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.apache.logging.log4j.LogManager.getLogger;

/**
 * Checks WPS requests: a user may describe and run a process only with READ
 * on a {@link WpsProcess} of its identifier. Execute also checks the
 * GeoServer layers that the inputs read (see {@link WpsRequests}): each
 * feature type, WMS layer and coverage needs READ, as in the WFS, WMS and WCS
 * interceptors. A refused process gives the same exception as an unknown
 * one; GeoServer sends it with HTTP 200, so the interceptor does, too.
 * <p>
 * GetExecutionStatus, GetExecutionResult and Dismiss only get the GeoServer
 * credentials: their execution id is a random id that only the client of the
 * Execute request knows.
 */
public class WpsRequestInterceptor implements WpsRequestInterceptorInterface {

    private static final Logger LOG = getLogger(WpsRequestInterceptor.class);

    private static final String DATA_INPUTS = "DataInputs";

    @Autowired
    @Qualifier("layerService")
    protected LayerService<Layer, LayerDao<Layer>> layerService;

    @Autowired
    @Qualifier("wpsProcessService")
    protected WpsProcessService<WpsProcess, WpsProcessDao<WpsProcess>> wpsProcessService;

    /**
     * The GeoServer URLs; references to their hosts are GeoServer layers.
     */
    @Autowired(required = false)
    @Qualifier("geoServerNameSpaces")
    protected Properties geoServerNameSpaces;

    @Value("${geoserver.username:}")
    private String gsUser;

    @Value("${geoserver.password:}")
    private String gsPass;

    @Override
    public MutableHttpServletRequest interceptGetCapabilities(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WPS GetCapabilities");
        // the response interceptor removes the processes without READ
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    @Override
    public MutableHttpServletRequest interceptDescribeProcess(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WPS DescribeProcess");
        Set<String> identifiers;
        if (isXmlPost(request)) {
            identifiers = WpsRequests.ofXml(OgcXmlUtil.getRequestBody(request), origins(request)).getProcesses();
        } else {
            identifiers = WpsRequests.ofDescribeProcessKvp(request.getParameterIgnoreCase("identifier"));
        }
        checkProcesses(identifiers);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    @Override
    public MutableHttpServletRequest interceptExecute(MutableHttpServletRequest request) {
        String operation = operation(request);
        if (!"Execute".equalsIgnoreCase(operation)) {
            LOG.debug("Intercepting WPS " + operation);
            return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
        }
        LOG.debug("Intercepting WPS Execute");
        WpsRequests.Names names;
        if (isXmlPost(request)) {
            names = WpsRequests.ofXml(OgcXmlUtil.getRequestBody(request), origins(request));
        } else {
            names = WpsRequests.ofExecuteKvp(request.getParameterIgnoreCase("identifier"),
                request.getParameter(parameterKey(request, DATA_INPUTS)), origins(request));
        }
        checkProcesses(names.getProcesses());
        checkLayers(request, names);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    private void checkProcesses(Set<String> identifiers) {
        if (identifiers.isEmpty()) {
            return;
        }
        List<WpsProcess> readable = wpsProcessService.findAll();
        for (String identifier : identifiers) {
            if (!WpsProcessService.isAllowed(readable, identifier)) {
                LOG.debug("No READ permission on WPS process " + identifier);
                throw unknownProcess(identifier);
            }
        }
    }

    /**
     * @return The exception that GeoServer sends for an unknown process
     */
    static OgcException unknownProcess(String identifier) {
        return new OgcException(OgcException.NO_APPLICABLE_CODE, null, "No such process: " + identifier)
            .withHttpStatus(HttpStatus.OK);
    }

    private void checkLayers(MutableHttpServletRequest request, WpsRequests.Names names) {
        if (names.getLayers().isEmpty() && names.getCoverages().isEmpty()) {
            return;
        }
        String namespace = LayerNames.endpointNamespace(request);
        Set<String> allowed = FeatureTypes.keys(layerService.findAll(), namespace);
        for (String layer : names.getLayers()) {
            if (!allowed.contains(LayerNames.key(layer, namespace))) {
                throw unknownLayer(layer);
            }
        }
        for (String coverage : names.getCoverages()) {
            if (!allowed.contains(LayerNames.coverageKey(coverage, namespace))) {
                throw unknownLayer(coverage);
            }
        }
    }

    private static OgcException unknownLayer(String name) {
        LOG.debug("No READ permission on the input layer " + name);
        return new OgcException(OgcException.INVALID_PARAMETER_VALUE, DATA_INPUTS, "Unknown layer: " + name);
    }

    /**
     * @return "scheme://host:port" of the GeoServer URLs (the request URI is
     * the GeoServer URL of the request)
     */
    private Set<String> origins(MutableHttpServletRequest request) {
        Set<String> origins = new LinkedHashSet<>();
        addOrigin(origins, request.getRequestURI());
        if (geoServerNameSpaces != null) {
            for (String name : geoServerNameSpaces.stringPropertyNames()) {
                addOrigin(origins, geoServerNameSpaces.getProperty(name));
            }
        }
        origins.remove("");
        return origins;
    }

    private static void addOrigin(Set<String> origins, String url) {
        if (StringUtils.isBlank(url)) {
            return;
        }
        try {
            origins.add(WpsRequests.origin(new URI(url.trim())));
        } catch (URISyntaxException e) {
            LOG.debug("Not a URL: " + url);
        }
    }

    private static boolean isXmlPost(MutableHttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String body = OgcXmlUtil.getRequestBody(request);
        return body != null && CapabilitiesFilter.isXml(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String operation(MutableHttpServletRequest request) {
        try {
            return MutableHttpServletRequest.getRequestParameterValue(request, "REQUEST");
        } catch (InterceptorException | IOException e) {
            return request.getParameterIgnoreCase("REQUEST");
        }
    }

    private static String parameterKey(MutableHttpServletRequest request, String name) {
        for (String key : request.getParameterMap().keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return name;
    }
}

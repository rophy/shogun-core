package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.interceptor.GeoserverAuthHeaderRequest;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import de.terrestris.shoguncore.util.interceptor.WcsRequestInterceptorInterface;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.Set;

import static org.apache.logging.log4j.LogManager.getLogger;

/**
 * Interceptor class for WCS requests. Adds basic auth headers based on the GS
 * properties and checks the read permission on the requested coverages. A
 * coverage is readable if the user may read a layer entity with an
 * {@link de.terrestris.shoguncore.model.layer.source.ImageWmsLayerDataSource}
 * of the same name (as for WFS).
 */
public class WcsRequestInterceptor extends BaseInterceptor implements WcsRequestInterceptorInterface {

    private static final Logger LOG = getLogger(WcsRequestInterceptor.class);

    /** WCS 2.0 exception code for unknown coverages. */
    static final String NO_SUCH_COVERAGE = "NoSuchCoverage";

    @Autowired
    @Qualifier("layerService")
    protected LayerService<Layer, LayerDao<Layer>> layerService;

    @Value("${geoserver.username:}")
    private String gsUser;

    @Value("${geoserver.password:}")
    private String gsPass;

    @Override
    public MutableHttpServletRequest interceptGetCapabilities(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WCS GetCapabilities");
        // response will be intercepted
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    @Override
    public MutableHttpServletRequest interceptDescribeCoverage(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WCS DescribeCoverage");
        checkCoverages(request);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    @Override
    public MutableHttpServletRequest interceptGetCoverage(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WCS GetCoverage");
        checkCoverages(request);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     * Refuses the request if the user may not read one of the coverages. A
     * coverage without permission gives the same error as an unknown one.
     * A request without coverages is sent to GeoServer, which reports the
     * missing value.
     */
    private void checkCoverages(MutableHttpServletRequest request) {
        String namespace = LayerNames.endpointNamespace(request);
        Set<String> allowed = LayerNames.wmsLayerKeys(layerService.findAll(),
            request.getContextPath() + "/geoserver.action", namespace);
        for (String id : CoverageIds.fromRequest(request)) {
            if (!allowed.contains(LayerNames.coverageKey(id, namespace))) {
                boolean wcs2 = StringUtils.startsWith(version(request), "2") || CoverageIds.isWcs2(request);
                forbidLayer(wcs2 ? NO_SUCH_COVERAGE : OgcException.INVALID_PARAMETER_VALUE,
                    CoverageIds.locator(request), id);
            }
        }
    }

    private static String version(MutableHttpServletRequest request) {
        return request.getParameterIgnoreCase("version");
    }
}

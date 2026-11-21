package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.dao.UserDao;
import de.terrestris.shoguncore.model.User;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.security.Permission;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.service.UserService;
import de.terrestris.shoguncore.util.interceptor.GeoserverAuthHeaderRequest;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import de.terrestris.shoguncore.util.interceptor.WfsRequestInterceptorInterface;

import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import static org.apache.logging.log4j.LogManager.getLogger;

import java.util.*;

/**
 * Interceptor class for WFS requests. Adds basic auth headers based on the GS
 * properties by default and checks for permission on layer
 *
 * @author Nils Bühner
 * @author Johannes Weskamm
 *
 */
public class WfsRequestInterceptor extends BaseInterceptor implements WfsRequestInterceptorInterface {

    /**
     *
     */
    private static final Logger LOG = getLogger(WfsRequestInterceptor.class);

    /**
     *
     */
    @Autowired
    @Qualifier("userService")
    protected UserService<User, UserDao<User>> userService;

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
    public MutableHttpServletRequest interceptGetCapabilities(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WFS GetCapabilities");
        // response will be intercepted
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptDescribeFeatureType(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WFS DescribeFeatureType");
        checkTypeNames(request, Permission.READ);
        // response will be intercepted
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptGetFeature(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WFS GetFeature");
        checkTypeNames(request, Permission.READ);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptLockFeature(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WFS LockFeature");
        checkTypeNames(request, Permission.UPDATE);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     *
     */
    @Override
    public MutableHttpServletRequest interceptTransaction(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WFS Transaction");
        // WFS 2.0, table 3: an unknown feature type in a transaction is an InvalidValue
        checkTypeNames(request, Permission.UPDATE, "InvalidValue");
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     * Reads the referenced feature types. Needs READ on them.
     */
    @Override
    public MutableHttpServletRequest interceptCreateStoredQuery(MutableHttpServletRequest request) {
        LOG.debug("Intercepting WFS CreateStoredQuery");
        checkTypeNames(request, Permission.READ);
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    /**
     * Checks the permission on all type names of the request. A type without
     * permission gives the same error as an unknown type. A request without
     * type names (e.g. a stored query) is sent to GeoServer; the response
     * interceptor checks the returned or locked features.
     *
     * @param request The request
     * @param permission READ or UPDATE
     */
    private void checkTypeNames(MutableHttpServletRequest request, Permission permission) {
        checkTypeNames(request, permission, OgcException.INVALID_PARAMETER_VALUE);
    }

    /**
     * @param code The exception code for a type without permission
     */
    private void checkTypeNames(MutableHttpServletRequest request, Permission permission, String code) {
        Set<String> typeNames = TypeNames.fromRequest(request);
        if (typeNames.isEmpty()) {
            // e.g. a stored query: the response interceptor checks the
            // returned (or locked) features
            return;
        }
        String namespace = LayerNames.endpointNamespace(request);
        Set<String> allowed = getAllowedTypeNames(namespace, permission);
        for (String typeName : typeNames) {
            if (!allowed.contains(TypeNames.key(typeName, namespace))) {
                LOG.debug("No " + permission + " permission on feature type " + TypeNames.display(typeName));
                forbidLayer(code,
                    request.getParameterIgnoreCase("typeNames") != null ? "typeNames" : "typeName",
                    TypeNames.display(typeName));
            }
        }
    }

    /**
     * @return The keys of the feature types that the user has the permission for
     */
    private Set<String> getAllowedTypeNames(String namespace, Permission permission) {
        List<Layer> layers = new ArrayList<>();
        for (Layer layer : layerService.findAll()) {
            // READ is checked by findAll
            if (permission == Permission.READ || checkForPermission(layer, permission)) {
                layers.add(layer);
            }
        }
        return FeatureTypes.keys(layers, namespace);
    }

    /**
     * check if user has permission on given layer by user or group permissions
     */
    private boolean checkForPermission(Layer layer, Permission permission) {
        return FeatureTypes.hasPermission(layer, userService.getUserBySession(), permission);
    }

}

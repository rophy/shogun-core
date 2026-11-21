package de.terrestris.shoguncore.util.interceptor;

import de.terrestris.shoguncore.util.model.Response;
import org.springframework.stereotype.Component;

@Component
public interface WfsResponseInterceptorInterface {

    Response interceptGetCapabilities(MutableHttpServletRequest request, Response response);

    Response interceptDescribeFeatureType(MutableHttpServletRequest request, Response response);

    Response interceptGetFeature(MutableHttpServletRequest request, Response response);

    Response interceptLockFeature(MutableHttpServletRequest request, Response response);

    Response interceptTransaction(MutableHttpServletRequest request, Response response);

    /**
     * WFS 2.0 GetPropertyValue.
     */
    default Response interceptGetPropertyValue(MutableHttpServletRequest request, Response response) {
        return interceptGetFeature(request, response);
    }

    /**
     * WFS 2.0 GetFeatureWithLock.
     */
    default Response interceptGetFeatureWithLock(MutableHttpServletRequest request, Response response) {
        return interceptGetFeature(request, response);
    }

    /**
     * WFS 2.0 ListStoredQueries, DescribeStoredQueries, CreateStoredQuery
     * and DropStoredQuery.
     */
    default Response interceptStoredQueryOperation(MutableHttpServletRequest request, Response response) {
        return response;
    }

}

package de.terrestris.shoguncore.util.interceptor;

import org.springframework.stereotype.Component;

@Component
public interface WfsRequestInterceptorInterface {

    MutableHttpServletRequest interceptGetCapabilities(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptDescribeFeatureType(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptGetFeature(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptLockFeature(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptTransaction(MutableHttpServletRequest request);

    /**
     * WFS 2.0 GetPropertyValue. Reads feature data like GetFeature.
     */
    default MutableHttpServletRequest interceptGetPropertyValue(MutableHttpServletRequest request) {
        return interceptGetFeature(request);
    }

    /**
     * WFS 2.0 GetFeatureWithLock. Locks features like LockFeature.
     */
    default MutableHttpServletRequest interceptGetFeatureWithLock(MutableHttpServletRequest request) {
        return interceptLockFeature(request);
    }

    /**
     * WFS 2.0 ListStoredQueries. Reads service metadata like GetCapabilities.
     */
    default MutableHttpServletRequest interceptListStoredQueries(MutableHttpServletRequest request) {
        return interceptGetCapabilities(request);
    }

    /**
     * WFS 2.0 DescribeStoredQueries. Reads service metadata like GetCapabilities.
     */
    default MutableHttpServletRequest interceptDescribeStoredQueries(MutableHttpServletRequest request) {
        return interceptGetCapabilities(request);
    }

    /**
     * WFS 2.0 CreateStoredQuery.
     */
    default MutableHttpServletRequest interceptCreateStoredQuery(MutableHttpServletRequest request) {
        return interceptGetCapabilities(request);
    }

    /**
     * WFS 2.0 DropStoredQuery.
     */
    default MutableHttpServletRequest interceptDropStoredQuery(MutableHttpServletRequest request) {
        return interceptGetCapabilities(request);
    }

}

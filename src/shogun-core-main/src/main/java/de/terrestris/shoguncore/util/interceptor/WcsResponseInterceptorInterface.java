package de.terrestris.shoguncore.util.interceptor;

import de.terrestris.shoguncore.util.model.Response;
import org.springframework.stereotype.Component;

@Component
public interface WcsResponseInterceptorInterface {

    Response interceptGetCapabilities(MutableHttpServletRequest request, Response response);

    Response interceptDescribeCoverage(MutableHttpServletRequest request, Response response);

    Response interceptGetCoverage(MutableHttpServletRequest request, Response response);

    /**
     * WCS 2.0 EO DescribeEOCoverageSet.
     */
    default Response interceptDescribeEOCoverageSet(MutableHttpServletRequest request, Response response) {
        return interceptDescribeCoverage(request, response);
    }

}

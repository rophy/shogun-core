package de.terrestris.shoguncore.util.interceptor;

import org.springframework.stereotype.Component;

@Component
public interface WcsRequestInterceptorInterface {

    MutableHttpServletRequest interceptGetCapabilities(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptDescribeCoverage(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptGetCoverage(MutableHttpServletRequest request);

    /**
     * WCS 2.0 EO DescribeEOCoverageSet (GeoServer extension wcs2_0-eo). It
     * describes the coverages of dataset series, like DescribeCoverage.
     */
    default MutableHttpServletRequest interceptDescribeEOCoverageSet(MutableHttpServletRequest request) {
        return interceptDescribeCoverage(request);
    }

}

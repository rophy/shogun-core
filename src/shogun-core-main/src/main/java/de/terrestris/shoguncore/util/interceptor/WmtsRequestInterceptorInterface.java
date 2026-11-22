package de.terrestris.shoguncore.util.interceptor;

import org.springframework.stereotype.Component;

@Component
public interface WmtsRequestInterceptorInterface {

    MutableHttpServletRequest interceptGetTile(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptGetCapabilities(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptGetFeatureInfo(MutableHttpServletRequest request);

    /**
     * The operations of the GeoServer WMTS multi-dimensional extension
     * (DescribeDomains, GetDomainValues, GetHistogram, GetFeature). They read
     * one layer (parameter LAYER), like GetTile.
     */
    default MutableHttpServletRequest interceptMultiDimensional(MutableHttpServletRequest request) {
        return interceptGetTile(request);
    }

}

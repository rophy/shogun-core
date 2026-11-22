package de.terrestris.shoguncore.util.interceptor;

import de.terrestris.shoguncore.util.model.Response;
import org.springframework.stereotype.Component;

@Component
public interface WmtsResponseInterceptorInterface {

    Response interceptGetTile(MutableHttpServletRequest request, Response response);

    Response interceptGetCapabilities(MutableHttpServletRequest request, Response response);

    Response interceptGetFeatureInfo(MutableHttpServletRequest request, Response response);

    /**
     * The operations of the GeoServer WMTS multi-dimensional extension.
     */
    default Response interceptMultiDimensional(MutableHttpServletRequest request, Response response) {
        return response;
    }

}

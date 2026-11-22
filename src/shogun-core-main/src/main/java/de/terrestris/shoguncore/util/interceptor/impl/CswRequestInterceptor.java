package de.terrestris.shoguncore.util.interceptor.impl;

import de.terrestris.shoguncore.util.interceptor.CswRequestInterceptorInterface;
import de.terrestris.shoguncore.util.interceptor.GeoserverAuthHeaderRequest;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import org.springframework.beans.factory.annotation.Value;

/**
 * CSW request interceptor that only adds the GeoServer credentials (no
 * permission checks; see the secure CswRequestInterceptor for these).
 */
public class CswRequestInterceptor implements CswRequestInterceptorInterface {

    @Value("${geoserver.username:}")
    private String gsUser;

    @Value("${geoserver.password:}")
    private String gsPass;

    private MutableHttpServletRequest credentials(MutableHttpServletRequest request) {
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    @Override
    public MutableHttpServletRequest interceptGetCapabilities(MutableHttpServletRequest request) {
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptDescribeRecord(MutableHttpServletRequest request) {
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptGetRecords(MutableHttpServletRequest request) {
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptGetRecordById(MutableHttpServletRequest request) {
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptGetDomain(MutableHttpServletRequest request) {
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptGetRepositoryItem(MutableHttpServletRequest request) {
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptDirectDownload(MutableHttpServletRequest request) {
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptCatalogChange(MutableHttpServletRequest request) {
        return credentials(request);
    }
}

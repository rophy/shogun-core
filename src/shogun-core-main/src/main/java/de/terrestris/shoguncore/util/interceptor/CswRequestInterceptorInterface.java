package de.terrestris.shoguncore.util.interceptor;

import org.springframework.stereotype.Component;

/**
 * Request interceptor of the CSW 2.0.2 catalogue service (GeoServer extension
 * csw). GeoServer adds GetRepositoryItem and DirectDownload.
 */
@Component
public interface CswRequestInterceptorInterface {

    MutableHttpServletRequest interceptGetCapabilities(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptDescribeRecord(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptGetRecords(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptGetRecordById(MutableHttpServletRequest request);

    MutableHttpServletRequest interceptGetDomain(MutableHttpServletRequest request);

    /**
     * GetRepositoryItem: the repository item of a record (e.g. its ISO
     * metadata document).
     */
    MutableHttpServletRequest interceptGetRepositoryItem(MutableHttpServletRequest request);

    /**
     * DirectDownload: the raw data of the resource of a record.
     */
    MutableHttpServletRequest interceptDirectDownload(MutableHttpServletRequest request);

    /**
     * Harvest and Transaction change the catalogue.
     */
    MutableHttpServletRequest interceptCatalogChange(MutableHttpServletRequest request);

}

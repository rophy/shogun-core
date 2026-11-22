package de.terrestris.shoguncore.util.interceptor;

import de.terrestris.shoguncore.util.model.Response;
import org.springframework.stereotype.Component;

/**
 * Response interceptor of the CSW 2.0.2 catalogue service.
 */
@Component
public interface CswResponseInterceptorInterface {

    Response interceptGetCapabilities(MutableHttpServletRequest request, Response response);

    Response interceptDescribeRecord(MutableHttpServletRequest request, Response response);

    Response interceptGetRecords(MutableHttpServletRequest request, Response response);

    Response interceptGetRecordById(MutableHttpServletRequest request, Response response);

    Response interceptGetDomain(MutableHttpServletRequest request, Response response);

    Response interceptGetRepositoryItem(MutableHttpServletRequest request, Response response);

    Response interceptDirectDownload(MutableHttpServletRequest request, Response response);

    Response interceptCatalogChange(MutableHttpServletRequest request, Response response);

}

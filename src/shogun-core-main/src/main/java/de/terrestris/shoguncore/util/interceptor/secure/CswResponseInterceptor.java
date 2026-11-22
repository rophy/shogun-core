package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.interceptor.CswResponseInterceptorInterface;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import de.terrestris.shoguncore.util.model.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.w3c.dom.Document;

import java.util.Set;

/**
 * Changes CSW 2.0.2 responses: GetRecords and GetRecordById contain only
 * readable records (see {@link CswRecords}), with counts of the readable
 * records, and all GeoServer service URLs (capabilities, record references)
 * point to the interceptor.
 */
public class CswResponseInterceptor implements CswResponseInterceptorInterface {

    @Autowired
    @Qualifier("layerService")
    protected LayerService<Layer, LayerDao<Layer>> layerService;

    @Override
    public Response interceptGetCapabilities(MutableHttpServletRequest request, Response response) {
        return rewrite(request, response);
    }

    @Override
    public Response interceptDescribeRecord(MutableHttpServletRequest request, Response response) {
        return rewrite(request, response);
    }

    @Override
    public Response interceptGetRecords(MutableHttpServletRequest request, Response response) {
        CswRecords.Paging paging = CswRecords.paging(request);
        if (paging != null && paging.isValidate()) {
            return response;
        }
        if (paging == null) {
            // the request interceptor could not read the paging
            throw new OgcException(OgcException.NO_APPLICABLE_CODE, null, "The request cannot be read.");
        }
        String namespace = LayerNames.endpointNamespace(request);
        Set<String> allowed = FeatureTypes.keys(layerService.findAll(), namespace);
        return filter(request, response, doc -> CswRecords.page(doc, allowed, namespace, paging));
    }

    @Override
    public Response interceptGetRecordById(MutableHttpServletRequest request, Response response) {
        String namespace = LayerNames.endpointNamespace(request);
        Set<String> allowed = FeatureTypes.keys(layerService.findAll(), namespace);
        return filter(request, response,
            doc -> CswRecords.removeUnreadable(doc.getDocumentElement(), allowed, namespace));
    }

    @Override
    public Response interceptGetDomain(MutableHttpServletRequest request, Response response) {
        return rewrite(request, response);
    }

    @Override
    public Response interceptGetRepositoryItem(MutableHttpServletRequest request, Response response) {
        // the item of a checked record (e.g. its ISO metadata document)
        return CapabilitiesFilter.isXml(response) ? rewrite(request, response) : response;
    }

    @Override
    public Response interceptDirectDownload(MutableHttpServletRequest request, Response response) {
        // the raw data (zip) of a checked layer
        return response;
    }

    @Override
    public Response interceptCatalogChange(MutableHttpServletRequest request, Response response) {
        return response;
    }

    private Response rewrite(MutableHttpServletRequest request, Response response) {
        return filter(request, response, doc -> { });
    }

    private interface Change {
        void apply(Document doc);
    }

    private Response filter(MutableHttpServletRequest request, Response response, Change change) {
        if (!CapabilitiesFilter.isXml(response)) {
            return response;
        }
        String baseUrl = ProxyUrls.baseUrl(request, request.getParameterIgnoreCase("CUSTOM_ENDPOINT"));
        String geoServerUri = request.getRequestURI();
        return CapabilitiesFilter.filter(response, doc -> {
            change.apply(doc);
            CswRecords.rewriteUrls(doc.getDocumentElement(), geoServerUri,
                WmtsRequestInterceptor.geoServerRoot(request), baseUrl);
        });
    }
}

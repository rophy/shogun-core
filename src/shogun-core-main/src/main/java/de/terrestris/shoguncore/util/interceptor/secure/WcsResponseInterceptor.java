package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.WcsResponseInterceptorInterface;
import de.terrestris.shoguncore.util.model.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.transaction.Transactional;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Filters WCS 1.0, 1.1 and 2.0 capabilities documents: removes the coverages
 * that the user may not read and rewrites the GeoServer URLs to the
 * interceptor URL.
 */
public class WcsResponseInterceptor implements WcsResponseInterceptorInterface {

    /** The coverage entries and their identifier, for WCS 2.0, 1.1 and 1.0. */
    private static final String COVERAGES = "//*[local-name()='CoverageSummary' or local-name()='CoverageOfferingBrief']";

    private static final String COVERAGE_ID = "*[local-name()='CoverageId' or local-name()='Identifier'"
        + " or local-name()='name']";

    private static final String XLINK = "http://www.w3.org/1999/xlink";

    @Autowired
    @Qualifier("layerService")
    protected LayerService<Layer, LayerDao<Layer>> layerService;

    @Override
    @Transactional(value = Transactional.TxType.REQUIRED)
    public Response interceptGetCapabilities(MutableHttpServletRequest request, Response response) {
        String endpoint = request.getParameterIgnoreCase("CUSTOM_ENDPOINT");
        String namespace = LayerNames.endpointNamespace(request);
        Set<String> allowed = LayerNames.wmsLayerKeys(layerService.findAll(),
            request.getContextPath() + "/geoserver.action", namespace);
        String baseUrl = ProxyUrls.baseUrl(request, endpoint);
        String geoServerUri = request.getRequestURI();
        return CapabilitiesFilter.filter(response, doc -> {
            removeCoverages(doc, allowed, namespace);
            updateUrls(doc, geoServerUri, baseUrl);
        });
    }

    @Override
    public Response interceptDescribeCoverage(MutableHttpServletRequest request, Response response) {
        return response;
    }

    @Override
    public Response interceptGetCoverage(MutableHttpServletRequest request, Response response) {
        return response;
    }

    private static void removeCoverages(Document doc, Set<String> allowed, String namespace)
        throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList coverages = (NodeList) xpath.evaluate(COVERAGES, doc, XPathConstants.NODESET);
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < coverages.getLength(); i++) {
            Element coverage = (Element) coverages.item(i);
            String id = xpath.evaluate(COVERAGE_ID, coverage);
            if (id.isEmpty() || !allowed.contains(LayerNames.coverageKey(id, namespace))) {
                toRemove.add(coverage);
            }
        }
        toRemove.forEach(element -> element.getParentNode().removeChild(element));
    }

    private static void updateUrls(Document doc, String geoServerUri, String baseUrl)
        throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList links = (NodeList) xpath.evaluate("//@*[local-name()='href']", doc, XPathConstants.NODESET);
        for (int i = 0; i < links.getLength(); i++) {
            Attr link = (Attr) links.item(i);
            if (XLINK.equals(link.getNamespaceURI()) || link.getNamespaceURI() == null) {
                link.setValue(ProxyUrls.rewrite(link.getValue(), geoServerUri, baseUrl));
            }
        }
    }
}

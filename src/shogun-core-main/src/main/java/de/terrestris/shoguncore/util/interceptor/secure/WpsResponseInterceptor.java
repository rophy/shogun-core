package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.WpsResponseInterceptorInterface;
import de.terrestris.shoguncore.util.model.Response;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

/**
 * Rewrites the GeoServer URLs in WPS responses (capabilities, execute
 * responses with status location and output references) to the interceptor
 * URL, so that clients do not bypass the interceptor.
 */
public class WpsResponseInterceptor implements WpsResponseInterceptorInterface {

    /** Attributes with GeoServer URLs: xlink:href, wps:Reference/@href, statusLocation, serviceInstance. */
    private static final String URL_ATTRIBUTES = "//@*[local-name()='href' or local-name()='statusLocation'"
        + " or local-name()='serviceInstance']";

    @Override
    public Response interceptGetCapabilities(MutableHttpServletRequest request, Response response) {
        return CapabilitiesFilter.filter(response, doc -> updateUrls(doc, request));
    }

    @Override
    public Response interceptDescribeProcess(MutableHttpServletRequest request, Response response) {
        return response;
    }

    /**
     * Also used for GetExecutionStatus and GetExecutionResult. Raw outputs
     * (not XML) are not changed.
     */
    @Override
    public Response interceptExecute(MutableHttpServletRequest request, Response response) {
        if (!CapabilitiesFilter.isXml(response)) {
            return response;
        }
        return CapabilitiesFilter.filter(response, doc -> updateUrls(doc, request));
    }

    private static void updateUrls(Document doc, MutableHttpServletRequest request)
        throws XPathExpressionException {
        String baseUrl = ProxyUrls.baseUrl(request, request.getParameterIgnoreCase("CUSTOM_ENDPOINT"));
        String geoServerUri = request.getRequestURI();
        NodeList links = (NodeList) XPathFactory.newInstance().newXPath()
            .evaluate(URL_ATTRIBUTES, doc, XPathConstants.NODESET);
        for (int i = 0; i < links.getLength(); i++) {
            Attr link = (Attr) links.item(i);
            link.setValue(ProxyUrls.rewrite(link.getValue(), geoServerUri, baseUrl));
        }
    }
}

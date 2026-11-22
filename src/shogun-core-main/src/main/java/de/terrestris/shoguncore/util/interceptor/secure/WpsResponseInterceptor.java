package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.WpsProcessDao;
import de.terrestris.shoguncore.model.wps.WpsProcess;
import de.terrestris.shoguncore.service.WpsProcessService;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.WpsResponseInterceptorInterface;
import de.terrestris.shoguncore.util.model.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.List;

/**
 * Rewrites the GeoServer URLs in WPS responses (capabilities, execute
 * responses with status location and output references) to the interceptor
 * URL, so that clients do not bypass the interceptor.
 * <p>
 * The capabilities (ProcessOfferings) and DescribeProcess responses contain
 * only the processes that the user may READ (see {@link WpsProcess} and
 * {@link WpsRequestInterceptor}).
 */
public class WpsResponseInterceptor implements WpsResponseInterceptorInterface {

    /** wps:Process of the capabilities. */
    private static final String PROCESSES = "//*[local-name()='ProcessOfferings']/*[local-name()='Process']";

    /** ProcessDescription of DescribeProcess. */
    private static final String DESCRIPTIONS = "//*[local-name()='ProcessDescription']";

    @Autowired
    @Qualifier("wpsProcessService")
    protected WpsProcessService<WpsProcess, WpsProcessDao<WpsProcess>> wpsProcessService;

    /** Attributes with GeoServer URLs: xlink:href, wps:Reference/@href, statusLocation, serviceInstance. */
    private static final String URL_ATTRIBUTES = "//@*[local-name()='href' or local-name()='statusLocation'"
        + " or local-name()='serviceInstance']";

    @Override
    public Response interceptGetCapabilities(MutableHttpServletRequest request, Response response) {
        List<WpsProcess> readable = wpsProcessService.findAll();
        return CapabilitiesFilter.filter(response, doc -> {
            removeProcesses(doc, PROCESSES, readable);
            updateUrls(doc, request);
        });
    }

    @Override
    public Response interceptDescribeProcess(MutableHttpServletRequest request, Response response) {
        List<WpsProcess> readable = wpsProcessService.findAll();
        return CapabilitiesFilter.filter(response, doc -> removeProcesses(doc, DESCRIPTIONS, readable));
    }

    /**
     * Removes the processes (selected by the XPath) whose ows:Identifier the
     * user may not READ.
     */
    static void removeProcesses(Document doc, String xpath, List<WpsProcess> readable)
        throws XPathExpressionException {
        NodeList processes = (NodeList) XPathFactory.newInstance().newXPath()
            .evaluate(xpath, doc, XPathConstants.NODESET);
        for (int i = 0; i < processes.getLength(); i++) {
            Element process = (Element) processes.item(i);
            String identifier = identifier(process);
            if (identifier == null || !WpsProcessService.isAllowed(readable, identifier)) {
                process.getParentNode().removeChild(process);
            }
        }
    }

    private static String identifier(Element process) {
        for (Node child = process.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && "Identifier".equals(child.getLocalName())) {
                return child.getTextContent().trim();
            }
        }
        return null;
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

package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.ImageWmsLayerDataSource;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.WmsResponseInterceptorInterface;
import de.terrestris.shoguncore.util.model.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.deegree.commons.xml.CommonNamespaces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.transaction.Transactional;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

import static org.apache.logging.log4j.LogManager.getLogger;

/**
 * @author Daniel Koch
 * @author Andreas Schmitz
 * @author Johannes Weskamm
 * @author terrestris GmbH & Co. KG
 */
public class WmsResponseInterceptor implements WmsResponseInterceptorInterface {

    private static final Logger LOG = getLogger(WmsResponseInterceptor.class);

    private static final String XLINK = "http://www.w3.org/1999/xlink";

    /**
     *
     */
    @Autowired
    @Qualifier("layerService")
    protected LayerService<Layer, LayerDao<Layer>> layerService;

    /**
     * Intercepts the response in order to filter the capabilities document.
     * URLs will be replaced to match the geoserver.action interface and
     * layers without permission will be removed from the document
     *
     * @param request The request
     * @param response The response to intercept
     */
    @Override
    @Transactional(value = Transactional.TxType.REQUIRED)
    public Response interceptGetCapabilities(MutableHttpServletRequest request, Response response) {
        String endpoint = request.getParameterIgnoreCase("CUSTOM_ENDPOINT");

        // get all layers allowed for this user in order to filter out not allowed ones
        List<String> layerNames = new ArrayList<String>();
        for (Layer layer : layerService.findAll()) {
            if (layer.getSource() instanceof ImageWmsLayerDataSource) {
                ImageWmsLayerDataSource source = (ImageWmsLayerDataSource) layer.getSource();
                layerNames.add(source.getLayerNames());
            }
        }

        String baseUrl = ProxyUrls.baseUrl(request, endpoint);
        String geoServerUri = request.getRequestURI();
        String namespace = LayerNames.endpointNamespace(request);
        return CapabilitiesFilter.filter(response, doc -> {
            String version = doc.getDocumentElement().getAttribute("version");
            String ns = version.equals("1.3.0") ? "http://www.opengis.net/wms" : "";
            removeLayers(doc, ns, layerNames, namespace);
            updateUrls(doc, ns, geoServerUri, baseUrl);
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response interceptGetMap(MutableHttpServletRequest mutableRequest, Response response) {
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response interceptGetFeatureInfo(MutableHttpServletRequest mutableRequest, Response response) {
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response interceptDescribeLayer(MutableHttpServletRequest mutableRequest, Response response) {
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response interceptGetLegendGraphic(MutableHttpServletRequest mutableRequest, Response response) {
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response interceptGetStyles(MutableHttpServletRequest mutableRequest, Response response) {
        return response;
    }

    /**
     * Removes layers from the capabilities document
     *
     * @param doc The document
     * @param namespace The namespace
     * @param layerNames The layerNames to keep
     * @throws XPathExpressionException
     */
    private void removeLayers(Document doc, String namespace, List<String> layerNames, String endpointNamespace)
        throws XPathExpressionException {
        Set<String> allowed = new HashSet<>();
        for (String names : layerNames) {
            if (names != null) {
                for (String name : names.split(",")) {
                    allowed.add(LayerNames.key(name, endpointNamespace));
                }
            }
        }
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        NamespaceContext nscontext = CommonNamespaces.getNamespaceContext();
        xpath.setNamespaceContext(nscontext);
        String prefix = namespace.equals("") ? "" : "wms:";
        XPathExpression expr = xpath.compile("//" + prefix + "Layer/" + prefix + "Name");
        NodeList nodeList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); ++i) {
            Element name = (Element) nodeList.item(i);
            if (!allowed.contains(LayerNames.key(name.getTextContent(), endpointNamespace))) {
                toRemove.add((Element) name.getParentNode());
            }
        }
        toRemove.forEach(element -> element.getParentNode().removeChild(element));
    }

    /**
     * Rewrites URLs in the capabilites document
     *
     * @param doc The document
     * @param namespace The namespace
     * @param baseUrl The baseUrl
     * @throws XPathExpressionException
     */
    private void updateUrls(Document doc, String namespace, String geoServerUri, String baseUrl)
        throws XPathExpressionException {
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        NamespaceContext nscontext = CommonNamespaces.getNamespaceContext();
        xpath.setNamespaceContext(nscontext);
        String prefix = namespace.equals("") ? "" : "wms:";
        XPathExpression expr = xpath.compile("//" + prefix + "OnlineResource");
        NodeList nodeList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); ++i) {
            Element link = (Element) nodeList.item(i);
            String url = link.getAttributeNS(XLINK, "href");
            String newUrl = ProxyUrls.rewrite(url, geoServerUri, baseUrl);
            if (!StringUtils.equals(url, newUrl)) {
                link.setAttributeNS(XLINK, "xlink:href", newUrl);
            }
        }
    }
}

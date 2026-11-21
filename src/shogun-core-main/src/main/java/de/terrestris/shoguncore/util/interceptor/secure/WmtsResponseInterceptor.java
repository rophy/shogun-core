package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.WmtsLayerDataSource;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.http.HttpUtil;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.WmtsResponseInterceptorInterface;
import de.terrestris.shoguncore.util.interceptor.WmtsUtil;
import de.terrestris.shoguncore.util.model.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpException;
import org.apache.logging.log4j.Logger;
import org.deegree.commons.xml.CommonNamespaces;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.transaction.Transactional;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.apache.logging.log4j.LogManager.getLogger;

/**
 * @author Daniel Koch
 * @author Andreas Schmitz
 * @author Johannes Weskamm
 * @author terrestris GmbH & Co. KG
 */
public class WmtsResponseInterceptor implements WmtsResponseInterceptorInterface {

    private static final Logger LOG = getLogger(WmsResponseInterceptor.class);

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
        List<Layer> layers = layerService.findAll();
        List<Layer> externalLayers = new ArrayList<Layer>();
        List<String> layerNames = new ArrayList<String>();
        for (Layer layer : layers) {
            if (layer.getSource() instanceof WmtsLayerDataSource) {
                WmtsLayerDataSource source = (WmtsLayerDataSource) layer.getSource();
                if (source.getUrl().contains("geoserver.action")) {
                    layerNames.add(source.getWmtsLayer());
                } else {
                    externalLayers.add(layer);
                }
            }
        }

        String baseUrl = ProxyUrls.baseUrl(request, endpoint);
        String geoServerUri = request.getRequestURI();
        String namespace = LayerNames.endpointNamespace(request);
        return CapabilitiesFilter.filter(response, doc -> {
            removeLayers(doc, "wmts", layerNames, namespace);
            removeNonStandardResourceUrls(doc);
            updateUrls(doc, geoServerUri, baseUrl);
            for (Layer layer : externalLayers) {
                addExternalLayer(doc, layer);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response interceptGetTile(MutableHttpServletRequest mutableRequest, Response response) {
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
        for (String name : layerNames) {
            if (name != null) {
                allowed.add(LayerNames.key(name, endpointNamespace));
            }
        }
        XPath xpath = createXPath(doc);
        String prefix = namespace.equals("") ? "" : "wmts:";
        XPathExpression expr = xpath.compile("//" + prefix + "Layer/ows:Identifier");
        NodeList nodeList = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
        List<Element> toRemove = new ArrayList<>();
        for (int i = 0; i < nodeList.getLength(); ++i) {
            Element name = (Element) nodeList.item(i);
            String key = LayerNames.key(name.getTextContent(), endpointNamespace);
            boolean otherNamespace = StringUtils.isNotEmpty(endpointNamespace)
                && !key.startsWith(endpointNamespace.toLowerCase(Locale.ROOT) + ":");
            if (otherNamespace || !allowed.contains(key)) {
                toRemove.add((Element) name.getParentNode());
            }
        }
        toRemove.forEach(element -> element.getParentNode().removeChild(element));
    }

    /**
     * Rewrites the GeoServer URLs in the capabilites document: KVP URLs to
     * the interceptor URL, RESTful templates to
     * {baseUrl}/gwc/service/wmts/rest/...
     *
     * @param doc The document
     * @param geoServerUri The GeoServer URI that the request was sent to
     * @param baseUrl The baseUrl
     * @throws XPathExpressionException
     */
    private void updateUrls(Document doc, String geoServerUri, String baseUrl) throws XPathExpressionException {
        XPath xpath = createXPath(doc);
        NodeList nodeList = (NodeList) xpath.compile("//@xlink:href | //wmts:ResourceURL/@template")
            .evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < nodeList.getLength(); ++i) {
            String url = nodeList.item(i).getNodeValue();
            int rest = url.indexOf(WmtsUtil.REST_PATH);
            int restRoot = url.indexOf("/gwc/service/wmts/rest");
            if (restRoot > -1 && ProxyUrls.sameServer(url, geoServerUri)) {
                nodeList.item(i).setNodeValue(baseUrl + url.substring(rest > -1 ? rest : restRoot));
            } else {
                nodeList.item(i).setNodeValue(ProxyUrls.rewrite(url, geoServerUri, baseUrl));
            }
        }
    }

    /**
     * Removes resource URLs other than "tile" and "FeatureInfo" (GeoServer
     * adds "TileJSON"). The WMTS 1.0.0 schema as published with the standard
     * (and used by the OGC test suite) allows these two resource types only;
     * the later schema on schemas.opengis.net allows any string. The
     * interceptor does not support other resource URLs either.
     */
    static void removeNonStandardResourceUrls(Document doc) throws XPathExpressionException {
        NodeList list = (NodeList) createXPath(doc).compile(
            "//wmts:ResourceURL[@resourceType!='tile' and @resourceType!='FeatureInfo']")
            .evaluate(doc, XPathConstants.NODESET);
        for (int i = 0; i < list.getLength(); i++) {
            list.item(i).getParentNode().removeChild(list.item(i));
        }
    }

    private static XPath createXPath(Document doc) {
        XPath xpath = XPathFactory.newInstance().newXPath();
        String owsVersion = doc.getDocumentElement().getAttribute("xmlns:ows");
        String wmtsVersion = doc.getDocumentElement().getAttribute("xmlns");
        NamespaceContext nscontext = CommonNamespaces.getNamespaceContext().
            addNamespace("ows", owsVersion).
            addNamespace("wmts", wmtsVersion);
        xpath.setNamespaceContext(nscontext);
        return xpath;
    }

    /**
     * Add external Layers and their TileMatrixSets to the document
     * @param doc
     * @param layer
     */
    private void addExternalLayer(Document doc, Layer layer) {
        WmtsLayerDataSource source = (WmtsLayerDataSource) layer.getSource();
        String externalCapabilities = source.getCapabilitiesUrl();
        if (StringUtils.isEmpty(externalCapabilities)) {
            return;
        }
        try {
            Response response = HttpUtil.get(externalCapabilities);
            if (!response.getStatusCode().is2xxSuccessful()) {
                return;
            }
            byte[] body = response.getBody();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document externalDoc = builder.parse(new ByteArrayInputStream(body));

            XPathFactory xfactory = XPathFactory.newInstance();
            XPath xpath = xfactory.newXPath();
            String owsVersion = externalDoc.getDocumentElement().getAttribute("xmlns:ows");
            String wmtsVersion = externalDoc.getDocumentElement().getAttribute("xmlns");
            if (StringUtils.isEmpty(owsVersion)) {
                owsVersion = "http://www.opengis.net/ows/1.1";
            }
            if (StringUtils.isEmpty(wmtsVersion)) {
                wmtsVersion = "http://www.opengis.net/wmts/1.0";
            }
            NamespaceContext nscontext = CommonNamespaces.getNamespaceContext().
                addNamespace("ows", owsVersion).
                addNamespace("wmts", wmtsVersion);
            xpath.setNamespaceContext(nscontext);
            String expressionString = "//wmts:Contents/wmts:Layer[ows:Identifier='" +
                ((WmtsLayerDataSource) layer.getSource()).getWmtsLayer() + "']";
            XPathExpression expr = xpath.compile(expressionString);
            NodeList nodeList = (NodeList) expr.evaluate(externalDoc, XPathConstants.NODESET);
            Element contentNode = (Element) xpath.compile(
                "//wmts:Contents").evaluate(doc, XPathConstants.NODE);
            for (int i = 0; i < nodeList.getLength(); ++i) {
                // add the layer entry
                Element el = (Element) nodeList.item(i);
                NodeList resourceUrls = (NodeList) el.getElementsByTagName("ResourceURL");
                for (int j = 0; j < resourceUrls.getLength(); ++j) {
                    resourceUrls.item(j).getAttributes().getNamedItem("template")
                        .setNodeValue(source.getUrls().get(0));
                }

                Node importedNode = doc.importNode(el, true);
                contentNode.appendChild(importedNode);

                // add connected tilematrixsets
                NodeList tmsList = (NodeList) xpath.compile("//wmts:Contents/wmts:TileMatrixSet").
                    evaluate(el, XPathConstants.NODESET);
                for (int j = 0; j < tmsList.getLength(); ++j) {
                    Node set = tmsList.item(j);
                    Node importedTmsNode = doc.importNode(set, true);
                    contentNode.appendChild(importedTmsNode);
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException | HttpException | XPathExpressionException |
            URISyntaxException e) {
            LOG.warn("Something went wrong when intercepting an external get capabilities response: " + e.getMessage());
            LOG.trace("Full stack trace: ", e);
        }
    }
}

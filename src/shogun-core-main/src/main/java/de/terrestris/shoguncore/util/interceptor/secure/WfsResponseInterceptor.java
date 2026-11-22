package de.terrestris.shoguncore.util.interceptor.secure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.dao.UserDao;
import de.terrestris.shoguncore.model.User;
import de.terrestris.shoguncore.model.security.Permission;
import de.terrestris.shoguncore.service.UserService;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.ImageWmsLayerDataSource;
import de.terrestris.shoguncore.model.layer.source.WfsLayerDataSource;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import de.terrestris.shoguncore.util.interceptor.WfsResponseInterceptorInterface;
import de.terrestris.shoguncore.util.model.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.deegree.commons.xml.CommonNamespaces;
import org.deegree.commons.xml.NamespaceBindings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static javax.xml.xpath.XPathConstants.NODESET;
import static org.apache.logging.log4j.LogManager.getLogger;

public class WfsResponseInterceptor implements WfsResponseInterceptorInterface {

    private static final Logger LOG = getLogger(WfsResponseInterceptor.class);

    private static final String XSD = "http://www.w3.org/2001/XMLSchema";

    /**
     *
     */
    @Autowired
    @Qualifier("layerService")
    protected LayerService<Layer, LayerDao<Layer>> layerService;

    @Autowired
    @Qualifier("userService")
    protected UserService<User, UserDao<User>> userService;

    private void interceptGetCapabilities100(Document doc, String baseUrl) throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        // xpath seems not to work with the default bindings
        NodeList list = (NodeList) xpath.compile("//*[local-name()=\"Get\"]").evaluate(doc.getDocumentElement(), NODESET);
        for (int i = 0; i < list.getLength(); ++i) {
            Element node = (Element) list.item(i);
            node.setAttribute("onlineResource", baseUrl);
        }
        list = (NodeList) xpath.compile("//*[local-name()=\"Post\"]").evaluate(doc.getDocumentElement(), NODESET);
        for (int i = 0; i < list.getLength(); ++i) {
            Element node = (Element) list.item(i);
            node.setAttribute("onlineResource", baseUrl);
        }
    }

    private void interceptGetCapabilities110And200(Document doc, String baseUrl, String owsNamespace) throws XPathExpressionException {
        NamespaceBindings nscontext = new NamespaceBindings();
        nscontext.addNamespace("ows", owsNamespace);
        nscontext.addNamespace("xlink", "http://www.w3.org/1999/xlink");
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(nscontext);
        NodeList list = (NodeList) xpath.compile("//ows:Get").evaluate(doc.getDocumentElement(), NODESET);
        for (int i = 0; i < list.getLength(); ++i) {
            Element node = (Element) list.item(i);
            node.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", baseUrl);
        }
        list = (NodeList) xpath.compile("//ows:Post").evaluate(doc.getDocumentElement(), NODESET);
        for (int i = 0; i < list.getLength(); ++i) {
            Element node = (Element) list.item(i);
            node.setAttributeNS("http://www.w3.org/1999/xlink", "xlink:href", baseUrl);
        }
    }

    @Override
    public Response interceptGetCapabilities(MutableHttpServletRequest request, Response response) {
        LOG.debug("Intercepting WFS GetCapabilities response");
        String endpoint = request.getParameterIgnoreCase("CUSTOM_ENDPOINT");
        String baseUrl = ProxyUrls.baseUrl(request, endpoint);
        String namespace = LayerNames.endpointNamespace(request);
        return CapabilitiesFilter.filter(response, doc -> {
            String version = doc.getDocumentElement().getAttribute("version");
            switch (version) {
                case "1.0.0":
                    interceptGetCapabilities100(doc, baseUrl);
                    break;
                case "1.1.0":
                    interceptGetCapabilities110And200(doc, baseUrl, CommonNamespaces.OWS_NS);
                    break;
                case "2.0.0":
                    interceptGetCapabilities110And200(doc, baseUrl, CommonNamespaces.OWS_11_NS);
                    break;
                default:
                    throw new IllegalArgumentException("WFS version is not supported");
            }
            removeLayers(doc, namespace);
        });
    }

    private void removeLayers(Document doc, String namespace) throws XPathExpressionException {
        // get all layers allowed for this user in order to filter out not allowed ones
        List<Layer> layers = layerService.findAll();
        List<String> layerNames = new ArrayList<>();
        for (Layer layer : layers) {
            if (layer.getSource() instanceof ImageWmsLayerDataSource) {
                ImageWmsLayerDataSource source = (ImageWmsLayerDataSource) layer.getSource();
                layerNames.add(source.getLayerNames());
            } else if (layer.getSource() instanceof WfsLayerDataSource) {
                WfsLayerDataSource source = (WfsLayerDataSource) layer.getSource();
                layerNames.add(source.getTypeName());
                layerNames.add(source.getTypeNames());
            }
        }
        XPath xpath = XPathFactory.newInstance().newXPath();
        xpath.setNamespaceContext(CommonNamespaces.getNamespaceContext().
            addNamespace("wfs", "http://www.opengis.net/wfs"));
        NodeList list = (NodeList) xpath.compile("//wfs:FeatureType/wfs:Name").evaluate(doc.getDocumentElement(), NODESET);
        List<Element> toRemove = new ArrayList<>();
        determineFeatureTypeNodesToRemove(layerNames, list, toRemove, namespace);
        xpath.setNamespaceContext(CommonNamespaces.getNamespaceContext().
            addNamespace("wfs", "http://www.opengis.net/wfs/2.0"));
        list = (NodeList) xpath.compile("//wfs:FeatureType/wfs:Name").evaluate(doc.getDocumentElement(), NODESET);
        determineFeatureTypeNodesToRemove(layerNames, list, toRemove, namespace);
        toRemove.forEach(element -> element.getParentNode().removeChild(element));
    }

    private void determineFeatureTypeNodesToRemove(List<String> layerNames, NodeList list, List<Element> toRemove,
                                                   String namespace) {
        Set<String> allowed = new HashSet<>();
        for (String names : layerNames) {
            if (names != null) {
                for (String name : names.split(",")) {
                    if (StringUtils.isNotBlank(name)) {
                        allowed.add(LayerNames.key(name, namespace));
                    }
                }
            }
        }
        for (int i = 0; i < list.getLength(); ++i) {
            Element name = (Element) list.item(i);
            if (!allowed.contains(LayerNames.key(name.getTextContent(), namespace))) {
                toRemove.add((Element) name.getParentNode());
            }
        }
    }

    /**
     * Removes the feature types that the user may not read from the schema
     * (XML) or the feature type list (JSON). A request with type names was
     * checked by the request interceptor; a request without type names gets
     * all types of GeoServer.
     */
    @Override
    public Response interceptDescribeFeatureType(MutableHttpServletRequest request, Response response) {
        LOG.debug("Intercepting WFS DescribeFeatureType response");
        String namespace = LayerNames.endpointNamespace(request);
        Set<String> allowed = FeatureTypes.keys(layerService.findAll(), namespace);
        if (CapabilitiesFilter.isXml(response)) {
            return CapabilitiesFilter.filter(response, doc -> removeFeatureTypes(doc, allowed, namespace));
        }
        String contentType = response.getHeaders() == null ? null
            : response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
        if (StringUtils.containsIgnoreCase(contentType, "json") && response.getBody() != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode json = objectMapper.readTree(response.getBody());
                String prefix = json.path("targetPrefix").asText("");
                JsonNode types = json.get("featureTypes");
                if (types instanceof ArrayNode) {
                    ArrayNode featureTypes = (ArrayNode) types;
                    for (int i = featureTypes.size() - 1; i >= 0; i--) {
                        String typeName = featureTypes.get(i).path("typeName").asText("");
                        if (!allowed.contains(LayerNames.key(prefix.isEmpty() ? typeName : prefix + ":" + typeName,
                            namespace))) {
                            featureTypes.remove(i);
                        }
                    }
                }
                response.setBody(objectMapper.writeValueAsBytes(json));
            } catch (IOException e) {
                LOG.warn("Could not filter the DescribeFeatureType response: " + e.getMessage());
                throw new OgcException(OgcException.NO_APPLICABLE_CODE, null,
                    "Could not filter the DescribeFeatureType response.");
            }
        }
        return response;
    }

    /**
     * Removes the global feature elements (elements with a substitution
     * group, not abstract) that the user may not read, and the complex types that only
     * these elements use. Other types (e.g. GeoServer's
     * AbstractSimpleFeatureType of WFS 1.0) stay.
     */
    static void removeFeatureTypes(Document doc, Set<String> allowed, String namespace) {
        Element schema = doc.getDocumentElement();
        String targetNamespace = schema.getAttribute("targetNamespace");
        String prefix = StringUtils.defaultString(schema.lookupPrefix(targetNamespace));
        List<Element> elements = new ArrayList<>();
        Map<String, Element> complexTypes = new HashMap<>();
        for (Node child = schema.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element && XSD.equals(child.getNamespaceURI())) {
                Element element = (Element) child;
                if ("element".equals(element.getLocalName())) {
                    elements.add(element);
                } else if ("complexType".equals(element.getLocalName())) {
                    complexTypes.put(element.getAttribute("name"), element);
                }
            }
        }
        Set<String> keptTypes = new HashSet<>();
        Set<String> removedTypes = new HashSet<>();
        for (Element element : elements) {
            String type = localPart(element.getAttribute("type"));
            String name = element.getAttribute("name");
            // abstract elements (e.g. _SimpleFeature of WFS 1.0) are not feature types
            boolean feature = element.hasAttribute("substitutionGroup")
                && !"true".equals(element.getAttribute("abstract"));
            String key = LayerNames.key(prefix.isEmpty() ? name : prefix + ":" + name, namespace);
            if (feature && !allowed.contains(key)) {
                schema.removeChild(element);
                removedTypes.add(type);
            } else {
                keptTypes.add(type);
            }
        }
        removedTypes.removeAll(keptTypes);
        for (String type : removedTypes) {
            Element complexType = complexTypes.get(type);
            if (complexType != null) {
                schema.removeChild(complexType);
            }
        }
    }

    private static String localPart(String qualifiedName) {
        return StringUtils.substringAfterLast(":" + StringUtils.defaultString(qualifiedName), ":");
    }

    @Override
    public Response interceptGetFeature(MutableHttpServletRequest request, Response response) {
        LOG.debug("Intercepting WFS GetFeature response");
        // the request interceptor checked the named types; a request without
        // type names (stored query) is checked by the returned features
        if (TypeNames.fromRequest(request).isEmpty() && response.getBody() != null) {
            String namespace = LayerNames.endpointNamespace(request);
            FeatureTypes.checkResponse(response.getBody(), FeatureTypes.keys(layerService.findAll(), namespace),
                namespace);
        }
        return response;
    }

    @Override
    public Response interceptLockFeature(MutableHttpServletRequest request, Response response) {
        LOG.debug("Intercepting WFS LockFeature response");
        checkLockedFeatures(request, response);
        return response;
    }

    /**
     * GetFeatureWithLock: the returned features need UPDATE.
     */
    @Override
    public Response interceptGetFeatureWithLock(MutableHttpServletRequest request, Response response) {
        LOG.debug("Intercepting WFS GetFeatureWithLock response");
        checkLockedFeatures(request, response);
        return response;
    }

    /**
     * For a lock without type names (stored query), the locked features need
     * UPDATE. The request interceptor checked locks with type names. A lock
     * of other features stays until it expires; the client gets an exception.
     */
    private void checkLockedFeatures(MutableHttpServletRequest request, Response response) {
        if (!TypeNames.fromRequest(request).isEmpty() || response.getBody() == null) {
            return;
        }
        User user = userService.getUserBySession();
        List<Layer> updatable = new ArrayList<>();
        for (Layer layer : layerService.findAll()) {
            if (FeatureTypes.hasPermission(layer, user, Permission.UPDATE)) {
                updatable.add(layer);
            }
        }
        String namespace = LayerNames.endpointNamespace(request);
        FeatureTypes.checkResponse(response.getBody(), FeatureTypes.keys(updatable, namespace), namespace);
    }

    @Override
    public Response interceptTransaction(MutableHttpServletRequest request, Response response) {
        LOG.debug("Intercepting WFS Transaction response");
        return response;
    }

}

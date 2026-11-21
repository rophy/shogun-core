package de.terrestris.shoguncore.util.interceptor.secure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.terrestris.shoguncore.model.User;
import de.terrestris.shoguncore.model.UserGroup;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.ImageWmsLayerDataSource;
import de.terrestris.shoguncore.model.layer.source.WfsLayerDataSource;
import de.terrestris.shoguncore.model.security.Permission;
import de.terrestris.shoguncore.model.security.PermissionCollection;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import org.apache.commons.lang3.StringUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Feature type permissions of the WFS interceptors.
 */
final class FeatureTypes {

    /** Elements that contain features in WFS 1.x and 2.0 responses. */
    private static final Set<String> MEMBER_ELEMENTS = new HashSet<>(Arrays.asList(
        "member", "featureMember", "featureMembers"));

    /** Containers that are not features (WFS 2.0 joins and nested collections). */
    private static final Set<String> CONTAINERS = new HashSet<>(Arrays.asList(
        "Tuple", "FeatureCollection", "SimpleFeatureCollection", "additionalObjects", "LockFeatureResponse"));

    /** SOAP envelope elements around the response. */
    private static final Set<String> SOAP_WRAPPERS = new HashSet<>(Arrays.asList(
        "Envelope", "Header", "Body", "Fault"));

    private FeatureTypes() {
    }

    /**
     * @param layers The layers to use (e.g. all readable ones)
     * @param namespace The endpoint namespace
     * @return The keys of the type names of WFS layer sources and the layer
     * names of WMS layer sources
     */
    static Set<String> keys(List<Layer> layers, String namespace) {
        Set<String> allowed = new HashSet<>();
        for (Layer layer : layers) {
            List<String> names = new ArrayList<>();
            if (layer.getSource() instanceof WfsLayerDataSource) {
                WfsLayerDataSource source = (WfsLayerDataSource) layer.getSource();
                names.add(source.getTypeName());
                names.add(source.getTypeNames());
            } else if (layer.getSource() instanceof ImageWmsLayerDataSource) {
                names.add(((ImageWmsLayerDataSource) layer.getSource()).getLayerNames());
            }
            for (String value : names) {
                if (value != null) {
                    for (String name : value.split(",")) {
                        if (StringUtils.isNotBlank(name)) {
                            allowed.add(LayerNames.key(name, namespace));
                        }
                    }
                }
            }
        }
        return allowed;
    }

    /**
     * Checks the features of a GetFeature response, for requests that name no
     * type (stored queries such as GetFeatureById). The client gets an
     * exception if the response has a feature of a type without permission,
     * or a format that cannot be checked.
     *
     * @param body The response body
     * @param allowed The allowed keys (see {@link #keys})
     * @param namespace The endpoint namespace
     */
    static void checkResponse(byte[] body, Set<String> allowed, String namespace) {
        Set<String> types;
        try {
            types = CapabilitiesFilter.isXml(body) ? typesInXml(body) : typesInJson(body);
        } catch (XMLStreamException | IOException | RuntimeException e) {
            throw denied();
        }
        for (String type : types) {
            if (!allowed.contains(LayerNames.key(type, namespace))) {
                throw denied();
            }
        }
    }

    /**
     * @return true if the user (or one of the groups) has the permission (or
     * ADMIN) on the layer
     */
    static boolean hasPermission(Layer layer, User user, Permission permission) {
        if (user == null) {
            return false;
        }
        PermissionCollection userPermissions = layer.getUserPermissions() == null ? null
            : layer.getUserPermissions().get(user);
        if (allows(userPermissions, permission)) {
            return true;
        }
        Set<UserGroup> groups = user.getUserGroups();
        if (groups != null && layer.getGroupPermissions() != null) {
            for (UserGroup group : groups) {
                if (allows(layer.getGroupPermissions().get(group), permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean allows(PermissionCollection collection, Permission permission) {
        return collection != null && collection.getPermissions() != null
            && (collection.getPermissions().contains(permission)
            || collection.getPermissions().contains(Permission.ADMIN));
    }

    private static OgcException denied() {
        return new OgcException(OgcException.NO_APPLICABLE_CODE, null,
            "The response has features without read permission, or a format that cannot be checked.");
    }

    /**
     * @return The qualified names (prefix:localName) of the features. An
     * exception report has none.
     */
    private static Set<String> typesInXml(byte[] body) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(body));
        Set<String> types = new LinkedHashSet<>();
        Deque<String> path = new ArrayDeque<>();
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String local = reader.getLocalName();
                    String parent = path.peek();
                    if (SOAP_WRAPPERS.contains(local) && (path.isEmpty() || SOAP_WRAPPERS.contains(parent))) {
                        // SOAP: the content of the body is the response
                        if ("Fault".equals(local)) {
                            return types;
                        }
                        continue;
                    }
                    if ("ResourceId".equals(local) && "FeaturesLocked".equals(parent)) {
                        // LockFeature: rid = "TypeName.fid"
                        String rid = StringUtils.defaultString(reader.getAttributeValue(null, "rid"));
                        if (!rid.contains(".")) {
                            throw denied();
                        }
                        types.add(rid.substring(0, rid.lastIndexOf('.')));
                    }
                    if (path.isEmpty()) {
                        if (local.contains("Exception")) {
                            return types;
                        }
                        if ("ValueCollection".equals(local)) {
                            // property values cannot be attributed to a type
                            throw denied();
                        }
                        if (!CONTAINERS.contains(local)) {
                            // GetFeatureById: the root element is the feature
                            types.add(qualified(reader));
                        }
                    } else if (MEMBER_ELEMENTS.contains(parent) && !CONTAINERS.contains(local)) {
                        types.add(qualified(reader));
                    }
                    path.push(local);
                } else if (event == XMLStreamConstants.END_ELEMENT && !path.isEmpty()
                    && path.peek().equals(reader.getLocalName())) {
                    path.pop();
                }
            }
        } finally {
            reader.close();
        }
        return types;
    }

    private static String qualified(XMLStreamReader reader) {
        String prefix = reader.getPrefix();
        return StringUtils.isEmpty(prefix) ? reader.getLocalName() : prefix + ":" + reader.getLocalName();
    }

    /**
     * GeoJSON of GeoServer: the feature ids are "TypeName.fid".
     */
    private static Set<String> typesInJson(byte[] body) throws IOException {
        Set<String> types = new LinkedHashSet<>();
        JsonNode root = new ObjectMapper().readTree(body);
        JsonNode features = root.get("features");
        if (features == null) {
            if (root.has("id")) {
                features = new ObjectMapper().createArrayNode().add(root);
            } else {
                throw denied();
            }
        }
        for (JsonNode feature : features) {
            String id = feature.path("id").asText("");
            if (!id.contains(".")) {
                throw denied();
            }
            types.add(id.substring(0, id.lastIndexOf('.')));
        }
        return types;
    }
}

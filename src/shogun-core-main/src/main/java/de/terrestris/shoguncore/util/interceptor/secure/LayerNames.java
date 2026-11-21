package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.ImageWmsLayerDataSource;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Compares layer names of requests with layer names of layer entities.
 * Workspace bound services (/geoserver.action/{workspace}) use unqualified
 * names, so an unqualified name gets the workspace of the endpoint.
 */
final class LayerNames {

    private LayerNames() {
    }

    /**
     * @return The namespace of the URL path endpoint, or an empty string
     */
    static String endpointNamespace(MutableHttpServletRequest request) {
        String endpoint = request.getParameterIgnoreCase("CUSTOM_ENDPOINT");
        if (StringUtils.isEmpty(endpoint)) {
            return "";
        }
        return endpoint.contains(":") ? endpoint.split(":")[0] : endpoint;
    }

    /**
     * @param name The layer name, qualified ("ns:name") or not
     * @param namespace The endpoint namespace (may be empty)
     * @return A key that is equal for the same layer
     */
    static String key(String name, String namespace) {
        String trimmed = name.trim();
        if (!trimmed.contains(":") && StringUtils.isNotEmpty(namespace)) {
            trimmed = namespace + ":" + trimmed;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * @param coverageId A WCS coverage identifier. GeoServer uses
     * "workspace__name" in WCS 2.0, as ":" is not allowed there.
     * @param namespace The endpoint namespace (may be empty)
     * @return A key that is equal for the same layer
     */
    static String coverageKey(String coverageId, String namespace) {
        String name = coverageId.trim();
        int index = name.indexOf("__");
        if (!name.contains(":") && index > 0) {
            name = name.substring(0, index) + ":" + name.substring(index + 2);
        }
        return key(name, namespace);
    }

    /**
     * @param layers The layers that the user may read
     * @param url The interceptor URL (context path + /geoserver.action)
     * @param namespace The endpoint namespace
     * @return The keys of the WMS layer names (comma separated names are
     * split) of the layers with the interceptor URL
     */
    static Set<String> wmsLayerKeys(List<Layer> layers, String url, String namespace) {
        Set<String> allowed = new HashSet<>();
        for (Layer layer : layers) {
            if (layer.getSource() instanceof ImageWmsLayerDataSource) {
                ImageWmsLayerDataSource source = (ImageWmsLayerDataSource) layer.getSource();
                if (source.getLayerNames() != null && url.equalsIgnoreCase(source.getUrl())) {
                    for (String name : source.getLayerNames().split(",")) {
                        if (StringUtils.isNotBlank(name)) {
                            allowed.add(key(name, namespace));
                        }
                    }
                }
            }
        }
        return allowed;
    }
}

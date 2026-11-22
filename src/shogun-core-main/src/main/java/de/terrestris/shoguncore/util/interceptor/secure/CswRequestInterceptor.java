package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.dao.LayerDao;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.service.LayerService;
import de.terrestris.shoguncore.util.http.HttpUtil;
import de.terrestris.shoguncore.util.interceptor.CswRequestInterceptorInterface;
import de.terrestris.shoguncore.util.interceptor.GeoserverAuthHeaderRequest;
import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;
import de.terrestris.shoguncore.util.interceptor.OgcXmlUtil;
import de.terrestris.shoguncore.util.model.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpException;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import static org.apache.logging.log4j.LogManager.getLogger;

/**
 * Checks CSW 2.0.2 requests. A record is readable if the user may read its
 * layer (see {@link CswRecords}).
 * <ul>
 * <li>GetRecords: GeoServer is asked for all matching records (up to
 * geoserver.interceptor.cswMaxRecords), so that the response interceptor can
 * page the readable records and give correct counts.</li>
 * <li>GetRecordById: the response interceptor removes unreadable records.</li>
 * <li>GetRepositoryItem (id) and DirectDownload (resourceId) need READ on the
 * layer; a refused id gets the exception of an unknown one.</li>
 * <li>GetDomain of a record property (PropertyName) lists the values of all
 * records, so it needs READ on all records. GetDomain of an operation
 * parameter (ParameterName) is allowed.</li>
 * <li>Harvest and Transaction (catalogue changes) are refused.</li>
 * </ul>
 */
public class CswRequestInterceptor implements CswRequestInterceptorInterface {

    private static final Logger LOG = getLogger(CswRequestInterceptor.class);

    private static final int DEFAULT_MAX_RECORDS = 1000;

    @Autowired
    @Qualifier("layerService")
    protected LayerService<Layer, LayerDao<Layer>> layerService;

    @Value("${geoserver.username:}")
    private String gsUser;

    @Value("${geoserver.password:}")
    private String gsPass;

    /**
     * The most records that GeoServer returns for one GetRecords request.
     */
    @Value("${geoserver.interceptor.cswMaxRecords:1000}")
    private int maxRecords = DEFAULT_MAX_RECORDS;

    @Override
    public MutableHttpServletRequest interceptGetCapabilities(MutableHttpServletRequest request) {
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptDescribeRecord(MutableHttpServletRequest request) {
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptGetRecords(MutableHttpServletRequest request) {
        LOG.debug("Intercepting CSW GetRecords");
        if (isXmlPost(request)) {
            rewriteXmlPaging(request);
        } else {
            CswRecords.Paging paging = new CswRecords.Paging(
                CswRecords.number(request.getParameterIgnoreCase("startPosition"), 1, "startPosition"),
                CswRecords.number(request.getParameterIgnoreCase("maxRecords"), CswRecords.DEFAULT_MAX_RECORDS,
                    "maxRecords"),
                StringUtils.defaultIfBlank(request.getParameterIgnoreCase("resultType"), "hits"));
            request.setAttribute(CswRecords.PAGING_ATTRIBUTE, paging);
            if (!paging.isValidate()) {
                setParameter(request, "startPosition", "1");
                setParameter(request, "maxRecords", Integer.toString(maxRecords));
                setParameter(request, "resultType", "results");
            }
        }
        return credentials(request);
    }

    private void rewriteXmlPaging(MutableHttpServletRequest request) {
        try {
            Document doc = CapabilitiesFilter.parserFactory().newDocumentBuilder()
                .parse(new InputSource(new StringReader(OgcXmlUtil.getRequestBody(request))));
            Element root = doc.getDocumentElement();
            CswRecords.Paging paging = new CswRecords.Paging(
                CswRecords.number(root.getAttribute("startPosition"), 1, "startPosition"),
                CswRecords.number(root.getAttribute("maxRecords"), CswRecords.DEFAULT_MAX_RECORDS, "maxRecords"),
                StringUtils.defaultIfBlank(root.getAttribute("resultType"), "hits"));
            request.setAttribute(CswRecords.PAGING_ATTRIBUTE, paging);
            if (paging.isValidate()) {
                return;
            }
            root.setAttribute("startPosition", "1");
            root.setAttribute("maxRecords", Integer.toString(maxRecords));
            root.setAttribute("resultType", "results");
            StringWriter writer = new StringWriter();
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            request.setInputStream(writer.toString());
        } catch (ParserConfigurationException | SAXException | IOException | TransformerException e) {
            // GeoServer reports the invalid request; without paging the
            // response interceptor refuses the response
            LOG.debug("Invalid GetRecords request: " + e.getMessage());
        }
    }

    @Override
    public MutableHttpServletRequest interceptGetRecordById(MutableHttpServletRequest request) {
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptGetDomain(MutableHttpServletRequest request) {
        LOG.debug("Intercepting CSW GetDomain");
        boolean propertyDomain = StringUtils.isNotBlank(request.getParameterIgnoreCase("PropertyName"))
            || (isXmlPost(request) && StringUtils.contains(OgcXmlUtil.getRequestBody(request), "PropertyName"));
        if (propertyDomain && !mayReadAllRecords(request)) {
            throw new OgcException(OgcException.OPERATION_NOT_SUPPORTED, "PropertyName",
                "GetDomain of a record property needs read permission on all records.");
        }
        return credentials(request);
    }

    /**
     * @return true if the user may read every record of the catalogue
     */
    private boolean mayReadAllRecords(MutableHttpServletRequest request) {
        Response response;
        try {
            URI uri = new URIBuilder(request.getRequestURI())
                .addParameter("SERVICE", "CSW").addParameter("VERSION", "2.0.2")
                .addParameter("REQUEST", "GetRecords").addParameter("typeNames", "csw:Record")
                .addParameter("elementSetName", "brief").addParameter("resultType", "results")
                .addParameter("maxRecords", Integer.toString(maxRecords)).build();
            response = fetch(uri);
        } catch (URISyntaxException | HttpException e) {
            LOG.warn("Could not read the CSW records: " + e.getMessage());
            return false;
        }
        if (response.getBody() == null) {
            return false;
        }
        String namespace = LayerNames.endpointNamespace(request);
        Set<String> allowed = FeatureTypes.keys(layerService.findAll(), namespace);
        try {
            Document doc = CapabilitiesFilter.parserFactory().newDocumentBuilder()
                .parse(new ByteArrayInputStream(response.getBody()));
            int total = countRecords(doc.getDocumentElement());
            CswRecords.page(doc, allowed, namespace, new CswRecords.Paging(1, Integer.MAX_VALUE, "results"));
            return countRecords(doc.getDocumentElement()) == total;
        } catch (ParserConfigurationException | SAXException | IOException | OgcException e) {
            return false;
        }
    }

    /**
     * @param uri A GeoServer URL
     * @return The response of GeoServer (with the GeoServer credentials)
     */
    protected Response fetch(URI uri) throws URISyntaxException, HttpException {
        return HttpUtil.get(uri, gsUser, gsPass);
    }

    private static int countRecords(Element element) {
        int count = CswRecords.isRecord(element) ? 1 : 0;
        for (org.w3c.dom.Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element) {
                count += countRecords((Element) child);
            }
        }
        return count;
    }

    @Override
    public MutableHttpServletRequest interceptGetRepositoryItem(MutableHttpServletRequest request) {
        String id = request.getParameterIgnoreCase("id");
        if (StringUtils.isNotBlank(id) && !isReadable(request, id)) {
            // GeoServer: HTTP 404 for an unknown id
            throw new OgcException("NotFound", "id", "No repository item found for id " + id)
                .withHttpStatus(HttpStatus.NOT_FOUND);
        }
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptDirectDownload(MutableHttpServletRequest request) {
        String resourceId = request.getParameterIgnoreCase("resourceId");
        if (StringUtils.isNotBlank(resourceId) && !isReadable(request, resourceId)) {
            // the exception of GeoServer for a coverage without download
            throw new OgcException(OgcException.INVALID_PARAMETER_VALUE, "resourceId",
                "No direct download available for " + resourceId);
        }
        return credentials(request);
    }

    @Override
    public MutableHttpServletRequest interceptCatalogChange(MutableHttpServletRequest request) {
        throw new OgcException(OgcException.OPERATION_NOT_SUPPORTED, "request",
            "Changes of the catalogue (Harvest, Transaction) are not allowed.");
    }

    private boolean isReadable(MutableHttpServletRequest request, String layerName) {
        String namespace = LayerNames.endpointNamespace(request);
        return FeatureTypes.keys(layerService.findAll(), namespace).contains(LayerNames.key(layerName, namespace));
    }

    private MutableHttpServletRequest credentials(MutableHttpServletRequest request) {
        return new GeoserverAuthHeaderRequest(request, gsUser, gsPass);
    }

    private static boolean isXmlPost(MutableHttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String body = OgcXmlUtil.getRequestBody(request);
        return body != null && StringUtils.trimToEmpty(body).startsWith("<");
    }

    private static void setParameter(MutableHttpServletRequest request, String name, String value) {
        for (String key : request.getParameterMap().keySet().toArray(new String[0])) {
            if (key.equalsIgnoreCase(name)) {
                request.removeParameter(key);
            }
        }
        request.setParameter(name, new String[]{value});
    }
}

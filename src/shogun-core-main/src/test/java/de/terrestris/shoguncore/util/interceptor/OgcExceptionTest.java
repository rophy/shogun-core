package de.terrestris.shoguncore.util.interceptor;

import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OgcExceptionTest {

    private static String report(OgcException e) {
        return new String(e.toReport(), StandardCharsets.UTF_8);
    }

    @Test
    public void wms130_gets_a_service_exception_report_with_http_200() {
        OgcException e = new OgcException(OgcException.LAYER_NOT_DEFINED, "layers", "Unknown layer: <x>")
            .forRequest("WMS", "1.3.0");
        assertTrue(report(e).contains("<ServiceExceptionReport version=\"1.3.0\" xmlns=\"http://www.opengis.net/ogc\">"));
        assertTrue(report(e).contains("code=\"LayerNotDefined\" locator=\"layers\">Unknown layer: &lt;x&gt;<"));
        assertEquals(HttpStatus.OK, e.getHttpStatus());
        assertEquals(MediaType.TEXT_XML, e.getMediaType());
    }

    @Test
    public void wms111_uses_its_media_type() {
        OgcException e = new OgcException(OgcException.NO_APPLICABLE_CODE, null, "x").forRequest("WMS", "1.1.1");
        assertEquals("application/vnd.ogc.se_xml", e.getMediaType().toString());
        assertTrue(!report(e).contains("code="));
    }

    @Test
    public void ows_services_get_an_exception_report_and_an_http_error() {
        OgcException wfs = new OgcException(OgcException.INVALID_PARAMETER_VALUE, "typeNames", "x")
            .forRequest("WFS", "2.0.0");
        assertTrue(report(wfs).contains("xmlns:ows=\"http://www.opengis.net/ows/1.1\" version=\"2.0.0\""));
        assertEquals(HttpStatus.BAD_REQUEST, wfs.getHttpStatus());

        OgcException wcs = new OgcException("NoSuchCoverage", "coverageId", "x").forRequest("WCS", "2.0.1");
        assertTrue(report(wcs).contains("http://www.opengis.net/ows/2.0"));
        assertEquals(HttpStatus.NOT_FOUND, wcs.getHttpStatus());

        OgcException wmts = new OgcException(OgcException.OPERATION_NOT_SUPPORTED, "request", "x")
            .forRequest("WMTS", "1.0.0");
        assertTrue(report(wmts).contains("version=\"1.0.0\""));
        assertEquals(HttpStatus.NOT_IMPLEMENTED, wmts.getHttpStatus());

        OgcException other = new OgcException(OgcException.NO_APPLICABLE_CODE, null, "x").forRequest(null, null);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, other.getHttpStatus());
    }

    @Test
    public void wms_getmap_can_get_the_exception_as_image() {
        Map<String, String[]> params = new HashMap<>();
        params.put("REQUEST", new String[]{"GetMap"});
        params.put("exceptions", new String[]{"BLANK"});
        params.put("FORMAT", new String[]{"image/png"});
        params.put("WIDTH", new String[]{"8"});
        params.put("HEIGHT", new String[]{"5"});
        ResponseEntity<byte[]> response = new OgcException(OgcException.LAYER_NOT_DEFINED, "layers", "x")
            .forRequest("WMS", "1.3.0").withParameters(params).toResponseEntity();
        assertEquals(MediaType.IMAGE_PNG, response.getHeaders().getContentType());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}

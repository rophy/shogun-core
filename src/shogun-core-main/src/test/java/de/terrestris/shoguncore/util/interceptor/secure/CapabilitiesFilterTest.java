package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.model.Response;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CapabilitiesFilterTest {

    @Test
    public void the_doctype_of_wms_111_capabilities_is_kept_and_not_loaded() {
        // the DTD URL cannot be loaded: the filter must not try
        String caps = "<?xml version='1.0' encoding='UTF-8'?>"
            + "<!DOCTYPE WMT_MS_Capabilities SYSTEM 'http://127.0.0.1:1/schemas/wms/1.1.1/WMS_MS_Capabilities.dtd'>"
            + "<WMT_MS_Capabilities version='1.1.1'><Service><Name>OGC:WMS</Name></Service></WMT_MS_Capabilities>";
        Response response = new Response(HttpStatus.OK, new HttpHeaders(), caps.getBytes(StandardCharsets.UTF_8));
        String filtered = new String(CapabilitiesFilter.filter(response, doc -> { }).getBody(), StandardCharsets.UTF_8);
        assertTrue(filtered, filtered.contains("<!DOCTYPE WMT_MS_Capabilities SYSTEM "
            + "\"http://127.0.0.1:1/schemas/wms/1.1.1/WMS_MS_Capabilities.dtd\">"));
        assertTrue(filtered.contains("<Name>OGC:WMS</Name>"));
    }

    @Test
    public void external_entities_are_not_resolved() {
        String caps = "<?xml version='1.0'?><!DOCTYPE c [<!ENTITY x SYSTEM 'file:///etc/hostname'>]>"
            + "<c>&x;</c>";
        Response response = new Response(HttpStatus.OK, new HttpHeaders(), caps.getBytes(StandardCharsets.UTF_8));
        try {
            String filtered = new String(CapabilitiesFilter.filter(response, doc -> { }).getBody(),
                StandardCharsets.UTF_8);
            assertFalse(filtered.matches("(?s).*<c>[^<&]+</c>.*"));
        } catch (de.terrestris.shoguncore.util.interceptor.OgcException e) {
            // refused: also fine
        }
    }
}

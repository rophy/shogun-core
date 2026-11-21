package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CoverageIdsTest {

    @Test
    public void kvp_coverage_ids_are_split() {
        MockHttpServletRequest mock = new MockHttpServletRequest("GET", "/geoserver.action/topp");
        mock.addParameter("COVERAGEID", "topp__tazdem,topp__tazbm");
        MutableHttpServletRequest request = new MutableHttpServletRequest(mock);
        assertEquals(Arrays.asList("topp__tazdem", "topp__tazbm"), CoverageIds.fromRequest(request));
        assertEquals("coverageId", CoverageIds.locator(request));
        assertTrue(CoverageIds.isWcs2(request));
    }

    @Test
    public void xml_coverage_ids_are_read() {
        MockHttpServletRequest mock = new MockHttpServletRequest("POST", "/geoserver.action/topp");
        mock.setContentType("text/xml");
        mock.setContent(("<wcs:GetCoverage xmlns:wcs='http://www.opengis.net/wcs/2.0' service='WCS' version='2.0.1'>"
            + "<wcs:CoverageId>topp__tazdem</wcs:CoverageId></wcs:GetCoverage>").getBytes(StandardCharsets.UTF_8));
        MutableHttpServletRequest request = new MutableHttpServletRequest(mock);
        assertEquals(Collections.singletonList("topp__tazdem"), CoverageIds.fromRequest(request));
        assertTrue(CoverageIds.isWcs2(request));
    }

    @Test
    public void wcs1_kvp_is_not_wcs2() {
        MockHttpServletRequest mock = new MockHttpServletRequest("GET", "/geoserver.action/topp");
        mock.addParameter("identifiers", "tazdem");
        MutableHttpServletRequest request = new MutableHttpServletRequest(mock);
        assertEquals(Collections.singletonList("tazdem"), CoverageIds.fromRequest(request));
        assertFalse(CoverageIds.isWcs2(request));
    }
}

package de.terrestris.shoguncore.util.interceptor;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OgcEndpointsTest {

    @Test
    public void ows_is_the_global_endpoint() {
        assertTrue(OgcEndpoints.isGlobal("ows"));
        assertTrue(OgcEndpoints.isGlobal("OWS"));
        assertFalse(OgcEndpoints.isGlobal("cite"));
        assertFalse(OgcEndpoints.isGlobal(""));
        assertFalse(OgcEndpoints.isGlobal(null));
    }
}

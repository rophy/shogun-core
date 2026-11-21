package de.terrestris.shoguncore.util.interceptor.secure;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class LayerNamesTest {

    @Test
    public void unqualified_names_get_the_endpoint_namespace() {
        assertEquals(LayerNames.key("cite:Lakes", "cite"), LayerNames.key("Lakes", "cite"));
        assertEquals("cite:lakes", LayerNames.key(" Lakes ", "cite"));
    }

    @Test
    public void qualified_names_keep_their_namespace() {
        assertNotEquals(LayerNames.key("cite:Lakes", "cite"), LayerNames.key("topp:Lakes", "cite"));
        assertEquals("topp:lakes", LayerNames.key("topp:Lakes", ""));
    }

    @Test
    public void wcs2_coverage_ids_map_to_layer_names() {
        assertEquals("topp:tazdem", LayerNames.coverageKey("topp__tazdem", "topp"));
        assertEquals("topp:tazdem", LayerNames.coverageKey("tazdem", "topp"));
        assertEquals("topp:tazdem", LayerNames.coverageKey("topp:tazdem", ""));
    }
}

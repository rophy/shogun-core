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

    @Test
    public void wcs_eo_granules_and_dataset_series_map_to_their_coverage() {
        assertEquals("sf:timeranges", LayerNames.coverageKey("sf__timeranges_granule_timeranges.1", "sf"));
        assertEquals("sf:timeranges", LayerNames.datasetSeriesKey("sf__timeranges_dss", "sf"));
        assertEquals("sf:timeranges", LayerNames.datasetSeriesKey("timeranges_dss", "sf"));
    }
}

package de.terrestris.shoguncore.model.wps;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class WpsProcessTest {

    @Test
    public void exact_identifiers_match() {
        WpsProcess process = new WpsProcess("JTS:buffer");
        assertTrue(process.matches("JTS:buffer"));
        assertTrue(process.matches(" JTS:buffer "));
        assertFalse(process.matches("JTS:bufferX"));
        assertFalse(process.matches("jts:buffer"));
        assertFalse(process.matches(null));
        assertFalse(new WpsProcess(null).matches("JTS:buffer"));
    }

    @Test
    public void namespace_patterns_match_the_processes_of_the_namespace() {
        WpsProcess process = new WpsProcess("JTS:*");
        assertTrue(process.matches("JTS:buffer"));
        assertFalse(process.matches("JTS:"));
        assertFalse(process.matches("gs:Bounds"));
        assertFalse(process.matches("JTSX:buffer"));
    }

    @Test
    public void the_wildcard_matches_all_processes() {
        assertTrue(new WpsProcess("*").matches("gs:Bounds"));
        assertFalse(new WpsProcess("*x").matches("gs:Bounds"));
    }

    @Test
    public void equals_and_hash_code_use_the_identifier() {
        WpsProcess a = new WpsProcess("JTS:buffer");
        WpsProcess b = new WpsProcess("JTS:buffer");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        b.setDescription("other");
        assertNotEquals(a, b);
        assertTrue(a.toString().contains("JTS:buffer"));
    }
}

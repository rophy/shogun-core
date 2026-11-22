package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.OgcException;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CswRecordsTest {

    private static final String CSW = "http://www.opengis.net/cat/csw/2.0.2";

    /** The user may read cite:A, cite:C and cite:E. */
    private static final Set<String> ALLOWED = new HashSet<>(Arrays.asList("cite:a", "cite:c", "cite:e"));

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    /** All five records (A-E) of GeoServer, startPosition 1. */
    private static Document allRecords() throws Exception {
        StringBuilder xml = new StringBuilder("<csw:GetRecordsResponse xmlns:csw='" + CSW + "'"
            + " xmlns:dc='http://purl.org/dc/elements/1.1/'><csw:SearchResults numberOfRecordsMatched='5'"
            + " numberOfRecordsReturned='5' nextRecord='0' elementSet='brief'>");
        for (String id : new String[]{"A", "B", "C", "D", "E"}) {
            xml.append("<csw:BriefRecord><dc:identifier>cite:").append(id).append("</dc:identifier></csw:BriefRecord>");
        }
        return parse(xml.append("</csw:SearchResults></csw:GetRecordsResponse>").toString());
    }

    private static Element results(Document doc) {
        return (Element) doc.getElementsByTagNameNS(CSW, "SearchResults").item(0);
    }

    private static String ids(Document doc) {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < doc.getElementsByTagNameNS("*", "identifier").getLength(); i++) {
            ids.append(doc.getElementsByTagNameNS("*", "identifier").item(i).getTextContent().replace("cite:", ""));
        }
        return ids.toString();
    }

    @Test
    public void first_page_of_readable_records() throws Exception {
        Document doc = allRecords();
        CswRecords.page(doc, ALLOWED, "", new CswRecords.Paging(1, 2, "results"));
        assertEquals("AC", ids(doc));
        assertEquals("3", results(doc).getAttribute("numberOfRecordsMatched"));
        assertEquals("2", results(doc).getAttribute("numberOfRecordsReturned"));
        assertEquals("3", results(doc).getAttribute("nextRecord"));
    }

    @Test
    public void last_page_has_next_record_0() throws Exception {
        Document doc = allRecords();
        CswRecords.page(doc, ALLOWED, "", new CswRecords.Paging(3, 2, "results"));
        assertEquals("E", ids(doc));
        assertEquals("1", results(doc).getAttribute("numberOfRecordsReturned"));
        assertEquals("0", results(doc).getAttribute("nextRecord"));
    }

    @Test
    public void start_after_the_last_record_gives_no_records() throws Exception {
        Document doc = allRecords();
        CswRecords.page(doc, ALLOWED, "", new CswRecords.Paging(9, 10, "results"));
        assertEquals("", ids(doc));
        assertEquals("3", results(doc).getAttribute("numberOfRecordsMatched"));
        assertEquals("0", results(doc).getAttribute("numberOfRecordsReturned"));
    }

    @Test
    public void hits_give_the_count_only() throws Exception {
        Document doc = allRecords();
        CswRecords.page(doc, ALLOWED, "", new CswRecords.Paging(1, 10, "hits"));
        assertEquals("", ids(doc));
        assertEquals("3", results(doc).getAttribute("numberOfRecordsMatched"));
        assertEquals("0", results(doc).getAttribute("nextRecord"));
    }

    @Test
    public void unqualified_identifiers_use_the_endpoint_namespace() throws Exception {
        Document doc = parse("<r xmlns:csw='" + CSW + "' xmlns:dc='http://purl.org/dc/elements/1.1/'>"
            + "<csw:Record><dc:identifier>A</dc:identifier></csw:Record>"
            + "<csw:Record><dc:identifier>B</dc:identifier></csw:Record></r>");
        assertEquals(1, CswRecords.removeUnreadable(doc.getDocumentElement(), ALLOWED, "cite"));
        assertEquals("A", ids(doc));
    }

    @Test
    public void iso_records_use_the_file_identifier() throws Exception {
        Document doc = parse("<r xmlns:gmd='http://www.isotc211.org/2005/gmd' xmlns:gco='http://www.isotc211.org/2005/gco'>"
            + "<gmd:MD_Metadata><gmd:fileIdentifier><gco:CharacterString>cite:B</gco:CharacterString>"
            + "</gmd:fileIdentifier></gmd:MD_Metadata>"
            + "<gmd:MD_Metadata><gmd:fileIdentifier><gco:CharacterString>cite:C</gco:CharacterString>"
            + "</gmd:fileIdentifier></gmd:MD_Metadata></r>");
        assertEquals(1, CswRecords.removeUnreadable(doc.getDocumentElement(), ALLOWED, ""));
        assertTrue(doc.getDocumentElement().getTextContent().contains("cite:C"));
    }

    @Test
    public void truncated_results_are_refused() throws Exception {
        Document doc = allRecords();
        results(doc).setAttribute("numberOfRecordsMatched", "5000");
        try {
            CswRecords.page(doc, ALLOWED, "", new CswRecords.Paging(1, 10, "results"));
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(OgcException.NO_APPLICABLE_CODE, e.getCode());
        }
    }

    @Test
    public void numbers_of_the_paging() {
        assertEquals(1, CswRecords.number(null, 1, "startPosition"));
        assertEquals(7, CswRecords.number(" 7 ", 1, "startPosition"));
        for (String bad : new String[]{"-1", "x"}) {
            try {
                CswRecords.number(bad, 1, "maxRecords");
                fail("Not refused: " + bad);
            } catch (OgcException e) {
                assertEquals("maxRecords", e.getLocator());
            }
        }
    }

    @Test
    public void geoserver_urls_in_records_point_to_the_interceptor() throws Exception {
        Document doc = parse("<r xmlns:dc='http://purl.org/dc/elements/1.1/'>"
            + "<dc:references scheme='OGC:WMS'> http://gs:8080/geoserver/wms?service=WMS&amp;layers=cite:A </dc:references>"
            + "<a href='http://gs:8080/geoserver/ows?service=CSW'/><b>not a url</b><c href='http://gs:8080/geoserver/'/></r>");
        CswRecords.rewriteUrls(doc.getDocumentElement(), "http://gs:8080/geoserver/cite/ows",
            "http://gs:8080/geoserver", "https://app.example.com/geoserver.action/cite");
        assertEquals("https://app.example.com/geoserver.action/cite",
            ((Element) doc.getElementsByTagName("c").item(0)).getAttribute("href"));
        String text = doc.getDocumentElement().getTextContent();
        assertTrue(text, text.contains("https://app.example.com/geoserver.action/cite?service=WMS"));
        assertTrue(((Element) doc.getElementsByTagName("a").item(0)).getAttribute("href")
            .startsWith("https://app.example.com/geoserver.action/cite"));
    }
}

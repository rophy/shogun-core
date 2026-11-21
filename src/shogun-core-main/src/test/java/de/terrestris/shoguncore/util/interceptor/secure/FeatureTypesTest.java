package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.OgcException;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

public class FeatureTypesTest {

    private static final Set<String> ALLOWED = Collections.singleton("sf:primitivegeofeature");

    private static void check(String body) {
        FeatureTypes.checkResponse(body.getBytes(StandardCharsets.UTF_8), ALLOWED, "sf");
    }

    @Test
    public void allowed_features_pass() {
        check("<wfs:FeatureCollection xmlns:wfs='http://www.opengis.net/wfs/2.0' xmlns:sf='urn:sf'>"
            + "<wfs:member><sf:PrimitiveGeoFeature><sf:name>a</sf:name></sf:PrimitiveGeoFeature></wfs:member>"
            + "</wfs:FeatureCollection>");
        // GetFeatureById
        check("<sf:PrimitiveGeoFeature xmlns:sf='urn:sf'/>");
        check("{\"type\":\"FeatureCollection\",\"features\":[{\"id\":\"PrimitiveGeoFeature.f001\"}]}");
        // SOAP
        check("<soap:Envelope xmlns:soap='http://www.w3.org/2003/05/soap-envelope'><soap:Body>"
            + "<sf:PrimitiveGeoFeature xmlns:sf='urn:sf'/></soap:Body></soap:Envelope>");
        check("<soap:Envelope xmlns:soap='http://www.w3.org/2003/05/soap-envelope'><soap:Body><soap:Fault/>"
            + "</soap:Body></soap:Envelope>");
        // LockFeature
        check("<wfs:LockFeatureResponse xmlns:wfs='http://www.opengis.net/wfs/2.0'"
            + " xmlns:fes='http://www.opengis.net/fes/2.0'><wfs:FeaturesLocked>"
            + "<fes:ResourceId rid='PrimitiveGeoFeature.f001'/></wfs:FeaturesLocked></wfs:LockFeatureResponse>");
        // exception reports pass unchanged
        check("<ows:ExceptionReport xmlns:ows='http://www.opengis.net/ows/1.1'/>");
    }

    @Test(expected = OgcException.class)
    public void other_features_are_refused() {
        check("<wfs:FeatureCollection xmlns:wfs='http://www.opengis.net/wfs/2.0' xmlns:sf='urn:sf'>"
            + "<wfs:member><sf:AggregateGeoFeature/></wfs:member></wfs:FeatureCollection>");
    }

    @Test(expected = OgcException.class)
    public void other_features_in_joins_are_refused() {
        check("<wfs:FeatureCollection xmlns:wfs='http://www.opengis.net/wfs/2.0' xmlns:sf='urn:sf'>"
            + "<wfs:member><wfs:Tuple><wfs:member><sf:PrimitiveGeoFeature/></wfs:member>"
            + "<wfs:member><sf:AggregateGeoFeature/></wfs:member></wfs:Tuple></wfs:member></wfs:FeatureCollection>");
    }

    @Test(expected = OgcException.class)
    public void other_json_features_are_refused() {
        check("{\"features\":[{\"id\":\"AggregateGeoFeature.f005\"}]}");
    }

    @Test(expected = OgcException.class)
    public void other_locked_features_are_refused() {
        check("<wfs:LockFeatureResponse xmlns:wfs='http://www.opengis.net/wfs/2.0'"
            + " xmlns:fes='http://www.opengis.net/fes/2.0'><wfs:FeaturesLocked>"
            + "<fes:ResourceId rid='AggregateGeoFeature.f005'/></wfs:FeaturesLocked></wfs:LockFeatureResponse>");
    }

    @Test(expected = OgcException.class)
    public void other_features_in_soap_are_refused() {
        check("<soap:Envelope xmlns:soap='http://www.w3.org/2003/05/soap-envelope'><soap:Body>"
            + "<sf:AggregateGeoFeature xmlns:sf='urn:sf'/></soap:Body></soap:Envelope>");
    }

    @Test(expected = OgcException.class)
    public void unknown_formats_are_refused() {
        check("a,b,c\n1,2,3");
    }

    @Test(expected = OgcException.class)
    public void property_values_are_refused() {
        check("<wfs:ValueCollection xmlns:wfs='http://www.opengis.net/wfs/2.0'/>");
    }
}

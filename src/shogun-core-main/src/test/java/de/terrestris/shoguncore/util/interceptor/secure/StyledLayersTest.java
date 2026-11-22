package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.OgcException;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class StyledLayersTest {

    private static LinkedHashSet<String> set(String... names) {
        return new LinkedHashSet<>(Arrays.asList(names));
    }

    @Test
    public void named_layers_of_sld_10_without_namespace_prefix() {
        String sld = "<StyledLayerDescriptor xmlns='http://www.opengis.net/sld' version='1.0.0'>"
            + "<NamedLayer><Name>cite:Lakes</Name><NamedStyle><Name>lake</Name></NamedStyle></NamedLayer>"
            + "<NamedLayer><Name> Forests </Name></NamedLayer></StyledLayerDescriptor>";
        assertEquals(set("cite:Lakes", "Forests"), StyledLayers.names(sld, "SLD_BODY"));
    }

    @Test
    public void named_layers_of_sld_11_with_se_names() {
        String sld = "<sld:StyledLayerDescriptor xmlns:sld='http://www.opengis.net/sld'"
            + " xmlns:se='http://www.opengis.net/se' version='1.1.0'>"
            + "<sld:NamedLayer><se:Name>cite:Lakes</se:Name></sld:NamedLayer></sld:StyledLayerDescriptor>";
        assertEquals(set("cite:Lakes"), StyledLayers.names(sld, "SLD_BODY"));
    }

    @Test
    public void sld_without_namespace() {
        String sld = "<StyledLayerDescriptor><NamedLayer><Name>cite:Lakes</Name></NamedLayer>"
            + "</StyledLayerDescriptor>";
        assertEquals(set("cite:Lakes"), StyledLayers.names(sld, "SLD_BODY"));
    }

    @Test
    public void user_layers_and_feature_type_constraints() {
        String sld = "<StyledLayerDescriptor xmlns='http://www.opengis.net/sld'>"
            + "<UserLayer><Name>cite:Ponds</Name><RemoteOWS><Service>WFS</Service>"
            + "<OnlineResource/></RemoteOWS><LayerFeatureConstraints><FeatureTypeConstraint>"
            + "<FeatureTypeName>cite:Streams</FeatureTypeName></FeatureTypeConstraint>"
            + "</LayerFeatureConstraints></UserLayer>"
            + "<NamedLayer><Name>cite:Lakes</Name><LayerFeatureConstraints><FeatureTypeConstraint>"
            + "<FeatureTypeName>cite:Bridges</FeatureTypeName></FeatureTypeConstraint>"
            + "</LayerFeatureConstraints></NamedLayer></StyledLayerDescriptor>";
        assertEquals(set("cite:Ponds", "cite:Streams", "cite:Lakes", "cite:Bridges"),
            StyledLayers.names(sld, "SLD_BODY"));
    }

    @Test
    public void inline_user_layers_have_no_layer_names() {
        String sld = "<StyledLayerDescriptor xmlns='http://www.opengis.net/sld'>"
            + "<UserLayer><Name>Inline</Name><InlineFeature/><UserStyle><Name>s</Name></UserStyle></UserLayer>"
            + "</StyledLayerDescriptor>";
        assertEquals(set(), StyledLayers.names(sld, "SLD_BODY"));
    }

    @Test
    public void style_names_are_not_layer_names() {
        String sld = "<StyledLayerDescriptor xmlns='http://www.opengis.net/sld'>"
            + "<NamedLayer><Name>cite:Lakes</Name><UserStyle><Name>cite:Forests</Name>"
            + "<FeatureTypeStyle><Name>cite:Ponds</Name></FeatureTypeStyle></UserStyle></NamedLayer>"
            + "</StyledLayerDescriptor>";
        assertEquals(set("cite:Lakes"), StyledLayers.names(sld, "SLD_BODY"));
    }

    @Test
    public void external_entities_are_not_resolved() {
        String sld = "<?xml version='1.0'?><!DOCTYPE s [<!ENTITY x SYSTEM 'file:///etc/hostname'>]>"
            + "<StyledLayerDescriptor><NamedLayer><Name>a&x;</Name></NamedLayer></StyledLayerDescriptor>";
        try {
            assertEquals(set("a"), StyledLayers.names(sld, "SLD_BODY"));
        } catch (OgcException e) {
            assertEquals(OgcException.INVALID_PARAMETER_VALUE, e.getCode());
        }
    }

    @Test
    public void invalid_documents_are_refused() {
        try {
            StyledLayers.names("not xml", "SLD_BODY");
            fail("Not refused");
        } catch (OgcException e) {
            assertEquals(OgcException.INVALID_PARAMETER_VALUE, e.getCode());
            assertEquals("SLD_BODY", e.getLocator());
        }
    }
}

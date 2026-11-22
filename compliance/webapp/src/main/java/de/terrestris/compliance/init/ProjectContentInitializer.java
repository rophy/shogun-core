package de.terrestris.compliance.init;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import de.terrestris.shoguncore.init.ContentInitializer;
import de.terrestris.shoguncore.model.PersistentObject;
import de.terrestris.shoguncore.model.User;
import de.terrestris.shoguncore.model.layer.Layer;
import de.terrestris.shoguncore.model.layer.source.ImageWmsLayerDataSource;
import de.terrestris.shoguncore.model.layer.source.WmtsLayerDataSource;
import de.terrestris.shoguncore.model.security.Permission;
import de.terrestris.shoguncore.model.security.PermissionCollection;
import de.terrestris.shoguncore.model.wps.WpsProcess;
import de.terrestris.shoguncore.service.InitializationService;

/**
 * Creates one layer entity per OGC CITE test layer, with READ permission for
 * the test user. The secure interceptors refuse requests for layers that have
 * no such entity.
 */
public class ProjectContentInitializer extends ContentInitializer {

    /** URL that the secure interceptors compare with (context path is "/"). */
    private static final String INTERCEPTOR_URL = "/geoserver.action";

    @Autowired
    @Qualifier("projectInitEnabled")
    private Boolean projectInitEnabled;

    @Autowired
    @Qualifier("defaultUser")
    private User testUser;

    @Autowired
    @Qualifier("limitedUser")
    private User limitedUser;

    @Value("${compliance.limitedLayers:}")
    private String[] limitedLayerNames;

    @Value("${compliance.wpsProcesses:}")
    private String[] wpsProcesses;

    @Value("${compliance.limitedWpsProcesses:}")
    private String[] limitedWpsProcesses;

    @Autowired
    @Qualifier("initializationService")
    private InitializationService initializationService;

    @Value("${compliance.layers:}")
    private String[] layerNames;

    @Override
    public void initializeDatabaseContent() {
        super.initializeDatabaseContent();
        if (projectInitEnabled) {
            logger.info("Creating " + layerNames.length + " compliance test layers");
            for (String layerName : layerNames) {
                String name = layerName.trim();
                if (!name.isEmpty()) {
                    createWmsLayer(name);
                    createWmtsLayer(name);
                }
            }
            createWpsProcesses();
        }
    }

    /** Used by the WMS and WFS secure interceptors. */
    private void createWmsLayer(String name) {
        ImageWmsLayerDataSource source = new ImageWmsLayerDataSource();
        source.setName(name);
        source.setType("ImageWMS");
        source.setUrl(INTERCEPTOR_URL);
        source.setFormat("image/png");
        source.setLayerNames(name);
        source.setLayerStyles("");
        save(source);
        save(readableLayer("WMS " + name, source));
    }

    /** Used by the WMTS secure interceptors. */
    private void createWmtsLayer(String name) {
        WmtsLayerDataSource source = new WmtsLayerDataSource();
        source.setName(name);
        source.setType("WMTS");
        source.setUrl(INTERCEPTOR_URL);
        source.setFormat("image/png");
        source.setWmtsLayer(name);
        source.setUrls(Collections.singletonList(INTERCEPTOR_URL));
        save(source);
        save(readableLayer("WMTS " + name, source));
    }

    private Layer readableLayer(String name, de.terrestris.shoguncore.model.layer.source.LayerDataSource source) {
        Layer layer = new Layer();
        layer.setName(name);
        layer.setSource(source);

        PermissionCollection read = new PermissionCollection();
        // UPDATE for the WFS-T tests (Transaction, LockFeature)
        read.setPermissions(new HashSet<>(Arrays.asList(Permission.READ, Permission.UPDATE)));
        save(read);

        Map<User, PermissionCollection> permissions = new HashMap<>();
        permissions.put(testUser, read);
        if (isLimitedLayer(source.getName())) {
            PermissionCollection limitedRead = new PermissionCollection();
            limitedRead.setPermissions(new HashSet<>(Arrays.asList(Permission.READ)));
            save(limitedRead);
            permissions.put(limitedUser, limitedRead);
        }
        layer.setUserPermissions(permissions);
        return layer;
    }

    /**
     * WPS processes with READ for the test user (compliance.wpsProcesses) and
     * for the limited user (compliance.limitedWpsProcesses).
     */
    private void createWpsProcesses() {
        Map<String, WpsProcess> processes = new HashMap<>();
        for (String identifier : wpsProcesses) {
            readableProcess(processes, identifier.trim(), testUser);
        }
        for (String identifier : limitedWpsProcesses) {
            readableProcess(processes, identifier.trim(), limitedUser);
        }
        for (WpsProcess process : processes.values()) {
            save(process);
        }
    }

    private void readableProcess(Map<String, WpsProcess> processes, String identifier, User user) {
        if (identifier.isEmpty()) {
            return;
        }
        WpsProcess process = processes.computeIfAbsent(identifier, WpsProcess::new);
        PermissionCollection read = new PermissionCollection();
        read.setPermissions(new HashSet<>(Arrays.asList(Permission.READ)));
        save(read);
        Map<User, PermissionCollection> permissions = new HashMap<>(process.getUserPermissions());
        permissions.put(user, read);
        process.setUserPermissions(permissions);
    }

    private boolean isLimitedLayer(String name) {
        for (String limited : limitedLayerNames) {
            if (limited.trim().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void save(PersistentObject object) {
        initializationService.savePersistentObject(object);
    }
}

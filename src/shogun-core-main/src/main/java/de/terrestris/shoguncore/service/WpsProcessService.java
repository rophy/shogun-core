package de.terrestris.shoguncore.service;

import de.terrestris.shoguncore.dao.WpsProcessDao;
import de.terrestris.shoguncore.model.wps.WpsProcess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service of the {@link WpsProcess} permission entities. {@link #findAll()}
 * returns the entities that the user may READ, so the GeoServer interceptor
 * uses it to find the processes of a user (see
 * {@link #isAllowed(List, String)}).
 *
 * @author terrestris GmbH &amp; Co. KG
 */
@Service("wpsProcessService")
public class WpsProcessService<E extends WpsProcess, D extends WpsProcessDao<E>>
    extends PermissionAwareCrudService<E, D> {

    /**
     * Default constructor, which calls the type-constructor
     */
    @SuppressWarnings("unchecked")
    public WpsProcessService() {
        this((Class<E>) WpsProcess.class);
    }

    /**
     * Constructor that sets the concrete entity class for the service.
     * Subclasses MUST call this constructor.
     *
     * @param entityClass The entity class
     */
    protected WpsProcessService(Class<E> entityClass) {
        super(entityClass);
    }

    /**
     * Call {@link #findAll()} through the Spring bean (not from this class),
     * so that it returns only the entities the user may READ.
     *
     * @param readable The entities that the user may READ
     * @param processIdentifier A process identifier
     * @return true if one entity matches
     */
    public static boolean isAllowed(List<? extends WpsProcess> readable, String processIdentifier) {
        for (WpsProcess process : readable) {
            if (process.matches(processIdentifier)) {
                return true;
            }
        }
        return false;
    }

    /**
     * We have to use {@link Qualifier} to define the correct dao here.
     * Otherwise, spring can not decide which dao has to be autowired here
     * as there are multiple candidates.
     */
    @Override
    @Autowired
    @Qualifier("wpsProcessDao")
    public void setDao(D dao) {
        this.dao = dao;
    }
}

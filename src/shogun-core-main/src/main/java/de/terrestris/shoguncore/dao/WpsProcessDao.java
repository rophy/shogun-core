package de.terrestris.shoguncore.dao;

import de.terrestris.shoguncore.model.wps.WpsProcess;
import org.springframework.stereotype.Repository;

/**
 * DAO of the {@link WpsProcess} permission entities.
 *
 * @author terrestris GmbH &amp; Co. KG
 */
@Repository("wpsProcessDao")
public class WpsProcessDao<E extends WpsProcess> extends GenericHibernateDao<E, Integer> {

    /**
     * Public default constructor for this DAO.
     */
    @SuppressWarnings("unchecked")
    public WpsProcessDao() {
        super((Class<E>) WpsProcess.class);
    }

    /**
     * Constructor that has to be called by subclasses.
     *
     * @param clazz The entity class
     */
    protected WpsProcessDao(Class<E> clazz) {
        super(clazz);
    }
}

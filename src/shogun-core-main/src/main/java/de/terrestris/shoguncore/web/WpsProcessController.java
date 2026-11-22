package de.terrestris.shoguncore.web;

import de.terrestris.shoguncore.dao.WpsProcessDao;
import de.terrestris.shoguncore.model.wps.WpsProcess;
import de.terrestris.shoguncore.service.WpsProcessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Web controller of the {@link WpsProcess} permission entities.
 *
 * @author terrestris GmbH &amp; Co. KG
 */
@Controller
@RequestMapping("/wpsprocesses")
public class WpsProcessController<E extends WpsProcess, D extends WpsProcessDao<E>,
    S extends WpsProcessService<E, D>> extends AbstractWebController<E, D, S> {

    /**
     * Default constructor, which calls the type-constructor
     */
    @SuppressWarnings("unchecked")
    public WpsProcessController() {
        this((Class<E>) WpsProcess.class);
    }

    /**
     * Constructor that sets the concrete entity class for the controller.
     * Subclasses MUST call this constructor.
     *
     * @param entityClass The entity class
     */
    protected WpsProcessController(Class<E> entityClass) {
        super(entityClass);
    }

    /**
     * We have to use {@link Qualifier} to define the correct service here.
     * Otherwise, spring can not decide which service has to be autowired here
     * as there are multiple candidates.
     */
    @Override
    @Autowired
    @Qualifier("wpsProcessService")
    public void setService(S service) {
        this.service = service;
    }
}

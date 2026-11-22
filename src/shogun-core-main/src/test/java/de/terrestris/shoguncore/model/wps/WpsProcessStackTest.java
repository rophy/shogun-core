package de.terrestris.shoguncore.model.wps;

import de.terrestris.shoguncore.dao.WpsProcessDao;
import de.terrestris.shoguncore.rest.WpsProcessRestController;
import de.terrestris.shoguncore.service.WpsProcessService;
import de.terrestris.shoguncore.web.WpsProcessController;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The DAO, service and controllers of {@link WpsProcess}.
 */
public class WpsProcessStackTest {

    @Test
    public void the_stack_uses_the_wps_process_entity() {
        WpsProcessDao<WpsProcess> dao = new WpsProcessDao<>();
        assertEquals(WpsProcess.class, dao.getEntityClass());

        WpsProcessService<WpsProcess, WpsProcessDao<WpsProcess>> service = new WpsProcessService<>();
        assertEquals(WpsProcess.class, service.getEntityClass());
        service.setDao(dao);
        assertSame(dao, ReflectionTestUtils.getField(service, "dao"));

        WpsProcessRestController<WpsProcess, WpsProcessDao<WpsProcess>,
            WpsProcessService<WpsProcess, WpsProcessDao<WpsProcess>>> rest = new WpsProcessRestController<>();
        rest.setService(service);
        assertSame(service, ReflectionTestUtils.getField(rest, "service"));

        WpsProcessController<WpsProcess, WpsProcessDao<WpsProcess>,
            WpsProcessService<WpsProcess, WpsProcessDao<WpsProcess>>> web = new WpsProcessController<>();
        assertEquals(WpsProcess.class, web.getEntityClass());
        web.setService(service);
        assertSame(service, ReflectionTestUtils.getField(web, "service"));
    }

    @Test
    public void a_process_is_allowed_if_one_readable_entity_matches() {
        List<WpsProcess> readable = Arrays.asList(new WpsProcess("gs:Bounds"), new WpsProcess("JTS:*"));
        assertTrue(WpsProcessService.isAllowed(readable, "gs:Bounds"));
        assertTrue(WpsProcessService.isAllowed(readable, "JTS:buffer"));
        assertFalse(WpsProcessService.isAllowed(readable, "gs:Import"));
        assertFalse(WpsProcessService.isAllowed(Collections.emptyList(), "gs:Bounds"));
    }
}

package de.terrestris.shoguncore.model;

import org.hibernate.SessionFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * USERPERMISSIONS and GROUPPERMISSIONS hold the permissions of all entity
 * types, so their ENTITY_ID column must not reference the table of one type.
 * Without the NO_CONSTRAINT mapping, Hibernate creates such a key for one
 * entity type (which one depends on the mapping order); permissions of all
 * other types then fail.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath*:META-INF/spring/test-context-dao.xml"})
@Transactional
public class PermissionTablesTest {

    @Autowired
    private SessionFactory sessionFactory;

    @Test
    @SuppressWarnings("unchecked")
    public void entity_id_has_no_foreign_key() {
        List<Object> keys = sessionFactory.getCurrentSession().createNativeQuery(
            "select FK_NAME from INFORMATION_SCHEMA.CROSS_REFERENCES"
                + " where FKTABLE_NAME in ('USERPERMISSIONS', 'GROUPPERMISSIONS') and FKCOLUMN_NAME = 'ENTITY_ID'")
            .list();
        assertEquals(0, keys.size());
    }
}

package de.terrestris.shoguncore.model.wps;

import de.terrestris.shoguncore.model.PersistentObject;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Cacheable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * A process of the GeoServer WPS that users may run. The GeoServer
 * interceptor lets a user see (GetCapabilities, DescribeProcess) and run
 * (Execute) a process only with READ on a WpsProcess of its identifier.
 * <p>
 * The identifier is the process name of GeoServer (e.g. "JTS:buffer"), or a
 * pattern: "JTS:*" for all processes of a namespace, "*" for all processes.
 * <p>
 * This is a permission entity. {@link WpsProcessExecute} is different: it
 * describes one call of a process (inputs and defaults) for a client plugin,
 * can be the input of another call, and one process can have many.
 *
 * @author terrestris GmbH &amp; Co. KG
 */
@Entity
@Table
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class WpsProcess extends PersistentObject {

    /**
     * Matches all processes, or (after "namespace:") all processes of a
     * namespace.
     */
    public static final String WILDCARD = "*";

    private static final long serialVersionUID = 1L;

    private static final int HASH_INITIAL = 37;

    private static final int HASH_MULTIPLIER = 71;

    /**
     * The process identifier, or a pattern (see the class description).
     */
    @Column(nullable = false)
    private String identifier;

    /**
     * An optional description for administrators.
     */
    private String description;

    /**
     * Default constructor.
     */
    public WpsProcess() {
        super();
    }

    /**
     * @param identifier The process identifier or pattern
     */
    public WpsProcess(String identifier) {
        super();
        this.identifier = identifier;
    }

    /**
     * @param processIdentifier A process identifier of a request, e.g.
     *                          "JTS:buffer"
     * @return true if this entity applies to the process
     */
    public boolean matches(String processIdentifier) {
        if (identifier == null || processIdentifier == null) {
            return false;
        }
        String pattern = identifier.trim();
        String name = processIdentifier.trim();
        if (WILDCARD.equals(pattern)) {
            return true;
        }
        if (pattern.endsWith(":" + WILDCARD)) {
            String prefix = pattern.substring(0, pattern.length() - WILDCARD.length());
            return name.startsWith(prefix) && name.length() > prefix.length();
        }
        return pattern.equals(name);
    }

    /**
     * @return the identifier
     */
    public String getIdentifier() {
        return identifier;
    }

    /**
     * @param identifier the identifier to set
     */
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder(HASH_INITIAL, HASH_MULTIPLIER)
            .appendSuper(super.hashCode())
            .append(getIdentifier())
            .append(getDescription())
            .toHashCode();
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof WpsProcess)) {
            return false;
        }
        WpsProcess other = (WpsProcess) obj;
        return new EqualsBuilder()
            .appendSuper(super.equals(other))
            .append(getIdentifier(), other.getIdentifier())
            .append(getDescription(), other.getDescription())
            .isEquals();
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.DEFAULT_STYLE)
            .appendSuper(super.toString())
            .append("identifier", getIdentifier())
            .append("description", getDescription())
            .toString();
    }
}

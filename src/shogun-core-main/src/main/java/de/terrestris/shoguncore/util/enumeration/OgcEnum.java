package de.terrestris.shoguncore.util.enumeration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.*;

/**
 * @author Daniel Koch
 * @author terrestris GmbH & Co. KG
 */
public class OgcEnum {

    /**
     * A map that contains a set of {@link OperationType}s for any possible
     * {@link ServiceType}. See also the opposite collection
     * SERVICETYPES_BY_OPERATION.
     */
    public static final Map<ServiceType, Set<OperationType>> OPERATIONS_BY_SERVICETYPE;
    /**
     * A map that contains a set of {@link ServiceType}s for any possible
     * {@link OperationType}. See also the opposite collection
     * OPERATIONS_BY_SERVICETYPE.
     */
    public static final Map<OperationType, Set<ServiceType>> SERVICETYPES_BY_OPERATION;

    static {
        Map<ServiceType, Set<OperationType>> map = new HashMap<ServiceType, Set<OperationType>>();

        Set<OperationType> wmsOps = new HashSet<OperationType>();
        wmsOps.add(OperationType.GET_CAPABILITIES);
        wmsOps.add(OperationType.GET_MAP);
        wmsOps.add(OperationType.GET_FEATURE_INFO);
        wmsOps.add(OperationType.DESCRIBE_LAYER);
        wmsOps.add(OperationType.GET_LEGEND_GRAPHIC);
        wmsOps.add(OperationType.GET_STYLES);

        Set<OperationType> wmtsOps = new HashSet<OperationType>();
        wmtsOps.add(OperationType.GET_CAPABILITIES);
        wmtsOps.add(OperationType.GET_TILE);
        wmtsOps.add(OperationType.GET_FEATURE_INFO);
        // GeoServer extension wmts-multi-dimensional
        wmtsOps.add(OperationType.DESCRIBE_DOMAINS);
        wmtsOps.add(OperationType.GET_DOMAIN_VALUES);
        wmtsOps.add(OperationType.GET_HISTOGRAM);
        wmtsOps.add(OperationType.GET_FEATURE);

        Set<OperationType> wfsOps = new HashSet<OperationType>();
        wfsOps.add(OperationType.GET_CAPABILITIES);
        wfsOps.add(OperationType.DESCRIBE_FEATURE_TYPE);
        wfsOps.add(OperationType.GET_FEATURE);
        wfsOps.add(OperationType.LOCK_FEATURE);
        wfsOps.add(OperationType.TRANSACTION);
        // WFS 2.0
        wfsOps.add(OperationType.GET_PROPERTY_VALUE);
        wfsOps.add(OperationType.GET_FEATURE_WITH_LOCK);
        wfsOps.add(OperationType.LIST_STORED_QUERIES);
        wfsOps.add(OperationType.DESCRIBE_STORED_QUERIES);
        wfsOps.add(OperationType.CREATE_STORED_QUERY);
        wfsOps.add(OperationType.DROP_STORED_QUERY);

        Set<OperationType> wcsOps = new HashSet<OperationType>();
        wcsOps.add(OperationType.GET_CAPABILITIES);
        wcsOps.add(OperationType.DESCRIBE_COVERAGE);
        wcsOps.add(OperationType.GET_COVERAGE);
        // WCS 2.0 Earth Observation Application Profile (GeoServer extension wcs2_0-eo)
        wcsOps.add(OperationType.DESCRIBE_EO_COVERAGE_SET);

        Set<OperationType> wpsOps = new HashSet<OperationType>();
        wpsOps.add(OperationType.GET_CAPABILITIES);
        wpsOps.add(OperationType.EXECUTE);
        wpsOps.add(OperationType.DESCRIBE_PROCESS);
        // GeoServer: status and result of asynchronous executions
        wpsOps.add(OperationType.GET_EXECUTION_STATUS);
        wpsOps.add(OperationType.GET_EXECUTION_RESULT);
        wpsOps.add(OperationType.DISMISS);

        // CSW 2.0.2 (GeoServer extension csw); GeoServer adds GetRepositoryItem and DirectDownload
        Set<OperationType> cswOps = new HashSet<OperationType>();
        cswOps.add(OperationType.GET_CAPABILITIES);
        cswOps.add(OperationType.DESCRIBE_RECORD);
        cswOps.add(OperationType.GET_RECORDS);
        cswOps.add(OperationType.GET_RECORD_BY_ID);
        cswOps.add(OperationType.GET_DOMAIN);
        cswOps.add(OperationType.GET_REPOSITORY_ITEM);
        cswOps.add(OperationType.DIRECT_DOWNLOAD);
        cswOps.add(OperationType.HARVEST);
        cswOps.add(OperationType.TRANSACTION);

        map.put(ServiceType.WMS, Collections.unmodifiableSet(wmsOps));
        map.put(ServiceType.WMTS, Collections.unmodifiableSet(wmtsOps));
        map.put(ServiceType.WFS, Collections.unmodifiableSet(wfsOps));
        map.put(ServiceType.WCS, Collections.unmodifiableSet(wcsOps));
        map.put(ServiceType.WPS, Collections.unmodifiableSet(wpsOps));
        map.put(ServiceType.CSW, Collections.unmodifiableSet(cswOps));

        // store it in the lookup
        OPERATIONS_BY_SERVICETYPE = Collections.unmodifiableMap(map);
    }

    static {
        Map<OperationType, Set<ServiceType>> map = new HashMap<OperationType, Set<ServiceType>>();

        // A set containing only the WMS ServiceType
        Set<ServiceType> wmsSet = new HashSet<ServiceType>();
        wmsSet.add(ServiceType.WMS);
        wmsSet = Collections.unmodifiableSet(wmsSet);

        // A set containing only the WMTS ServiceType
        Set<ServiceType> wmtsSet = new HashSet<ServiceType>();
        wmtsSet.add(ServiceType.WMTS);
        wmtsSet = Collections.unmodifiableSet(wmtsSet);

        // A set containing only the WFS ServiceType
        Set<ServiceType> wfsSet = new HashSet<ServiceType>();
        wfsSet.add(ServiceType.WFS);
        wfsSet = Collections.unmodifiableSet(wfsSet);

        // A set containing only the WCS ServiceType
        Set<ServiceType> wcsSet = new HashSet<ServiceType>();
        wcsSet.add(ServiceType.WCS);
        wcsSet = Collections.unmodifiableSet(wcsSet);

        // A set containing only the WPS ServiceType
        Set<ServiceType> wpsSet = new HashSet<ServiceType>();
        wpsSet.add(ServiceType.WPS);
        wpsSet = Collections.unmodifiableSet(wpsSet);

        // A set containing the WMS, WMTS, WFS, WCS and WPS ServiceTypes
        Set<ServiceType> getCapSet = new HashSet<ServiceType>();
        getCapSet.add(ServiceType.WMS);
        getCapSet.add(ServiceType.WMTS);
        getCapSet.add(ServiceType.WFS);
        getCapSet.add(ServiceType.WCS);
        getCapSet.add(ServiceType.WPS);
        getCapSet.add(ServiceType.CSW);
        getCapSet = Collections.unmodifiableSet(getCapSet);

        // A set containing only the CSW ServiceType
        Set<ServiceType> cswSet = Collections.singleton(ServiceType.CSW);

        // look up all WMS operations from the previously created map
        Set<OperationType> wmsOperations = OPERATIONS_BY_SERVICETYPE.get(ServiceType.WMS);
        // look up all WMTS operations from the previously created map
        Set<OperationType> wmtsOperations = OPERATIONS_BY_SERVICETYPE.get(ServiceType.WMTS);
        // look up all WFS operations from the previously created map
        Set<OperationType> wfsOperations = OPERATIONS_BY_SERVICETYPE.get(ServiceType.WFS);
        // look up all WCS operations from the previously created map
        Set<OperationType> wcsOperations = OPERATIONS_BY_SERVICETYPE.get(ServiceType.WCS);
        // look up all WPS operations from the previously created map
        Set<OperationType> wpsOperations = OPERATIONS_BY_SERVICETYPE.get(ServiceType.WPS);

        // put all ServiceTypes for the GetCapability operation
        map.put(OperationType.GET_CAPABILITIES, getCapSet);
        // CSW first: operations that WFS has, too (Transaction), stay WFS
        // operations below, as in GeoServer without a SERVICE parameter
        for (OperationType cswOperation : OPERATIONS_BY_SERVICETYPE.get(ServiceType.CSW)) {
            if (!OperationType.GET_CAPABILITIES.equals(cswOperation)) {
                map.put(cswOperation, cswSet);
            }
        }
        // for WMS operations, put the WMS set, unless it's the GetCapability op
        for (OperationType wmsOperation : wmsOperations) {
            if (!OperationType.GET_CAPABILITIES.equals(wmsOperation)) {
                map.put(wmsOperation, wmsSet);
            }
        }
        // for WMTS operations, put the WMTS set, unless it's the GetCapability op
        for (OperationType wmtsOperation : wmtsOperations) {
            if (!OperationType.GET_CAPABILITIES.equals(wmtsOperation)) {
                map.put(wmtsOperation, wmtsSet);
            }
        }
        // for WFS operations, put the WFS set, unless it's the GetCapability op
        for (OperationType wfsOperation : wfsOperations) {
            if (!OperationType.GET_CAPABILITIES.equals(wfsOperation)) {
                map.put(wfsOperation, wfsSet);
            }
        }
        // for WCS operations, put the WCS set, unless it's the GetCapability op
        for (OperationType wcsOperation : wcsOperations) {
            if (!OperationType.GET_CAPABILITIES.equals(wcsOperation)) {
                map.put(wcsOperation, wcsSet);
            }
        }
        // for WPS operations, put the WPS set, unless it's the GetCapability op
        for (OperationType wpsOperation : wpsOperations) {
            if (!OperationType.GET_CAPABILITIES.equals(wpsOperation)) {
                map.put(wpsOperation, wpsSet);
            }
        }
        // store it in the lookup
        SERVICETYPES_BY_OPERATION = Collections.unmodifiableMap(map);
    }

    /**
     * A enum type for the allowed service format.
     */
    public enum Service {
        SERVICE("SERVICE");

        private final String value;

        /**
         * Enum constructor
         *
         * @param value
         */
        Service(String value) {
            this.value = value;
        }

        /**
         * Static method to get an enum based on a string value.
         * This method is annotated with {@link JsonCreator},
         * which allows the client to send case insensitive string
         * values (like "jSon"), which will be converted to the
         * correct enum value.
         *
         * @param inputValue
         * @return
         */
        @JsonCreator
        public static Service fromString(String inputValue) {
            if (inputValue != null) {
                for (Service type : Service.values()) {
                    if (inputValue.equalsIgnoreCase(type.value)) {
                        return type;
                    }
                }
            }
            return null;
        }

        /**
         * This method is annotated with {@link JsonValue},
         * so that jackson will serialize the enum value to
         * the (lowercase) {@link #value}.
         */
        @Override
        @JsonValue
        public String toString() {
            return value;
        }
    }

    /**
     * A enum type for the allowed operation format.
     */
    public enum Operation {
        OPERATION("REQUEST");

        private final String value;

        /**
         * Enum constructor
         *
         * @param value
         */
        Operation(String value) {
            this.value = value;
        }

        /**
         * Static method to get an enum based on a string value.
         * This method is annotated with {@link JsonCreator},
         * which allows the client to send case insensitive string
         * values (like "jSon"), which will be converted to the
         * correct enum value.
         *
         * @param inputValue
         * @return
         */
        @JsonCreator
        public static OperationType fromString(String inputValue) {
            if (inputValue != null) {
                for (OperationType type : OperationType.values()) {
                    if (inputValue.equalsIgnoreCase(type.value)) {
                        return type;
                    }
                }
            }
            return null;
        }

        /**
         * This method is annotated with {@link JsonValue},
         * so that jackson will serialize the enum value to
         * the (lowercase) {@link #value}.
         */
        @Override
        @JsonValue
        public String toString() {
            return value;
        }
    }

    /**
     * A enum type for the allowed endPoint format.
     */
    public enum EndPoint {
        LAYERS("LAYERS"),
        LAYER("LAYER"),
        TYPENAME("TYPENAME"),
        TYPENAMES("TYPENAMES"),
        NAMESPACE("NAMESPACE"),
        CUSTOM_ENDPOINT("CUSTOM_ENDPOINT");

        private final String value;

        /**
         * Enum constructor
         *
         * @param value
         */
        EndPoint(String value) {
            this.value = value;
        }

        /**
         * Static method to get an enum based on a string value.
         * This method is annotated with {@link JsonCreator},
         * which allows the client to send case insensitive string
         * values (like "jSon"), which will be converted to the
         * correct enum value.
         *
         * @param inputValue
         * @return
         */
        @JsonCreator
        public static EndPoint fromString(String inputValue) {
            if (inputValue != null) {
                for (EndPoint type : EndPoint.values()) {
                    if (inputValue.equalsIgnoreCase(type.value)) {
                        return type;
                    }
                }
            }
            return null;
        }

        /**
         * Returns all enum values as string array.
         *
         * @return
         */
        public static String[] getAllValues() {
            EndPoint[] endPoints = values();
            String[] values = new String[endPoints.length];

            for (int i = 0; i < endPoints.length; i++) {
                values[i] = endPoints[i].value;
            }

            return values;
        }

        /**
         * This method is annotated with {@link JsonValue},
         * so that jackson will serialize the enum value to
         * the (lowercase) {@link #value}.
         */
        @Override
        @JsonValue
        public String toString() {
            return value;
        }
    }

    /**
     * A enum type for the allowed service type format.
     */
    public enum ServiceType {
        WMS("WMS"),
        WMTS("WMTS"),
        WFS("WFS"),
        WCS("WCS"),
        WPS("WPS"),
        CSW("CSW");

        private final String value;

        /**
         * Enum constructor
         *
         * @param value
         */
        ServiceType(String value) {
            this.value = value;
        }

        /**
         * Static method to get an enum based on a string value.
         * This method is annotated with {@link JsonCreator},
         * which allows the client to send case insensitive string
         * values (like "jSon"), which will be converted to the
         * correct enum value.
         *
         * @param inputValue
         * @return
         */
        @JsonCreator
        public static ServiceType fromString(String inputValue) {
            if (inputValue != null) {
                for (ServiceType type : ServiceType.values()) {
                    if (inputValue.equalsIgnoreCase(type.value)) {
                        return type;
                    }
                }
            }
            return null;
        }

        /**
         * This method is annotated with {@link JsonValue},
         * so that jackson will serialize the enum value to
         * the (lowercase) {@link #value}.
         */
        @Override
        @JsonValue
        public String toString() {
            return value;
        }
    }

    /**
     * A enum type for the allowed operation type format.
     */
    public enum OperationType {
        GET_MAP("GetMap"),
        GET_CAPABILITIES("GetCapabilities"),
        GET_FEATURE_INFO("GetFeatureInfo"),
        DESCRIBE_LAYER("DescribeLayer"),
        GET_LEGEND_GRAPHIC("GetLegendGraphic"),
        GET_STYLES("GetStyles"),
        DESCRIBE_FEATURE_TYPE("DescribeFeatureType"),
        GET_FEATURE("GetFeature"),
        LOCK_FEATURE("LockFeature"),
        TRANSACTION("Transaction"),
        GET_PROPERTY_VALUE("GetPropertyValue"),
        GET_FEATURE_WITH_LOCK("GetFeatureWithLock"),
        LIST_STORED_QUERIES("ListStoredQueries"),
        DESCRIBE_STORED_QUERIES("DescribeStoredQueries"),
        CREATE_STORED_QUERY("CreateStoredQuery"),
        DROP_STORED_QUERY("DropStoredQuery"),
        DESCRIBE_COVERAGE("DescribeCoverage"),
        GET_COVERAGE("GetCoverage"),
        EXECUTE("Execute"),
        DESCRIBE_PROCESS("DescribeProcess"),
        GET_EXECUTION_STATUS("GetExecutionStatus"),
        GET_EXECUTION_RESULT("GetExecutionResult"),
        DISMISS("Dismiss"),
        GET_TILE("GetTile"),
        DESCRIBE_DOMAINS("DescribeDomains"),
        GET_DOMAIN_VALUES("GetDomainValues"),
        GET_HISTOGRAM("GetHistogram"),
        DESCRIBE_EO_COVERAGE_SET("DescribeEOCoverageSet"),
        DESCRIBE_RECORD("DescribeRecord"),
        GET_RECORDS("GetRecords"),
        GET_RECORD_BY_ID("GetRecordById"),
        GET_DOMAIN("GetDomain"),
        GET_REPOSITORY_ITEM("GetRepositoryItem"),
        DIRECT_DOWNLOAD("DirectDownload"),
        HARVEST("Harvest");

        private final String value;

        /**
         * Enum constructor
         *
         * @param value
         */
        OperationType(String value) {
            this.value = value;
        }

        /**
         * Static method to get an enum based on a string value.
         * This method is annotated with {@link JsonCreator},
         * which allows the client to send case insensitive string
         * values (like "jSon"), which will be converted to the
         * correct enum value.
         *
         * @param inputValue
         * @return
         */
        @JsonCreator
        public static OperationType fromString(String inputValue) {
            if (inputValue != null) {
                for (OperationType type : OperationType.values()) {
                    if (inputValue.equalsIgnoreCase(type.value)) {
                        return type;
                    }
                }
            }
            return null;
        }

        /**
         * This method is annotated with {@link JsonValue},
         * so that jackson will serialize the enum value to
         * the (lowercase) {@link #value}.
         */
        @Override
        @JsonValue
        public String toString() {
            return value;
        }
    }

}

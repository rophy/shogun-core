package de.terrestris.shoguncore.util.interceptor;

import de.terrestris.shoguncore.util.enumeration.InterceptorEnum;
import de.terrestris.shoguncore.util.enumeration.OgcEnum;
import de.terrestris.shoguncore.util.enumeration.OgcEnum.OperationType;
import de.terrestris.shoguncore.util.enumeration.OgcEnum.ServiceType;
import de.terrestris.shoguncore.util.model.Response;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Dispatch of the WMTS multi-dimensional, WCS EO and CSW operations.
 */
public class OgcMessageDistributorOwsTest {

    private OgcMessageDistributor distributor;

    private final MutableHttpServletRequest request = new MutableHttpServletRequest(new MockHttpServletRequest());

    private final Response response = new Response();

    private WmtsRequestInterceptorInterface wmtsRequest;

    private WmtsResponseInterceptorInterface wmtsResponse;

    private WcsRequestInterceptorInterface wcsRequest;

    private WcsResponseInterceptorInterface wcsResponse;

    private CswRequestInterceptorInterface cswRequest;

    private CswResponseInterceptorInterface cswResponse;

    @Before
    public void setUp() {
        distributor = new OgcMessageDistributor();
        wmtsRequest = mock(WmtsRequestInterceptorInterface.class);
        wmtsResponse = mock(WmtsResponseInterceptorInterface.class);
        wcsRequest = mock(WcsRequestInterceptorInterface.class);
        wcsResponse = mock(WcsResponseInterceptorInterface.class);
        cswRequest = mock(CswRequestInterceptorInterface.class);
        cswResponse = mock(CswResponseInterceptorInterface.class);
        distributor.setWmtsRequestInterceptor(wmtsRequest);
        distributor.setWmtsResponseInterceptor(wmtsResponse);
        distributor.setWcsRequestInterceptor(wcsRequest);
        distributor.setWcsResponseInterceptor(wcsResponse);
        distributor.setCswRequestInterceptor(cswRequest);
        distributor.setCswResponseInterceptor(cswResponse);
        when(wmtsRequest.interceptMultiDimensional(any())).thenReturn(request);
        when(wcsRequest.interceptDescribeEOCoverageSet(any())).thenReturn(request);
        when(wmtsResponse.interceptMultiDimensional(any(), any())).thenReturn(response);
        when(wcsResponse.interceptDescribeEOCoverageSet(any(), any())).thenReturn(response);
    }

    private static OgcMessage message(ServiceType service, OperationType operation) {
        return new OgcMessage(service, operation, "cite", InterceptorEnum.RuleType.MODIFY,
            InterceptorEnum.RuleType.MODIFY);
    }

    @Test
    public void wmts_multi_dimensional_operations() throws InterceptorException {
        for (OperationType operation : new OperationType[]{OperationType.DESCRIBE_DOMAINS,
            OperationType.GET_DOMAIN_VALUES, OperationType.GET_HISTOGRAM, OperationType.GET_FEATURE}) {
            OgcMessage message = message(ServiceType.WMTS, operation);
            assertTrue(message.isWmtsMultiDimensional());
            distributor.distributeToRequestInterceptor(request, message);
            distributor.distributeToResponseInterceptor(request, response, message);
        }
        verify(wmtsRequest, times(4)).interceptMultiDimensional(request);
        verify(wmtsResponse, times(4)).interceptMultiDimensional(request, response);
        assertFalse(message(ServiceType.WFS, OperationType.GET_FEATURE).isWmtsMultiDimensional());
    }

    @Test
    public void wcs_eo_describe_eo_coverage_set() throws InterceptorException {
        OgcMessage message = message(ServiceType.WCS, OperationType.DESCRIBE_EO_COVERAGE_SET);
        distributor.distributeToRequestInterceptor(request, message);
        distributor.distributeToResponseInterceptor(request, response, message);
        verify(wcsRequest).interceptDescribeEOCoverageSet(request);
        verify(wcsResponse).interceptDescribeEOCoverageSet(request, response);
    }

    @Test
    public void csw_operations() throws InterceptorException {
        when(cswRequest.interceptGetRecords(any())).thenReturn(request);
        when(cswResponse.interceptGetRecords(any(), any())).thenReturn(response);
        when(cswRequest.interceptCatalogChange(any())).thenReturn(request);
        when(cswResponse.interceptCatalogChange(any(), any())).thenReturn(response);
        OgcMessage records = message(ServiceType.CSW, OperationType.GET_RECORDS);
        distributor.distributeToRequestInterceptor(request, records);
        distributor.distributeToResponseInterceptor(request, response, records);
        verify(cswRequest).interceptGetRecords(request);
        verify(cswResponse).interceptGetRecords(request, response);
        OgcMessage transaction = message(ServiceType.CSW, OperationType.TRANSACTION);
        distributor.distributeToRequestInterceptor(request, transaction);
        verify(cswRequest).interceptCatalogChange(request);
    }

    @Test
    public void shared_operations_without_service_stay_wfs() {
        assertEquals(1, OgcEnum.SERVICETYPES_BY_OPERATION.get(OperationType.GET_FEATURE).size());
        assertTrue(OgcEnum.SERVICETYPES_BY_OPERATION.get(OperationType.GET_FEATURE).contains(ServiceType.WFS));
        assertTrue(OgcEnum.SERVICETYPES_BY_OPERATION.get(OperationType.TRANSACTION).contains(ServiceType.WFS));
        assertTrue(OgcEnum.OPERATIONS_BY_SERVICETYPE.get(ServiceType.WMTS).contains(OperationType.GET_FEATURE));
        assertTrue(OgcEnum.OPERATIONS_BY_SERVICETYPE.get(ServiceType.CSW).contains(OperationType.TRANSACTION));
        assertTrue(OgcEnum.SERVICETYPES_BY_OPERATION.get(OperationType.GET_RECORDS).contains(ServiceType.CSW));
        assertTrue(OgcEnum.SERVICETYPES_BY_OPERATION.get(OperationType.GET_CAPABILITIES).contains(ServiceType.CSW));
    }

    @Test
    public void w3ds_is_not_a_service() {
        assertEquals(null, ServiceType.fromString("W3DS"));
        assertEquals(null, OperationType.fromString("GetScene"));
    }
}

package de.terrestris.shoguncore.util.interceptor.secure;

import de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest;
import de.terrestris.shoguncore.util.interceptor.OgcException;

/**
 * <p>BaseInterceptor class.</p>
 */
public class BaseInterceptor {

    /**
     * Refuses the request. The client gets an OGC exception report.
     *
     * @param request a {@link de.terrestris.shoguncore.util.interceptor.MutableHttpServletRequest} object.
     * @return never returns
     */
    protected MutableHttpServletRequest forbidRequest(MutableHttpServletRequest request) {
        throw new OgcException(OgcException.NO_APPLICABLE_CODE, null, "Access denied.");
    }

    /**
     * Refuses a request for a layer that does not exist or that the user may
     * not read. Both cases give the same error, so that the response does not
     * show which layers exist.
     *
     * @param code The exception code (e.g. LayerNotDefined)
     * @param locator The parameter name
     * @param name The layer name
     * @return never returns
     */
    protected MutableHttpServletRequest forbidLayer(String code, String locator, String name) {
        throw new OgcException(code, locator, "Unknown layer: " + name);
    }
}

package com.jeedsoft.tomcat.filter;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * To resolve vulnerability scanning problem about HTTP request smuggling
 * You can add this filter to YOUR_WEB_APP/WEB-INF/web.xml:
 *   <filter>
 *     <filter-name>SmugglingFilter</filter-name>
 *     <filter-class>com.jeedsoft.tomcat.filter.SmugglingFilter</filter-class>
 *   </filter>
 *   <filter-mapping>
 *     <filter-name>SmugglingFilter</filter-name>
 *     <url-pattern>YOUR_PATTERN1</url-pattern>
 *     <url-pattern>YOUR_PATTERN2</url-pattern>
 *     ...
 *   </filter-mapping>
 */
public class SmugglingFilter implements Filter
{
    private static final Log log = LogFactory.getLog(SmugglingFilter.class);

    private static final String METHOD_GET = "GET";

    private static final Set<String> validMethods = new HashSet<>();

    static {
        validMethods.add("CONNECT");
        validMethods.add("GET");
        validMethods.add("DELETE");
        validMethods.add("HEAD");
        validMethods.add("OPTIONS");
        validMethods.add("PATCH");
        validMethods.add("POST");
        validMethods.add("PUT");
        validMethods.add("TRACE");
    }

    @Override
    public void init(FilterConfig filterConfig) {

    }

    @Override
    public void destroy() {

    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException
    {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        // Method
        String method = request.getMethod().toUpperCase();
        if (!validMethods.contains(method)) {
            response.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
            return;
        }
        // GET Content-Length
        if (METHOD_GET.equals(method) && request.getContentLength() > 0) {
            handleSmuggling(response, "A \"GET\" request should not has a \"Content-Length\" header.");
            return;
        }
        Enumeration<String> encodingEnumeration = request.getHeaders("Transfer-Encoding");
        List<String> encodings = encodingEnumeration == null ? new ArrayList<String>() : Collections.list(encodingEnumeration);
        // TE-TE
        if (encodings.size() > 1) {
            handleSmuggling(response, "A request should not has two or more \"Transfer-Encoding\" headers.");
            return;
        }
        // CL-CL
        Enumeration<String> lengthEnumeration = request.getHeaders("Content-Length");
        List<String> lengths = lengthEnumeration == null ? new ArrayList<String>() : Collections.list(lengthEnumeration);
        if (lengths.size() > 1) {
            handleSmuggling(response, "A request should not has two or more \"Content-Length\" headers.");
            return;
        }
        // TE-CL or CL-TE
        if (!encodings.isEmpty() && !lengths.isEmpty()) {
            String message = "A request should not has a \"Content-Length\" header or a \"Transfer-Encoding\" header at the same time.";
            handleSmuggling(response, message);
            return;
        }
        chain.doFilter(request, response);
    }

    private void handleSmuggling(HttpServletResponse response, String message)
    {
        response.setContentType("text/plain");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.resetBuffer();
        log.error(message);
    }
}

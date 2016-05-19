package aa.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@ControllerAdvice
public class StopWatchAdvice extends OncePerRequestFilter implements ResponseBodyAdvice<Object> {

  private static final Logger LOG = LoggerFactory.getLogger(StopWatchAdvice.class);

  @Override
  public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                ServerHttpRequest req, ServerHttpResponse response) {
    if (req instanceof ServletServerHttpRequest && LOG.isTraceEnabled()) {
      Object start = ((ServletServerHttpRequest) req).getServletRequest().getAttribute("start_ms");
      if (start != null && start instanceof Long) {
        String took = String.valueOf(System.currentTimeMillis() - (Long) start);
        response.getHeaders().add("X-Timer", took);
        LOG.trace("{} took {} ms", returnType.getMethod().getName(), took);
      }
    }
    return body;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
    request.setAttribute("start_ms", System.currentTimeMillis());
    filterChain.doFilter(request, response);
  }
}

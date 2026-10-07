package com.tip.ecommerce.rating.observability;

import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.*;
import org.springframework.web.servlet.mvc.method.annotation.*;

/** Summarizes already-decoded objects; does not buffer or consume HTTP streams. */
@ControllerAdvice(basePackages = "com.tip.ecommerce.rating")
public class PayloadLoggingAdvice extends RequestBodyAdviceAdapter
    implements ResponseBodyAdvice<Object> {
  @Override
  public boolean supports(MethodParameter p, Type t, Class<? extends HttpMessageConverter<?>> c) {
    return true;
  }

  @Override
  public Object afterBodyRead(
      Object body,
      HttpInputMessage input,
      MethodParameter p,
      Type t,
      Class<? extends HttpMessageConverter<?>> c) {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a)
      a.getRequest().setAttribute(HttpOperationLogging.REQUEST, OperationalLog.summary(body));
    return body;
  }

  @Override
  public boolean supports(MethodParameter p, Class<? extends HttpMessageConverter<?>> c) {
    return true;
  }

  @Override
  public Object beforeBodyWrite(
      Object body,
      MethodParameter p,
      MediaType type,
      Class<? extends HttpMessageConverter<?>> c,
      ServerHttpRequest request,
      ServerHttpResponse response) {
    if (request instanceof ServletServerHttpRequest servlet
        && MediaType.APPLICATION_JSON.isCompatibleWith(type)) {
      // GET bodies are useful only when debugging; do not summarize large reads at INFO.
      if (!"GET".equals(servlet.getServletRequest().getMethod())
          || org.slf4j.LoggerFactory.getLogger(HttpOperationLogging.class).isDebugEnabled())
        servlet
            .getServletRequest()
            .setAttribute(HttpOperationLogging.RESPONSE, OperationalLog.summary(body));
    }
    return body;
  }
}

package oasis.web.providers;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.joda.JodaModule;

@Provider
@Produces({"application/*+json", "text/json"})
public class JacksonContextResolver implements ContextResolver<ObjectMapper> {
  private ObjectMapper objectMapper;

  public JacksonContextResolver() throws Exception {
    this.objectMapper = new ObjectMapper().registerModule(new JodaModule());
  }

  public ObjectMapper getContext(Class<?> objectType) {
    return objectMapper;
  }
}

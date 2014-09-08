package oasis.web.kibana;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import com.google.common.io.Resources;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;

import oasis.web.authn.Authenticated;
import oasis.web.authn.User;
import oasis.web.utils.ResponseFactory;
import oasis.web.view.SoyView;
import oasis.web.view.soy.KibanaConfigSoyInfo;

@Path("/kibana/")
public class Kibana {

  @GET
  @Path("/")
  @Produces(MediaType.TEXT_HTML)
  @Authenticated @User
  public Response get() throws IOException {
    return getResource("index.html");
  }

  @GET
  @Path("{resource: .+\\.html}")
  @Produces(MediaType.TEXT_HTML)
  @Authenticated @User
  public Response html(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("config.js")
  @Produces("application/javascript")
  public Response config() throws IOException {
    SoyMapData model = new SoyMapData(
        KibanaConfigSoyInfo.Param.ES_PATH, UriBuilder.fromResource(ElasticSearchProxy.class).build("").toString()
    );
    return Response.ok(new SoyView(KibanaConfigSoyInfo.KIBANA_CONFIG, SanitizedContent.ContentKind.JS, model)).build();
  }

  @GET
  @Path("{resource: .+\\.js$}")
  @Produces("application/javascript")
  public Response js(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.json$}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response json(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.css$}")
  @Produces("text/css")
  public Response css(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.png$}")
  @Produces("image/png")
  public Response png(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.gif$}")
  @Produces("image/gif")
  public Response gif(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.otf}")
  @Produces("application/x-font-opentype")
  public Response otf(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.ttf}")
  @Produces("application/x-font-ttf")
  public Response ttf(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  @GET
  @Path("{resource: .+\\.woff}")
  @Produces("application/font-woff")
  public Response woff(@PathParam("resource") String resourceName) throws IOException {
    return getResource(resourceName);
  }

  private Response getResource(String resourceName) throws IOException {
    final URL resource;
    try {
      resource = Resources.getResource("kibana/" + resourceName);
    } catch (IllegalArgumentException iae) {
      return ResponseFactory.NOT_FOUND;
    }

    URLConnection conn = resource.openConnection();
    Response.ResponseBuilder response = Response.ok()
        .entity(conn.getInputStream());

    long lastModified = conn.getLastModified();
    if (lastModified != 0) {
      response.lastModified(new Date(lastModified));
    }

    return response.build();
  }
}

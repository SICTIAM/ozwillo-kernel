package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.jboss.resteasy.spi.ResteasyUriInfo;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.authn.SidToken;
import oasis.services.authn.TokenHandler;
import oasis.web.authn.testing.TestUserFilter;

@RunWith(JukitoRunner.class)
public class UserAuthenticationFilterTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(UserAuthenticationFilter.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);
    }
  }

  static final Instant now = Instant.now();

  static final SidToken validSidToken = new SidToken();
  static {
    validSidToken.setId("validSession");
    validSidToken.setCreationTime(now.minus(Duration.standardHours(1)));
    validSidToken.setExpirationTime(now.plus(Duration.standardHours(1)));
  }

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(TokenHandler tokenHandler) {
    when(tokenHandler.getCheckedToken("valid", SidToken.class)).thenReturn(validSidToken);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getProviderFactory().register(UserAuthenticationFilter.class);
    resteasy.getDeployment().getRegistry().addPerRequestResource(DummyResource.class);
  }

  @Test public void testUnauthenticated() {
    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).path(DummyResource.class, "authRequired").build())
        .queryParam("qux", "quux").request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertThat(location.getAbsolutePath()).isEqualTo(UriBuilder.fromUri(InProcessResteasy.BASE_URI).path(LoginPage.class).build());
    assertThat(location.getQueryParameters().getFirst("continue")).isEqualTo("http://localhost/foo/bar?qux=quux");
  }

  @Test public void testAuthenticated() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(validSidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).path(DummyResource.class, "authRequired").build())
        .queryParam("qux", "quux").request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    assertThat(response.readEntity(SidToken.class)).isEqualToComparingFieldByField(validSidToken);
  }

  @Test public void testLoginResponse() {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(validSidToken));

    Response response = resteasy.getClient().target(UriBuilder.fromResource(DummyResource.class).path(DummyResource.class, "redirectToLogin").build())
        .queryParam("foo", "bar").request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    UriInfo location = new ResteasyUriInfo(response.getLocation());
    assertThat(location.getAbsolutePath()).isEqualTo(UriBuilder.fromUri(InProcessResteasy.BASE_URI).path(LoginPage.class).build());
    assertThat(location.getQueryParameters().getFirst("continue")).isEqualTo("http://localhost/qux/quux?foo=bar");

    // Make sure we don't log the user out!
    assertThat(response.getCookies()).doesNotContainKey(UserFilter.COOKIE_NAME);
  }

  @Path("/")
  public static class DummyResource {
    @Context SecurityContext securityContext;

    @Path("/foo/bar")
    @GET
    @Authenticated @User
    @Produces(MediaType.APPLICATION_JSON)
    public SidToken authRequired() {
      return ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
    }

    @Path("/qux/quux")
    @GET
    @User
    public Response redirectToLogin(@Context UriInfo uriInfo) {
      assertThat(securityContext.getUserPrincipal()).isNotNull();

      return UserAuthenticationFilter.loginResponse(uriInfo.getRequestUri(), null, securityContext);
    }
  }
}

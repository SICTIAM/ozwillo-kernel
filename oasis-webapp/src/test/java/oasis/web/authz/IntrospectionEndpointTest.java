package oasis.web.authz;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.jukito.TestSingleton;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;

import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.Account;
import oasis.model.accounts.AccountRepository;
import oasis.model.applications.ApplicationRepository;
import oasis.model.applications.DataProvider;
import oasis.model.authn.AccessToken;
import oasis.model.authn.Token;
import oasis.services.authn.TokenHandler;
import oasis.services.authz.GroupService;
import oasis.web.authn.testing.TestClientAuthenticationFilter;

@RunWith(JukitoRunner.class)
public class IntrospectionEndpointTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(IntrospectionEndpoint.class);

      bindMock(TokenHandler.class).in(TestSingleton.class);
      bindMock(GroupService.class).in(TestSingleton.class);
    }
  }

  static final DataProvider dp1 = new DataProvider() {{
    setId("dp1");
    setScopeIds(ImmutableSet.of("dp1s1", "dp1s2", "dp1s3"));
  }};
  static final DataProvider dp2 = new DataProvider() {{
    setId("dp2");
    setScopeIds(ImmutableSet.of("dp2s1"));
  }};

  static final Account account = new Account() {{
    setId("account");
  }};

  static final AccessToken validToken = new AccessToken() {{
    setId("valid");
    setAccountId(account.getId());
    setScopeIds(ImmutableSet.of("openid", "dp1s1", "dp1s3", "dp3s1"));
    expiresIn(Duration.standardDays(1));
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(TokenHandler tokenHandler, AccountRepository accountRepository, ApplicationRepository applicationRepository,
      GroupService groupService) {
    when(tokenHandler.getCheckedToken(eq("valid"), any(Class.class))).thenReturn(validToken);
    when(tokenHandler.getCheckedToken(eq("invalid"), any(Class.class))).thenReturn(null);

    when(accountRepository.getAccount(account.getId())).thenReturn(account);

    when(applicationRepository.getDataProvider(dp1.getId())).thenReturn(dp1);
    when(applicationRepository.getDataProvider(dp2.getId())).thenReturn(dp2);

    when(groupService.getGroups(account)).thenReturn(Arrays.asList("gp1", "gp2"));
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(IntrospectionEndpoint.class);
  }

  @Test public void testUnauthorizedProvider() {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter("unauthorized"));

    // when
    Response resp = introspect("valid");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.UNAUTHORIZED);
  }

  @Test public void testEmptyToken() {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(dp1.getId()));

    // when
    Response resp = introspect("");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertThat(response).isEqualToComparingFieldByField(new IntrospectionResponse().setActive(false));
  }

  @Test public void testInvalidToken() {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(dp1.getId()));

    // when
    Response resp = introspect("invalid");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertThat(response).isEqualToComparingFieldByField(new IntrospectionResponse().setActive(false));
  }

  @Test public void testValidToken() {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(dp1.getId()));

    // when
    Response resp = introspect("valid");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertThat(response.isActive()).isTrue();
    assertThat(response.getIat()).isEqualTo((Long) TimeUnit.MILLISECONDS.toSeconds(validToken.getCreationTime().getMillis()));
    assertThat(response.getExp()).isEqualTo((Long) TimeUnit.MILLISECONDS.toSeconds(validToken.getExpirationTime().getMillis()));
    assertThat(response.getClient_id()).isEqualTo(validToken.getServiceProviderId());
    assertThat(response.getScope().split(" ")).containsOnly("dp1s1", "dp1s3");
    assertThat(response.getSub()).isEqualTo(account.getId());
    assertThat(response.getSub_groups()).containsOnly("gp1", "gp2");
  }

  @Test public void testStolenToken() {
    // given
    resteasy.getDeployment().getProviderFactory().register(new TestClientAuthenticationFilter(dp2.getId()));

    // when
    Response resp = introspect("valid");

    // then
    assertThat(resp.getStatusInfo()).isEqualTo(Response.Status.OK);
    IntrospectionResponse response = resp.readEntity(IntrospectionResponse.class);
    assertThat(response).isEqualToComparingFieldByField(new IntrospectionResponse().setActive(false));
  }

  private Response introspect(String token) {
    return resteasy.getClient().target(UriBuilder.fromResource(IntrospectionEndpoint.class)).request()
        .post(Entity.form(new Form("token", token)));
  }
}
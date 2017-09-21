/**
 * Ozwillo Kernel
 * Copyright (C) 2015  The Ozwillo Kernel Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package oasis.web.authn;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.Collections;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.assertj.core.api.AbstractCharSequenceAssert;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jukito.JukitoModule;
import org.jukito.JukitoRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.Iterables;
import com.google.common.html.HtmlEscapers;
import com.google.common.net.UrlEscapers;
import com.google.inject.Inject;
import com.ibm.icu.util.ULocale;

import oasis.auth.AuthModule;
import oasis.auth.RedirectUri;
import oasis.http.testing.InProcessResteasy;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.model.i18n.LocalizableString;
import oasis.security.KeyPairLoader;
import oasis.services.cookies.CookieFactory;
import oasis.soy.TestingSoyGuiceModule;
import oasis.urls.ImmutableUrls;
import oasis.urls.Urls;
import oasis.urls.UrlsModule;
import oasis.web.authn.testing.TestUserFilter;
import oasis.web.authz.KeysEndpoint;
import oasis.web.view.SoyTemplateBodyWriter;

@RunWith(JukitoRunner.class)
public class LogoutPageTest {

  public static class Module extends JukitoModule {
    @Override
    protected void configureTest() {
      bind(LogoutPage.class);

      install(new TestingSoyGuiceModule());
      install(new UrlsModule(ImmutableUrls.builder()
          .landingPage(URI.create("https://oasis/landing-page"))
          .build()));

      bind(AuthModule.Settings.class).toInstance(AuthModule.Settings.builder()
          .setKeyPair(KeyPairLoader.generateRandomKeyPair())
          .build());
    }
  }

  private static final String cookieName = CookieFactory.getCookieName(UserFilter.COOKIE_NAME, true);

  private static final UserAccount account = new UserAccount() {{
    setId("accountId");
    setNickname("Nickname");
    setLocale(ULocale.ROOT);
  }};

  private static final SidToken sidToken = new SidToken() {{
    setId("sessionId");
    setAccountId(account.getId());
  }};

  private static final AppInstance appInstance = new AppInstance() {{
    setId("appInstance");
    setName(new LocalizableString("Test Application Instance"));
  }};

  private static final Service service = new Service() {{
    setId("service provider");
    setInstance_id(appInstance.getId());
    setName(new LocalizableString("Test Service"));
    setService_uri("https://application/service");
    setPost_logout_redirect_uris(Collections.singleton("https://application/after_logout"));
  }};

  @Inject @Rule public InProcessResteasy resteasy;

  @Before public void setUpMocks(AccountRepository accountRepository, AppInstanceRepository appInstanceRepository, ServiceRepository serviceRepository) {
    when(accountRepository.getUserAccountById(account.getId())).thenReturn(account);

    when(appInstanceRepository.getAppInstance(appInstance.getId())).thenReturn(appInstance);
    when(appInstanceRepository.getAppInstances(anyCollectionOf(String.class))).thenReturn(Collections.emptyList());

    when(serviceRepository.getServiceByPostLogoutRedirectUri(appInstance.getId(), Iterables.getOnlyElement(service.getPost_logout_redirect_uris())))
        .thenReturn(service);
  }

  @Before public void setUp() {
    resteasy.getDeployment().getRegistry().addPerRequestResource(LogoutPage.class);
    resteasy.getDeployment().getProviderFactory().register(SoyTemplateBodyWriter.class);
  }
  @Test public void testGet_notLoggedIn_noParam(Urls urls) {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LogoutPage.class)).request().get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(urls.landingPage().get());
    if (response.getCookies().containsKey(cookieName)) {
      assertThat(response.getCookies().get(cookieName).getExpiry()).isInThePast();
    }
  }

  @Test public void testGet_loggedIn_noIdTokenHint(TokenRepository tokenRepository) {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LogoutPage.class))
        .queryParam("post_logout_redirect_uri", "http://www.google.com")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    if (response.getCookies().containsKey(cookieName)) {
      assertThat(response.getCookies().get(cookieName).getExpiry()).isInTheFuture();
    }
    assertLogoutPage(response)
        // post_logout_redirect_url should not be used
        .doesNotMatch(hiddenInput("continue", "http://www.google.com"));

    verify(tokenRepository, never()).revokeToken(sidToken.getId());
  }

  @Test public void testGet_loggedIn_withIdTokenHint(TokenRepository tokenRepository, AuthModule.Settings settings) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setSubject(sidToken.getAccountId());
    claims.setAudience(appInstance.getId());
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(settings.keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LogoutPage.class))
        .queryParam("id_token_hint", idToken)
        .queryParam("post_logout_redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getPost_logout_redirect_uris())))
        .queryParam("state", "some&state")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    if (response.getCookies().containsKey(cookieName)) {
      assertThat(response.getCookies().get(cookieName).getExpiry()).isInTheFuture();
    }
    assertLogoutPage(response)
        .contains(appInstance.getName().get(ULocale.ROOT))
        .contains(service.getService_uri())
        .matches(hiddenInput("continue", new RedirectUri(Iterables.getOnlyElement(service.getPost_logout_redirect_uris()))
            .setState("some&state")
            .toString()));

    verify(tokenRepository, never()).revokeToken(sidToken.getId());
  }

  @Test public void testGet_loggedIn_withIdTokenHintAndBadPostLogoutRedirectUri(TokenRepository tokenRepository,
      AuthModule.Settings settings) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setSubject(sidToken.getAccountId());
    claims.setAudience(appInstance.getId());
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(settings.keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LogoutPage.class))
        .queryParam("id_token_hint", idToken)
        .queryParam("post_logout_redirect_uri", "https://unregistered")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    if (response.getCookies().containsKey(cookieName)) {
      assertThat(response.getCookies().get(cookieName).getExpiry()).isInTheFuture();
    }
    assertLogoutPage(response)
        .contains(appInstance.getName().get(ULocale.ROOT))
        .doesNotMatch(hiddenInput("continue", "https://unregistered"));

    verify(tokenRepository, never()).revokeToken(sidToken.getId());
  }

  @Test public void testGet_loggedIn_badIdTokenHint(TokenRepository tokenRepository) throws Throwable {
    resteasy.getDeployment().getProviderFactory().register(new TestUserFilter(sidToken));

    // See tests for the parseIdTokenHint method for what is and isn't a valid ID Token hint.
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LogoutPage.class))
        .queryParam("id_token_hint", "invalid id_token_hint")
        .queryParam("post_logout_redirect_uri", "http://example.com")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
    if (response.getCookies().containsKey(cookieName)) {
      assertThat(response.getCookies().get(cookieName).getExpiry()).isInTheFuture();
    }
    assertLogoutPage(response)
        .doesNotContain(appInstance.getName().get(ULocale.ROOT))
        .doesNotContain(service.getService_uri())
        .doesNotMatch(hiddenInput("continue", "http://example.com"));

    verify(tokenRepository, never()).revokeToken(sidToken.getId());
  }

  @Test public void testGet_notLoggedIn_noIdTokenHint(Urls urls) {
    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LogoutPage.class))
        .queryParam("post_logout_redirect_uri", "http://www.google.com")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    // post_logout_redirect_url should not be used
    assertThat(response.getLocation()).isEqualTo(urls.landingPage().get());
    if (response.getCookies().containsKey(cookieName)) {
      assertThat(response.getCookies().get(cookieName).getExpiry()).isInTheFuture();
    }
  }

  @Test public void testGet_notLoggedIn_withIdTokenHint(AuthModule.Settings settings) throws Throwable {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setSubject(sidToken.getAccountId());
    claims.setAudience(appInstance.getId());
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(settings.keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LogoutPage.class))
        .queryParam("id_token_hint", idToken)
        .queryParam("post_logout_redirect_uri", UrlEscapers.urlFormParameterEscaper().escape(Iterables.getOnlyElement(service.getPost_logout_redirect_uris())))
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isEqualTo(URI.create(Iterables.getOnlyElement(service.getPost_logout_redirect_uris())));
    if (response.getCookies().containsKey(cookieName)) {
      assertThat(response.getCookies().get(cookieName).getExpiry()).isInTheFuture();
    }
  }

  @Test public void testGet_notLoggedIn_withIdTokenHintAndBadPostLogoutRedirectUri(AuthModule.Settings settings) throws Throwable {
    JwtClaims claims = new JwtClaims();
    claims.setIssuer(resteasy.getBaseUri().toString());
    claims.setSubject(sidToken.getAccountId());
    claims.setAudience(appInstance.getId());
    JsonWebSignature jws = new JsonWebSignature();
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setKeyIdHeaderValue(KeysEndpoint.JSONWEBKEY_PK_ID);
    jws.setPayload(claims.toJson());
    jws.setKey(settings.keyPair.getPrivate());
    String idToken = jws.getCompactSerialization();

    Response response = resteasy.getClient().target(resteasy.getBaseUriBuilder().path(LogoutPage.class))
        .queryParam("id_token_hint", idToken)
        .queryParam("post_logout_redirect_uri", "https://unregistered")
        .request()
        .get();

    assertThat(response.getStatusInfo()).isEqualTo(Response.Status.SEE_OTHER);
    assertThat(response.getLocation()).isNotEqualTo(URI.create("https://unregistered"));
    if (response.getCookies().containsKey(cookieName)) {
      assertThat(response.getCookies().get(cookieName).getExpiry()).isInTheFuture();
    }
  }

  private AbstractCharSequenceAssert<?, String> assertLogoutPage(Response response) {
    assertThat(response.getMediaType().toString()).startsWith(MediaType.TEXT_HTML);
    // XXX: this is really poor-man's checking. We should use the DOM (through Cucumber/Capybara, or an HTML5 parser)
    return assertThat(response.readEntity(String.class))
        .matches("(?s).*\\baction=([\"']?)" + Pattern.quote(UriBuilder.fromResource(LogoutPage.class).build().toString()) + "\\1[\\s>].*");
  }

  private String hiddenInput(String name, @Nullable String value) {
    return "(?s).*<input[^>]+type=([\"']?)hidden\\1[^>]+name=([\"']?)"
        + Pattern.quote(HtmlEscapers.htmlEscaper().escape(name))
        + "(\\2)[^>]+value=([\"']?)"
        + (value == null ? "[^\"]*" : Pattern.quote(HtmlEscapers.htmlEscaper().escape(value)))
        + "\\3[\\s>].*";
  }
}

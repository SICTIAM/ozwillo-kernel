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

import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.ibm.icu.text.Collator;

import oasis.auth.AuthModule;
import oasis.auth.FranceConnectModule;
import oasis.auth.RedirectUri;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.model.bootstrap.ClientIds;
import oasis.model.branding.BrandInfo;
import oasis.model.branding.BrandRepository;
import oasis.services.branding.BrandHelper;
import oasis.services.cookies.CookieFactory;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.LogoutSoyInfo;
import oasis.soy.templates.LogoutSoyInfo.LogoutSoyTemplateInfo;
import oasis.urls.BaseUrls;
import oasis.urls.UrlsFactory;
import oasis.urls.Urls;
import oasis.web.authn.franceconnect.FranceConnectLogoutCallback;
import oasis.web.authn.franceconnect.FranceConnectLogoutState;
import oasis.web.openidconnect.IdTokenHintParser;
import oasis.web.security.StrictReferer;

@User
@Path("/a/logout")
public class LogoutPage {
  private static final Logger logger = LoggerFactory.getLogger(LogoutPage.class);

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @Inject TokenRepository tokenRepository;
  @Inject AuthModule.Settings settings;
  @Inject @Nullable FranceConnectModule.Settings franceConnectSettings;
  @Inject BaseUrls baseUrls;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject AccountRepository accountRepository;
  @Inject SessionManagementHelper sessionManagementHelper;
  @Inject SecureRandom secureRandom;
  @Inject UrlsFactory urlsFactory;
  @Inject BrandRepository brandRepository;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get(
      @Nullable @QueryParam("id_token_hint") String id_token_hint,
      @Nullable @QueryParam("post_logout_redirect_uri") String post_logout_redirect_uri,
      @Nullable @QueryParam("state") String state) {
    final SidToken sidToken = (securityContext.getUserPrincipal() != null)
        ? ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken()
        : null;

    final String audience = parseIdTokenHint(id_token_hint, sidToken);
    post_logout_redirect_uri = Strings.emptyToNull(post_logout_redirect_uri);

    final AppInstance appInstance;
    if (audience != null) {
      appInstance = appInstanceRepository.getAppInstance(audience);
      if (appInstance == null) {
        logger.debug("No app instance for id_token_hint audience: {}", audience);
      }
    } else {
      appInstance = null;
    }

    final Service service;
    if (appInstance != null && post_logout_redirect_uri != null) {
      service = serviceRepository.getServiceByPostLogoutRedirectUri(appInstance.getId(), post_logout_redirect_uri);
      if (service == null) {
        logger.debug("No service found for id_token_hint audience {} and post_logout_redirect_uri {}",
            appInstance.getId(), post_logout_redirect_uri);
      }
      if (service == null && !appInstance.isRedirect_uri_validation_disabled()) {
        // don't act as an open redirector!
        post_logout_redirect_uri = null;
      }
    } else {
      service = null;
      // don't act as an open redirector!
      post_logout_redirect_uri = null;
    }

    // Note: validate the URI even if it's in the whitelist, just in case. You can never be too careful.
    if (post_logout_redirect_uri != null && !RedirectUri.isValid(post_logout_redirect_uri)) {
      logger.debug("Invalid post_logout_redirect_uri {}", post_logout_redirect_uri);
      post_logout_redirect_uri = null;
    }

    if (securityContext.getUserPrincipal() == null) {
      // Not authenticated (we'll assume the user already signed out but the app didn't caught it up)
      return redirectTo(post_logout_redirect_uri != null ? URI.create(post_logout_redirect_uri) : null);
    }
    assert sidToken != null;

    UserAccount account = accountRepository.getUserAccountById(sidToken.getAccountId());

    ImmutableMap.Builder<String, Object> viewModel = ImmutableMap.<String, Object>builder()
        .put(LogoutSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(LogoutPage.class).build().toString());
    if (post_logout_redirect_uri != null) {
      viewModel.put(LogoutSoyTemplateInfo.POST_LOGOUT_REDIRECT_URI, post_logout_redirect_uri);
      if (state != null) {
        viewModel.put(LogoutSoyTemplateInfo.STATE, state);
      }
    }
    if (appInstance != null) {
      viewModel.put(LogoutSoyTemplateInfo.APP_ID, appInstance.getId());
      viewModel.put(LogoutSoyTemplateInfo.APP_NAME, appInstance.getName().get(account.getLocale()));
    }
    // FIXME: services don't all have a service_uri for now so we need to workaround it.
    if (service != null && !Strings.isNullOrEmpty(service.getService_uri())) {
      viewModel.put(LogoutSoyTemplateInfo.SERVICE_URL, service.getService_uri());
    }
    ArrayList<String> otherApps = new ArrayList<>();
    for (AppInstance otherAppInstance : appInstanceRepository.getAppInstances(tokenRepository.getAllClientsForSession(sidToken.getId()))) {
      if (otherAppInstance == null) {
        // that shouldn't happen, but we don't want to break if that's the case
        continue;
      }
      otherApps.add(otherAppInstance.getName().get(account.getLocale()));
    }
    otherApps.sort(Collator.getInstance(account.getLocale()));
    viewModel.put(LogoutSoyTemplateInfo.OTHER_APPS, otherApps);
    viewModel.put(LogoutSoyTemplateInfo.IS_PORTAL, appInstance != null && appInstance.isPortal());

    BrandInfo brandInfo = brandRepository.getBrandInfo(appInstance == null ? BrandInfo.DEFAULT_BRAND : appInstance.getBrandId());

    Urls urls = urlsFactory.create(brandInfo.getPortal_base_uri());
    urls.myOasis().ifPresent(url -> viewModel.put(LogoutSoyTemplateInfo.PORTAL_URL, url.toString()));

    return Response.ok(new SoyTemplate(LogoutSoyInfo.LOGOUT, account.getLocale(), viewModel.build(),
        brandInfo)).build();
  }

  private Response redirectTo(@Nullable URI continueUrl) {
    if (continueUrl == null) {
      continueUrl = LoginPage.defaultContinueUrl(baseUrls.landingPage(), uriInfo);
    }
    return Response.seeOther(continueUrl).build();
  }

  @Nullable
  private String parseIdTokenHint(@Nullable String idTokenHint, @Nullable SidToken sidToken) {
    if (idTokenHint == null) {
      return null;
    }
    return IdTokenHintParser.parseIdTokenHintGetAudience(idTokenHint, settings.keyPair.getPublic(), getIssuer(), sidToken == null ? null : sidToken.getAccountId());
  }

  private String getIssuer() {
    if (baseUrls.canonicalBaseUri().isPresent()) {
      return baseUrls.canonicalBaseUri().get().toString();
    }
    return uriInfo.getBaseUri().toString();
  }

  @POST
  @Authenticated
  @StrictReferer
  public Response post(
      @Nullable @FormParam("app_id") String appId,
      @Nullable @FormParam("post_logout_redirect_uri") String post_logout_redirect_uri,
      @Nullable @FormParam("state") String state,
      @FormParam(BrandHelper.BRAND_PARAM) @DefaultValue(BrandInfo.DEFAULT_BRAND) String brandId
  ) {
    BrandInfo brandInfo = brandRepository.getBrandInfo(brandId);

    final SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
    boolean tokenRevoked = tokenRepository.revokeToken(sidToken.getId());
    if (!tokenRevoked) {
      logger.error("No SidToken was found when trying to revoke it.");
    }

    if (sidToken.getFranceconnectIdToken() != null && franceConnectSettings != null) {
      String fcState = FranceConnectLogoutState.generateStateKey(secureRandom);
      return Response.seeOther(
          franceConnectSettings.endSessionEndpoint().newBuilder()
              .addQueryParameter("id_token_hint", sidToken.getFranceconnectIdToken())
              .addQueryParameter("post_logout_redirect_uri", uriInfo.getBaseUriBuilder().path(FranceConnectLogoutCallback.class).build().toString())
              .addQueryParameter("state", fcState)
              .build().uri())
          .cookie(CookieFactory.createExpiredCookie(UserFilter.COOKIE_NAME, securityContext.isSecure(), true))
          .cookie(SessionManagementHelper.createBrowserStateCookie(securityContext.isSecure(), sessionManagementHelper.generateBrowserState()))
          .cookie(FranceConnectLogoutState.createCookie(fcState, appId, post_logout_redirect_uri, state, securityContext.isSecure()))
          .build();
    }

    // XXX: don't validate the post_logout_redirect_uri here; we assume no XSS and we have @StrictReferer
    URI continueUrl;
    if (post_logout_redirect_uri != null) {
      if (!Strings.isNullOrEmpty(state)) {
        continueUrl = URI.create(new RedirectUri(post_logout_redirect_uri)
            .setState(state)
            .toString());
      } else {
        continueUrl = URI.create(post_logout_redirect_uri);
      }
    } else {
      continueUrl = LoginPage.defaultContinueUrl(baseUrls.landingPage(), uriInfo);
    }
    return Response.seeOther(continueUrl)
        .cookie(CookieFactory.createExpiredCookie(UserFilter.COOKIE_NAME, securityContext.isSecure(), true))
        .cookie(SessionManagementHelper.createBrowserStateCookie(securityContext.isSecure(), sessionManagementHelper.generateBrowserState()))
        .build();
  }

}

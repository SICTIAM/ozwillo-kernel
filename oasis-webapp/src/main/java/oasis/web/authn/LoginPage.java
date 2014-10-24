package oasis.web.authn;

import java.net.URI;
import java.util.Locale;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.security.auth.login.LoginException;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.template.soy.data.SoyMapData;

import oasis.auditlog.AuditLogEvent;
import oasis.auditlog.AuditLogService;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.SidToken;
import oasis.model.authn.TokenRepository;
import oasis.openidconnect.OpenIdConnectModule;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.services.authn.UserPasswordAuthenticator;
import oasis.services.cookies.CookieFactory;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.LoginSoyInfo;
import oasis.soy.templates.LoginSoyInfo.LoginSoyTemplateInfo;
import oasis.soy.templates.LoginSoyInfo.ReauthSoyTemplateInfo;
import oasis.web.StaticResources;
import oasis.web.resteasy.Resteasy1099;
import oasis.web.security.StrictReferer;
import oasis.web.utils.UserAgentFingerprinter;

@User
@Path("/a/login")
public class LoginPage {
  private static final Logger logger = LoggerFactory.getLogger(LoginPage.class);

  public static final String CONTINUE_PARAM = "continue";
  public static final String CANCEL_PARAM = "cancel";

  @Inject UserPasswordAuthenticator userPasswordAuthenticator;
  @Inject TokenRepository tokenRepository;
  @Inject TokenHandler tokenHandler;
  @Inject AccountRepository accountRepository;
  @Inject AuditLogService auditLogService;
  @Inject UserAgentFingerprinter fingerprinter;
  @Inject OpenIdConnectModule.Settings settings;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Response get(@QueryParam(CONTINUE_PARAM) URI continueUrl) {
    return loginOrReauthForm(Response.ok(), continueUrl, null);
  }

  @POST
  @StrictReferer
  @Produces(MediaType.TEXT_HTML)
  public Response post(
      @Context HttpHeaders headers,
      @FormParam("u") @DefaultValue("") String userName,
      @FormParam("pwd") @DefaultValue("") String password,
      @FormParam("continue") URI continueUrl
  ) {
    if (userName.isEmpty()) {
      return loginOrReauthForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, null);
    }
    if (continueUrl == null) {
      continueUrl = defaultContinueUrl();
    }

    UserAccount account;
    try {
      account = userPasswordAuthenticator.authenticate(userName, password);
    } catch (LoginException e) {
      log(userName, LoginLogEvent.LoginResult.AUTHENTICATION_FAILED);
      return loginOrReauthForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, LoginError.INCORRECT_USERNAME_OR_PASSWORD);
    }

    if (securityContext.getUserPrincipal() != null) {
      return reAuthenticate(userName, account, continueUrl);
    }

    byte[] fingerprint = fingerprinter.fingerprint(headers);
    return authenticate(userName, account, continueUrl, fingerprint);
  }

  private Response authenticate(String userName, UserAccount account, URI continueUrl, byte[] fingerprint) {
    return authenticate(userName, account, continueUrl, fingerprint, tokenHandler, auditLogService, securityContext);
  }

  // XXX: Pending activation email
  static Response authenticate(String userName, UserAccount account, URI continueUrl, byte[] fingerprint, TokenHandler tokenHandler,
      AuditLogService auditLogService, SecurityContext securityContext) {
    String pass = tokenHandler.generateRandom();
    SidToken sidToken = tokenHandler.createSidToken(account.getId(), fingerprint, pass);
    if (sidToken == null) {
      // XXX: This shouldn't be audited because it shouldn't be the user fault
      logger.error("No SidToken was created for Account {}.", account.getId());
      return Response.serverError().build();
    }

    log(auditLogService, userName, LoginLogEvent.LoginResult.AUTHENTICATION_SUCCEEDED);

    // TODO: One-Time Password
    return Response
        .seeOther(continueUrl)
        .cookie(CookieFactory.createSessionCookie(UserFilter.COOKIE_NAME, TokenSerializer.serialize(sidToken, pass), securityContext.isSecure())) // TODO: remember me
        .build();
  }

  private Response reAuthenticate(String userName, UserAccount account, URI continueUrl) {
    SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
    if (!account.getId().equals(sidToken.getAccountId())) {
      // Form has been tampered with, or user signed in with another account since the form was generated
      // Re-display the form: if user signed out/in, it will show the new (current) user.
      return loginOrReauthForm(Response.status(Response.Status.BAD_REQUEST), continueUrl, null);
    }
    if (!tokenRepository.reAuthSidToken(sidToken.getId())) {
      // XXX: This shouldn't be audited because it shouldn't be the user fault
      logger.error("Error when updating SidToken {} for Account {}.", sidToken.getId(), account.getId());
      return Response.serverError().build();
    }

    log(userName, LoginLogEvent.LoginResult.RE_ENTER_PASSWORD_SUCCEEDED);

    return Response.seeOther(continueUrl).build();
  }

  private Response loginOrReauthForm(Response.ResponseBuilder builder, @Nullable URI continueUrl, @Nullable LoginError error) {
    if (continueUrl == null) {
      continueUrl = defaultContinueUrl();
    }

    if (securityContext.getUserPrincipal() != null) {
      SidToken sidToken = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken();
      UserAccount account = accountRepository.getUserAccountById(sidToken.getAccountId());
      // XXX: what if account is null?
      return reauthForm(builder, continueUrl, error, account);
    }
    return loginForm(builder, continueUrl, settings, error);
  }

  private static Response reauthForm(Response.ResponseBuilder builder, URI continueUrl, @Nullable LoginError error, UserAccount userAccount) {
    SoyTemplate soyTemplate = new SoyTemplate(LoginSoyInfo.REAUTH, userAccount.getLocale(), new SoyMapData(
        ReauthSoyTemplateInfo.REAUTH_EMAIL, userAccount.getEmail_address(),
        ReauthSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(LoginPage.class).build().toString(),
        ReauthSoyTemplateInfo.CONTINUE, continueUrl.toString(),
        ReauthSoyTemplateInfo.ERROR, error == null ? null : error.name()
    ));

    return buildResponseFromView(builder, soyTemplate);
  }

  private static Response loginForm(Response.ResponseBuilder builder, URI continueUrl, OpenIdConnectModule.Settings settings, @Nullable LoginError error) {
    return loginAndSignupForm(builder, continueUrl, settings, error);
  }

  static Response signupForm(Response.ResponseBuilder builder, URI continueUrl, OpenIdConnectModule.Settings settings, @Nullable SignupError error) {
    return loginAndSignupForm(builder, continueUrl, settings, error);
  }

  private static Response loginAndSignupForm(Response.ResponseBuilder builder, URI continueUrl, OpenIdConnectModule.Settings settings, @Nullable Enum<?> error) {
    // TODO: I18N
    SoyTemplate soyTemplate = new SoyTemplate(LoginSoyInfo.LOGIN, Locale.ROOT, new SoyMapData(
        LoginSoyTemplateInfo.SIGN_UP_FORM_ACTION, UriBuilder.fromResource(SignUpPage.class).build().toString(),
        LoginSoyTemplateInfo.LOGIN_FORM_ACTION, UriBuilder.fromResource(LoginPage.class).build().toString(),
        LoginSoyTemplateInfo.CONTINUE, continueUrl.toString(),
        LoginSoyTemplateInfo.ERROR, error == null ? null : error.name(),
        LoginSoyTemplateInfo.OVERVIEW, settings.landingPage == null ? null : settings.landingPage.toString()
    ));

    return buildResponseFromView(builder, soyTemplate);
  }

  private static Response buildResponseFromView(Response.ResponseBuilder builder, SoyTemplate soyTemplate) {
    return builder
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(soyTemplate)
        .build();
  }

  private URI defaultContinueUrl() {
    return defaultContinueUrl(settings.landingPage, uriInfo);
  }

  static URI defaultContinueUrl(URI landingPage, UriInfo uriInfo) {
    if (landingPage != null) {
      return landingPage;
    }
    return Resteasy1099.getBaseUriBuilder(uriInfo).path(StaticResources.class).path(StaticResources.class, "home").build();
  }

  private void log(String userName, LoginLogEvent.LoginResult loginResult) {
    log(auditLogService, userName, loginResult);
  }

  static void log(AuditLogService auditLogService, String userName, LoginLogEvent.LoginResult loginResult) {
    auditLogService.event(LoginLogEvent.class)
        .setUserName(userName)
        .setLoginResult(loginResult)
        .log();
  }

  public static class LoginLogEvent extends AuditLogEvent {
    public LoginLogEvent() {
      super("login_event");
    }

    public LoginLogEvent setUserName(String userName) {
      this.addContextData("user_name", userName);
      return this;
    }

    public LoginLogEvent setLoginResult(LoginResult loginResult) {
      this.addContextData("login_result", loginResult);
      return this;
    }

    public static enum LoginResult {
      AUTHENTICATION_SUCCEEDED,
      AUTHENTICATION_FAILED,
      RE_ENTER_PASSWORD_SUCCEEDED,
    }
  }

  enum LoginError {
    INCORRECT_USERNAME_OR_PASSWORD
  }

  enum SignupError {
    MISSING_REQUIRED_FIELD,
    ACCOUNT_ALREADY_EXISTS,
    MESSAGING_ERROR
  }
}

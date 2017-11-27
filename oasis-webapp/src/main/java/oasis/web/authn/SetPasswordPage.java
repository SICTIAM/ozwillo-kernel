/**
 * Ozwillo Kernel
 * Copyright (C) 2017  The Ozwillo Kernel Authors
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

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.MoreObjects;
import com.google.template.soy.data.SoyMapData;

import oasis.auth.AuthModule;
import oasis.mail.MailMessage;
import oasis.mail.MailSender;
import oasis.model.DuplicateKeyException;
import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.authn.ClientType;
import oasis.model.authn.CredentialsRepository;
import oasis.model.authn.SetPasswordToken;
import oasis.services.authn.TokenHandler;
import oasis.services.authn.TokenSerializer;
import oasis.soy.SoyTemplate;
import oasis.soy.templates.InitPasswordMailSoyInfo;
import oasis.soy.templates.InitPasswordMailSoyInfo.ActivatePasswordSoyTemplateInfo;
import oasis.soy.templates.InitPasswordSoyInfo;
import oasis.soy.templates.InitPasswordSoyInfo.InitPasswordSoyTemplateInfo;
import oasis.urls.Urls;

@Path("/a/setpassword")
@Authenticated @User
@Produces(MediaType.TEXT_HTML)
public class SetPasswordPage {
  private static final Logger logger = LoggerFactory.getLogger(SetPasswordPage.class);

  @Inject AccountRepository accountRepository;
  @Inject CredentialsRepository credentialsRepository;
  @Inject TokenHandler tokenHandler;
  @Inject AuthModule.Settings authSettings;
  @Inject Urls urls;
  @Inject MailSender mailSender;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @POST
  public Response post(
      @FormParam("u") String email,
      @FormParam("pwd") String pwd
  ) {
    String userId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();

    if (credentialsRepository.getCredentials(ClientType.USER, userId) != null) {
      // If the account already has a password, this is most likely a race condition (or tampered form)
      UserAccount account = accountRepository.getUserAccountById(userId);
      return ChangePasswordPage.form(Response.status(Response.Status.CONFLICT), authSettings, urls,
          account, ChangePasswordPage.PasswordChangeError.BAD_PASSWORD);
    }

    if (pwd.length() < authSettings.passwordMinimumLength) {
      UserAccount account = accountRepository.getUserAccountById(userId);
      return form(Response.status(Response.Status.BAD_REQUEST), account, PasswordInitError.PASSWORD_TOO_SHORT);
    }

    UserAccount account;
    try {
      account = accountRepository.setEmailAddress(userId, email);
    } catch (DuplicateKeyException dke) {
      account = accountRepository.getUserAccountById(userId);
      return form(Response.status(Response.Status.BAD_REQUEST), account, PasswordInitError.EMAIL_ALREADY_EXISTS);
    }

    String pass = tokenHandler.generateRandom();
    SetPasswordToken setPasswordToken = tokenHandler.createSetPasswordToken(account.getId(), pwd, pass);
    URI initPasswordLink = uriInfo.getBaseUriBuilder()
        .path(InitPasswordPage.class)
        .queryParam(LoginPage.LOCALE_PARAM, account.getLocale().toLanguageTag())
        .build(TokenSerializer.serialize(setPasswordToken, pass));
    try {
      mailSender.send(new MailMessage()
          .setRecipient(account.getEmail_address(), account.getDisplayName())
          .setLocale(account.getLocale())
          .setSubject(InitPasswordMailSoyInfo.ACTIVATE_PASSWORD_SUBJECT)
          .setBody(InitPasswordMailSoyInfo.ACTIVATE_PASSWORD)
          .setHtml()
          .setData(new SoyMapData(
              ActivatePasswordSoyTemplateInfo.NICKNAME, MoreObjects.firstNonNull(account.getNickname(), account.getDisplayName()),
              ActivatePasswordSoyTemplateInfo.ACTIVATION_LINK, initPasswordLink.toString()
          )));
    } catch (MessagingException e) {
      logger.error("Error sending init-password email", e);
      return form(Response.serverError(), account, PasswordInitError.MESSAGING_ERROR);
    }

    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
        // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(InitPasswordSoyInfo.PASSWORD_PENDING_ACTIVATION, account.getLocale()))
        .build();
  }

  private Response form(Response.ResponseBuilder builder, UserAccount account, @Nullable PasswordInitError error) {
    return form(builder, authSettings, urls, account, error);
  }

  static Response form(Response.ResponseBuilder builder, AuthModule.Settings authSettings, Urls urls, UserAccount account, @Nullable PasswordInitError error) {
    return builder
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
        // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(InitPasswordSoyInfo.INIT_PASSWORD,
            account.getLocale(),
            new SoyMapData(
                InitPasswordSoyTemplateInfo.EMAIL, account.getEmail_address(),
                InitPasswordSoyTemplateInfo.FORM_ACTION, UriBuilder.fromResource(SetPasswordPage.class).build().toString(),
                InitPasswordSoyTemplateInfo.PORTAL_URL, urls.myProfile().map(URI::toString).orElse(null),
                InitPasswordSoyTemplateInfo.ERROR, error == null ? null : error.name(),
                InitPasswordSoyTemplateInfo.PWD_MIN_LENGTH, authSettings.passwordMinimumLength
            )
        ))
        .build();
  }

  enum PasswordInitError {
    EMAIL_ALREADY_EXISTS,
    PASSWORD_TOO_SHORT,
    MESSAGING_ERROR,
    EXPIRED_LINK,
    BAD_USER
  }
}

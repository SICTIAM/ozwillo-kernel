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
package oasis.web.applications;

import java.net.URI;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.google.template.soy.parseinfo.SoyTemplateInfo;
import com.ibm.icu.util.ULocale;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AccessControlEntry;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.authn.AppInstanceInvitationToken;
import oasis.model.authn.TokenRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.services.authn.TokenHandler;
import oasis.services.authz.AppAdminHelper;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo.AcceptedAppInstanceInvitationAdminMessageSoyTemplateInfo;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo.AcceptedAppInstanceInvitationRequesterMessageSoyTemplateInfo;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo.RejectedAppInstanceInvitationAdminMessageSoyTemplateInfo;
import oasis.soy.templates.AppInstanceInvitationNotificationSoyInfo.RejectedAppInstanceInvitationRequesterMessageSoyTemplateInfo;
import oasis.soy.templates.AppInstanceInvitationSoyInfo;
import oasis.soy.templates.AppInstanceInvitationSoyInfo.AppInstanceInvitationSoyTemplateInfo;
import oasis.urls.Urls;
import oasis.web.authn.Authenticated;
import oasis.web.authn.User;
import oasis.web.authn.UserSessionPrincipal;
import oasis.web.i18n.LocaleHelper;
import oasis.web.security.StrictReferer;

@Path("/apps/invitation/{token}")
@Produces(MediaType.TEXT_HTML)
@Authenticated @User
public class AppInstanceInvitationPage {
  private static final Logger logger = LoggerFactory.getLogger(AppInstanceInvitationPage.class);

  @PathParam("token") String serializedToken;

  @Inject AccountRepository accountRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject NotificationRepository notificationRepository;
  @Inject AccessControlRepository accessControlRepository;
  @Inject TokenRepository tokenRepository;
  @Inject SoyTemplateRenderer templateRenderer;
  @Inject TokenHandler tokenHandler;
  @Inject Urls urls;
  @Inject AppAdminHelper appAdminHelper;

  @Context Request request;
  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @GET
  @Path("")
  public Response showInvitation() {
    String userId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    UserAccount user = accountRepository.getUserAccountById(userId);
    ULocale userLocale = user.getLocale();

    AppInstanceInvitationToken appInstanceInvitationToken = tokenHandler.getCheckedToken(serializedToken, AppInstanceInvitationToken.class);
    if (appInstanceInvitationToken == null) {
      return generateNotFoundPage(userLocale);
    }

    AccessControlEntry pendingAccessControlEntry = accessControlRepository.getPendingAccessControlEntry(
        appInstanceInvitationToken.getAceId());
    if (pendingAccessControlEntry == null) {
      // The token is not related to a pending ACE so it is useless
      // Let's remove it
      tokenRepository.revokeToken(appInstanceInvitationToken.getId());
      return generateNotFoundPage(userLocale);
    }

    AppInstance appInstance = appInstanceRepository.getAppInstance(pendingAccessControlEntry.getInstance_id());
    if (appInstance == null) {
      return generateNotFoundPage(userLocale);
    }

    // XXX: if app-instance is in STOPPED status, we allow the user to proceed, just in case it's later moved back to RUNNING
    // app-instance should not be in PENDING state if we reach this code.
    return generatePage(userLocale, pendingAccessControlEntry, appInstance);
  }

  @POST
  @StrictReferer
  @Path("/accept")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response acceptInvitation() {
    AppInstanceInvitationToken appInstanceInvitationToken = tokenHandler.getCheckedToken(serializedToken, AppInstanceInvitationToken.class);
    if (appInstanceInvitationToken == null) {
      return goBackToFirstStep();
    }
    tokenRepository.revokeToken(appInstanceInvitationToken.getId());

    String currentAccountId = ((UserSessionPrincipal) securityContext.getUserPrincipal()).getSidToken().getAccountId();
    AccessControlEntry pendingAccessControlEntry = accessControlRepository.getPendingAccessControlEntry(
        appInstanceInvitationToken.getAceId());
    if (pendingAccessControlEntry == null) {
      return goBackToFirstStep();
    }
    AccessControlEntry entry = accessControlRepository
        .acceptPendingAccessControlEntry(appInstanceInvitationToken.getAceId(), currentAccountId);
    if (entry == null) {
      return goBackToFirstStep();
    }

    try {
      AppInstance appInstance = appInstanceRepository.getAppInstance(pendingAccessControlEntry.getInstance_id());
      if (appInstance.getStatus() == AppInstance.InstantiationStatus.RUNNING) {
        UserAccount requester = accountRepository.getUserAccountById(pendingAccessControlEntry.getCreator_id());
        notifyRequester(appInstance, pendingAccessControlEntry.getEmail(), requester.getId(), true);
        notifyAdmins(appInstance, pendingAccessControlEntry.getEmail(), requester, true);
      }
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying admins for accepted app-instance invitation.", e);
    }

    if (urls.myNetwork().isPresent()) {
      return Response.seeOther(urls.myNetwork().get()).build();
    }
    return Response.seeOther(uriInfo.getBaseUri()).build();
  }

  @POST
  @StrictReferer
  @Path("/refuse")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  public Response refuseInvitation() {
    AppInstanceInvitationToken appInstanceInvitationToken = tokenHandler.getCheckedToken(serializedToken, AppInstanceInvitationToken.class);
    if (appInstanceInvitationToken == null) {
      return goBackToFirstStep();
    }
    tokenRepository.revokeToken(appInstanceInvitationToken.getId());

    AccessControlEntry pendingAccessControlEntry = accessControlRepository
        .getPendingAccessControlEntry(appInstanceInvitationToken.getAceId());
    if (pendingAccessControlEntry == null) {
      return goBackToFirstStep();
    }

    boolean deleted = accessControlRepository.deletePendingAccessControlEntry(appInstanceInvitationToken.getAceId());
    if (!deleted) {
      return goBackToFirstStep();
    }

    try {
      AppInstance appInstance = appInstanceRepository.getAppInstance(pendingAccessControlEntry.getInstance_id());
      if (appInstance.getStatus() == AppInstance.InstantiationStatus.RUNNING) {
        UserAccount requester = accountRepository.getUserAccountById(pendingAccessControlEntry.getCreator_id());
        notifyRequester(appInstance, pendingAccessControlEntry.getEmail(), requester.getId(), false);
        notifyAdmins(appInstance, pendingAccessControlEntry.getEmail(), requester, false);
      }
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying admins for accepted app-instance invitation.", e);
    }

    if (urls.myNetwork().isPresent()) {
      return Response.seeOther(urls.myNetwork().get()).build();
    }
    return Response.seeOther(uriInfo.getBaseUri()).build();
  }

  private Response generatePage(ULocale locale, AccessControlEntry pendingAccessControlEntry, AppInstance appInstance) {
    UserAccount requester = accountRepository.getUserAccountById(pendingAccessControlEntry.getCreator_id());

    URI acceptFormAction = uriInfo.getBaseUriBuilder()
        .path(AppInstanceInvitationPage.class)
        .path(AppInstanceInvitationPage.class, "acceptInvitation")
        .build(serializedToken);
    URI refuseFormAction = uriInfo.getBaseUriBuilder()
        .path(AppInstanceInvitationPage.class)
        .path(AppInstanceInvitationPage.class, "refuseInvitation")
        .build(serializedToken);
    return Response.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(
            AppInstanceInvitationSoyInfo.APP_INSTANCE_INVITATION,
            locale,
            new SoyMapData(
                AppInstanceInvitationSoyTemplateInfo.ACCEPT_FORM_ACTION, acceptFormAction.toString(),
                AppInstanceInvitationSoyTemplateInfo.REFUSE_FORM_ACTION, refuseFormAction.toString(),
                AppInstanceInvitationSoyTemplateInfo.APP_INSTANCE_NAME, appInstance.getName().get(locale),
                AppInstanceInvitationSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName(),
                AppInstanceInvitationSoyTemplateInfo.INVITED_EMAIL, pendingAccessControlEntry.getEmail()
            )
        ))
        .build();
  }

  private Response generateNotFoundPage(ULocale locale) {
    return Response.status(Response.Status.NOT_FOUND)
        .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        .header("Pragma", "no-cache")
            // cf. https://www.owasp.org/index.php/List_of_useful_HTTP_headers
        .header("X-Frame-Options", "DENY")
        .header("X-Content-Type-Options", "nosniff")
        .header("X-XSS-Protection", "1; mode=block")
        .entity(new SoyTemplate(AppInstanceInvitationSoyInfo.APP_INSTANCE_INVITATION_TOKEN_ERROR, locale))
        .build();
  }

  private Response goBackToFirstStep() {
    // The token was expired between showInvitation page loading and form action
    // So let's restart the process by sending the user to the showInvitation page which should display an error
    URI showInvitationUri = uriInfo.getBaseUriBuilder()
        .path(AppInstanceInvitationPage.class)
        .path(AppInstanceInvitationPage.class, "showInvitation")
        .build(serializedToken);
    return Response.seeOther(showInvitationUri).build();
  }

  private void notifyAdmins(AppInstance appInstance, String invitedUserEmail, UserAccount requester, boolean acceptedInvitation) {
    SoyMapData data = new SoyMapData();
    final SoyTemplateInfo templateInfo;
    final String appInstanceNameParamName;
    if (acceptedInvitation) {
      templateInfo = AppInstanceInvitationNotificationSoyInfo.ACCEPTED_APP_INSTANCE_INVITATION_ADMIN_MESSAGE;
      data.put(AcceptedAppInstanceInvitationAdminMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail);
      data.put(AcceptedAppInstanceInvitationAdminMessageSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName());
      appInstanceNameParamName = AcceptedAppInstanceInvitationAdminMessageSoyTemplateInfo.APP_INSTANCE_NAME;
    } else {
      templateInfo = AppInstanceInvitationNotificationSoyInfo.REJECTED_APP_INSTANCE_INVITATION_ADMIN_MESSAGE;
      data.put(RejectedAppInstanceInvitationAdminMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail);
      data.put(RejectedAppInstanceInvitationAdminMessageSoyTemplateInfo.REQUESTER_NAME, requester.getDisplayName());
      appInstanceNameParamName = RejectedAppInstanceInvitationAdminMessageSoyTemplateInfo.APP_INSTANCE_NAME;
    }

    Notification notificationPrototype = new Notification();
    notificationPrototype.setTime(Instant.now());
    notificationPrototype.setStatus(Notification.Status.UNREAD);
    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      data.put(appInstanceNameParamName, appInstance.getName().get(locale));
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notificationPrototype.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(templateInfo, locale,
          SanitizedContent.ContentKind.TEXT, data)));
    }

    Iterable<String> admins = appAdminHelper.getAdmins(appInstance);
    for (String admin : admins) {
      if (admin.equals(requester.getId())) {
        continue;
      }

      try {
        Notification notification = new Notification(notificationPrototype);
        notification.setUser_id(admin);
        notificationRepository.createNotification(notification);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying admin {} for accepted or refused app-instance invitation.", admin, e);
      }
    }
  }

  private void notifyRequester(AppInstance appInstance, String invitedUserEmail, String requesterId, boolean acceptedInvitation) {
    SoyMapData data = new SoyMapData();
    final SoyTemplateInfo templateInfo;
    final String appInstanceNameParamName;
    if (acceptedInvitation) {
      templateInfo = AppInstanceInvitationNotificationSoyInfo.ACCEPTED_APP_INSTANCE_INVITATION_REQUESTER_MESSAGE;
      data.put(AcceptedAppInstanceInvitationRequesterMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail);
      appInstanceNameParamName = AcceptedAppInstanceInvitationRequesterMessageSoyTemplateInfo.APP_INSTANCE_NAME;
    } else {
      templateInfo = AppInstanceInvitationNotificationSoyInfo.REJECTED_APP_INSTANCE_INVITATION_REQUESTER_MESSAGE;
      data.put(RejectedAppInstanceInvitationRequesterMessageSoyTemplateInfo.INVITED_USER_EMAIL, invitedUserEmail);
      appInstanceNameParamName = RejectedAppInstanceInvitationRequesterMessageSoyTemplateInfo.APP_INSTANCE_NAME;
    }

    Notification notification = new Notification();
    notification.setTime(Instant.now());
    notification.setStatus(Notification.Status.UNREAD);
    notification.setUser_id(requesterId);
    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      data.put(appInstanceNameParamName, appInstance.getName().get(locale));
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notification.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(templateInfo, locale,
          SanitizedContent.ContentKind.TEXT, data)));
    }

    try {
      notificationRepository.createNotification(notification);
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying requester {} for accepted or refused app-instance invitation.", requesterId, e);
    }
  }
}

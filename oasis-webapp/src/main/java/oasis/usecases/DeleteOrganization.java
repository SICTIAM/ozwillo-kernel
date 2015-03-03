package oasis.usecases;

import javax.inject.Inject;

import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.template.soy.data.SanitizedContent;
import com.google.template.soy.data.SoyMapData;
import com.ibm.icu.util.ULocale;

import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.directory.DirectoryRepository;
import oasis.model.directory.Organization;
import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.soy.SoyTemplate;
import oasis.soy.SoyTemplateRenderer;
import oasis.soy.templates.DeletedOrganizationSoyInfo;
import oasis.soy.templates.DeletedOrganizationSoyInfo.DeletedOrganizationMessageSoyTemplateInfo;
import oasis.web.i18n.LocaleHelper;

@Value.Nested
public class DeleteOrganization {
  private static final Logger logger = LoggerFactory.getLogger(DeleteOrganization.class);

  @Inject AppInstanceRepository appInstanceRepository;
  @Inject DirectoryRepository directoryRepository;
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject NotificationRepository notificationRepository;
  @Inject DeleteAppInstance deleteAppInstance;
  @Inject SoyTemplateRenderer templateRenderer;

  public ResponseStatus deleteOrganization(Request request) {
    if (request.checkStatus().isPresent()) {
      // If the status has to be checked, the deletion should occur before removing app instance and memberships to
      // prevent status conflict after removing all application instances
      boolean deleted = directoryRepository.deleteOrganization(request.organization().getId(), request.checkStatus().get());
      if (!deleted) {
        logger.error("The organization {} wasn't in STOPPED status", request.organization().getId());
        return ResponseStatus.BAD_ORGANIZATION_STATUS;
      }
    } else {
      directoryRepository.deleteOrganization(request.organization().getId());
    }

    organizationMembershipRepository.deleteMembershipsInOrganization(request.organization().getId());

    ResponseStatus responseStatus = ResponseStatus.SUCCESS;

    Iterable<AppInstance> appInstances = appInstanceRepository.findByOrganizationId(request.organization().getId());
    for (AppInstance appInstance : appInstances) {
      ImmutableDeleteAppInstance.Request deleteAppInstanceRequest = ImmutableDeleteAppInstance.Request.builder()
          .instanceId(appInstance.getId())
          .callProvider(true)
          .checkStatus(Optional.<AppInstance.InstantiationStatus>absent())
          .checkVersions(Optional.<long[]>absent())
          .notifyAdmins(false)
          .build();
      DeleteAppInstance.Status deleteAppInstanceStatus = deleteAppInstance.deleteInstance(deleteAppInstanceRequest, new DeleteAppInstance.Stats());
      switch (deleteAppInstanceStatus) {
        case PROVIDER_CALL_ERROR:
        case PROVIDER_STATUS_ERROR:
          logger.error("The provider has returned an error while calling him after deleting the app-instances related to organization {}",
              request.organization().getId());
          responseStatus = ResponseStatus.APP_INSTANCE_PROVIDER_ERROR;
          // It doesn't really matter: it will be deleted later by another cron task
          break;
        case DELETED_INSTANCE:
        case DELETED_LEFTOVERS:
        case NOTHING_TO_DELETE:
        default:
          // noop
      }
    }

    try {
      notifyAdmins(request.organization());
    } catch (Exception e) {
      // Don't fail if we can't notify
      logger.error("Error notifying admins after deleting the organization {}", request.organization().getId(), e);
    }

    return responseStatus;
  }

  private void notifyAdmins(Organization organization) {
    Notification notificationPrototype = new Notification();
    SoyMapData data = new SoyMapData();
    data.put(DeletedOrganizationMessageSoyTemplateInfo.ORGANIZATION_NAME, organization.getName());
    for (ULocale locale : LocaleHelper.SUPPORTED_LOCALES) {
      ULocale messageLocale = locale;
      if (LocaleHelper.DEFAULT_LOCALE.equals(locale)) {
        messageLocale = ULocale.ROOT;
      }
      notificationPrototype.getMessage().set(messageLocale, templateRenderer.renderAsString(new SoyTemplate(
          DeletedOrganizationSoyInfo.DELETED_ORGANIZATION_MESSAGE, locale, SanitizedContent.ContentKind.TEXT, data)));
    }

    Iterable<OrganizationMembership> admins = organizationMembershipRepository.getAdminsOfOrganization(organization.getId());
    for (OrganizationMembership admin : admins) {
      try {
        Notification notification = new Notification(notificationPrototype);
        notification.setUser_id(admin.getAccountId());
        notificationRepository.createNotification(notification);
      } catch (Exception e) {
        // Don't fail if we can't notify
        logger.error("Error notifying admin {} after deleting the organization {}", admin.getAccountId(), organization.getName(), e);
      }
    }
  }

  @Value.Immutable
  public static interface Request {
    Organization organization();

    Optional<Organization.Status> checkStatus();
  }

  public static enum ResponseStatus {
    SUCCESS,
    BAD_ORGANIZATION_STATUS,
    APP_INSTANCE_PROVIDER_ERROR
  }
}

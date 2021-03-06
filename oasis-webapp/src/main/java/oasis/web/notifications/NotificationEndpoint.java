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
package oasis.web.notifications;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.authn.AccessToken;
import oasis.model.notification.Notification;
import oasis.model.notification.NotificationRepository;
import oasis.web.authn.Authenticated;
import oasis.web.authn.Client;
import oasis.web.authn.ClientPrincipal;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/n")
@Authenticated
public class NotificationEndpoint {

  @Inject NotificationRepository notificationRepository;
  @Inject ServiceRepository serviceRepository;

  @Context SecurityContext securityContext;

  @POST
  @Path("/publish")
  @Consumes(MediaType.APPLICATION_JSON)
  @Client
  public Response publish(IncomingNotification incomingNotification) {
    String clientId = ((ClientPrincipal) securityContext.getUserPrincipal()).getClientId();

    Service service = serviceRepository.getService(incomingNotification.service_id);
    if (service == null || !clientId.equals(service.getInstance_id())) {
      return ResponseFactory.unprocessableEntity("Unknown service for the authenticated app-instance");
    }

    // TODO: check existence of users?

    Notification template = new Notification();
    template.setInstance_id(clientId);
    template.setService_id(incomingNotification.service_id);
    template.setMessage(incomingNotification.message);
    template.setAction_uri(incomingNotification.action_uri);
    template.setAction_label(incomingNotification.action_label);
    template.setTime(Instant.now());
    template.setStatus(Notification.Status.UNREAD);

    List<Notification> notifications = new ArrayList<>(incomingNotification.user_ids.length);
    for (String user_id : incomingNotification.user_ids) {
      Notification notification = new Notification(template);
      notification.setUser_id(user_id);
      notifications.add(notification);
    }

    notificationRepository.createNotifications(notifications);

    return ResponseFactory.NO_CONTENT;
  }

  @GET
  @Path("/{userId}/messages")
  @Produces(MediaType.APPLICATION_JSON)
  @OAuth
  public Response get(
      @PathParam("userId") String userId,
      @QueryParam("instance") String instanceId,
      @QueryParam("status") Notification.Status status
  ) {
    AccessToken accessToken = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken();
    if (!accessToken.getAccountId().equals(userId)) {
      return ResponseFactory.forbidden("Cannot read notifications for another user");
    }

    // TODO: rework NetworkRepository API wrt filtering
    if (instanceId == null) {
      if (!accessToken.isPortal()) {
        return ResponseFactory.forbidden("Cannot read all notifications for user");
      }
      return Response.ok()
          .entity(new GenericEntity<Iterable<Notification>>(
              status == null
                  ? notificationRepository.getNotifications(userId)
                  : notificationRepository.getNotifications(userId, status)
          ) {})
          .build();
    }

    if (!instanceId.equals(accessToken.getServiceProviderId()) && !accessToken.isPortal()) {
      return ResponseFactory.forbidden("Cannot read notifications for another app-instance");
    }
    return Response.ok()
        .entity(new GenericEntity<Iterable<Notification>>(
            status == null
                ? notificationRepository.getNotifications(userId, instanceId)
                : notificationRepository.getNotifications(userId, instanceId, status)
        ) {})
        .build();
  }

  @POST
  @Path("/{userId}/messages")
  @Consumes(MediaType.APPLICATION_JSON)
  @OAuth
  public Response post(
      @PathParam("userId") String userId,
      Mark mark
  ) {
    String oAuthUserId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    if (!oAuthUserId.equals(userId)) {
      return ResponseFactory.forbidden("Cannot change read status for another user's notifications");
    }

    notificationRepository.markNotifications(userId, ImmutableList.copyOf(mark.message_ids), mark.status);
    return ResponseFactory.NO_CONTENT;
  }

  static class Mark {

    @JsonProperty String[] message_ids;

    @JsonProperty Notification.Status status;
  }
}

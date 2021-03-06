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

import java.util.stream.Stream;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Streams;

import oasis.model.accounts.AccountRepository;
import oasis.model.accounts.UserAccount;
import oasis.model.applications.v2.AccessControlRepository;
import oasis.model.applications.v2.AppInstance;
import oasis.model.applications.v2.AppInstanceRepository;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;
import oasis.model.applications.v2.UserSubscription;
import oasis.model.applications.v2.UserSubscriptionRepository;
import oasis.model.i18n.LocalizableString;
import oasis.services.authz.AppAdminHelper;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.authn.Portal;
import oasis.web.utils.ResponseFactory;

@Path("/apps/subscriptions/user/{user_id}")
@Authenticated @OAuth
@Portal
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserSubscriptionEndpoint {
  @Inject UserSubscriptionRepository userSubscriptionRepository;
  @Inject ServiceRepository serviceRepository;
  @Inject AppInstanceRepository appInstanceRepository;
  @Inject AppAdminHelper appAdminHelper;
  @Inject AccessControlRepository accessControlRepository;
  @Inject AccountRepository accountRepository;
  @Inject EtagService etagService;

  @Context SecurityContext securityContext;
  @Context UriInfo uriInfo;

  @PathParam("user_id") String userId;

  @GET
  public Response getSubscriptions() {
    if (!userId.equals(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId())) {
      return ResponseFactory.forbidden("Cannot list subscriptions for another user");
    }

    Iterable<UserSubscription> subscriptions = userSubscriptionRepository.getUserSubscriptions(userId);
    return Response.ok()
        .entity(new GenericEntity<Stream<UserSub>>(Streams.stream(subscriptions).map(
            input -> {
              UserSub sub = new UserSub();
              sub.id = input.getId();
              sub.subscription_uri = uriInfo.getBaseUriBuilder().path(SubscriptionEndpoint.class).build(input.getId()).toString();
              sub.subscription_etag = etagService.getEtag(input).toString();
              sub.service_id = input.getService_id();
              final Service service = serviceRepository.getService(input.getService_id());
              sub.service_name = service == null ? null : service.getName();
              sub.subscription_type = input.getSubscription_type();
              sub.creator_id = MoreObjects.firstNonNull(input.getCreator_id(), input.getUser_id());
              // TODO: check access rights to the user name
              final UserAccount creator = accountRepository.getUserAccountById(sub.creator_id);
              sub.creator_name = creator == null ? null : creator.getDisplayName();
              return sub;
            })) {})
        .build();
  }

  @POST
  public Response subscribe(UserSubscription subscription) {
    if (subscription.getUser_id() != null && !userId.equals(subscription.getUser_id())) {
      return ResponseFactory.unprocessableEntity("user_id doesn't match URL");
    }
    subscription.setUser_id(userId);
    if (subscription.getSubscription_type() != UserSubscription.SubscriptionType.ORGANIZATION) {
      return subscribePersonal(subscription);
    } else {
      return subscribeOrganization(subscription);
    }
  }

  /** Called when the subscription_type is NOT ORGANIZATION. */
  private Response subscribePersonal(UserSubscription subscription) {
    subscription.setSubscription_type(UserSubscription.SubscriptionType.PERSONAL);
    if (!userId.equals(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId())) {
      return ResponseFactory.forbidden("Cannot create a personal subscription for another user");
    }
    Service service = serviceRepository.getService(subscription.getService_id());
    if (service == null) {
      return ResponseFactory.unprocessableEntity("Unknown service");
    }
    // a personal subscription can only target a public service, or one for which the user is an app_user
    if (!service.isVisible()) {
      AppInstance instance = appInstanceRepository.getAppInstance(service.getInstance_id());
      if (instance == null) {
        return ResponseFactory.build(Response.Status.INTERNAL_SERVER_ERROR, "Oops, service has no app instance");
      }
      if (accessControlRepository.getAccessControlEntry(instance.getId(), userId) == null
          && !appAdminHelper.isAdmin(userId, instance)) {
        return ResponseFactory.forbidden("Cannot subscribe to this service");
      }
    }
    return createSubscription(subscription);
  }

  /** Called when the subscription_type is ORGANIZATION. */
  private Response subscribeOrganization(UserSubscription subscription) {
    Service service = serviceRepository.getService(subscription.getService_id());
    if (service == null) {
      return ResponseFactory.unprocessableEntity("Unknown service");
    }
    AppInstance instance = appInstanceRepository.getAppInstance(service.getInstance_id());
    if (instance == null) {
      return ResponseFactory.build(Response.Status.INTERNAL_SERVER_ERROR, "Oops, service has no app instance");
    }
    if (Strings.isNullOrEmpty(instance.getProvider_id())) {
      return ResponseFactory.forbidden("Cannot create a non-personal subscription for a personal app instance");
    }
    if (!appAdminHelper.isAdmin(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId(), instance)) {
      return ResponseFactory.forbidden("Current user is not an app_admin for the service");
    }
    if (accessControlRepository.getAccessControlEntry(instance.getId(), userId) == null
        && !appAdminHelper.isAdmin(userId, instance)) {
      return ResponseFactory.unprocessableEntity("Target user is neither an app_admin or app_user for the service");
    }
    return createSubscription(subscription);
  }

  private Response createSubscription(UserSubscription subscription) {
    subscription.setCreator_id(((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId());
    subscription = userSubscriptionRepository.createUserSubscription(subscription);
    if (subscription == null) {
      return ResponseFactory.conflict("Subscription for that user and service already exists");
    }
    return Response.created(uriInfo.getBaseUriBuilder().path(SubscriptionEndpoint.class).build(subscription.getId()))
        .tag(etagService.getEtag(subscription))
        .entity(subscription)
        .build();
  }

  static class UserSub {
    @JsonProperty String id;
    @JsonProperty String subscription_uri;
    @JsonProperty String subscription_etag;
    @JsonProperty String service_id;
    @JsonProperty LocalizableString service_name;
    @JsonProperty UserSubscription.SubscriptionType subscription_type;
    @JsonProperty String creator_id;
    @JsonProperty String creator_name;
  }
}

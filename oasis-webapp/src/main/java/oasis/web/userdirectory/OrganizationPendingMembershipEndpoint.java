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
package oasis.web.userdirectory;

import java.util.stream.Stream;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Streams;

import oasis.model.directory.OrganizationMembership;
import oasis.model.directory.OrganizationMembershipRepository;
import oasis.services.etag.EtagService;
import oasis.web.authn.Authenticated;
import oasis.web.authn.OAuth;
import oasis.web.authn.OAuthPrincipal;
import oasis.web.utils.ResponseFactory;

@Path("/d/pending-memberships/org/{organization_id}")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated @OAuth
public class OrganizationPendingMembershipEndpoint {
  @Inject OrganizationMembershipRepository organizationMembershipRepository;
  @Inject EtagService etagService;

  @Context UriInfo uriInfo;
  @Context SecurityContext securityContext;

  @PathParam("organization_id") String organizationId;

  @GET
  public Response getPendingMemberships(
      @QueryParam("start") int start,
      @QueryParam("limit") int limit
  ) {
    String accountId = ((OAuthPrincipal) securityContext.getUserPrincipal()).getAccessToken().getAccountId();
    OrganizationMembership membership = organizationMembershipRepository.getOrganizationMembership(accountId, organizationId);
    if (membership == null || !membership.isAdmin()) {
      return ResponseFactory.forbidden("Current user is not an admin for the organization");
    }
    Iterable<OrganizationMembership> memberships = organizationMembershipRepository.getPendingMembersOfOrganization(organizationId, start, limit);
    return toResponse(memberships);
  }

  private Response toResponse(Iterable<OrganizationMembership> memberships) {
    return Response.ok()
        .entity(new GenericEntity<Stream<PendingOrgMembership>>(Streams.stream(memberships).map(
            organizationMembership -> {
              PendingOrgMembership membership = new PendingOrgMembership();
              membership.id = organizationMembership.getId();
              membership.pending_membership_uri = uriInfo.getBaseUriBuilder()
                  .path(PendingMembershipEndpoint.class)
                  .build(organizationMembership.getId())
                  .toString();
              membership.pending_membership_etag = etagService.getEtag(organizationMembership).toString();
              membership.email = organizationMembership.getEmail();
              membership.admin = organizationMembership.isAdmin();
              return membership;
            })) {})
        .build();
  }

  static class PendingOrgMembership {
    @JsonProperty String id;
    @JsonProperty String pending_membership_uri;
    @JsonProperty String pending_membership_etag;
    @JsonProperty String email;
    @JsonProperty boolean admin;
  }
}

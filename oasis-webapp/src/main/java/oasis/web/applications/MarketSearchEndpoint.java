/**
 * Ozwillo Kernel
 * Copyright (C) 2018  The Ozwillo Kernel Authors
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

import javax.annotation.Nullable;
import javax.ws.rs.Path;

import com.google.common.base.Strings;

import oasis.model.authn.AccessToken;
import oasis.model.bootstrap.ClientIds;

@Path("/m/search")
public class MarketSearchEndpoint extends AbstractMarketSearchEndpoint {
  @Nullable
  @Override
  protected String getPortal(@Nullable AccessToken accessToken, @Nullable String portal) {
    if (!Strings.isNullOrEmpty(portal)) {
      return portal;
    }
    // Behave like the canonical portal for non-portal (including anonymous) clients.
    // XXX: should this endpoint only be accessible to portals? (or anonymous clients)
    return accessToken != null && accessToken.isPortal() ? accessToken.getServiceProviderId() : ClientIds.PORTAL;
  }
}

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

import java.util.Iterator;
import java.util.stream.Stream;

import javax.ws.rs.Path;

import com.google.common.collect.Streams;

import oasis.model.applications.v2.ImmutableCatalogEntryRepository;
import oasis.model.applications.v2.SimpleCatalogEntry;
import oasis.model.authn.AccessToken;
import oasis.model.bootstrap.ClientIds;

@Path("/m/search")
public class MarketSearchEndpoint extends AbstractMarketSearchEndpoint {
  @Override
  protected Iterator<SimpleCatalogEntry> doSearch(
      AccessToken accessToken, ImmutableCatalogEntryRepository.SearchRequest.Builder requestBuilder) {
    // TODO: add information about apps the user has already "bought" (XXX: limit to client_id=portal! to avoid leaking data)
    Iterable<SimpleCatalogEntry> results = catalogEntryRepository.search(requestBuilder
        // Behave like the canonical portal for non-portal clients
        // XXX: should this endpoint only be accessible to portals?
        .portal(accessToken.isPortal() ? accessToken.getServiceProviderId() : ClientIds.PORTAL)
        .build());
    if (accessToken.isPortal()) {
      return results.iterator();
    } else {
      // hide 'portals' to non-portal clients
      // XXX: should this endpoint only be accessible to portals?
      return Streams.stream(results)
          .map(entry -> {
            entry.setPortals(null);
            return entry;
          })
          .iterator();
    }
  }
}

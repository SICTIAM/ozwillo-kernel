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
package oasis.jongo.applications.v2;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Longs;
import com.mongodb.DuplicateKeyException;

import oasis.jongo.JongoBootstrapper;
import oasis.model.InvalidVersionException;
import oasis.model.applications.v2.Service;
import oasis.model.applications.v2.ServiceRepository;

public class JongoServiceRepository implements ServiceRepository, JongoBootstrapper {
  private static final Logger logger = LoggerFactory.getLogger(ServiceRepository.class);

  static final String SERVICES_COLLECTION = "services";

  private final Jongo jongo;

  @Inject
  JongoServiceRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  private MongoCollection getServicesCollection() {
    return jongo.getCollection(SERVICES_COLLECTION);
  }

  @Override
  public Service createService(Service service) {
    JongoService jongoService = new JongoService(service);
    jongoService.initCreated();
    try {
      getServicesCollection().insert(jongoService);
    } catch (DuplicateKeyException e) {
      return null;
    }
    return jongoService;
  }

  @Override
  public Service getService(String serviceId) {
    return getServicesCollection()
        .findOne("{ id: # }", serviceId)
        .as(JongoService.class);
  }

  public Iterable<JongoService> getAllInCatalog() {
    return getServicesCollection()
        .find("{ visible: true, $or: [ { restricted: { $exists: 0 } }, { restricted: false } ], status: { $ne: # } }", Service.Status.NOT_AVAILABLE)
        .as(JongoService.class);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Iterable<Service> getServicesOfInstance(String instanceId) {
    return (Iterable<Service>) (Iterable<?>) getServicesCollection()
        .find("{ instance_id: # }", instanceId)
        .as(JongoService.class);
  }

  @Override
  public Service getServiceByRedirectUri(String instanceId, String redirect_uri) {
    return getServicesCollection()
        .findOne("{ instance_id: #, redirect_uris: # }", instanceId, redirect_uri)
        .as(JongoService.class);
  }

  @Override
  public Service getServiceByPostLogoutRedirectUri(String instanceId, String post_logout_redirect_uri) {
    return getServicesCollection()
        .findOne("{ instance_id: #, post_logout_redirect_uris: # }", instanceId, post_logout_redirect_uri)
        .as(JongoService.class);
  }

  @Override
  public boolean deleteService(String serviceId, long[] versions) throws InvalidVersionException {
    int n = getServicesCollection()
        .remove("{ id: #, modified: { $in: # } }", serviceId, Longs.asList(versions))
        .getN();

    if (n == 0) {
      if (getServicesCollection().count("{ id: # }", serviceId) > 0) {
        throw new InvalidVersionException("service", serviceId);
      }
      return false;
    }

    if (n > 1) {
      logger.error("Deleted {} services with ID {}, that shouldn't have happened", n, serviceId);
    }
    return true;
  }

  @Override
  @SuppressWarnings("deprecation")
  public Service updateService(Service service, long[] versions) throws InvalidVersionException {
    String serviceId = service.getId();
    Preconditions.checkArgument(!Strings.isNullOrEmpty(serviceId));
    // Copy to get the modified field, then reset ID (not copied over) to make sure we won't generate a new one
    service = new JongoService(service);
    service.setId(serviceId);
    // XXX: don't allow updating those properties (should we return an error if attempted?)
    service.setLocal_id(null);
    service.setInstance_id(null);
    service.setProvider_id(null);
    service.setPortals(null);
    // FIXME: allow unsetting properties; for now only support visible/restricted
    Map<String, Boolean> unsetObject = new LinkedHashMap<>();
    if (service.getVisible() == null) {
      unsetObject.put("visible", true);
    }
    if (service.getRestricted() == null) {
      unsetObject.put("restricted", true);
    }
    try {
      service = getServicesCollection()
          .findAndModify("{ id: #, modified: { $in: # } }", serviceId, Longs.asList(versions))
          // MongoDB rejects empty modifiers: https://jira.mongodb.org/browse/SERVER-12266
          .with(unsetObject.isEmpty() ? "{ $set: # }" : "{ $set: #, $unset: # }",
              unsetObject.isEmpty() ? new Object[]{service} : new Object[]{service, unsetObject})
          .returnNew()
          .as(JongoService.class);
    } catch (DuplicateKeyException e) {
      throw new oasis.model.DuplicateKeyException();
    }
    if (service == null) {
      if (getServicesCollection().count("{ id: # }", serviceId) > 0) {
        throw new InvalidVersionException("service", serviceId);
      }
      return null;
    }
    return service;
  }

  @Override
  public int deleteServicesOfInstance(String instanceId) {
    return getServicesCollection()
        .remove("{ instance_id: # }", instanceId)
        .getN();
  }

  @Override
  public int changeServicesStatusForInstance(String instanceId, Service.Status status) {
    return getServicesCollection()
        .update("{ instance_id: # }", instanceId)
        .multi()
        .with("{ $set: { status: #, modified: # } }", status, System.currentTimeMillis())
        .getN();
  }

  @Override
  public Service addPortal(String serviceId, String portalId, long[] versions) throws InvalidVersionException {
    return JongoCatalogEntryRepository.addPortal(getServicesCollection(), JongoService.class, "service", serviceId, portalId, versions);
  }

  @Override
  public Service removePortal(String serviceId, String portalId, long[] versions) throws InvalidVersionException {
    return JongoCatalogEntryRepository.removePortal(getServicesCollection(), JongoService.class, "service", serviceId, portalId, versions);
  }

  @Override
  public void bootstrap() {
    getServicesCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getServicesCollection().ensureIndex("{ instance_id: 1, local_id: 1 }", "{ unique: 1, sparse: 1 }");

    getServicesCollection().ensureIndex("{ instance_id: 1, redirect_uris: 1 }", "{ unique: 1, sparse: 1 }");
    // We cannot make this index unique as that would rule out having several services,
    // for the same instance, without post_logout_redirect_uri at all.
    // XXX: we should probably move post_logout_redirect_uris to app_instances eventually.
    getServicesCollection().ensureIndex("{ instance_id: 1, post_logout_redirect_uris: 1 }", "{ sparse: 1 }");
  }
}

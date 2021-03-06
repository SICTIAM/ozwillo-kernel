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
package oasis.jongo.authn;

import java.time.Instant;
import java.util.Date;

import javax.inject.Inject;

import org.jongo.Jongo;
import org.jongo.MongoCollection;

import com.mongodb.DuplicateKeyException;

import oasis.jongo.JongoBootstrapper;
import oasis.model.authn.JtiRepository;

public class JongoJtiRepository implements JtiRepository, JongoBootstrapper {
  private final Jongo jongo;

  @Inject JongoJtiRepository(Jongo jongo) {
    this.jongo = jongo;
  }

  protected MongoCollection getUsedJtisCollection() {
    return jongo.getCollection("used_jtis");
  }

  @Override
  public boolean markAsUsed(String jti, Instant expirationTime) {
    try {
      // TODO: Pass directly the instance of Instant
      getUsedJtisCollection().insert("{ id: #, expirationTime: # }", jti, Date.from(expirationTime));
    } catch (DuplicateKeyException e) {
      return false;
    }
    return true;
  }

  @Override
  public void bootstrap() {
    getUsedJtisCollection().ensureIndex("{ id: 1 }", "{ unique: 1 }");
    getUsedJtisCollection().ensureIndex("{ expirationTime: 1 }", "{ background: 1, expireAfterSeconds: 0 }");
  }
}

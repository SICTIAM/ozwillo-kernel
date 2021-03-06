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
package oasis.jongo.etag;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.EntityTag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.primitives.Longs;

import oasis.services.etag.EtagService;

public class JongoEtagService implements EtagService {

  private static final Logger logger = LoggerFactory.getLogger(JongoEtagService.class);

  @Override
  public boolean hasEtag(Object o, long[] versions) {
    Preconditions.checkArgument(o instanceof HasModified);
    return Longs.contains(versions, ((HasModified) o).getModified());
  }

  @Override
  public EntityTag getEtag(Object o) {
    Preconditions.checkArgument(o instanceof HasModified);
    return new EntityTag(Long.toString(((HasModified) o).getModified()));
  }

  @Override
  public long[] parseEtag(List<EntityTag> ifMatch) {

    if (ifMatch == null || ifMatch.isEmpty()) {
      return new long[0];
    }

    List<Long> etags = new ArrayList<>();
    for (EntityTag etag : ifMatch) {
      try {
        etags.add(Long.valueOf(etag.getValue()));
      } catch (NumberFormatException nfe) {
        logger.debug("Invalid etag '{}'", etag, nfe);
      }
    }

    return Longs.toArray(etags);
  }
}

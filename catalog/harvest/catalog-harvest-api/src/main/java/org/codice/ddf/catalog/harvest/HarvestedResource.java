/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.catalog.harvest;

import ddf.catalog.resource.Resource;
import java.net.URI;
import java.util.Map;

/** A resource harvested from an external data store. */
public interface HarvestedResource {

  Map<String, Object> getProperties();

  /**
   * A globally unique identifier for this {@code HarvestedResource}.
   *
   * @return a globally unique identifier
   */
  String getCorrelationId();

  /**
   * The location of this {@code HarvestedResource}.
   *
   * @return a uri
   */
  URI getUri();

  /**
   * A {@link Resource} representing the contents of this {@code HarvestedResource}.
   *
   * @return the {@link Resource}
   */
  Resource getResource();
}

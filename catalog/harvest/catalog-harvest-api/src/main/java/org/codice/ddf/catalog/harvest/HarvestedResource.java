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
import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * A resource harvested from an external data store.
 *
 * <p><b> This code is experimental. While this class is functional and tested, it may change or be
 * removed in a future version of the library. </b>
 */
public interface HarvestedResource extends Resource {

  /**
   * The location of this {@code HarvestedResource}.
   *
   * @return a uri
   */
  URI getUri();

  /**
   * @return a {@link Map} of {@link ddf.catalog.data.Attribute} names to a {@link List} of override
   *     values, or an empty {@link Map} if no overrides were specified
   */
  Map<String, List<String>> getAttributeOverrides();

  /**
   * A {@code Map} of properties associated with this {@code HarvestedResource}.
   *
   * @return a map of properties
   */
  Map<String, Serializable> getProperties();
}

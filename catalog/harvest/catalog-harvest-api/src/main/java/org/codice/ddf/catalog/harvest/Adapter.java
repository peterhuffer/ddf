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

/**
 * An {@code Adapter} is responsible for persisting {@link HarvestedResource}s to some data store.
 */
public interface Adapter {

  /**
   * Stores the {@link HarvestedResource} in this {@code Adapter}'s data store.
   *
   * @param resource the {@link HarvestedResource} to store
   * @return a unique identifier for the created data, or null if resource couldn't be created
   */
  String create(HarvestedResource resource);

  /**
   * Updates the {@link HarvestedResource} in this {@code Adapter}'s data store.
   *
   * @param resource the {@link HarvestedResource} to to update the existing data in the store
   * @param id the id for the existing data to update
   * @return a unique identifier for the updated data, or null if resource couldn't be updated
   */
  String update(HarvestedResource resource, String id);

  /**
   * Deletes data from this {@code Adapter}'s data store.
   *
   * @param id unique identifier for the data to delete
   * @return true if the data was deleted, otherwise false
   */
  boolean delete(String id);
}

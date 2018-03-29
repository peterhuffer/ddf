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

/** Responds to events from a {@link Harvester}. */
public interface Listener {

  /**
   * Called when a {@link Harvester} receives a newly created file on a remote server.
   *
   * @param resource the {@link HarvestedResource} created on the remote server
   */
  void onCreate(HarvestedResource resource);

  /**
   * Called when a {@link Harvester} receives an updated file on a remote server.
   *
   * @param resource the {@link HarvestedResource} updated on the remote server
   */
  void onUpdate(HarvestedResource resource);

  /**
   * Called when a {@link Harvester} receives a deleted file on a remote server. On a delete event,
   * the resource will not be available, as it has already been deleted. {@link Listener}s should
   * use the {@link HarvestedResource#getCorrelationId()} method to identify the current resource
   * being processed.
   *
   * @param resource the {@link HarvestedResource} deleted on the remote server
   */
  void onDelete(HarvestedResource resource);
}

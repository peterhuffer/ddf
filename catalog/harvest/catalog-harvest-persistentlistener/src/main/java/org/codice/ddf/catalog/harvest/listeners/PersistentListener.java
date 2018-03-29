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
package org.codice.ddf.catalog.harvest.listeners;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.catalog.harvest.Adapter;
import org.codice.ddf.catalog.harvest.HarvestedResource;
import org.codice.ddf.catalog.harvest.Listener;
import org.codice.ddf.catalog.harvest.common.FileSystemPersistenceProvider;

public class PersistentListener implements Listener {

  private final Adapter adapter;

  private final FileSystemPersistenceProvider persistenceProvider;

  public PersistentListener(Adapter adapter, String persistenceName) {
    this.adapter = adapter;
    persistenceProvider =
        new FileSystemPersistenceProvider(
            "harvest/persistent/" + DigestUtils.sha1Hex(persistenceName));
  }

  @Override
  public void onCreate(HarvestedResource resource) {
    String id = adapter.create(resource);

    if (StringUtils.isNotEmpty(id)) {
      persistenceProvider.store(resource.getCorrelationId(), id);
    }
  }

  @Override
  public void onUpdate(HarvestedResource resource) {
    String resourceId = getResourceId(resource.getCorrelationId());

    if (StringUtils.isNotEmpty(resourceId)) {
      String newResourceId = adapter.update(resource, resourceId);

      if (StringUtils.isNotEmpty(newResourceId)) {
        persistenceProvider.store(resource.getCorrelationId(), newResourceId);
      }
    }
  }

  @Override
  public void onDelete(HarvestedResource resource) {
    String correlationId = resource.getCorrelationId();
    String resourceId = getResourceId(correlationId);

    if (StringUtils.isNotEmpty(resourceId) && adapter.delete(resourceId)) {
      persistenceProvider.delete(correlationId);
    }
  }

  private String getResourceId(String key) {
    if (persistenceProvider.loadAllKeys().contains(key)) {
      return (String) persistenceProvider.loadFromPersistence(key);
    }

    return null;
  }
}

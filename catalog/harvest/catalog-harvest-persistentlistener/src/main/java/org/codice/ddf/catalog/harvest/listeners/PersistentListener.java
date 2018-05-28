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

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.codice.ddf.catalog.harvest.HarvestException;
import org.codice.ddf.catalog.harvest.HarvestedResource;
import org.codice.ddf.catalog.harvest.Harvester;
import org.codice.ddf.catalog.harvest.Listener;
import org.codice.ddf.catalog.harvest.StorageAdaptor;
import org.codice.ddf.catalog.harvest.common.FileSystemPersistenceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Listener} that tracks which {@link HarvestedResource}s have already been processed by a
 * {@link org.codice.ddf.catalog.harvest.Harvester}.
 */
public class PersistentListener implements Listener {

  private static final Logger LOGGER = LoggerFactory.getLogger(PersistentListener.class);

  private final StorageAdaptor adaptor;

  private final PersistentListenerServiceNotifier persistentListenerServiceNotifier;

  private FileSystemPersistenceProvider persistenceProvider;

  private String id;

  /**
   * Constructor.
   *
   * @param adaptor the {@link StorageAdaptor} to send CUD events to
   */
  public PersistentListener(
      StorageAdaptor adaptor, PersistentListenerServiceNotifier persistentListenerServiceNotifier) {
    this.adaptor = adaptor;
    this.persistentListenerServiceNotifier = persistentListenerServiceNotifier;
  }

  public void init() {
    persistenceProvider =
        new FileSystemPersistenceProvider(
            "harvest/persistent/"
                + Hashing.sha256().hashString(id, StandardCharsets.UTF_8).toString());
    persistentListenerServiceNotifier.addListener(this);
  }

  @SuppressWarnings(
      "squid:S1172" /* The code parameter is required in blueprint-cm-1.0.7. See https://issues.apache.org/jira/browse/ARIES-1436. */)
  public void destroy(int code) {
    persistentListenerServiceNotifier.removeListener(this);
  }

  @Override
  public void onCreate(HarvestedResource resource) {
    String key =
        Hashing.sha256()
            .hashString(resource.getUri().toASCIIString(), StandardCharsets.UTF_8)
            .toString();
    if (resourceNotCreated(key)) {
      String resourceId = null;
      try {
        resourceId = adaptor.create(resource);
      } catch (HarvestException e) {
        LOGGER.debug("Failed to create resource [{}].", resource.getName(), e);
      }

      if (StringUtils.isNotEmpty(resourceId)) {
        persistenceProvider.store(key, resourceId);
      }
    } else {
      LOGGER.debug("Already created resource [{}]. Doing nothing", resource.getName());
    }
  }

  @Override
  public void onUpdate(HarvestedResource resource) {
    String key =
        Hashing.sha256()
            .hashString(resource.getUri().toASCIIString(), StandardCharsets.UTF_8)
            .toString();
    String resourceId = getResourceId(key);

    if (StringUtils.isNotEmpty(resourceId)) {
      try {
        adaptor.update(resource, resourceId);
      } catch (HarvestException e) {
        LOGGER.debug(
            "Failed to update resource [{}] using id [{}].", resource.getName(), resourceId, e);
      }
    }
  }

  @Override
  public void onDelete(String uri) {
    String key = Hashing.sha256().hashString(uri, StandardCharsets.UTF_8).toString();
    String resourceId = getResourceId(key);

    if (StringUtils.isNotEmpty(resourceId)) {
      try {
        adaptor.delete(resourceId);
        persistenceProvider.delete(key);
      } catch (HarvestException e) {
        LOGGER.debug(
            "Failed to delete resource with uri [{}] using id [{}]. Resources in this listener's cache may be out of sync.",
            uri,
            resourceId,
            e);
      }
    }
  }

  /**
   * Invoked when updates are made to the configuration of existing WebDav monitors. This method is
   * invoked by the container as specified by the update-strategy and update-method attributes in
   * Blueprint XML file.
   *
   * @param properties - properties map for the configuration
   */
  public void updateCallback(Map<String, Object> properties) {
    if (MapUtils.isNotEmpty(properties)) {
      setId(getPropertyAs(properties, "id", String.class));
    }
  }

  public void registerHarvester(Harvester harvester) {
    if (harvester.getId().equals(id)) {
      // This harvester is our corresponding match for this listener
      harvester.registerListener(this);
    }
  }

  public void unregisterHarvester(Harvester harvester) {
    if (harvester.getId().equals(id)) {
      // This harvester is our corresponding match for this listener
      harvester.unregisterListener(this);
    }
  }

  private <T> T getPropertyAs(Map<String, Object> properties, String key, Class<T> clazz) {
    Object property = properties.get(key);
    if (clazz.isInstance(property)) {
      return clazz.cast(property);
    }

    throw new IllegalArgumentException(
        String.format(
            "Received invalid configuration value of [%s] for property [%s]. Expected type of [%s]",
            property, key, clazz.getName()));
  }

  public void setId(String id) {
    this.id = id;
  }

  private boolean resourceNotCreated(String resourcePid) {
    return StringUtils.isEmpty(getResourceId(resourcePid));
  }

  private String getResourceId(String key) {
    if (persistenceProvider.loadAllKeys().contains(key)) {
      return (String) persistenceProvider.loadFromPersistence(key);
    }

    return null;
  }
}

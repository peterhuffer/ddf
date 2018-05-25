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
package org.codice.ddf.catalog.harvest.file;

import com.google.common.hash.Hashing;
import ddf.catalog.Constants;
import java.io.File;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.codice.ddf.catalog.harvest.Listener;
import org.codice.ddf.catalog.harvest.StorageAdaptor;
import org.codice.ddf.catalog.harvest.common.FileSystemPersistenceProvider;
import org.codice.ddf.catalog.harvest.common.PollingHarvester;
import org.codice.ddf.catalog.harvest.listeners.PersistentListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.codice.ddf.catalog.harvest.Harvester} which polls a configured directory for
 * changes.
 */
public class DirectoryHarvester extends PollingHarvester {

  private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryHarvester.class);

  private static final long DEFAULT_POLL_INTERVAL = 5;

  private final Set<Listener> listeners = Collections.synchronizedSet(new HashSet<>());

  private final StorageAdaptor adaptor;

  private Map<String, Serializable> attributeOverrides = new HashMap<>();

  private String directoryPath;

  private File harvestFile;

  private FileSystemPersistenceProvider fileSystemPersistenceProvider;

  private FileAlterationObserver fileAlterationObserver;

  private FileAlterationListenerAdaptor fileListener;

  private String persistenceKey;

  /**
   * Constructor. Does not start polling.
   *
   * @param adaptor {@link StorageAdaptor} defining out content is stored
   */
  public DirectoryHarvester(StorageAdaptor adaptor) {
    super(DEFAULT_POLL_INTERVAL);
    this.adaptor = adaptor;
    fileSystemPersistenceProvider = new FileSystemPersistenceProvider("harvest/directory");
    fileListener = new DirectoryHarvesterListenerAdaptor(listeners);
  }

  public void init() {
    harvestFile = new File(directoryPath);
    validateDirectory(directoryPath);
    persistenceKey = Hashing.sha256().hashString(directoryPath, StandardCharsets.UTF_8).toString();

    registerListener(new PersistentListener(adaptor, persistenceKey));
    fileAlterationObserver = getCachedObserverOrCreate(persistenceKey, directoryPath);
    super.init();
  }

  public void destroy(int code) {
    super.destroy();
  }

  private FileAlterationObserver getCachedObserverOrCreate(String key, String dir) {
    if (fileSystemPersistenceProvider.loadAllKeys().contains(key)) {
      LOGGER.trace("existing file observer for persistence key [{}] found, loading observer", key);
      return (FileAlterationObserver) fileSystemPersistenceProvider.loadFromPersistence(key);
    }

    LOGGER.trace("no existing file observer for persistence key [{}], creating new observer", key);
    return new FileAlterationObserver(dir);
  }

  @Override
  public void poll() {
    fileAlterationObserver.addListener(fileListener);
    fileAlterationObserver.checkAndNotify();
    // Remove listener before persisting to file system since it is not serializable
    fileAlterationObserver.removeListener(fileListener);
    fileSystemPersistenceProvider.store(persistenceKey, fileAlterationObserver);
  }

  @Override
  public void registerListener(Listener listener) {
    if (!listeners.isEmpty()) {
      throw new IllegalArgumentException(
          "Only 1 registered listener is currently supported for this harvester.");
    }
    listeners.add(listener);
  }

  @Override
  public void unregisterListener(Listener listener) {
    listeners.remove(listener);
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
      setDirectoryPath(getPropertyAs(properties, "directoryPath", String.class));

      Object o = properties.get(Constants.ATTRIBUTE_OVERRIDES_KEY);
      if (o instanceof String[]) {
        String[] incomingAttrOverrides = (String[]) o;
        setAttributeOverrides(Arrays.asList(incomingAttrOverrides));
      }

      destroy(0);
      init();
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

  private void validateDirectory(String dir) {
    try {
      if (!harvestFile.exists()) {
        LOGGER.warn("File [{}] does not exist.", dir);
        throw new IllegalArgumentException(String.format("File [%s] does not exist.", dir));
      }

      if (!harvestFile.isDirectory()) {
        LOGGER.warn("File [{}] is not a directory.", dir);
        throw new IllegalArgumentException(String.format("File [%s] is not a directory.", dir));
      }

      if (!harvestFile.canRead()) {
        LOGGER.warn("Insufficient read privileges from [{}].", dir);
        throw new IllegalArgumentException(
            String.format("Insufficient read privileges from [%s].", dir));
      }
    } catch (AccessControlException e) {
      throw new IllegalArgumentException(
          String.format(
              "Directory [%s] cannot be accessed. Do security manager permissions need to be added?",
              dir),
          e);
    }
  }

  public void setAttributeOverrides(List<String> incomingAttrOverrides) {
    attributeOverrides.clear();

    for (String keyValuePair : incomingAttrOverrides) {
      String[] parts = keyValuePair.split("=");

      if (parts.length != 2) {
        throw new IllegalArgumentException(
            String.format("Invalid attribute override key value pair of [%s].", keyValuePair));
      }

      attributeOverrides.put(parts[0], parts[1]);
    }
  }

  public void setDirectoryPath(String directoryPath) {
    this.directoryPath = stripEndingSlash(directoryPath);
  }

  /**
   * Strips the trailing slash from the harvest location, if it exists. This will treat, for
   * example, "/foo/bar" and "/foo/bar/" the same from a persistence tracking standpoint.
   *
   * @param location harvest location
   * @return new string with strip slashed
   */
  private String stripEndingSlash(String location) {
    if (location.endsWith("/")) {
      return location.substring(0, location.length() - 1);
    }
    return location;
  }
}

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

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.codice.ddf.catalog.harvest.Listener;
import org.codice.ddf.catalog.harvest.common.FileSystemPersistenceProvider;
import org.codice.ddf.catalog.harvest.common.PollingHarvester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectoryHarvester extends PollingHarvester {

  private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryHarvester.class);

  private static final long DEFAULT_POLL_INTERVAL = 5000;

  private final Set<Listener> listeners = new HashSet<>();

  private final File harvestFile;

  private final FileSystemPersistenceProvider fileSystemPersistenceProvider;

  private final FileAlterationObserver fileAlterationObserver;

  private final FileAlterationListenerAdaptor fileListener;

  private final String persistenceKey;

  /**
   * Creates and starts a {@link org.codice.ddf.catalog.harvest.Harvester} that harvests a
   * directory.
   *
   * @param dir an absolute path to a directory
   */
  public DirectoryHarvester(String dir) {
    this(dir, Collections.emptySet());
  }

  /**
   * Creates and starts a {@link org.codice.ddf.catalog.harvest.Harvester} that harvests a
   * directory.
   *
   * @param dir an absolute path to a directory
   * @param listeners initial set of {@link Listener}s
   */
  public DirectoryHarvester(String dir, Set<Listener> listeners) {
    this(dir, DEFAULT_POLL_INTERVAL, listeners);
  }

  public DirectoryHarvester(String dir, long pollInterval, Set<Listener> inititialListeners) {
    super(pollInterval);
    harvestFile = new File(dir);
    validateDirectory(dir);
    persistenceKey = DigestUtils.sha1Hex(dir);

    listeners.addAll(inititialListeners);
    fileSystemPersistenceProvider = new FileSystemPersistenceProvider("harvest/directory");
    fileAlterationObserver = getCachedObserverOrCreate(persistenceKey, dir);
    fileListener = new DirectoryHarvesterListenerAdaptor(listeners);

    super.init();
  }

  private FileAlterationObserver getCachedObserverOrCreate(String key, String dir) {
    FileAlterationObserver observer = null;
    if (fileSystemPersistenceProvider.loadAllKeys().contains(key)) {
      LOGGER.debug("existing file observer for persistence key [{}] found, loading observer", key);
      observer = (FileAlterationObserver) fileSystemPersistenceProvider.loadFromPersistence(key);
    }

    if (observer == null) {
      LOGGER.debug(
          "no existing file observer for persistence key [{}], creating new observer", key);
      observer = new FileAlterationObserver(dir);
    }

    return observer;
  }

  @Override
  public void poll() {
    fileAlterationObserver.addListener(fileListener);
    fileAlterationObserver.checkAndNotify();
    fileAlterationObserver.removeListener(fileListener);
    fileSystemPersistenceProvider.store(persistenceKey, fileAlterationObserver);
  }

  @Override
  public void registerListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void unregisterListener(Listener listener) {
    listeners.remove(listener);
  }

  private void validateDirectory(String dir) {
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
  }
}

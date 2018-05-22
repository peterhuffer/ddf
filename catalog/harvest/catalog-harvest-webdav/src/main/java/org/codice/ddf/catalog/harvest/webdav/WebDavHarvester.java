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
package org.codice.ddf.catalog.harvest.webdav;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.google.common.hash.Hashing;
import ddf.security.common.audit.SecurityLogger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.event.EventListenerSupport;
import org.codice.ddf.catalog.harvest.HarvestedResource;
import org.codice.ddf.catalog.harvest.Harvester;
import org.codice.ddf.catalog.harvest.Listener;
import org.codice.ddf.catalog.harvest.common.FileSystemPersistenceProvider;
import org.codice.ddf.catalog.harvest.common.HarvestedFile;
import org.codice.ddf.catalog.harvest.common.PollingHarvester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Polls an HTTP WebDav address. */
public class WebDavHarvester extends PollingHarvester {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebDavHarvester.class);

  private final EventListenerSupport<Listener> listeners =
      EventListenerSupport.create(Listener.class);

  private final Sardine sardine = SardineFactory.begin();

  private final String persistentKey;

  private final DavAlterationObserver observer;

  private final FileSystemPersistenceProvider persistenceProvider;

  private final HarvestedResourceListener webdavListener;

  /**
   * Creates a WebDav {@link Harvester} which will harvest products from the provided address.
   *
   * @param address http URL WebDav address
   * @param initialListeners {@link Listener}s to register to this harvester
   */
  public WebDavHarvester(String address, Set<Listener> initialListeners) {
    super(5L);
    Validate.notEmpty(address, "Argument address may not be empty");

    persistenceProvider = new FileSystemPersistenceProvider("harvest/webdav");
    webdavListener = new HarvestedResourceListener();

    initialListeners.forEach(this::registerListener);
    persistentKey = Hashing.sha256().hashString(address, StandardCharsets.UTF_8).toString();
    observer = getCachedObserverOrCreate(persistentKey, address);

    super.init();
  }

  private DavAlterationObserver getCachedObserverOrCreate(String key, String rootEntryLocation) {
    if (persistenceProvider.loadAllKeys().contains(key)) {
      LOGGER.trace(
          "existing webdav observer for persistence key [{}] found, loading observer", key);
      return (DavAlterationObserver) persistenceProvider.loadFromPersistence(key);
    }

    LOGGER.trace(
        "no existing webdav observer for persistence key [{}], creating new observer", key);
    return new DavAlterationObserver(new DavEntry(rootEntryLocation));
  }

  @Override
  public void poll() {
    observer.addListener(webdavListener);
    observer.checkAndNotify(sardine);
    // Remove listener before persisting to file system since it is not serializable
    observer.removeListener(webdavListener);
    persistenceProvider.store(persistentKey, observer);
  }

  @Override
  public void registerListener(Listener listener) {
    listeners.addListener(listener, false);
  }

  @Override
  public void unregisterListener(Listener listener) {
    listeners.removeListener(listener);
  }

  private class HarvestedResourceListener implements EntryAlterationListener {
    @Override
    public void onDirectoryCreate(DavEntry entry) {
      // noop
    }

    @Override
    public void onFileCreate(DavEntry entry) {
      createHarvestedResource(entry)
          .ifPresent(harvestedResource -> listeners.fire().onCreate(harvestedResource));
    }

    @Override
    public void onDirectoryChange(DavEntry entry) {
      // noop
    }

    @Override
    public void onFileChange(DavEntry entry) {
      createHarvestedResource(entry)
          .ifPresent(harvestedResource -> listeners.fire().onUpdate(harvestedResource));
    }

    @Override
    public void onDirectoryDelete(DavEntry entry) {
      // noop
    }

    @Override
    public void onFileDelete(DavEntry entry) {
      listeners.fire().onDelete(entry.getLocation());
    }

    private Optional<HarvestedResource> createHarvestedResource(DavEntry entry) {
      File file;
      try {
        file = entry.getFile(SardineFactory.begin());
      } catch (IOException e) {
        LOGGER.debug(
            "Error retrieving dav file [{}]. File won't be processed.", entry.getLocation(), e);
        return Optional.empty();
      }

      try {
        SecurityLogger.audit("Opening file {}", file.toPath());
        return Optional.of(
            new HarvestedFile(new FileInputStream(file), file.getName(), entry.getLocation()));
      } catch (FileNotFoundException e) {
        LOGGER.debug(
            "Failed to get input stream from file [{}]. Event will not be sent to listener",
            file.toURI());
        return Optional.empty();
      }
    }
  }
}

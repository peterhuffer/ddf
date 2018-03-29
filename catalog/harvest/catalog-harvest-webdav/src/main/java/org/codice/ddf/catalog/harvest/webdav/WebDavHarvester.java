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
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.Validate;
import org.codice.ddf.catalog.harvest.Harvester;
import org.codice.ddf.catalog.harvest.Listener;
import org.codice.ddf.catalog.harvest.common.FileSystemPersistenceProvider;
import org.codice.ddf.catalog.harvest.common.PollingHarvester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Polls an HTTP WebDav address. */
public class WebDavHarvester extends PollingHarvester {

  private static final Logger LOGGER = LoggerFactory.getLogger(WebDavHarvester.class);

  private final Set<Listener> listeners = new HashSet<>();

  private final Sardine sardine = SardineFactory.begin();

  private String persistentKey;

  private DavAlterationObserver observer;

  private FileSystemPersistenceProvider persistenceProvider;

  private HarvestedResourceListener webdavListener;

  /**
   * Creates a WebDav {@link Harvester} which will harvest products from the provided address.
   *
   * @param address http URL WebDav address
   */
  public WebDavHarvester(String address) {
    super(5L);
    Validate.notEmpty(address, "address {location} cannot be empty");

    persistenceProvider = new FileSystemPersistenceProvider("harvest/webdav");
    webdavListener = new HarvestedResourceListener();

    persistentKey = DigestUtils.sha1Hex(address);
    observer = getCachedObserverOrCreate(persistentKey, address);

    super.init();
  }

  private DavAlterationObserver getCachedObserverOrCreate(String key, String rootEntryLocation) {
    DavAlterationObserver davObserver = null;
    if (persistenceProvider.loadAllKeys().contains(key)) {
      LOGGER.debug(
          "existing webdav observer for persistence key [{}] found, loading observer", key);
      observer = (DavAlterationObserver) persistenceProvider.loadFromPersistence(key);
    }

    if (davObserver == null) {
      LOGGER.debug(
          "no existing webdav observer for persistence key [{}], creating new observer", key);
      davObserver = new DavAlterationObserver(new DavEntry(rootEntryLocation));
    }

    return davObserver;
  }

  @Override
  public void poll() {
    observer.addListener(webdavListener);
    observer.checkAndNotify(sardine);
    observer.removeListener(webdavListener);
    persistenceProvider.store(persistentKey, observer);
  }

  @Override
  public void registerListener(Listener listener) {
    listeners.add(listener);
  }

  @Override
  public void unregisterListener(Listener listener) {
    listeners.remove(listener);
  }

  private class HarvestedResourceListener implements EntryAlterationListener {
    @Override
    public void onDirectoryCreate(DavEntry entry) {
      // noop
    }

    @Override
    public void onFileCreate(DavEntry entry) {
      listeners.forEach(listener -> listener.onCreate(new HarvestedWebdavResource(entry)));
    }

    @Override
    public void onDirectoryChange(DavEntry entry) {
      // noop
    }

    @Override
    public void onFileChange(DavEntry entry) {
      listeners.forEach(listener -> listener.onUpdate(new HarvestedWebdavResource(entry)));
    }

    @Override
    public void onDirectoryDelete(DavEntry entry) {
      // noop
    }

    @Override
    public void onFileDelete(DavEntry entry) {
      listeners.forEach(listener -> listener.onDelete(new HarvestedWebdavResource(entry)));
    }
  }
}

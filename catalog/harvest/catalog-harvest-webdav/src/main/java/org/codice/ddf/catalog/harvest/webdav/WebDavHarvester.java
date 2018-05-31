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
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import ddf.catalog.Constants;
import ddf.security.common.audit.SecurityLogger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections.MapUtils;
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

  private final Set<Listener> listeners = Collections.synchronizedSet(new HashSet<>());

  private final HarvestedResourceListener webdavListener = new HarvestedResourceListener();

  private final Sardine sardine = SardineFactory.begin();

  private Map<String, Serializable> attributeOverrides = new HashMap<>();

  private String id;

  private String persistentKey;

  private DavAlterationObserver observer;

  private FileSystemPersistenceProvider persistenceProvider;

  private String webdavAddress;

  /** Creates a WebDav {@link Harvester} which will harvest products from the provided address. */
  public WebDavHarvester() {
    super(5L);
    persistenceProvider = new FileSystemPersistenceProvider("harvest/webdav");
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

  /**
   * The {@code WebDavHarvester} will only begin sending events when {@link Listener}s are
   * available, after which events will start being recorded.
   */
  @Override
  public void poll() {
    if (!listeners.isEmpty()) {
      observer.addListener(webdavListener);
      observer.checkAndNotify(sardine);
      // Remove listener before persisting to file system since it is not serializable
      observer.removeListener(webdavListener);
      persistenceProvider.store(persistentKey, observer);
    }
  }

  @Override
  public void registerListener(Listener listener) {
    if (!listeners.isEmpty()) {
      throw new IllegalArgumentException(
          "Only 1 registered listener is currently supported for this harvester.");
    }
    listeners.add(listener);
  }

  public void init() {
    persistentKey = Hashing.sha256().hashString(webdavAddress, StandardCharsets.UTF_8).toString();
    observer = getCachedObserverOrCreate(persistentKey, webdavAddress);
    super.init();
  }

  @Override
  public void unregisterListener(Listener listener) {
    listeners.remove(listener);
  }

  @Override
  public String getId() {
    return id;
  }

  private class HarvestedResourceListener implements EntryAlterationListener {
    @Override
    public void onDirectoryCreate(DavEntry entry) {
      // noop
    }

    @Override
    public void onFileCreate(DavEntry entry) {
      createHarvestedResource(entry)
          .ifPresent(
              harvestedResource ->
                  listeners.forEach(listener -> listener.onCreate(harvestedResource)));
      entry.deleteCacheIfExists();
    }

    @Override
    public void onDirectoryChange(DavEntry entry) {
      // noop
    }

    @Override
    public void onFileChange(DavEntry entry) {
      createHarvestedResource(entry)
          .ifPresent(
              harvestedResource ->
                  listeners.forEach(listener -> listener.onUpdate(harvestedResource)));
      entry.deleteCacheIfExists();
    }

    @Override
    public void onDirectoryDelete(DavEntry entry) {
      // noop
    }

    @Override
    public void onFileDelete(DavEntry entry) {
      listeners.forEach(listener -> listener.onDelete(entry.getLocation()));
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
        Map<String, Object> properties =
            ImmutableMap.of(Constants.ATTRIBUTE_OVERRIDES_KEY, attributeOverrides);
        return Optional.of(
            new HarvestedFile(
                new FileInputStream(file), file.getName(), entry.getLocation(), properties));
      } catch (FileNotFoundException e) {
        LOGGER.debug(
            "Failed to get input stream from file [{}]. Event will not be sent to listener",
            file.toURI());
        return Optional.empty();
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
      setWebdavAddress(getPropertyAs(properties, "webdavAddress", String.class));
      setId(getPropertyAs(properties, "id", String.class));

      Object o = properties.get(Constants.ATTRIBUTE_OVERRIDES_KEY);
      if (o instanceof String[]) {
        String[] incomingAttrOverrides = (String[]) o;
        setAttributeOverrides(Arrays.asList(incomingAttrOverrides));
      }

      destroy(0);
      init();
    }
  }

  @SuppressWarnings(
      "squid:S1172" /* The code parameter is required in blueprint-cm-1.0.7. See https://issues.apache.org/jira/browse/ARIES-1436. */)
  public void destroy(int code) {
    super.destroy();
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

  public void setWebdavAddress(String webdavAddress) {
    this.webdavAddress = stripEndingSlash(webdavAddress);
  }

  public void setId(String id) {
    this.id = id;
  }

  /**
   * Strips the trailing slash from the harvest location, if it exists. This will treat, for
   * example, "http://localhost:8080/" and "http://localhost:8080" the same from a persistence
   * tracking standpoint.
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

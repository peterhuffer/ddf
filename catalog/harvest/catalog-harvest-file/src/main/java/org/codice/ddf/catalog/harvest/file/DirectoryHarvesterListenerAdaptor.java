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

import ddf.security.common.audit.SecurityLogger;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Optional;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.lang3.event.EventListenerSupport;
import org.codice.ddf.catalog.harvest.HarvestedResource;
import org.codice.ddf.catalog.harvest.Listener;
import org.codice.ddf.catalog.harvest.common.HarvestedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectoryHarvesterListenerAdaptor extends FileAlterationListenerAdaptor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DirectoryHarvesterListenerAdaptor.class);

  private final EventListenerSupport<Listener> listeners =
      EventListenerSupport.create(Listener.class);

  @Override
  public void onFileCreate(final File file) {
    createHarvestedResource(file)
        .ifPresent(harvestedResource -> listeners.fire().onCreate(harvestedResource));
  }

  @Override
  public void onFileChange(final File file) {
    createHarvestedResource(file)
        .ifPresent(harvestedResource -> listeners.fire().onUpdate(harvestedResource));
  }

  @Override
  public void onFileDelete(final File file) {
    listeners.fire().onDelete(file.toURI().toASCIIString());
  }

  void registerListener(Listener listener) {
    listeners.addListener(listener, false);
  }

  void unregisterListener(Listener listener) {
    listeners.removeListener(listener);
  }

  private Optional<HarvestedResource> createHarvestedResource(File file) {
    try {
      SecurityLogger.audit("Opening file {}", file.toPath());
      return Optional.of(
          new HarvestedFile(
              new FileInputStream(file), file.getName(), file.toURI().toASCIIString()));
    } catch (FileNotFoundException e) {
      LOGGER.debug(
          "Failed to get input stream from file [{}]. Create event will not be sent to listener",
          file.toURI(),
          e);
      return Optional.empty();
    }
  }
}

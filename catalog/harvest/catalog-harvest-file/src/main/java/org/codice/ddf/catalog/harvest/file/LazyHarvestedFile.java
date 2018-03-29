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

import ddf.catalog.resource.Resource;
import ddf.catalog.resource.impl.ResourceImpl;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.codice.ddf.catalog.harvest.HarvestedResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LazyHarvestedFile implements HarvestedResource {

  private static final Logger LOGGER = LoggerFactory.getLogger(LazyHarvestedFile.class);

  private final Map<String, Object> properties;

  private final File file;

  private final String correlationId;

  private Resource resource;

  private boolean isLoaded = false;

  public LazyHarvestedFile(File file) {
    this(file, new HashMap<>());
  }

  public LazyHarvestedFile(File file, Map<String, Object> properties) {
    this.properties = properties;
    this.file = file;
    correlationId = DigestUtils.sha1Hex(file.getAbsolutePath());
  }

  @Override
  public Map<String, Object> getProperties() {
    return properties;
  }

  @Override
  public String getCorrelationId() {
    return correlationId;
  }

  @Override
  public Resource getResource() {
    if (!isLoaded) {
      try {
        ResourceImpl resourceImpl = new ResourceImpl(new FileInputStream(file), file.getName());
        resourceImpl.setSize(file.length());
        resource = resourceImpl;
        isLoaded = true;
      } catch (IOException e) {
        LOGGER.debug("Error getting file [{}]. Was it deleted?", file.getName(), e);
      }
    }
    return resource;
  }

  @Override
  public URI getUri() {
    return file.toURI();
  }
}

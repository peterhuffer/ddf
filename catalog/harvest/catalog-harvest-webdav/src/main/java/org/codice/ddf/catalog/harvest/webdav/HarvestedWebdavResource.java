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

import com.github.sardine.SardineFactory;
import ddf.catalog.resource.Resource;
import ddf.catalog.resource.impl.ResourceImpl;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.codec.digest.DigestUtils;
import org.codice.ddf.catalog.harvest.HarvestedResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HarvestedWebdavResource implements HarvestedResource {

  private static final Logger LOGGER = LoggerFactory.getLogger(HarvestedWebdavResource.class);

  private Map<String, Object> properties = new HashMap<>();

  private final DavEntry davEntry;

  private final String correlationId;

  private URI uri = null;

  private Resource resource = null;

  public HarvestedWebdavResource(DavEntry davEntry) {
    this.davEntry = davEntry;

    String location = davEntry.getLocation();
    correlationId = DigestUtils.sha1Hex(location);
    try {
      uri = new URI(davEntry.getLocation());
    } catch (URISyntaxException e) {
      LOGGER.debug("Received dav entry with bad URI [{}].", location, e);
    }
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
  public URI getUri() {
    return uri;
  }

  @Override
  public Resource getResource() {
    if (resource == null) {
      File davFile = null;
      try {
        davFile = davEntry.getFile(SardineFactory.begin());
      } catch (IOException e) {
        LOGGER.debug("Failed to get file for dav resource [{}]", davEntry.getLocation());
      }

      if (davFile != null)
        try {
          resource = new ResourceImpl(new FileInputStream(davFile), davFile.getName());
        } catch (FileNotFoundException e) {
          LOGGER.debug("File not found for dav resource [{}].", davEntry.getLocation());
        }
    }

    return resource;
  }
}

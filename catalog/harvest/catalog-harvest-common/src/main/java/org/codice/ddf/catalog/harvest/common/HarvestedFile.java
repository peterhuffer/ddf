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
package org.codice.ddf.catalog.harvest.common;

import static org.apache.commons.lang3.Validate.notNull;

import ddf.catalog.resource.impl.ResourceImpl;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.codice.ddf.catalog.harvest.HarvestedResource;

public class HarvestedFile extends ResourceImpl implements HarvestedResource {

  private final URI uri;

  private Map<String, List<String>> attributeOverrides;

  private final Map<String, Serializable> properties;

  public HarvestedFile(InputStream is, String name, String uri) {
    this(is, name, uri, Collections.emptyMap(), Collections.emptyMap());
  }

  public HarvestedFile(
      InputStream is,
      String name,
      String uri,
      Map<String, List<String>> attributeOverrides,
      Map<String, Serializable> properties) {
    super(is, name);
    try {
      this.uri = new URI(uri);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(String.format("Invalid URI [%s] received.", uri));
    }

    this.attributeOverrides = notNull(attributeOverrides);
    this.properties = notNull(properties);
  }

  @Override
  public Map<String, Serializable> getProperties() {
    return properties;
  }

  @Override
  public Map<String, List<String>> getAttributeOverrides() {
    return attributeOverrides;
  }

  @Override
  public URI getUri() {
    return uri;
  }
}

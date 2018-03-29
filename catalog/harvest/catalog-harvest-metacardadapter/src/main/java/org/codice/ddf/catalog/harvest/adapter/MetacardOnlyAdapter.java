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
package org.codice.ddf.catalog.harvest.adapter;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Metacard;
import ddf.catalog.operation.CreateRequest;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteRequest;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.UpdateRequest;
import ddf.catalog.operation.UpdateResponse;
import ddf.catalog.operation.impl.CreateRequestImpl;
import ddf.catalog.operation.impl.DeleteRequestImpl;
import ddf.catalog.operation.impl.UpdateRequestImpl;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.security.SecurityConstants;
import ddf.security.Subject;
import ddf.security.service.SecurityServiceException;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Validate;
import org.codice.ddf.catalog.harvest.Adapter;
import org.codice.ddf.catalog.harvest.HarvestedResource;
import org.codice.ddf.security.common.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Harvester {@link Adapter} for creating {@link Metacard}s in the catalog. */
public class MetacardOnlyAdapter implements Adapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetacardOnlyAdapter.class);

  private static final Security SECURITY = Security.getInstance();

  private final CatalogFramework catalogFramework;

  private final HarvestedResourceTransformer harvestedResourceTransformer;

  public MetacardOnlyAdapter(
      CatalogFramework catalogFramework,
      HarvestedResourceTransformer harvestedResourceTransformer) {
    Validate.notNull(catalogFramework, "argument {catalogFramework} cannot be null");
    Validate.notNull(
        harvestedResourceTransformer, "argument {harvestedResourceTransformer} cannot be null");

    this.catalogFramework = catalogFramework;
    this.harvestedResourceTransformer = harvestedResourceTransformer;
  }

  @Override
  public String create(HarvestedResource resource) {
    Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(resource);

    if (metacard == null) {
      LOGGER.debug(
          "No valid input transformer found for resource [{}]. Resource will not be processed.",
          resource.getResource().getName());
      return null;
    }

    CreateRequest createRequest =
        new CreateRequestImpl(Collections.singletonList(metacard), getSecurityMap());

    return runWithSubjectOrDefault(
        () -> {
          CreateResponse response;
          try {
            response = catalogFramework.create(createRequest);

            List<Metacard> createdMetacards = response.getCreatedMetacards();
            if (createdMetacards.size() == 1) {
              return createdMetacards.get(0).getId();
            } else {
              LOGGER.debug(
                  "Received {} metacards for a single created resource.", createdMetacards.size());
              return null;
            }
          } catch (IngestException | SourceUnavailableException e) {
            LOGGER.debug("Failed to ingest resource [{}].", resource.getResource().getName(), e);
            return null;
          }
        },
        null);
  }

  @Override
  public String update(HarvestedResource resource, String updateId) {
    Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(resource, updateId);

    if (metacard == null) {
      LOGGER.debug(
          "No valid input transformer found for resource [{}] with update id [{}]. Resource will not be processed.",
          resource.getResource().getName(),
          updateId);
      return null;
    }

    UpdateRequest updateRequest = new UpdateRequestImpl(updateId, metacard);

    return runWithSubjectOrDefault(
        () -> {
          UpdateResponse response;
          try {
            response = catalogFramework.update(updateRequest);

            List<String> updatedMetacardIds =
                response
                    .getUpdatedMetacards()
                    .stream()
                    .map(update -> update.getNewMetacard().getId())
                    .collect(Collectors.toList());

            if (updatedMetacardIds.size() == 1) {
              return updatedMetacardIds.get(0);
            } else {
              LOGGER.debug(
                  "Received {} metacards for a single updated resource.",
                  updatedMetacardIds.size());
              return null;
            }
          } catch (IngestException | SourceUnavailableException e) {
            LOGGER.debug("Failed to update resource [{}].", resource.getResource().getName(), e);
            return null;
          }
        },
        null);
  }

  @Override
  public boolean delete(String id) {
    DeleteRequest deleteRequest = new DeleteRequestImpl(id);

    return runWithSubjectOrDefault(
        () -> {
          try {
            DeleteResponse response = catalogFramework.delete(deleteRequest);

            if (response.getDeletedMetacards().isEmpty()) {
              LOGGER.debug("Failed to delete harvested resource with id [{}]", id);
              return false;
            }
          } catch (IngestException | SourceUnavailableException e) {
            LOGGER.debug("Failed to delete harvested resource with id [{}].", id, e);
            return false;
          }

          return true;
        },
        false);
  }

  private <T> T runWithSubjectOrDefault(final Callable<T> callable, final T defaultValue) {
    return SECURITY.runAsAdmin(
        () -> {
          try {
            return SECURITY.runWithSubjectOrElevate(callable);
          } catch (SecurityServiceException | InvocationTargetException e) {
            LOGGER.debug("Error executing code with subject", e);
            return defaultValue;
          }
        });
  }

  private Map<String, Serializable> getSecurityMap() {
    Subject subject = SECURITY.runAsAdmin(SECURITY::getSystemSubject);
    Map<String, Serializable> requestArgs = new HashMap<>();
    requestArgs.put(SecurityConstants.SECURITY_SUBJECT, subject);
    return requestArgs;
  }
}

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
import org.codice.ddf.catalog.harvest.HarvestException;
import org.codice.ddf.catalog.harvest.HarvestedResource;
import org.codice.ddf.catalog.harvest.StorageAdapter;
import org.codice.ddf.security.common.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Harvester {@link StorageAdapter} for creating {@link Metacard}s in the catalog. */
public class MetacardOnlyAdapter implements StorageAdapter {

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
  public String create(HarvestedResource resource) throws HarvestException {
    Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(resource);

    if (metacard == null) {
      LOGGER.debug(
          "Unable to transform resource [{}] to a metacard. Resource will not be processed.",
          resource.getName());
      throw new HarvestException(
          String.format("Unable to transform resource [%s].", resource.getName()));
    }

    CreateRequest createRequest =
        new CreateRequestImpl(Collections.singletonList(metacard), getSecurityMap());

    return runWithSubjectOrDefault(
        () -> {
          try {
            CreateResponse response;
            response = catalogFramework.create(createRequest);

            List<Metacard> createdMetacards = response.getCreatedMetacards();
            if (createdMetacards.size() == 1) {
              return createdMetacards.get(0).getId();
            } else {
              LOGGER.debug(
                  "Received multiple metacards for a single created resource. [{}].",
                  createdMetacards);
              throw new HarvestException(
                  String.format(
                      "Multiple metacards returned for single create for resource [%s]",
                      resource.getName()));
            }
          } catch (IngestException | SourceUnavailableException e) {
            LOGGER.debug("Failed to ingest resource [{}].", resource.getName(), e);
            throw new HarvestException(
                String.format("Failed to create resource [%s]", resource.getName()), e);
          }
        });
  }

  @Override
  public void update(HarvestedResource resource, String updateId) throws HarvestException {
    Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(resource, updateId);

    if (metacard == null) {
      LOGGER.debug(
          "Unable to transform resource [{}] with update id [{}]. Resource will not be processed.",
          resource.getName(),
          updateId);
      throw new HarvestException(
          String.format("Unable to transform resource [%s]", resource.getName()));
    }

    UpdateRequest updateRequest = new UpdateRequestImpl(updateId, metacard);

    runWithSubjectOrDefault(
        () -> {
          try {
            UpdateResponse response;
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
                  "Received {} metacards for a single updated resource. [{}]",
                  updatedMetacardIds.size(),
                  updatedMetacardIds);
              throw new HarvestException(
                  String.format(
                      "Multiple metacards returned for single update for resource [%s]",
                      resource.getName()));
            }
          } catch (IngestException | SourceUnavailableException e) {
            LOGGER.debug("Failed to update resource [{}].", resource.getName(), e);
            throw new HarvestException(
                String.format("Failed to update resource [%s]", resource.getName()), e);
          }
        });
  }

  @Override
  public void delete(String id) {
    DeleteRequest deleteRequest = new DeleteRequestImpl(id);

    runWithSubjectOrDefault(
        () -> {
          try {
            DeleteResponse response = catalogFramework.delete(deleteRequest);

            if (response.getDeletedMetacards().isEmpty()) {
              LOGGER.debug("No metacards retrieved from catalog delete request for id [{}].", id);
              throw new HarvestException(
                  String.format(
                      "No metacards retrieved from catalog delete request for id [%s]", id));
            }
          } catch (IngestException | SourceUnavailableException e) {
            LOGGER.debug("Failed to delete harvested resource with id [{}].", id, e);
            throw new HarvestException(
                String.format("Failed to delete harvested resource with id [%s]", id), e);
          }

          return null;
        });
  }

  private <T> T runWithSubjectOrDefault(final Callable<T> callable) {
    return SECURITY.runAsAdmin(
        () -> {
          try {
            return SECURITY.runWithSubjectOrElevate(callable);
          } catch (SecurityServiceException | InvocationTargetException e) {
            LOGGER.debug("Error executing code with subject", e);
            return null;
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

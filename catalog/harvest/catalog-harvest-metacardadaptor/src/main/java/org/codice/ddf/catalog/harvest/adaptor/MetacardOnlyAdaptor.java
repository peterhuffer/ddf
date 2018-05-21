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
package org.codice.ddf.catalog.harvest.adaptor;

import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Metacard;
import ddf.catalog.operation.CreateRequest;
import ddf.catalog.operation.CreateResponse;
import ddf.catalog.operation.DeleteRequest;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.Update;
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
import org.apache.commons.lang3.Validate;
import org.codice.ddf.catalog.harvest.HarvestException;
import org.codice.ddf.catalog.harvest.HarvestedResource;
import org.codice.ddf.catalog.harvest.StorageAdaptor;
import org.codice.ddf.security.common.Security;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Harvester {@link StorageAdaptor} for creating {@link Metacard}s in the catalog. */
public class MetacardOnlyAdaptor implements StorageAdaptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetacardOnlyAdaptor.class);

  private static final Security SECURITY = Security.getInstance();

  private final CatalogFramework catalogFramework;

  private final HarvestedResourceTransformer harvestedResourceTransformer;

  public MetacardOnlyAdaptor(
      CatalogFramework catalogFramework,
      HarvestedResourceTransformer harvestedResourceTransformer) {
    Validate.notNull(catalogFramework, "Argument catalogFramework may not be null");
    Validate.notNull(
        harvestedResourceTransformer, "Argument harvestedResourceTransformer may not be null");

    this.catalogFramework = catalogFramework;
    this.harvestedResourceTransformer = harvestedResourceTransformer;
  }

  @Override
  public String create(HarvestedResource resource) throws HarvestException {
    Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(resource);

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
            } else if (createdMetacards.isEmpty()) {
              throw new HarvestException(
                  String.format(
                      "No metacards were created from resource [%s].", resource.getName()));
            } else {
              throw new HarvestException(
                  String.format(
                      "Multiple metacards returned for single created resource [%s]",
                      resource.getName()));
            }
          } catch (IngestException | SourceUnavailableException e) {
            throw new HarvestException(
                String.format("Failed to create resource [%s]", resource.getName()), e);
          }
        });
  }

  @Override
  public void update(HarvestedResource resource, String updateId) throws HarvestException {
    Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(resource, updateId);

    UpdateRequest updateRequest = new UpdateRequestImpl(updateId, metacard);

    runWithSubjectOrDefault(
        () -> {
          try {
            UpdateResponse response;
            response = catalogFramework.update(updateRequest);

            List<Update> updatedMetacard = response.getUpdatedMetacards();
            if (updatedMetacard.size() == 1) {
              return updatedMetacard.get(0).getNewMetacard().getId();
            } else if (updatedMetacard.isEmpty()) {
              throw new HarvestException(
                  String.format(
                      "No metacard updates received for resource [%s]", resource.getName()));
            } else {
              throw new HarvestException(
                  String.format(
                      "Multiple metacards returned for single update for resource [%s]",
                      resource.getName()));
            }
          } catch (IngestException | SourceUnavailableException e) {
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

            List<Metacard> deletedMetacard = response.getDeletedMetacards();

            if (deletedMetacard.isEmpty()) {
              throw new HarvestException(
                  String.format(
                      "No metacards retrieved from catalog delete request for id [%s]", id));
            } else if (deletedMetacard.size() > 1) {
              throw new HarvestException(
                  String.format("Multiple metacards deleted for metacard id [%s]", id));
            }
          } catch (IngestException | SourceUnavailableException e) {
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

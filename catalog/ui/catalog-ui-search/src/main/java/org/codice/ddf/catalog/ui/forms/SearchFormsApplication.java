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
package org.codice.ddf.catalog.ui.forms;

import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.codice.ddf.catalog.ui.forms.data.AttributeGroupType.ATTRIBUTE_GROUP_TAG;
import static org.codice.ddf.catalog.ui.forms.data.QueryTemplateType.QUERY_TEMPLATE_TAG;
import static spark.Spark.delete;
import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.put;

import com.google.common.collect.ImmutableMap;
import ddf.catalog.CatalogFramework;
import ddf.catalog.data.Attribute;
import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.Result;
import ddf.catalog.federation.FederationException;
import ddf.catalog.filter.FilterBuilder;
import ddf.catalog.operation.DeleteResponse;
import ddf.catalog.operation.QueryResponse;
import ddf.catalog.operation.impl.CreateRequestImpl;
import ddf.catalog.operation.impl.DeleteRequestImpl;
import ddf.catalog.operation.impl.QueryImpl;
import ddf.catalog.operation.impl.QueryRequestImpl;
import ddf.catalog.operation.impl.UpdateRequestImpl;
import ddf.catalog.source.IngestException;
import ddf.catalog.source.SourceUnavailableException;
import ddf.catalog.source.UnsupportedQueryException;
import ddf.security.Subject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.shiro.SecurityUtils;
import org.boon.json.JsonFactory;
import org.boon.json.JsonParserFactory;
import org.boon.json.JsonSerializerFactory;
import org.boon.json.ObjectMapper;
import org.codice.ddf.catalog.ui.forms.model.FilterNodeValueSerializer;
import org.codice.ddf.catalog.ui.forms.model.pojo.CommonTemplate;
import org.codice.ddf.catalog.ui.util.EndpointUtil;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.servlet.SparkApplication;

/** Provides an internal REST interface for working with custom form data for Intrigue. */
public class SearchFormsApplication implements SparkApplication {
  private static final ObjectMapper MAPPER =
      JsonFactory.create(
          new JsonParserFactory().usePropertyOnly(),
          new JsonSerializerFactory()
              .addPropertySerializer(new FilterNodeValueSerializer())
              .useAnnotations()
              .includeEmpty()
              .includeDefaultValues()
              .setJsonFormatForDates(false));

  private final CatalogFramework catalogFramework;

  private final TemplateTransformer transformer;

  private final EndpointUtil util;

  private final FilterBuilder filterBuilder;

  private final boolean readOnly;

  private static final String RESP_MSG = "message";

  private static final String SOMETHING_WENT_WRONG = "Something went wrong.";

  private static final Logger LOGGER = LoggerFactory.getLogger(SearchFormsApplication.class);

  public SearchFormsApplication(
      FilterBuilder filterBuilder,
      CatalogFramework catalogFramework,
      TemplateTransformer transformer,
      EndpointUtil util) {
    this.catalogFramework = catalogFramework;
    this.transformer = transformer;
    this.util = util;
    this.filterBuilder = filterBuilder;
    this.readOnly = !SearchFormsLoader.enabled();
  }

  /**
   * Called via blueprint on initialization. Reads configuration in {@code etc/forms} and
   * initializes Solr with query templates and attribute groups using the {@link
   * ddf.catalog.data.types.Core#TITLE} field as a unique key.
   */
  public void setup() {
    List<Metacard> systemTemplates = SearchFormsLoader.config().get();
    if (systemTemplates.isEmpty()) {
      return;
    }
    SearchFormsLoader.bootstrap(catalogFramework, util, systemTemplates);
  }

  /**
   * Spark's API-mandated init (not OSGi related) for registering REST functions. If no forms
   * directory exists, no PUT/DELETE routes will be registered. The feature is effectively "off".
   */
  @Override
  public void init() {
    get(
        "/forms/query",
        (req, res) ->
            util.getMetacardsByFilter(QUERY_TEMPLATE_TAG)
                .values()
                .stream()
                .map(Result::getMetacard)
                .filter(Objects::nonNull)
                .map(TemplateTransformer::toFormTemplate)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(CommonTemplate::getTitle))
                .collect(Collectors.toList()),
        MAPPER::toJson);

    get(
        "/forms/result",
        (req, res) ->
            util.getMetacardsByFilter(ATTRIBUTE_GROUP_TAG)
                .values()
                .stream()
                .map(Result::getMetacard)
                .filter(Objects::nonNull)
                .map(transformer::toFieldFilter)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(CommonTemplate::getTitle))
                .collect(Collectors.toList()),
        MAPPER::toJson);

    // If no forms directory was created, disable intrusive catalog operations
    // Bailing out of this method early means the below routes will not be setup
    if (readOnly) {
      return;
    }

    put(
        "/forms/query",
        APPLICATION_JSON,
        (req, res) ->
            runWhenNotGuest(
                res,
                () ->
                    doCreateOrUpdate(
                        res,
                        Stream.of(safeGetBody(req))
                            .map(MAPPER.parser()::parseMap)
                            .map(transformer::toQueryTemplateMetacard)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null))),
        MAPPER::toJson);

    put(
        "/forms/result",
        APPLICATION_JSON,
        (req, res) ->
            runWhenNotGuest(
                res,
                () ->
                    doCreateOrUpdate(
                        res,
                        Stream.of(safeGetBody(req))
                            .map(MAPPER.parser()::parseMap)
                            .map(transformer::toAttributeGroupMetacard)
                            .filter(Objects::nonNull)
                            .findFirst()
                            .orElse(null))),
        MAPPER::toJson);

    delete(
        "/forms/:id",
        APPLICATION_JSON,
        (req, res) -> {
          String id = req.params(":id");
          DeleteResponse deleteResponse = catalogFramework.delete(new DeleteRequestImpl(id));
          if (!deleteResponse.getProcessingErrors().isEmpty()) {
            res.status(500);
            LOGGER.debug("Failed to Delete Form {}", id);
            return ImmutableMap.of(RESP_MSG, "Failed to delete.");
          }
          return ImmutableMap.of(RESP_MSG, "Successfully deleted.");
        },
        util::getJson);

    exception(
        IllegalArgumentException.class,
        (e, req, res) -> {
          LOGGER.debug("Template input was not valid", e);
          res.status(400);
          res.header(CONTENT_TYPE, APPLICATION_JSON);
          res.body(util.getJson(ImmutableMap.of(RESP_MSG, "Input was not valid.")));
        });

    exception(
        IngestException.class,
        (ex, req, res) -> {
          LOGGER.debug("Failed to persist form", ex);
          res.status(404);
          res.header(CONTENT_TYPE, APPLICATION_JSON);
          res.body(
              util.getJson(ImmutableMap.of(RESP_MSG, "Form is either restricted or not found.")));
        });

    exception(
        UnsupportedOperationException.class,
        (e, req, res) -> {
          LOGGER.debug("Could not use filter JSON because it contains unsupported operations", e);
          res.status(501);
          res.header(CONTENT_TYPE, APPLICATION_JSON);
          res.body(util.getJson(ImmutableMap.of(RESP_MSG, "This operation is not supported.")));
        });

    exception(
        SourceUnavailableException.class,
        (ex, req, res) -> {
          LOGGER.debug("Failed to persist form", ex);
          res.status(503);
          res.header(CONTENT_TYPE, APPLICATION_JSON);
          res.body(
              util.getJson(
                  ImmutableMap.of(RESP_MSG, "Source not available, please try again later.")));
        });

    exception(UncheckedIOException.class, util::handleIOException);

    exception(
        RuntimeException.class,
        (e, req, res) -> {
          LOGGER.error(SOMETHING_WENT_WRONG, e);
          res.status(500);
          res.header(CONTENT_TYPE, APPLICATION_JSON);
          res.body(util.getJson(ImmutableMap.of(RESP_MSG, SOMETHING_WENT_WRONG)));
        });

    exception(
        Exception.class,
        (e, req, res) -> {
          LOGGER.error(SOMETHING_WENT_WRONG, e);
          res.status(500);
          res.header(CONTENT_TYPE, APPLICATION_JSON);
          res.body(util.getJson(ImmutableMap.of(RESP_MSG, SOMETHING_WENT_WRONG)));
        });
  }

  private Map<String, Object> runWhenNotGuest(
      Response res, CheckedSupplier<Map<String, Object>> templateOperation) throws Exception {
    Subject subject = (Subject) SecurityUtils.getSubject();
    if (subject.isGuest()) {
      res.status(403);
      return ImmutableMap.of(RESP_MSG, "Guests cannot perform this action.");
    }
    return templateOperation.get();
  }

  private String safeGetBody(Request req) {
    try {
      return util.safeGetBody(req);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private Map<String, Object> doCreateOrUpdate(Response response, Metacard metacard)
      throws IngestException, SourceUnavailableException, FederationException,
          UnsupportedQueryException {
    if (metacard == null) {
      response.status(400);
      return ImmutableMap.of(RESP_MSG, "Could not create, no valid template specified");
    }

    // The UI should not send an ID during a PUT unless the metacard already exists
    String id = metacard.getId();
    if (id != null) {
      Metacard oldMetacard = getMetacardIfExistsOrNull(id);
      if (oldMetacard != null) {
        for (AttributeDescriptor descriptor :
            oldMetacard.getMetacardType().getAttributeDescriptors()) {
          Attribute metacardAttribute = metacard.getAttribute(descriptor.getName());
          if (metacardAttribute == null || metacardAttribute.getValue() == null) {
            continue;
          }
          oldMetacard.setAttribute(metacardAttribute);
        }
        catalogFramework.update(new UpdateRequestImpl(id, oldMetacard));
        return ImmutableMap.of(RESP_MSG, "Successfully updated");
      }
    }
    catalogFramework.create(new CreateRequestImpl(metacard));
    return ImmutableMap.of(RESP_MSG, "Successfully created");
  }

  private Metacard getMetacardIfExistsOrNull(String id)
      throws UnsupportedQueryException, SourceUnavailableException, FederationException {
    Filter idFilter = filterBuilder.attribute(Metacard.ID).is().equalTo().text(id);
    Filter tagsFilter = filterBuilder.attribute(Metacard.TAGS).is().like().text("*");
    Filter filter = filterBuilder.allOf(idFilter, tagsFilter);

    QueryResponse queryResponse =
        catalogFramework.query(new QueryRequestImpl(new QueryImpl(filter), false));

    if (!queryResponse.getResults().isEmpty()) {
      return queryResponse.getResults().get(0).getMetacard();
    }

    return null;
  }

  @FunctionalInterface
  @SuppressWarnings("squid:S00112" /* Supplier to mimic Spark's routing API */)
  private interface CheckedSupplier<T> {
    T get() throws Exception;
  }
}

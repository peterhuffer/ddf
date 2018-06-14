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

import com.google.common.annotations.VisibleForTesting;
import ddf.catalog.data.AttributeDescriptor;
import ddf.catalog.data.AttributeRegistry;
import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.xml.bind.DatatypeConverter;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class OverrideAttributesSupport {

  private static final Logger LOGGER = LoggerFactory.getLogger(OverrideAttributesSupport.class);

  /**
   * This method was copied from {@link
   * ddf.catalog.impl.operations.OverrideAttributesSupport#overrideAttributeValue} and then updated
   * to check that the {@link AttributeDescriptor} is in the {@link AttributeRegistry} and if it
   * {@link AttributeDescriptor#isMultiValued()}.
   */
  @VisibleForTesting
  static void overrideAttributes(
      Metacard metacard,
      Map<String, List<String>> attributeOverrides,
      AttributeRegistry attributeRegistry) {
    if (MapUtils.isEmpty(attributeOverrides)) {
      return;
    }

    for (final Map.Entry<String, List<String>> entry : attributeOverrides.entrySet()) {
      final String attributeName = entry.getKey();
      final List<String> newValues = entry.getValue();

      final int newValuesCount = newValues.size();
      if (newValuesCount < 1) {
        throw new IllegalArgumentException(
            "The attributeOverrides map should only contain override values, not empty lists");
      }

      final Optional<AttributeDescriptor> attributeDescriptorOptional =
          attributeRegistry.lookup(attributeName);
      if (!attributeDescriptorOptional.isPresent()) {
        LOGGER.trace(
            "Attribute name \"{}\" was not found in the registry. Not setting that attribute",
            attributeName);
      } else {
        final AttributeDescriptor attributeDescriptor = attributeDescriptorOptional.get();

        if (newValuesCount > 1 && !attributeDescriptor.isMultiValued()) {
          if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "Unable to override attribute \"{}\" with values \"{}\" because it is not registered as a multi-valued attribute",
                attributeName,
                StringUtils.join(newValues, ", "));
          }
        } else {
          final Function<String, Serializable> mapFunction;
          switch (attributeDescriptor.getType().getAttributeFormat()) {
            case BOOLEAN:
              mapFunction = Boolean::parseBoolean;
              break;
            case DATE:
              mapFunction = override -> DatatypeConverter.parseDateTime(override).getTime();
              break;
            case SHORT:
              mapFunction = Short::parseShort;
              break;
            case INTEGER:
              mapFunction = Integer::parseInt;
              break;
            case LONG:
              mapFunction = Long::parseLong;
              break;
            case FLOAT:
              mapFunction = Float::parseFloat;
              break;
            case DOUBLE:
              mapFunction = Double::parseDouble;
              break;
            case BINARY:
              mapFunction = override -> override.getBytes(Charset.forName("UTF-8"));
              break;
            default:
              mapFunction = override -> override;
          }

          metacard.setAttribute(
              new AttributeImpl(
                  attributeName, newValues.stream().map(mapFunction).collect(Collectors.toList())));
        }
      }
    }
  }
}

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
package org.codice.ddf.catalog.harvest.adaptor

import ddf.catalog.data.*
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.Charset

class OverrideAttributesSupportSpec extends Specification {

    final Metacard metacard = Mock(Metacard) {
        getAttribute(_ as String) >> null
    }

    final AttributeRegistry attributeRegistry = Mock(AttributeRegistry)

    def 'test no overrides'() {
        given:
        attributeRegistry.lookup(_ as String) >> Optional.of(Mock(AttributeDescriptor))

        when:
        OverrideAttributesSupport.overrideAttributes(metacard, [:], attributeRegistry)

        then: "there should be no interactions with the metacard"
        0 * metacard._
    }

    def 'test empty list of overrides'() {
        when:
        OverrideAttributesSupport.overrideAttributes(metacard, [_: []], attributeRegistry)

        then:
        thrown IllegalArgumentException

        // verify there are no other interactions with the metacard
        0 * metacard._
    }

    @Unroll
    def 'test override an attribute of type #attributeType'() {
        given:
        final String attributeName = "attributeName"

        attributeRegistry.lookup(attributeName) >> Optional.of(Mock(AttributeDescriptor) {
            isMultiValued() >> true
            getType() >> Mock(AttributeType) {
                getAttributeFormat() >> attributeType
            }
        })

        when:
        OverrideAttributesSupport.overrideAttributes(metacard, [(attributeName): overrideValues], attributeRegistry)

        then:
        1 * metacard.setAttribute({
            it.getName() == attributeName
            it.getValues() == expectedMetacardValues
        } as Attribute)

        // verify there are no other interactions with the metacard
        0 * metacard._

        where:
        overrideValues | attributeType                         | expectedMetacardValues
        ["one"]        | AttributeType.AttributeFormat.STRING  | ["one"]
        ["true"]       | AttributeType.AttributeFormat.BOOLEAN | [true]
        ["1"]          | AttributeType.AttributeFormat.SHORT   | [(short) 1]
        ["2"]          | AttributeType.AttributeFormat.INTEGER | [(int) 2]
        ["3"]          | AttributeType.AttributeFormat.LONG    | [(long) 3]
        ["4"]          | AttributeType.AttributeFormat.FLOAT   | [(float) 4]
        ["5"]          | AttributeType.AttributeFormat.DOUBLE  | [(double) 5]
        ["two"]        | AttributeType.AttributeFormat.BINARY  | ["two".getBytes(Charset.forName("UTF-8"))]
        ["three"]      | AttributeType.AttributeFormat.XML     | ["three"]
        ["four"]       | AttributeType.AttributeFormat.OBJECT  | ["four"]
    }

    def 'test override multiple attributes'() {
        given:
        final String attributeName1 = "attributeName1"
        final String attributeName2 = "attributeName2"
        final String attributeName3 = "attributeName3"
        final String attributeName4 = "attributeName4"
        final Map<String, List<String>> attributeOverrides = [
                (attributeName1): [_ as String],
                (attributeName2): [_ as String],
                (attributeName3): [_ as String],
                (attributeName4): [_ as String]
        ]

        attributeRegistry.lookup(_ as String) >> Optional.of(Mock(AttributeDescriptor) {
            isMultiValued() >> true
            getType() >> Mock(AttributeType) {
                getAttributeFormat() >> AttributeType.AttributeFormat.STRING
            }
        })

        when:
        OverrideAttributesSupport.overrideAttributes(metacard, attributeOverrides, attributeRegistry)

        then: 'the multi-value override for an Attribute name that is not registered as multi-valued in the AttributeRegistry should not be set on the metacard, and any other overrides should still be set'
        1 * metacard.setAttribute({
            it.getName() == attributeName1
        } as Attribute)
        1 * metacard.setAttribute({
            it.getName() == attributeName2
        } as Attribute)
        1 * metacard.setAttribute({
            it.getName() == attributeName3
        } as Attribute)
        1 * metacard.setAttribute({
            it.getName() == attributeName4
        } as Attribute)

        // verify there are no other interactions with the metacard
        0 * metacard._
    }

    def 'test override an attribute that is already populated'() {
        given:
        final String attributeName = "attributeName"
        final List<String> attributeValues = [_ as String]

        metacard.getAttribute(attributeName) >> Mock(Attribute)

        attributeRegistry.lookup(_ as String) >> Optional.of(Mock(AttributeDescriptor) {
            isMultiValued() >> true
            getType() >> Mock(AttributeType) {
                getAttributeFormat() >> AttributeType.AttributeFormat.STRING
            }
        })

        when:
        OverrideAttributesSupport.overrideAttributes(metacard, [(attributeName): attributeValues], attributeRegistry)

        then:
        1 * metacard.setAttribute({
            it.getName() == attributeName
            it.getValues() == attributeValues
        } as Attribute)

        // verify there are no other interactions with the metacard
        0 * metacard._
    }

    def 'test skip an attribute that is not found in the registry'() {
        given:
        final String attributeName1 = "attributeName1"
        final String notRegisteredAttributeName = "notRegisteredAttributeName"
        final String attributeName2 = "attributeName2"
        final Map<String, List<String>> attributeOverrides = [
                (attributeName1)            : [_ as String],
                (notRegisteredAttributeName): [_ as String],
                (attributeName2)            : [_ as String]
        ]

        attributeRegistry.lookup(notRegisteredAttributeName) >> Optional.empty()
        attributeRegistry.lookup(_ as String) >> Optional.of(Mock(AttributeDescriptor) {
            isMultiValued() >> true
            getType() >> Mock(AttributeType) {
                getAttributeFormat() >> AttributeType.AttributeFormat.STRING
            }
        })

        when:
        OverrideAttributesSupport.overrideAttributes(metacard, attributeOverrides, attributeRegistry)

        then: 'the override for an Attribute name that is not found in the AttributeRegistry should not be set on the metacard, and any other overrides should still be set'
        1 * metacard.setAttribute({
            it.getName() == attributeName1
        } as Attribute)
        1 * metacard.setAttribute({
            it.getName() == attributeName2
        } as Attribute)
        0 * metacard.setAttribute({
            it.getName() == notRegisteredAttributeName
        } as Attribute)

        // verify there are no other interactions with the metacard
        0 * metacard._
    }

    def 'test override a multi-valued attribute'() {
        given:
        final String multivaluedAttributeName = "multivaluedAttributeName"
        final List<String> multivaluedAttributeValues = ["value1", "value2"]

        attributeRegistry.lookup(multivaluedAttributeName) >> Optional.of(Mock(AttributeDescriptor) {
            isMultiValued() >> true
            getType() >> Mock(AttributeType) {
                getAttributeFormat() >> AttributeType.AttributeFormat.STRING
            }
        })

        when:
        OverrideAttributesSupport.overrideAttributes(metacard, [(multivaluedAttributeName): multivaluedAttributeValues], attributeRegistry)

        then:
        1 * metacard.setAttribute({
            it.getName() == multivaluedAttributeName
            it.getValues() == multivaluedAttributeValues
        } as Attribute)

        // verify there are no other interactions with the metacard
        0 * metacard._
    }

    def 'test skip a single-valued attribute with multiple override values'() {
        given:
        final String attributeName1 = "attributeName1"
        final String singleValuedAttributeName = "singleValuedAttributeName"
        final String attributeName2 = "attributeName2"
        final Map<String, List<String>> attributeOverrides = [
                (attributeName1)           : [_ as String],
                (singleValuedAttributeName): [_ as String, _ as String, _ as String],
                (attributeName2)           : [_ as String]
        ]

        attributeRegistry.lookup(singleValuedAttributeName) >> Optional.of(Mock(AttributeDescriptor) {
            isMultiValued() >> false
            getType() >> Mock(AttributeType) {
                getAttributeFormat() >> AttributeType.AttributeFormat.STRING
            }
        })
        attributeRegistry.lookup(_ as String) >> Optional.of(Mock(AttributeDescriptor) {
            isMultiValued() >> true
            getType() >> Mock(AttributeType) {
                getAttributeFormat() >> AttributeType.AttributeFormat.STRING
            }
        })

        when:
        OverrideAttributesSupport.overrideAttributes(metacard, attributeOverrides, attributeRegistry)

        then: 'the multi-value override for an Attribute name that is not registered as multi-valued in the AttributeRegistry should not be set on the metacard, and any other overrides should still be set'
        1 * metacard.setAttribute({
            it.getName() == attributeName1
        } as Attribute)
        1 * metacard.setAttribute({
            it.getName() == attributeName2
        } as Attribute)
        0 * metacard.setAttribute({
            it.getName() == singleValuedAttributeName
        } as Attribute)

        // verify there are no other interactions with the metacard
        0 * metacard._
    }
}

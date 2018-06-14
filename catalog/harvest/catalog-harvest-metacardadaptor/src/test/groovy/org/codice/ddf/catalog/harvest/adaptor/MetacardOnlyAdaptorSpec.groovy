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

import ddf.catalog.CatalogFramework
import ddf.catalog.data.Metacard
import ddf.catalog.operation.*
import ddf.catalog.source.IngestException
import ddf.catalog.source.SourceUnavailableException
import org.codice.ddf.catalog.harvest.HarvestException
import org.codice.ddf.catalog.harvest.HarvestedResource
import spock.lang.Specification

import java.util.concurrent.Callable

class MetacardOnlyAdaptorSpec extends Specification {

    final CatalogFramework catalogFramework = Mock(CatalogFramework)

    final HarvestedResourceTransformer harvestedResourceTransformer = Mock(HarvestedResourceTransformer)

    final Map<String, Serializable> securityProperties = [securityPropretyName: "securityPropertyValue"]

    final MetacardOnlyAdaptor metacardOnlyAdaptor = new TestableMetacardOnlyAdaptor(catalogFramework, harvestedResourceTransformer)

    def 'test null constructor arguments'() {
        when:
        new MetacardOnlyAdaptor(catalogFramework, harvestedResourceTransformer)

        then:
        thrown NullPointerException

        where:
        catalogFramework       | harvestedResourceTransformer
        null                   | null
        null                   | Mock(HarvestedResourceTransformer)
        Mock(CatalogFramework) | null
    }

    def 'test create'() {
        given:
        final String metacardId = _

        final Map<String, Serializable> harvestedResourceProperties = [harvestedResourcePropretyName: "harvestedResourcePropertyValue"]

        1 * catalogFramework.create({
            it.getProperties() == harvestedResourceProperties + securityProperties
        } as CreateRequest) >> Mock(CreateResponse) {
            getCreatedMetacards() >> [Mock(Metacard) {
                getId() >> metacardId
            }]
        }

        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getProperties() >> harvestedResourceProperties
        }

        expect:
        metacardOnlyAdaptor.create(harvestedResource) == metacardId
    }

    def 'test unexpected number of metacards created'() {
        given:
        1 * catalogFramework.create(_ as CreateRequest) >> Mock(CreateResponse) {
            getCreatedMetacards() >> createdMetacards
        }

        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getProperties() >> [:]
        }

        when:
        metacardOnlyAdaptor.create(harvestedResource)

        then:
        thrown HarvestException

        where:
        createdMetacards << [[], [Mock(Metacard), Mock(Metacard)]]
    }

    def 'test CatalogFramework create exceptions'() {
        given:
        1 * catalogFramework.create(_ as CreateRequest) >> {
            throw exception
        }

        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getProperties() >> [:]
        }

        when:
        metacardOnlyAdaptor.create(harvestedResource)

        then:
        thrown HarvestException

        where:
        exception << [new IngestException(), new SourceUnavailableException()]
    }

    def 'test update'() {
        given:
        final Map<String, Serializable> harvestedResourceProperties = [harvestedResourcePropretyName: "harvestedResourcePropertyValue"]

        1 * catalogFramework.update({
            it.getProperties() == harvestedResourceProperties + securityProperties
        } as UpdateRequest) >> Mock(UpdateResponse) {
            getUpdatedMetacards() >> [Mock(Metacard)]
        }

        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getProperties() >> harvestedResourceProperties
        }

        when:
        metacardOnlyAdaptor.update(harvestedResource, _ as String)

        then:
        noExceptionThrown()
    }

    def 'test unexpected number of metacards updated'() {
        given:
        1 * catalogFramework.update(_ as UpdateRequest) >> Mock(UpdateResponse) {
            getUpdatedMetacards() >> updatedMetacards
        }

        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getProperties() >> [:]
        }

        when:
        metacardOnlyAdaptor.update(harvestedResource, _ as String)

        then:
        thrown HarvestException

        where:
        updatedMetacards << [[], [Mock(Metacard), Mock(Metacard)]]
    }

    def 'test CatalogFramework update exceptions'() {
        given:
        1 * catalogFramework.update(_ as UpdateRequest) >> {
            throw exception
        }

        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getProperties() >> [:]
        }

        when:
        metacardOnlyAdaptor.update(harvestedResource, _ as String)

        then:
        thrown HarvestException

        where:
        exception << [new IngestException(), new SourceUnavailableException()]
    }

    def 'test delete'() {
        given:
        1 * catalogFramework.delete({
            it.getProperties() == securityProperties
        } as DeleteRequest) >> Mock(DeleteResponse) {
            getDeletedMetacards() >> [Mock(Metacard)]
        }

        when:
        metacardOnlyAdaptor.delete(_ as String)

        then:
        noExceptionThrown()
    }

    def 'test unexpected number of metacards deleted'() {
        given:
        1 * catalogFramework.delete(_ as DeleteRequest) >> Mock(DeleteResponse) {
            getDeletedMetacards() >> deletedMetacards
        }

        when:
        metacardOnlyAdaptor.delete(_ as String)

        then:
        thrown HarvestException

        where:
        deletedMetacards << [[], [Mock(Metacard), Mock(Metacard)]]
    }

    def 'test CatalogFramework delete exceptions'() {
        given:
        1 * catalogFramework.delete(_ as DeleteRequest) >> { throw exception }

        when:
        metacardOnlyAdaptor.delete(_ as String)

        then:
        thrown HarvestException

        where:
        exception << [new IngestException(), new SourceUnavailableException()]
    }

    private class TestableMetacardOnlyAdaptor extends MetacardOnlyAdaptor {

        TestableMetacardOnlyAdaptor(CatalogFramework catalogFramework, HarvestedResourceTransformer harvestedResourceTransformer) {
            super(catalogFramework, harvestedResourceTransformer)
        }

        @Override
        <T> T runWithSubjectOrDefault(final Callable<T> callable) {
            return callable.call()
        }

        @Override
        Map<String, Serializable> getSecurityMap() {
            return securityProperties
        }
    }
}

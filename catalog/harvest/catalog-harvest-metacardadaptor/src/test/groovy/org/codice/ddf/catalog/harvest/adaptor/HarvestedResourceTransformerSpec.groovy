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
import ddf.catalog.data.impl.AttributeImpl
import ddf.catalog.data.impl.MetacardImpl
import ddf.catalog.transform.CatalogTransformerException
import ddf.catalog.transform.InputTransformer
import ddf.mime.MimeTypeMapper
import ddf.mime.MimeTypeResolutionException
import ddf.mime.MimeTypeToTransformerMapper
import org.codice.ddf.catalog.harvest.HarvestException
import org.codice.ddf.catalog.harvest.HarvestedResource
import spock.lang.Specification

import javax.activation.MimeType

class HarvestedResourceTransformerSpec extends Specification {

    final MimeTypeToTransformerMapper mimeTypeToTransformerMapper = Mock(MimeTypeToTransformerMapper)

    final MimeTypeMapper mimeTypeMapper = Mock(MimeTypeMapper)

    final AttributeRegistry attributeRegistry = Mock(AttributeRegistry)

    final HarvestedResourceTransformer harvestedResourceTransformer = new HarvestedResourceTransformer(mimeTypeToTransformerMapper, mimeTypeMapper, attributeRegistry)

    def 'test null constructor arguments'() {
        when:
        new HarvestedResourceTransformer(mimeTypeToTransformerMapper, mimeTypeMapper, attributeRegistry)

        then:
        thrown NullPointerException

        where:
        mimeTypeToTransformerMapper       | mimeTypeMapper       | attributeRegistry
        null                              | null                 | null
        null                              | null                 | Mock(AttributeRegistry)
        null                              | Mock(MimeTypeMapper) | null
        null                              | Mock(MimeTypeMapper) | Mock(AttributeRegistry)
        Mock(MimeTypeToTransformerMapper) | null                 | null
        Mock(MimeTypeToTransformerMapper) | null                 | Mock(AttributeRegistry)
        Mock(MimeTypeToTransformerMapper) | Mock(MimeTypeMapper) | null
    }

    def 'test without an id'() {
        given:
        final byte[] bytes = 'hello'.bytes
        final MimeType mimeType = Mock(MimeType)
        final String name = _
        final long size = bytes.size()
        final URI uri = new URI("something:3839ab393df930303#frag")
        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getInputStream() >> new ByteArrayInputStream(bytes)
            getMimeType() >> mimeType
            getName() >> name
            getSize() >> size
            getUri() >> uri
        }

        final Metacard generatedMetacard = new MetacardImpl()
        mimeTypeToTransformerMapper.findMatches(InputTransformer.class, mimeType) >> [Mock(InputTransformer) {
            transform(_ as InputStream) >> generatedMetacard
        }]

        when:
        final Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(harvestedResource)

        then:
        metacard == generatedMetacard
        with(metacard) {
            getTitle() == name
            Long.valueOf(getResourceSize()) == size
            getResourceURI() == uri
        }
    }

    def 'test with an id'() {
        given:
        final byte[] bytes = 'hello'.bytes
        final MimeType mimeType = Mock(MimeType)
        final String name = _
        final long size = bytes.size()
        final URI uri = new URI("something:3839ab393df930303#frag")

        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getInputStream() >> new ByteArrayInputStream(bytes)
            getMimeType() >> mimeType
            getName() >> name
            getSize() >> size
            getUri() >> uri
        }

        final String id = _
        final Metacard generatedMetacard = new MetacardImpl()
        generatedMetacard.setId(id)
        mimeTypeToTransformerMapper.findMatches(InputTransformer.class, mimeType) >> [Mock(InputTransformer) {
            transform(_ as InputStream, id) >> generatedMetacard
        }]

        when:
        final Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(harvestedResource, id)

        then:
        metacard == generatedMetacard
        with(metacard) {
            getTitle() == name
            Long.valueOf(getResourceSize()) == size
            getResourceURI() == uri
            getId() == id
        }
    }

    def 'test guess MimeType'() {
        given:
        final byte[] bytes = 'hello'.bytes
        final String name = "hello.txt"
        final long size = bytes.size()
        final URI uri = new URI("something:3839ab393df930303#frag")
        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getInputStream() >> new ByteArrayInputStream(bytes)
            getName() >> name
            getSize() >> size
            getUri() >> uri
        }

        final String mimeTypeString = "text/plain"
        final Metacard generatedMetacard = new MetacardImpl()
        mimeTypeToTransformerMapper.findMatches(InputTransformer.class, {
            it.toString() == mimeTypeString
        } as MimeType) >> [Mock(InputTransformer) {
            transform(_ as InputStream) >> generatedMetacard
        }]
        mimeTypeMapper.guessMimeType(_ as InputStream, "txt") >> mimeTypeString

        when:
        final Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(harvestedResource)

        then:
        metacard == generatedMetacard
        with(metacard) {
            getTitle() == name
            Long.valueOf(getResourceSize()) == size
            getResourceURI() == uri
        }
    }

    def 'test failure to guess MimeType'() {
        given:
        final byte[] bytes = 'hello'.bytes
        final String name = "hello.txt"
        final long size = bytes.size()
        final URI uri = new URI("something:3839ab393df930303#frag")
        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getInputStream() >> new ByteArrayInputStream(bytes)
            getName() >> name
            getSize() >> size
            getUri() >> uri
        }

        final Metacard generatedMetacard = new MetacardImpl()
        mimeTypeToTransformerMapper.findMatches(InputTransformer.class, null) >> [Mock(InputTransformer) {
            transform(_ as InputStream) >> generatedMetacard
        }]
        mimeTypeMapper.guessMimeType(_ as InputStream, "txt") >> null

        when:
        final Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(harvestedResource)

        then:
        metacard == generatedMetacard
        with(metacard) {
            getTitle() == name
            Long.valueOf(getResourceSize()) == size
            getResourceURI() == uri
        }
    }

    def 'test exception guessing MimeType'() {
        given:
        final byte[] bytes = 'hello'.bytes
        final String name = "hello.txt"
        final long size = bytes.size()
        final URI uri = new URI("something:3839ab393df930303#frag")
        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getInputStream() >> new ByteArrayInputStream(bytes)
            getName() >> name
            getSize() >> size
            getUri() >> uri
        }

        final Metacard generatedMetacard = new MetacardImpl()
        mimeTypeToTransformerMapper.findMatches(InputTransformer.class, null) >> [Mock(InputTransformer) {
            transform(_ as InputStream) >> generatedMetacard
        }]
        mimeTypeMapper.guessMimeType(_ as InputStream, "txt") >> { throw new MimeTypeResolutionException() }

        when:
        final Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(harvestedResource)

        then:
        metacard == generatedMetacard
        with(metacard) {
            getTitle() == name
            Long.valueOf(getResourceSize()) == size
            getResourceURI() == uri
        }
    }

    def 'test multiple InputTransformer matches'() {
        given:
        final byte[] bytes = 'hello'.bytes
        final MimeType mimeType = Mock(MimeType)
        final String name = _
        final long size = bytes.size()
        final URI uri = new URI("something:3839ab393df930303#frag")

        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getInputStream() >> new ByteArrayInputStream(bytes)
            getMimeType() >> mimeType
            getName() >> name
            getSize() >> size
            getUri() >> uri
        }

        final Metacard generatedMetacard = new MetacardImpl()
        mimeTypeToTransformerMapper.findMatches(InputTransformer.class, mimeType) >> [Mock(InputTransformer) {
            transform(_ as InputStream) >> { throw new CatalogTransformerException() }
        }, Mock(InputTransformer) {
            transform(_ as InputStream) >> { throw new CatalogTransformerException() }
        }, Mock(InputTransformer) {
            transform(_ as InputStream) >> generatedMetacard
        }, Mock(InputTransformer) {
            transform(_ as InputStream) >> { throw new CatalogTransformerException() }
        }]

        when:
        final Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(harvestedResource)

        then:
        metacard == generatedMetacard
        with(metacard) {
            getTitle() == name
            Long.valueOf(getResourceSize()) == size
            getResourceURI() == uri
        }
    }

    def 'test no InputTransformer matches'() {
        given:
        final MimeType mimeType = Mock(MimeType)
        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            final byte[] bytes = 'hello'.bytes

            getInputStream() >> new ByteArrayInputStream(bytes)
            getMimeType() >> mimeType
            getName() >> _
            getSize() >> bytes.size()
            getUri() >> new URI("something:3839ab393df930303#frag")
        }

        mimeTypeToTransformerMapper.findMatches(InputTransformer.class, mimeType) >> []

        when:
        harvestedResourceTransformer.transformHarvestedResource(harvestedResource)

        then:
        thrown HarvestException
    }

    def 'test InputTransformer IOException'() {
        given:
        final MimeType mimeType = Mock(MimeType)
        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            final byte[] bytes = 'hello'.bytes

            getInputStream() >> new ByteArrayInputStream(bytes)
            getMimeType() >> mimeType
            getName() >> _
            getSize() >> bytes.size()
            getUri() >> new URI("something:3839ab393df930303#frag")
        }

        // TODO If one of the InputTransformers throws and IOException, do we want to try the other ones?
        mimeTypeToTransformerMapper.findMatches(InputTransformer.class, mimeType) >> [Mock(InputTransformer) {
            transform(_ as InputStream) >> { throw new IOException() }
        }, Mock(InputTransformer) {
            transform(_ as InputStream) >> new MetacardImpl()
        }]

        when:
        harvestedResourceTransformer.transformHarvestedResource(harvestedResource)

        then:
        thrown HarvestException
    }

    def 'test existing attributes'() {
        given:
        final byte[] bytes = 'hello'.bytes
        final MimeType mimeType = Mock(MimeType)
        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getInputStream() >> new ByteArrayInputStream(bytes)
            getMimeType() >> mimeType
            getName() >> _
            getSize() >> bytes.size()
            getUri() >> new URI("something:134t4h46y456y#qual")
        }

        final Metacard generatedMetacard = Mock(Metacard) {
            getAttribute(_) >> { return Mock(Attribute) }
        }
        mimeTypeToTransformerMapper.findMatches(InputTransformer.class, mimeType) >> [Mock(InputTransformer) {
            transform(_ as InputStream) >> generatedMetacard
        }]

        when:
        final Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(harvestedResource)

        then: "metacard attributes should not be not overridden with details from the HarvestedResource"
        metacard == generatedMetacard
        0 * generatedMetacard.setAttribute(_)
    }

    def 'test attribute overrides'() {
        given:
        final String attributeName = "attributeName"
        final List<String> attributeValue = ["value"]
        final String overrideAttributeName = "overrideAttributeName"
        final List<String> overrideAttributeValues = ["new value"]

        final byte[] bytes = 'hello'.bytes
        final MimeType mimeType = Mock(MimeType)
        final String name = _
        final long size = bytes.size()
        final URI uri = new URI("something:3839ab393df930303#frag")
        final HarvestedResource harvestedResource = Mock(HarvestedResource) {
            getInputStream() >> new ByteArrayInputStream(bytes)
            getMimeType() >> mimeType
            getName() >> name
            getSize() >> size
            getUri() >> uri
            getAttributeOverrides() >> [(attributeName): attributeValue, (overrideAttributeName): overrideAttributeValues]
        }

        final Metacard generatedMetacard = new MetacardImpl()
        generatedMetacard.setAttribute(new AttributeImpl(overrideAttributeName, ["this value will be overridden"]))
        mimeTypeToTransformerMapper.findMatches(InputTransformer.class, mimeType) >> [Mock(InputTransformer) {
            transform(_ as InputStream) >> generatedMetacard
        }]
        attributeRegistry.lookup(attributeName) >> Optional.of(Mock(AttributeDescriptor) {
            isMultiValued() >> true
            getType() >> Mock(AttributeType) {
                getAttributeFormat() >> AttributeType.AttributeFormat.STRING
            }
        })
        attributeRegistry.lookup(overrideAttributeName) >> Optional.of(Mock(AttributeDescriptor) {
            isMultiValued() >> true
            getType() >> Mock(AttributeType) {
                getAttributeFormat() >> AttributeType.AttributeFormat.STRING
            }
        })

        when:
        final Metacard metacard = harvestedResourceTransformer.transformHarvestedResource(harvestedResource)

        then:
        metacard == generatedMetacard
        with(metacard) {
            getTitle() == name
            Long.valueOf(getResourceSize()) == size
            getResourceURI() == uri
            getAttribute(attributeName).getValues() == attributeValue
            getAttribute(overrideAttributeName).getValues() == overrideAttributeValues
        }
    }
}

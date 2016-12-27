/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.ddf.catalog.async.processingframework.api.internal;

import org.codice.ddf.catalog.async.data.api.internal.ProcessDeleteItem;
import org.codice.ddf.catalog.async.data.api.internal.ProcessRequest;
import org.codice.ddf.catalog.async.data.api.internal.ProcessResourceItem;

/**
 * <p>
 * <b> This code is experimental. While this interface is functional and tested, it may change or be
 * removed in a future version of the library. </b>
 * </p>
 * <p>
 * The {@code ProcessingFramework} processes all requests submitted to it. Available requests for processing
 * are as follows:
 * <ul>
 * <li>{@link ProcessRequest<ProcessResourceItem>}</li>
 * <li>{@link ProcessRequest<ProcessDeleteItem>}</li>
 * </ul>
 */
public interface ProcessingFramework {

    /**
     * Submits a {@link ProcessRequest<ProcessResourceItem>} to be processed by the {@code ProcessingFramework}.
     *
     * @param input the {@code ProcessUpdateRequest} to be processed
     */
    void submit(ProcessRequest<ProcessResourceItem> input);

    /**
     * Submits a {@link ProcessRequest<ProcessDeleteItem>} to be processed by the {@code ProcessingFramework}.
     *
     * @param input the {@code ProcessDeleteRequest} to be processed
     */
    void submitDelete(ProcessRequest<ProcessDeleteItem> input);
}

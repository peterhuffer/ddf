/**
 * Copyright (c) Codice Foundation
 * <p/>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.catalog.transformer.nitf;

/*
import ddf.catalog.data.Metacard;
import ddf.catalog.data.dynamic.api.MetacardFactory;
import ddf.catalog.data.dynamic.api.MetacardPropertyDescriptor;
import org.apache.commons.beanutils.LazyDynaClass;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
*/

/**
 * Declaration of "nitf" metacard constants. The actual definition of the metacard is handled
 * by the xml files.
 */
public interface Nitf {
    public static final String NAME = "ddf/catalog/transformer/nitf";

    public static final String NITF_VERSION = "version";

    public static final String FILE_DATE_TIME = "fileDateTime";

    public static final String FILE_TITLE = "fileTitle";

    /* File Size in Bytes*/
    public static final String FILE_SIZE = "fileSize";

    public static final String COMPLEXITY_LEVEL = "complexityLevel";

    public static final String ORIGINATOR_NAME = "originatorName";

    public static final String ORIGINATING_STATION_ID = "originatingStationId";

    public static final String IMAGE_ID = "imageId";

    public static final String ISOURCE = "isource";

    public static final String NUMBER_OF_ROWS = "numberOfRows";

    public static final String NUMBER_OF_COLUMNS = "numberOfColumns";

    public static final String NUMBER_OF_BANDS = "numberOfBands";

    public static final String NUMBER_OF_MULTISPECTRAL_BANDS = "numberOfMultispectralBands";

    public static final String REPRESENTATION = "representation";

    public static final String SUBCATEGORY = "subcategory";

    public static final String BITS_PER_PIXEL_PER_BAND = "bitsPerPixelPerBand";

    public static final String IMAGE_MODE = "imageMode";

    public static final String COMPRESSION = "compression";

    public static final String RATE_CODE = "rateCode";

    public static final String TARGET_ID = "targetId";

    public static final String COMMENT = "comment";

    /* NITF Security */
    public static final String CODE_WORDS = "codeWords";

    public static final String CONTROL_CODE = "controlCode";

    public static final String RELEASE_INSTRUCTION = "releaseInstruction";

    public static final String CONTROL_NUMBER = "controlNumber";

    public static final String CLASSIFICATION_SYSTEM = "system";

    public static final String CLASSIFICATION_AUTHORITY = "authority";

    public static final String CLASSIFICATION_AUTHORITY_TYPE = "authorityType";

    public static final String CLASSIFICATION_TEXT = "text";

    public static final String CLASSIFICATION_REASON = "reason";

    public static final String CLASSIFICATION_DATE = "classificationDate";

    public static final String DECLASSIFICATION_TYPE = "declassificationType";

    public static final String DECLASSIFICATION_DATE = "declassificationDate";

    public static final String SECURITY = "";

}

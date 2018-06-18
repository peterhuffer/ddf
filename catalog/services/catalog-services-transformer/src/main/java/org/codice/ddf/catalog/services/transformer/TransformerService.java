package org.codice.ddf.catalog.services.transformer;

import ddf.catalog.data.Metacard;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.mime.MimeTypeResolutionException;
import java.io.IOException;
import java.io.InputStream;
import javax.activation.MimeType;

/**
 * Creates a {@link Metacard} from {@link ddf.catalog.transform.InputTransformer}s.
 *
 * <p>This code is experimental. While this interface is functional and tested, it may change or be
 * removed in a future version of the library.
 */
public interface TransformerService {

  Metacard tranform(InputStream inputStream) throws IOException, CatalogTransformerException;

  Metacard tranform(InputStream inputStream, MimeType mimeType)
      throws IOException, CatalogTransformerException;

  Metacard transform(InputStream inputStream, String fileExtension)
      throws IOException, CatalogTransformerException, MimeTypeResolutionException;
}

package org.codice.ddf.catalog.services.transformer;

import ddf.catalog.data.Metacard;
import ddf.catalog.transform.CatalogTransformerException;
import ddf.catalog.transform.InputTransformer;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import javax.activation.MimeType;
import org.apache.commons.lang3.Validate;

public class TransformerServiceImpl implements TransformerService {

  Set<InputTransformer> inputTransformers;

  public TransformerServiceImpl(Set<InputTransformer> inputTransformers) {
    Validate.notEmpty(inputTransformers, "Argument inputTransformers cannot be null or empty");
    this.inputTransformers = inputTransformers;
  }

  @Override
  public Metacard tranform(InputStream inputStream)
      throws IOException, CatalogTransformerException {
    return null;
  }

  @Override
  public Metacard tranform(InputStream inputStream, MimeType mimeType)
      throws IOException, CatalogTransformerException {
    return null;
  }

  @Override
  public Metacard transform(InputStream inputStream, String fileExtension)
      throws IOException, CatalogTransformerException {
    return null;
  }
}

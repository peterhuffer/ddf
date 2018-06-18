package org.codice.ddf.catalog.services.transformer.osgi;

import com.google.common.collect.ImmutableSet;
import ddf.catalog.Constants;
import ddf.catalog.data.Metacard;
import ddf.catalog.transform.InputTransformer;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.activation.MimeType;
import org.codice.ddf.catalog.services.transformer.TransformerService;
import org.codice.ddf.catalog.services.transformer.TransformerServiceImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for {@link TransformerService}s that create {@link Metacard}s from registered {@link
 * ddf.catalog.transform.InputTransformer} OSGi services.
 */
public class OsgiTransformers {

  private static final Logger LOGGER = LoggerFactory.getLogger(OsgiTransformers.class);

  private static final String TRANSFORMER_SERVICE_ID_KEY = Constants.SERVICE_ID;

  private OsgiTransformers() {}

  /**
   * Will attempt to transform against all registered {@link
   * ddf.catalog.transform.InputTransformer}s.
   *
   * @return {@link TransformerService}
   */
  public TransformerService withAllTransformers() {
    return createTransformerServiceFromFilter(null);
  }

  /**
   * Convenience for the common case of selecting a {@link ddf.catalog.transform.InputTransformer}
   * by its "id" service property.
   *
   * @param value id of the {@link ddf.catalog.transform.InputTransformer}
   * @return {@link TransformerService}
   */
  public TransformerService withTransformerId(Object value) {
    return withServiceProperty(TRANSFORMER_SERVICE_ID_KEY, value);
  }

  /**
   * Creates a {@link TransformerService} containing all {@link
   * ddf.catalog.transform.InputTransformer}s that contain the service property.
   *
   * @param key key of the service property
   * @param value value of the service property
   * @return {@link TransformerService}
   */
  public TransformerService withServiceProperty(String key, Object value) {
    return createTransformerServiceFromFilter(buildLdapFilter(key, value));
  }

  // TODO: phuffer - how to do with MimeTypeToTransformerMapper service?
  public TransformerService withMimeType(MimeType mimeType) {
    return null;
  }

  private String buildLdapFilter(String key, Object value) {
    return String.format("(|(%s=%s))", key, value);
  }

  private TransformerService createTransformerServiceFromFilter(String filter) {
    return new TransformerServiceImpl(getInputTransformersByFilter(filter));
  }

  private Set<InputTransformer> getInputTransformersByFilter(String filter) {
    final BundleContext bundleContext = getBundleContext();
    final Set<InputTransformer> inputTransformers;
    try {
      inputTransformers =
          bundleContext
              .getServiceReferences(InputTransformer.class, filter)
              .stream()
              .map(bundleContext::getService)
              .filter(Objects::nonNull)
              .collect(Collectors.toSet());
    } catch (InvalidSyntaxException e) {
      LOGGER.debug("Invalid service reference filter provided", e);
      return Collections.emptySet();
    }

    return ImmutableSet.copyOf(inputTransformers);
  }

  private BundleContext getBundleContext() {
    Bundle bundle = FrameworkUtil.getBundle(OsgiTransformers.class);
    if (bundle != null) {
      BundleContext bundleContext = bundle.getBundleContext();
      if (bundleContext != null) {
        return bundleContext;
      }
    }

    throw new IllegalStateException("No bundle context available");
  }
}

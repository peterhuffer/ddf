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
package org.codice.ddf.catalog.harvest.listeners;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.codice.ddf.catalog.harvest.Harvester;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A helper class for the {@link PersistentListener} that will be notified when certain services are
 * registered (in this case {@link org.codice.ddf.catalog.harvest.Harvester} services). Since {@link
 * PersistentListener} are created through a Managed Service Factory, this bean will be injected
 * into each service that is created so that the {@link PersistentListener} can effectively listen
 * for registration and un-registration events of {@link Harvester} services.
 */
public class PersistentListenerServiceNotifier {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(PersistentListenerServiceNotifier.class);

  private Set<PersistentListener> listeners = Collections.synchronizedSet(new HashSet<>());

  private Set<Harvester> harvesters = Collections.synchronizedSet(new HashSet<>());

  public PersistentListenerServiceNotifier() {
    setInitialHarvesters();
  }

  /**
   * Retrieves the {@link Harvester} from the service reference and notifies any {@link
   * PersistentListener}s of the newly registered {@link Harvester}.
   *
   * @param ref {@link Harvester} service reference being registered in the OSGi service registry
   */
  public void bind(ServiceReference<Harvester> ref) {
    if (ref != null) {
      Optional<BundleContext> bundleContextOpt = getBundleContext();
      if (bundleContextOpt.isPresent()) {
        Harvester harvester = bundleContextOpt.get().getService(ref);
        if (harvester != null) {
          harvesters.add(harvester);
          listeners.forEach(listener -> listener.registerHarvester(harvester));
        }
      }
    }
  }

  public void unbind(ServiceReference<Harvester> ref) {
    if (ref != null) {
      Optional<BundleContext> bundleContextOpt = getBundleContext();
      if (bundleContextOpt.isPresent()) {
        Harvester harvester = bundleContextOpt.get().getService(ref);
        harvesters.remove(harvester);
      }
    }
  }

  /**
   * A {@link PersistentListener} must be added to start being notified of registered {@link
   * Harvester} services. It will be notified first of any existing {@link Harvester}s.
   *
   * @param listener listener to start notifying
   */
  public void addListener(PersistentListener listener) {
    listeners.add(listener);
    notifyListenerOfExistingHarvesters(listener);
  }

  public void removeListener(PersistentListener listener) {
    listeners.remove(listener);
    harvesters.forEach(listener::unregisterHarvester);
  }

  private void notifyListenerOfExistingHarvesters(PersistentListener listener) {
    harvesters.forEach(listener::registerHarvester);
  }

  private void setInitialHarvesters() {
    Optional<BundleContext> bundleContextOpt = getBundleContext();
    if (bundleContextOpt.isPresent()) {
      BundleContext bundleContext = bundleContextOpt.get();
      try {
        Collection<ServiceReference<Harvester>> serviceReferences =
            bundleContext.getServiceReferences(Harvester.class, null);

        for (ServiceReference<Harvester> harvesterReference : serviceReferences) {
          Harvester harvester = bundleContext.getService(harvesterReference);
          if (harvester != null) {
            harvesters.add(harvester);
          }
        }
      } catch (InvalidSyntaxException e) {
        // Should never hit this since the filter is null
        LOGGER.debug("Error fetching initial Harvester services.", e);
      }
    }
  }

  private Optional<BundleContext> getBundleContext() {
    Bundle bundle = FrameworkUtil.getBundle(PersistentListenerServiceNotifier.class);
    if (bundle != null) {
      BundleContext context = bundle.getBundleContext();
      if (context != null) {
        return Optional.of(context);
      }
    }

    return Optional.empty();
  }
}

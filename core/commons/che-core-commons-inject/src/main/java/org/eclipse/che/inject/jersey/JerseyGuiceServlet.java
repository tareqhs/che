/*
 * Copyright (c) 2012-2018 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.inject.jersey;

import com.google.inject.Binding;
import com.google.inject.Injector;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Provider;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.ws.rs.Path;
import org.glassfish.hk2.api.Factory;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.internal.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prepares the jersey application.
 *
 * @author tareq.sha@gmail.com
 */
public class JerseyGuiceServlet extends ServletContainer {
  private static final long serialVersionUID = 1L;

  private static final Logger LOG = LoggerFactory.getLogger(JerseyGuiceServlet.class);
  static final String RESOURCE_CONFIG_ATTRIBUTE = "aaa";

  @Override
  public void init(ServletConfig cfg) throws ServletException {
    // take the global JAX-RS configuration and use it at servlet level
    ResourceConfig app = createResourceConfig(cfg.getServletContext());
    Utils.store(app, cfg.getServletContext(), cfg.getServletName());
    super.init(cfg);
  }

  /**
   * Create a configuration that collects all JAX-RS services and providers that are bound in the
   * given injector, delegating their instantiation to the Guice providers.
   */
  protected ResourceConfig createResourceConfig(ServletContext sc) {
    ResourceConfig app = new ResourceConfig();
    GuiceHk2Binder binder = new GuiceHk2Binder();
    Injector inj = (Injector) sc.getAttribute(Injector.class.getName());
    inj.getBindings().values().forEach(binding -> processBinding(binding, app, binder));
    app.register(binder);
    return app;
  }

  private <T> void processBinding(Binding<T> binding, ResourceConfig app, GuiceHk2Binder binder) {
    Type type = binding.getKey().getTypeLiteral().getType();
    if (type instanceof Class) {
      @SuppressWarnings("unchecked")
      Class<T> clazz = (Class<T>) type;
      // if the type is a service register an adaptive factory to Guice
      if (clazz.getAnnotation(Path.class) != null
          || clazz.getAnnotation(javax.ws.rs.ext.Provider.class) != null) {
        // register the class in Jersey configuration
        app.register(clazz);
        // bind a delegating factory so that jersey uses Guice to instantiate it
        binder.bindings.add(b -> b.bindFactory(asFactory(binding.getProvider())).to(clazz));
        LOG.debug("registered in jersey: {}", clazz);
      }
    }
  }

  private static class GuiceHk2Binder extends AbstractBinder {
    List<Consumer<GuiceHk2Binder>> bindings = new LinkedList<>();

    @Override
    protected void configure() {
      bindings.forEach(b -> b.accept(this));
      bindings = null; // free up
    }
  }

  /** Create an HK2 factory that delegates to the given provider. */
  private static <T> Factory<T> asFactory(Provider<T> prov) {
    return new Factory<T>() {
      @Override
      public T provide() {
        return prov.get();
      }

      @Override
      public void dispose(T instance) {}
    };
  }
}

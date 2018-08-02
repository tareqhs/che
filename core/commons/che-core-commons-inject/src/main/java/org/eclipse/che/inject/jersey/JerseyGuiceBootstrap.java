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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletContext;

/**
 * Prepares the jersey application.
 *
 * @author tareq.sha@gmail.com
 */
public abstract class JerseyGuiceBootstrap extends GuiceServletContextListener {
  // this acts as a drop-in replacement for EverrestGuiceContextListener

  protected Injector getInjector(ServletContext servletContext) {
    return (Injector) servletContext.getAttribute(Injector.class.getName());
  }

  @Override
  protected final Injector getInjector() {
    return Guice.createInjector(Stage.PRODUCTION, createModules());
  }

  private List<Module> createModules() {
    List<Module> all = new ArrayList<>();
    ServletModule servletModule = getServletModule();
    if (servletModule != null) {
      all.add(servletModule);
    }
    // TODO replace these
    // all.add(new EverrestModule());
    // all.add(new EverrestConfigurationModule());
    List<Module> modules = getModules();
    if (modules != null && modules.size() > 0) {
      all.addAll(modules);
    }
    return all;
  }

  /** Get the dependency injection modules to load into the injector. */
  protected abstract List<Module> getModules();

  /** Create a default ServletModule that serves everything with JerseyGuiceServlet. */
  protected ServletModule getServletModule() {
    return new ServletModule() {
      @Override
      protected void configureServlets() {
        serve("/*").with(JerseyGuiceServlet.class);
      }
    };
  }
}

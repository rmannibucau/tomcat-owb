/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tomcat.webbeans;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;

import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Valve;
import org.apache.tomcat.InstanceManager;
import org.apache.webbeans.servlet.WebBeansConfigurationListener;


/**
 * Context lifecycle listener. Adapted from
 * OpenEJB Tomcat and updated.
 * 
 * @version $Rev$ $Date$
 *
 */
public class ContextLifecycleListener implements LifecycleListener {

    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        try {
            if (event.getSource() instanceof Context) {
                Context context = (Context) event.getSource();
                if (event.getType().equals(Lifecycle.CONFIGURE_START_EVENT)) {
                    ServletContext scontext = context.getServletContext();
                    URL url = getBeansXml(scontext);
                    if (url != null) {
                        // Registering ELResolver with JSP container
                        System.setProperty(
                                "org.apache.webbeans.application.jsp", "true");

                        addOwbListeners(context);
                        addOwbValves(context.getPipeline());
                    }
                }
            } else if (event.getType().equals(Lifecycle.START_EVENT) && event.getSource() instanceof Pipeline) {
                // This notification occurs once the configuration is fully done, including naming resources setup
                // Otherwise, the instance manager is not ready for creation
                if (((Pipeline) event.getSource()).getContainer() instanceof Context) {
                    wrapInstanceManager((Context) ((Pipeline) event.getSource()).getContainer());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void addOwbListeners(Context context) {
        String[] oldListeners = context.findApplicationListeners();
        LinkedList<String> listeners = new LinkedList<>();

        listeners.addFirst(WebBeansConfigurationListener.class.getName());

        for (String listener : oldListeners) {
            listeners.add(listener);
            context.removeApplicationListener(listener);
        }

        for (String listener : listeners) {
            context.addApplicationListener(listener);
        }
    }

    private void addOwbValves(Pipeline pipeline) {
        boolean securityValveFound = false;
        for (Valve valve : pipeline.getValves()) {
            if (valve instanceof TomcatSecurityValve) {
                securityValveFound = true;
            }
        }
        if (!securityValveFound) {
            pipeline.addValve(new TomcatSecurityValve());
        }
        // Add to the corresponding pipeline to get a notification once configure is done
        if (pipeline instanceof Lifecycle) {
            boolean contextLifecycleListenerFound = false;
            for (LifecycleListener listener : ((Lifecycle) pipeline).findLifecycleListeners()) {
                if (listener instanceof ContextLifecycleListener) {
                    contextLifecycleListenerFound = true;
                }
            }
            if (!contextLifecycleListenerFound) {
                ((Lifecycle) pipeline).addLifecycleListener(this);
            }
        }
    }

    private URL getBeansXml(ServletContext scontext)
            throws MalformedURLException {
        URL url = scontext.getResource("/WEB-INF/beans.xml");
        if (url == null) {
            url = scontext.getResource("/WEB-INF/classes/META-INF/beans.xml");
        }
        return url;
    }

    private void wrapInstanceManager(Context context) {
        if (context.getInstanceManager() instanceof TomcatInstanceManager) {
            return;
        }
        InstanceManager processor = context.getInstanceManager();
        if (processor == null) {
            processor = context.createInstanceManager();
        }
        InstanceManager custom = new TomcatInstanceManager(
                context.getLoader().getClassLoader(), processor);
        context.setInstanceManager(custom);

        context.getServletContext()
                .setAttribute(InstanceManager.class.getName(), custom);
    }

}

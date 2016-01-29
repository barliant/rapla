package org.rapla.server.internal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.rapla.entities.DependencyException;
import org.rapla.entities.configuration.internal.RaplaMapImpl;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.jsonrpc.server.JsonServlet;
import org.rapla.jsonrpc.server.WebserviceCreator;
import org.rapla.jsonrpc.server.WebserviceCreatorMap;
import org.rapla.storage.RaplaSecurityException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class RaplaRpcAndRestProcessor
{
    //final ServerServiceContainer serverContainer;
    final Map<String, WebserviceCreator> webserviceMap;
    Logger logger;

    public RaplaRpcAndRestProcessor(Logger logger, WebserviceCreatorMap webservices)
    {
        this.logger = logger;
        this.webserviceMap = webservices.asMap();
    }

    Map<Class, JsonServlet> servletMap = new HashMap<Class, JsonServlet>();

    public class Path
    {
        final String path;
        final String subPath;

        Path(String path, String subPath)
        {
            this.path = path;
            this.subPath = subPath;
        }
    }

    public Path find(HttpServletRequest request, String page)
    {
        String path = null;
        String appendix = null;
        String requestURI = request.getPathInfo();
        String subPath;
//        if ( page != null)
//        {
//            subPath = page;
//        }
//        else 
            if (requestURI != null && requestURI.startsWith("/rapla/"))
        {
            subPath = requestURI.substring("/rapla/".length());
        }
        else if (requestURI != null && requestURI.length() > 0)
        {
            subPath = requestURI.substring(1);
        }
        else
        {
            return  null;
        }
        for (String key : webserviceMap.keySet())
        {
            if (subPath.startsWith(key))
            {
                path = key;
                if (subPath.length() > key.length())
                {
                    appendix = subPath.substring(key.length() + 1);
                }
            }
        }
        if (path == null)
        {
            return null;
        }
        return new Path(path, appendix);
    }

    public void generate(ServletContext servletContext, HttpServletRequest request, HttpServletResponse response, Path path) throws IOException
    {
        final WebserviceCreator webserviceCreator = webserviceMap.get(path.path);
        Class serviceClass = webserviceCreator.getServiceClass();
        // try to find a json servlet in map
        JsonServlet servlet = servletMap.get(serviceClass);
        if (servlet == null)
        {
            // try to create one from the service interface
            try
            {
                servlet = new RaplaJsonServlet(logger, serviceClass);
            }
            catch (Exception ex)
            {
                logger.error(ex.getMessage(), ex);
                JsonServlet.writeException(request, response, ex);
                return;
            }
            servletMap.put(serviceClass, servlet);
        }

        // instanciate the service Object
        Object impl;
        try
        {
            impl = webserviceCreator.create(request, response);
        }
        catch (RaplaSecurityException ex)
        {
            servlet.serviceError(request, response, servletContext, ex);
            return;
        }
        catch (RaplaException ex)
        {
            logger.error(ex.getMessage(), ex);
            servlet.serviceError(request, response, servletContext, ex);
            return;
        }
        // proccess the call with the servlet
        servlet.service(request, response, servletContext, impl, path.subPath);
    }

    class RaplaJsonServlet extends JsonServlet
    {
        Logger logger = null;

        public RaplaJsonServlet(Logger logger, Class class1) throws Exception
        {
            super(class1);
            this.logger = logger;
        }

        @Override protected JsonElement getParams(Throwable failure)
        {
            JsonArray params = null;
            if (failure instanceof DependencyException)
            {
                params = new JsonArray();
                for (String dep : ((DependencyException) failure).getDependencies())
                {
                    params.add(new JsonPrimitive(dep));
                }
            }
            return params;
        }

        @Override protected void debug(String childLoggerName, String out)
        {
            Logger childLogger = logger.getChildLogger(childLoggerName);
            if (childLogger.isDebugEnabled())
            {
                childLogger.debug(out);
            }
        }

        @Override protected void error(String message, Throwable ex)
        {
            logger.error(message, ex);
        }

        @Override protected boolean isSecurityException(Throwable i)
        {
            return i instanceof RaplaSecurityException;
        }

        @Override protected Class[] getAdditionalClasses()
        {
            return new Class[] { RaplaMapImpl.class };
        }
    }

}

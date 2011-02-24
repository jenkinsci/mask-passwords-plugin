/*
 * The MIT License
 *
 * Copyright (c) 2011, Manufacture Francaise des Pneumatiques Michelin,
 * Romain Seguy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.michelin.cio.hudson.plugins.maskpasswords;

import hudson.ExtensionList;
import hudson.PluginManager;
import hudson.XmlFile;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.model.ParameterValue;
import hudson.model.PasswordParameterDefinition;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Singleton class to manage Mask Passwords global settings.
 *
 * @author Romain Seguy  (http://openromain.blogspot.com)
 * @since 2.5
 */
public class MaskPasswordsConfig {

    private final static String CONFIG_FILE = "com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsConfig.xml";

    private static MaskPasswordsConfig config;

    /**
     * Contains the set of {@link ParameterDefinition}s whose value must be
     * masked in builds' console.
     */
    private Set<String> maskPasswordsParamDefClasses;
    /**
     * Contains the set of {@link ParameterValue}s whose value must be masked in
     * builds' console.
     */
    private transient Set<String> maskPasswordsParamValueClasses;

    public MaskPasswordsConfig() {
        maskPasswordsParamDefClasses = new LinkedHashSet<String>();

        // default values for the first time the config is created
        addMaskedPasswordParameterDefinition(PasswordParameterDefinition.class.getName());
    }

    /**
     * @param className The class name of a {@link ParameterDescriptor} to be added
     *                  to the list of parameters which will prevent the rebuild
     *                  action to be enabled for a build
     */
    public void addMaskedPasswordParameterDefinition(String className) {
        maskPasswordsParamDefClasses.add(className);
    }

    public void clear() {
        maskPasswordsParamDefClasses.clear();
    }

    public static MaskPasswordsConfig getInstance() {
        if(config == null) {
            config = load();
        }
        return config;
    }

    private static XmlFile getConfigFile() {
        return new XmlFile(new File(Hudson.getInstance().getRootDir(), CONFIG_FILE));
    }

    /**
     * Returns a map of all {@link ParameterDefinition}s that can be used in
     * jobs.
     *
     * <p>The key is the class name of the {@link ParameterDefinition}, the value
     * is its display name.</p>
     */
    public static Map<String, String> getParameterDefinitions() {
        Map<String, String> params = new HashMap<String, String>();

        ExtensionList<ParameterDefinition.ParameterDescriptor> paramExtensions =
                Hudson.getInstance().getExtensionList(ParameterDefinition.ParameterDescriptor.class);
        for(ParameterDefinition.ParameterDescriptor paramExtension: paramExtensions) {
            // we need the getEnclosingClass() to drop the inner ParameterDescriptor
            // and work directly with the ParameterDefinition
            params.put(paramExtension.getClass().getEnclosingClass().getName(), paramExtension.getDisplayName());
        }

        return params;
    }

    /**
     * Returns true if the specified parameter value class name corresponds to
     * a parameter definition class name selected in Hudson's/Jenkins' main
     * configuration screen.
     */
    public boolean isMasked(String paramValueClassName) {
        try {
            // do we need to build the set of parameter values which must be
            // masked?
            if(maskPasswordsParamValueClasses == null) {
                maskPasswordsParamValueClasses = new LinkedHashSet<String>();

                // The only way to find parameter definition/parameter value
                // couples is to reflect the 3 methods of parameter definition
                // classes which instantiate the parameter value.
                // This means that this algorithm expects that the developers do
                // clearly redefine the return type when implementing parameter
                // definitions/values.
                for(String paramDefClassName: maskPasswordsParamDefClasses) {
                    final Class paramDefClass = Hudson.getInstance().getPluginManager().uberClassLoader.loadClass(paramDefClassName);

                    List<Method> methods = new ArrayList<Method>() {{
                        // ParameterDefinition.getDefaultParameterValue()
                        try {
                            add(paramDefClass.getMethod("getDefaultParameterValue"));
                        } catch(Exception e) {
                            LOGGER.log(Level.INFO, "No getDefaultParameterValue(String) method for " + paramDefClass);
                        }
                        // ParameterDefinition.createValue(String)
                        try {
                            add(paramDefClass.getMethod("createValue", String.class));
                        } catch(Exception e) {
                            LOGGER.log(Level.INFO, "No createValue(String) method for " + paramDefClass);
                        }
                        // ParameterDefinition.createValue(org.kohsuke.stapler.StaplerRequest, net.sf.json.JSONObjec)
                        try {
                            add(paramDefClass.getMethod("createValue", StaplerRequest.class, JSONObject.class));
                        }  catch (Exception e) {
                            LOGGER.log(Level.INFO, "No createValue(StaplerRequest, JSONObject) method for " + paramDefClass);
                        }
                    }};

                    for(Method m: methods) {
                        maskPasswordsParamValueClasses.add(m.getReturnType().getName());
                    }
                }
            }
        }
        catch(Exception e) {
            LOGGER.log(Level.WARNING, "Error while initializing Mask Passwords: " + e);
            return false;
        }

        return maskPasswordsParamValueClasses.contains(paramValueClassName);
    }

    /**
     * Returns true if the specified parameter definition class name has been
     * selected in Hudson's/Jenkins' main configuration screen.
     */
    public boolean isSelected(String paramDefClassName) {
        return maskPasswordsParamDefClasses.contains(paramDefClassName);
    }

    public static MaskPasswordsConfig load() {
        LOGGER.entering(CLASS_NAME, "load");
        try {
            return (MaskPasswordsConfig) getConfigFile().read();
        }
        catch(FileNotFoundException e) {
            LOGGER.log(Level.WARNING, "No configuration found for Mask Passwords plugin");
        }
        catch(Exception e) {
            LOGGER.log(Level.WARNING, "Unable to load Mask Passwords plugin configuration from " + CONFIG_FILE, e);
        }

        return new MaskPasswordsConfig();
    }

    public static void save(MaskPasswordsConfig config) throws IOException {
        LOGGER.entering(CLASS_NAME, "save");
        getConfigFile().write(config);
        LOGGER.exiting(CLASS_NAME, "save");
    }

    private final static String CLASS_NAME = MaskPasswordsConfig.class.getName();
    private final static Logger LOGGER = Logger.getLogger(CLASS_NAME);

}

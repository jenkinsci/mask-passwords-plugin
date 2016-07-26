/*
 * The MIT License
 *
 * Copyright (c) 2011, Manufacture Francaise des Pneumatiques Michelin, Romain Seguy
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

import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper.VarPasswordPair;
import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper.VarMaskRegex;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.model.ParameterValue;
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
import org.apache.commons.lang.StringUtils;
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
    /**
     * Users can define name/password pairs at the global level to share common
     * passwords with several jobs.
     *
     * <p>Never ever use this attribute directly: Use {@link #getGlobalVarPasswordPairsList} to avoid
     * potential NPEs.</p>
     *
     * @since 2.7
     */
    private List<VarPasswordPair> globalVarPasswordPairs;
    /**
     * Users can define regexes at the global level to mask in jobs.
     *
     * <p>Never ever use this attribute directly: Use {@link #getGlobalVarPasswordPairsList} to avoid
     * potential NPEs.</p>
     *
     * @since 2.9
     */
    private List<VarMaskRegex> globalVarMaskRegexes;

    public MaskPasswordsConfig() {
        maskPasswordsParamDefClasses = new LinkedHashSet<String>();

        // default values for the first time the config is created
        addMaskedPasswordParameterDefinition(hudson.model.PasswordParameterDefinition.class.getName());
        addMaskedPasswordParameterDefinition(com.michelin.cio.hudson.plugins.passwordparam.PasswordParameterDefinition.class.getName());
    }

    /**
     * Adds a name/password pair at the global level.
     *
     * <p>If either name or password is blank (as defined per the Commons Lang
     * library), then the pair is not added.</p>
     *
     * @since 2.7
     */
    public void addGlobalVarPasswordPair(VarPasswordPair varPasswordPair) {
        // blank values are forbidden
        if(StringUtils.isBlank(varPasswordPair.getVar()) || StringUtils.isBlank(varPasswordPair.getPassword())) {
            return;
        }
        getGlobalVarPasswordPairsList().add(varPasswordPair);
    }

    /**
     * Adds a regex at the global level.
     *
     * <p>If regex is blank (as defined per the Commons Lang
     * library), then the pair is not added.</p>
     *
     * @since 2.9
     */
    public void addGlobalVarMaskRegex(VarMaskRegex varMaskRegex) {
        // blank values are forbidden
        if(StringUtils.isBlank(varMaskRegex.getRegex())) {
            return;
        }
        getGlobalVarMaskRegexesList().add(varMaskRegex);
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
        getGlobalVarPasswordPairsList().clear();
        getGlobalVarMaskRegexesList().clear();
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
     * Returns the list of name/password pairs defined at the global level.
     *
     * <p>Modifications broughts to the returned list has no impact on this
     * configuration (the returned value is a copy). Also, the list can be
     * empty but never {@code null}.</p>
     *
     * @since 2.7
     */
    public List<VarPasswordPair> getGlobalVarPasswordPairs() {
        List<VarPasswordPair> r = new ArrayList<VarPasswordPair>(getGlobalVarPasswordPairsList().size());

        // deep copy
        for(VarPasswordPair varPasswordPair: getGlobalVarPasswordPairsList()) {
            r.add((VarPasswordPair) varPasswordPair.clone());
        }

        return r;
    }

    /**
     * Returns the list of regexes defined at the global level.
     *
     * <p>Modifications broughts to the returned list has no impact on this
     * configuration (the returned value is a copy). Also, the list can be
     * empty but never {@code null}.</p>
     *
     * @since 2.9
     */
    public List<VarMaskRegex> getGlobalVarMaskRegexes() {
        List<VarMaskRegex> r = new ArrayList<VarMaskRegex>(getGlobalVarMaskRegexesList().size());

        // deep copy
        for(VarMaskRegex varMaskRegex: getGlobalVarMaskRegexesList()) {
            r.add((VarMaskRegex) varMaskRegex.clone());
        }

        return r;
    }

    /**
     * Fixes JENKINS-11514: When {@code MaskPasswordsConfig.xml} is there but was created from
     * version 2.6.1 (or older) of the plugin, {@link #globalVarPasswordPairs} can actually be
     * {@code null} ==> Always use this getter to avoid NPEs.
     *
     * @since 2.7.1
     */
    private List<VarPasswordPair> getGlobalVarPasswordPairsList() {
        if(globalVarPasswordPairs == null) {
            globalVarPasswordPairs = new ArrayList<VarPasswordPair>();
        }
        return globalVarPasswordPairs;
    }

    /**
     * Fixes JENKINS-11514: When {@code MaskPasswordsConfig.xml} is there but was created from
     * version 2.8 (or older) of the plugin, {@link #globalVarPasswordPairs} can actually be
     * {@code null} ==> Always use this getter to avoid NPEs.
     *
     * @since 2.9
     */
    private List<VarMaskRegex> getGlobalVarMaskRegexesList() {
        if(globalVarMaskRegexes == null) {
            globalVarMaskRegexes = new ArrayList<VarMaskRegex>();
        }
        return globalVarMaskRegexes;
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

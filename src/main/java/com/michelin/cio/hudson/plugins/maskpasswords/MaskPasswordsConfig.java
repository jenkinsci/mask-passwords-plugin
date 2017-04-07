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

import com.google.common.annotations.VisibleForTesting;
import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper.VarPasswordPair;
import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper.VarMaskRegex;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.cli.CLICommand;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.model.ParameterValue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Singleton class to manage Mask Passwords global settings.
 *
 * @author Romain Seguy  (http://openromain.blogspot.com)
 * @since 2.5
 */
public class MaskPasswordsConfig {

    private final static String CONFIG_FILE = "com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsConfig.xml";
    private static Object CONFIG_FILE_LOCK = new Object();
    @GuardedBy("CONFIG_FILE_LOCK")
    private static MaskPasswordsConfig config;

    /**
     * Contains the set of {@link ParameterDefinition}s whose value must be
     * masked in builds' console.
     */
    @GuardedBy("this")
    private Set<String> maskPasswordsParamDefClasses;
    /**
     * Contains the set of {@link ParameterValue}s whose value must be masked in
     * builds' console.
     */
    @Nonnull
    @GuardedBy("this")
    private final transient Set<String> paramValueCache_maskedClasses = new HashSet<String>();
    
    /**
     * Cache of values, which are not subjects for masking. 
     */
    @Nonnull
    @GuardedBy("this")
    private final transient Set<String> paramValueCache_nonMaskedClasses = new HashSet<String>();
    
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
     * <p>Never ever use this attribute directly: Use {@link #getGlobalVarMaskRegexes} to avoid
     * potential NPEs.</p>
     *
     * @since 2.9
     */
    private List<VarMaskRegex> globalVarMaskRegexes;
    /**
     * Whether or not to enable the plugin globally on ALL BUILDS.
     *
     * @since 2.9
     */
    private boolean globalVarEnableGlobally;

    public MaskPasswordsConfig() {
        maskPasswordsParamDefClasses = new LinkedHashSet<String>();
        reset();
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
            LOGGER.fine("addGlobalVarPasswordPair NOT adding pair with null var or password");
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
            LOGGER.fine("addGlobalVarMaskRegex NOT adding null regex");
            return;
        }
        getGlobalVarMaskRegexesList().add(varMaskRegex);
    }

    /**
     * @param className The class name of a {@link ParameterDescriptor} to be added
     *                  to the list of parameters which will prevent the rebuild
     *                  action to be enabled for a build
     */
    public synchronized void addMaskedPasswordParameterDefinition(String className) {
        maskPasswordsParamDefClasses.add(className);
        // Maybe is it masked now
        paramValueCache_nonMaskedClasses.clear();
    }

    public void setGlobalVarEnabledGlobally(boolean state) {
      globalVarEnableGlobally = state;
    }

    /**
     * Resets configuration to the default state.
     */
    @Restricted(NoExternalUse.class)
    @VisibleForTesting
    public final synchronized void reset() {
        // Wipe the data
        clear();
        
        // default values for the first time the config is created
        addMaskedPasswordParameterDefinition(hudson.model.PasswordParameterDefinition.class.getName());
        addMaskedPasswordParameterDefinition(com.michelin.cio.hudson.plugins.passwordparam.PasswordParameterDefinition.class.getName());
    }
    
    public synchronized void clear() {
        maskPasswordsParamDefClasses.clear();
        getGlobalVarPasswordPairsList().clear();
        getGlobalVarMaskRegexesList().clear();
        globalVarEnableGlobally = false;
        
        // Drop caches
        invalidatePasswordValueClassCaches();
    }
    
    /*package*/ synchronized void invalidatePasswordValueClassCaches() {
        paramValueCache_maskedClasses.clear();
        paramValueCache_nonMaskedClasses.clear();
    }

    public static MaskPasswordsConfig getInstance() {
        synchronized(CONFIG_FILE_LOCK) {
            if(config == null) {
                config = load();
            }
            return config;
        }
    }

    private static XmlFile getConfigFile() {
        return new XmlFile(new File(Jenkins.getActiveInstance().getRootDir(), CONFIG_FILE));
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
                Jenkins.getActiveInstance().getExtensionList(ParameterDefinition.ParameterDescriptor.class);
        for(ParameterDefinition.ParameterDescriptor paramExtension: paramExtensions) {
            // we need the getEnclosingClass() to drop the inner ParameterDescriptor
            // and work directly with the ParameterDefinition
            params.put(paramExtension.getClass().getEnclosingClass().getName(), paramExtension.getDisplayName());
        }

        return params;
    }

    /**
     * Returns whether the plugin is enabled globally for ALL BUILDS.
     */
    public boolean isEnabledGlobally() {
      return globalVarEnableGlobally;
    }

    /**
     * Check if the parameter value class needs to be masked
     * @deprecated There is a high risk of false-negatives. Use {@link #isMasked(hudson.model.ParameterValue, java.lang.String)} at least
     * @param paramValueClassName Class name of the {@link ParameterValue}
     * @return {@code true} if the parameter value should be masked.
     *         {@code false} if the plugin is not sure, may be false-negative 
     */
    @Deprecated
    public synchronized boolean isMasked(final @Nonnull String paramValueClassName) {
        return isMasked(null, paramValueClassName);
    }
    
    /**
     * Returns true if the specified parameter value class name corresponds to
     * a parameter definition class name selected in Jenkins' main
     * configuration screen.
     * @param value Parameter value. Without it there is a high risk of false negatives.
     * @param paramValueClassName Class name of the {@link ParameterValue} class implementation
     * @return {@code true} if the parameter value should be masked.
     *         {@code false} if the plugin is not sure, may be false-negative especially if the value is {@code null}.
     * @since TODO
     */
    public boolean isMasked(final @CheckForNull ParameterValue value, 
            final @Nonnull String paramValueClassName) {
        
        // We always mask sensitive variables, the configuration does not matter in such case
        if (value != null && value.isSensitive()) {
            return true;
        }
        
        synchronized(this) {
            // Check if the value is in the cache
            if (paramValueCache_maskedClasses.contains(paramValueClassName)) {
                return true;
            }
            if (paramValueCache_nonMaskedClasses.contains(paramValueClassName)) {
                return false;
            }
         
            // Now guess
            boolean guessSo = guessIfShouldMask(paramValueClassName);
            if (guessSo) {
                // We are pretty sure it requires masking
                paramValueCache_maskedClasses.add(paramValueClassName);
                return true;
            } else {
                // It does not require masking, but we are not so sure
                // The warning will be printed each time the cache is invalidated due to whatever reason
                LOGGER.log(Level.WARNING, "Identified the {0} class as a ParameterValue class, which does not require masking. It may be false-negative", paramValueClassName);
                paramValueCache_nonMaskedClasses.add(paramValueClassName);
                return false;
            }
        }
    }
    
    //TODO: add support of specifying masked parameter values byt the... parameter value classs name. So obvious, yeah?
    /**
     * Tries to guess if the parameter value class should be masked.
     * @param paramValueClassName Parameter value class name
     * @return {@code true} if we are sure that the class has to be masked
     *         {@code false} otherwise, there is a risk of false negative due to the presumptions.
     */
    /*package*/ synchronized boolean guessIfShouldMask(final @Nonnull String paramValueClassName) {
        // The only way to find parameter definition/parameter value
        // couples is to reflect the methods of parameter definition
        // classes which instantiate the parameter value.
        // This means that this algorithm expects that the developers do
        // clearly redefine the return type when implementing parameter
        // definitions/values.
        for(String paramDefClassName: maskPasswordsParamDefClasses) {
            final Class<?> paramDefClass;
            try {
                paramDefClass = Jenkins.getActiveInstance().getPluginManager().uberClassLoader.loadClass(paramDefClassName);
            } catch (ClassNotFoundException ex) {
                LOGGER.log(Level.WARNING, "Cannot check ParamDef for masking " + paramDefClassName, ex);
                continue;
            }

            tryProcessMethod(paramDefClass, "getDefaultParameterValue", true);
            tryProcessMethod(paramDefClass, "createValue", true, StaplerRequest.class, JSONObject.class);
            tryProcessMethod(paramDefClass, "createValue", true, StaplerRequest.class);
            tryProcessMethod(paramDefClass, "createValue", true, CLICommand.class, String.class);
            // This custom implementation is not a part of the API, but let's try it
            tryProcessMethod(paramDefClass, "createValue", false, String.class);
            
            // If the parameter value class has been added to the cache, exit
            if (paramValueCache_maskedClasses.contains(paramValueClassName)) {
                return true;
            }
        }
        
        // Always mask the hudson.model.PasswordParameterValue class and its overrides
        // This class does not comply with the criteria above, but it is sensitive starting from 1.378
        final Class<?> valueClass;
        try {
            valueClass = Jenkins.getActiveInstance().getPluginManager().uberClassLoader.loadClass(paramValueClassName);
        } catch (Exception ex) {
            // Move on. Whatever happens here, it will blow up somewhere else
            LOGGER.log(Level.FINE, "Failed to load class for the ParameterValue " + paramValueClassName, ex);
            return false;
        }
        
        return hudson.model.PasswordParameterValue.class.isAssignableFrom(valueClass);
    }
    
    /**
     * Processes the methods in the {@link ParameterValue} class and caches all ParameterValue implementations as ones requiring masking.
     * @param clazz Class
     * @param methodName Method name
     * @param parameterTypes Parameters
     */
    private synchronized void tryProcessMethod(Class<?> clazz, String methodName, boolean expectedToExist, Class<?> ... parameterTypes) {
        
        final Method method;
        try {
            method = clazz.getMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException ex) {
            Level logLevel = expectedToExist ? Level.INFO : Level.CONFIG;
            if (LOGGER.isLoggable(logLevel)) {
                String methodSpec = String.format("%s(%s)", methodName, StringUtils.join(parameterTypes, ","));
                LOGGER.log(logLevel, "No method {0} for class {1}", new Object[] {methodSpec, clazz});
            }
            return;
        } catch (RuntimeException ex) {
            Level logLevel = expectedToExist ? Level.INFO : Level.CONFIG;
            if (LOGGER.isLoggable(logLevel)) {
                String methodSpec = String.format("%s(%s)", methodName, StringUtils.join(parameterTypes, ","));
                LOGGER.log(logLevel, "Failed to retrieve the method {0} for class {1}", new Object[] {methodSpec, clazz});
            }
            return;
        }
        
        Class<?> returnType = method.getReturnType();
            // We do not veto the the root class
            if (ParameterValue.class.isAssignableFrom(returnType)) {
                if (!ParameterValue.class.equals(returnType)) {
                    // Add this class to the cache
                    paramValueCache_maskedClasses.add(returnType.getName());
                }
            }
    }

    /**
     * Returns true if the specified parameter definition class name has been
     * selected in Jenkins main configuration screen.
     */
    public synchronized boolean isSelected(String paramDefClassName) {
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
        LOGGER.log(Level.FINE, "No Mask Passwords config file loaded; using defaults");
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

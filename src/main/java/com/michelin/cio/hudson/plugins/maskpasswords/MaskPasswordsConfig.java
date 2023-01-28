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
import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper.VarMaskRegex;
import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper.VarPasswordPair;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.cli.CLICommand;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterDefinition.ParameterDescriptor;
import hudson.model.ParameterValue;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.structs.describable.CustomDescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.concurrent.GuardedBy;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    @NonNull
    @GuardedBy("this")
    private transient Set<String> paramValueCache_maskedClasses = new HashSet<>();
    
    /**
     * Cache of values, which are not subjects for masking. 
     */
    @NonNull
    @GuardedBy("this")
    private transient Set<String> paramValueCache_nonMaskedClasses = new HashSet<>();
    
    /**
     * Users can define key/password pairs at the global level to share common
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
     * <p>Never ever use this attribute directly: Use {@link #getGlobalVarMaskRegexesMap} to avoid
     * potential NPEs.</p>
     *
     * @since 2.9
     *
     * Deprecated in favor of globalVarMaskRegexesMap which has label names mapped to value's for better identification
     */
    @Deprecated
    public List<VarMaskRegex> globalVarMaskRegexes;
    public List<VarMaskRegexEntry> globalVarMaskRegexesU;
    public HashMap<String, VarMaskRegex> globalVarMaskRegexesMap;
    /**
     * Whether or not to enable the plugin globally on ALL BUILDS.
     *
     * @since 2.9
     */
    private boolean globalVarEnableGlobally;

    public MaskPasswordsConfig() {
        maskPasswordsParamDefClasses = new LinkedHashSet<>();
        reset();
    }
    
    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification = "readResolve()")
    private Object readResolve() {
        // Reinit caches
        synchronized(this) {
            if (paramValueCache_maskedClasses == null) {
                paramValueCache_maskedClasses = new HashSet<>();
            }
            if (paramValueCache_nonMaskedClasses == null) {
                paramValueCache_nonMaskedClasses = new HashSet<>();
            }
        }
       
     return this;
    }

    private boolean isGlobalVarMaskRegexesNull() {
        return (this.globalVarMaskRegexesMap == null) || (this.globalVarMaskRegexesU == null);
    }

    /**
     * Adds a key/password pair at the global level.
     *
     * <p>If either key or password is blank (as defined per the Commons Lang
     * library), then the pair is not added.</p>
     *
     * @since 2.7
     */
    public void addGlobalVarPasswordPair(VarPasswordPair varPasswordPair) {
        // blank values are forbidden
        if(StringUtils.isBlank(varPasswordPair.getVar()) || varPasswordPair.getPlainTextPassword() == null) {
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
        addGlobalVarMaskRegex("", varMaskRegex);
    }

    public void addGlobalVarMaskRegex(String name, String regex) {
        addGlobalVarMaskRegex(name, new VarMaskRegex(regex));
    }

    public void addGlobalVarMaskRegex(String name, VarMaskRegex varMaskRegex) {
        // blank values are forbidden
        if(StringUtils.isBlank(varMaskRegex.getRegex())) {
            LOGGER.fine("addGlobalVarMaskRegex NOT adding null regex");
            return;
        }
        // blank values are forbidden, will give default numbered key name
        if(StringUtils.isBlank(name)) {
            LOGGER.fine("Generating default numbered key for VarMaskRegex");
            name = "VarMaskRegex" + getGlobalVarMaskRegexesMap().size();
        }
        HashMap<String, VarMaskRegex> regexMap = getGlobalVarMaskRegexesMap();
        regexMap.put(name, varMaskRegex);
        getGlobalVarMaskRegexesUList().clear();
        for (Map.Entry<String, VarMaskRegex> entry: getGlobalVarMaskRegexesMap().entrySet()) {
            getGlobalVarMaskRegexesUList().add(new VarMaskRegexEntry(entry.getKey(), entry.getValue()));
        }

        saveSafeIO(this);
    }

    public void removeGlobalVarMaskRegexByName(@NonNull String name) {
        if (!isGlobalVarMaskRegexesNull()) {
            VarMaskRegex r = getGlobalVarMaskRegexesMap().get(name);
            if (r != null) {
                VarMaskRegexEntry e = new VarMaskRegexEntry(name, r);
                getGlobalVarMaskRegexesMap().remove(name);
                getGlobalVarMaskRegexesUList().remove(e);
            }
        }
        saveSafeIO(this);
    }

    public void removeGlobalVarMaskRegex(String name, String regex) {
        if (!isGlobalVarMaskRegexesNull()) {
            VarMaskRegexEntry e = new VarMaskRegexEntry(name, regex);
            if (getGlobalVarMaskRegexesUList().remove(e)) {
                HashMap<String, VarMaskRegex> map = getGlobalVarMaskRegexesMap();
                VarMaskRegex r = map.get(name);
                if (r != null && r.getRegex() != null && regex != null && r.getRegex().equals(regex)) {
                    map.remove(name);
                }
            }

        }
        saveSafeIO(this);
    }

    /**
     * @param className The class key of a {@link ParameterDescriptor} to be added
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
        getGlobalVarMaskRegexesUList().clear();
        getGlobalVarMaskRegexesMap().clear();
        globalVarEnableGlobally = false;
        
        // Drop caches
        invalidatePasswordValueClassCaches();
    }

    public synchronized void clear(boolean doSave) {
        clear();
        if (doSave) {
            saveSafeIO(this);
        }
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
        return new XmlFile(new File(Jenkins.get().getRootDir(), CONFIG_FILE));
    }

    /**
     * Returns the list of key/password pairs defined at the global level.
     *
     * <p>Modifications broughts to the returned list has no impact on this
     * configuration (the returned value is a copy). Also, the list can be
     * empty but never {@code null}.</p>
     *
     * @since 2.7
     */
    public List<VarPasswordPair> getGlobalVarPasswordPairs() {
        List<VarPasswordPair> r = new ArrayList<>(getGlobalVarPasswordPairsList().size());

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
    public List<VarMaskRegexEntry> getGlobalVarMaskRegexesU() {
        List<VarMaskRegexEntry> r = new ArrayList<>(getGlobalVarMaskRegexesMap().size());

        // deep copy
        for(Map.Entry<String, VarMaskRegex> entry: getGlobalVarMaskRegexesMap().entrySet()) {
            r.add(new VarMaskRegexEntry(entry.getKey(), (VarMaskRegex) entry.getValue().clone()));
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
            globalVarPasswordPairs = new ArrayList<>();
        }
        return globalVarPasswordPairs;
    }

    /**
     * Fixes JENKINS-11514: When {@code MaskPasswordsConfig.xml} is there but was created from
     * version 2.8 (or older) of the plugin, {@link #globalVarPasswordPairs} can actually be
     * {@code null} ==&gt; Always use this getter to avoid NPEs.
     *
     * @since 2.9
     */
    @Deprecated
    public List<VarMaskRegex> getGlobalVarMaskRegexesList() {
        if(globalVarMaskRegexes == null) {
            globalVarMaskRegexes = new ArrayList<>();
        }
        return globalVarMaskRegexes;
    }

    public List<VarMaskRegexEntry> getGlobalVarMaskRegexesUList() {
        if (this.globalVarMaskRegexesU == null) {
            globalVarMaskRegexesU = new ArrayList<>();
        }
        return globalVarMaskRegexesU;
    }

    public HashMap<String, VarMaskRegex> getGlobalVarMaskRegexesMap() {
        if (globalVarMaskRegexesMap == null) {
            globalVarMaskRegexesMap = new HashMap<>();
            /* upon initialization, create entries from globalVarMaskRegex (List) */
            LOGGER.info("Initializing global var mask regexes map");
            if (getGlobalVarMaskRegexesList().size() > 0) {
                for (int i = 0 ; i < getGlobalVarMaskRegexesList().size(); i++) {
                    globalVarMaskRegexesMap.put("Regex_" + String.valueOf(i), getGlobalVarMaskRegexesList().get(i));
                }
                getGlobalVarMaskRegexesList().clear();
                try {
                    save(this);
                } catch (IOException e) {
                    LOGGER.info("IO Exception when trying to initialize global var mask value map from list:\n" + e.getMessage());
                }
            }
        }
        return globalVarMaskRegexesMap;
    }


    /**
     * Returns a map of all {@link ParameterDefinition}s that can be used in
     * jobs.
     *
     * <p>The key is the class key of the {@link ParameterDefinition}, the value
     * is its display key.</p>
     */
    public static Map<String, String> getParameterDefinitions() {
        Map<String, String> params = new HashMap<>();

        ExtensionList<ParameterDefinition.ParameterDescriptor> paramExtensions =
                Jenkins.get().getExtensionList(ParameterDefinition.ParameterDescriptor.class);
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
     * @param paramValueClassName Class key of the {@link ParameterValue}
     * @return {@code true} if the parameter value should be masked.
     *         {@code false} if the plugin is not sure, may be false-negative 
     */
    @Deprecated
    public synchronized boolean isMasked(final @NonNull String paramValueClassName) {
        return isMasked(null, paramValueClassName);
    }
    
    /**
     * Returns true if the specified parameter value class key corresponds to
     * a parameter definition class key selected in Jenkins' main
     * configuration screen.
     * @param value Parameter value. Without it there is a high risk of false negatives.
     * @param paramValueClassName Class key of the {@link ParameterValue} class implementation
     * @return {@code true} if the parameter value should be masked.
     *         {@code false} if the plugin is not sure, may be false-negative especially if the value is {@code null}.
     * @since TODO
     */
    public boolean isMasked(final @CheckForNull ParameterValue value,
            final @NonNull String paramValueClassName) {
        
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
    
    //TODO: add support of specifying masked parameter values byt the... parameter value classs key. So obvious, yeah?
    /**
     * Tries to guess if the parameter value class should be masked.
     * @param paramValueClassName Parameter value class key
     * @return {@code true} if we are sure that the class has to be masked
     *         {@code false} otherwise, there is a risk of false negative due to the presumptions.
     */
    /*package*/ synchronized boolean guessIfShouldMask(final @NonNull String paramValueClassName) {
        // The only way to find parameter definition/parameter value
        // couples is to reflect the methods of parameter definition
        // classes which instantiate the parameter value.
        // This means that this algorithm expects that the developers do
        // clearly redefine the return type when implementing parameter
        // definitions/values.
        for(String paramDefClassName: maskPasswordsParamDefClasses) {
            final Class<?> paramDefClass;
            try {
                paramDefClass = Jenkins.get().getPluginManager().uberClassLoader.loadClass(paramDefClassName);
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
            valueClass = Jenkins.get().getPluginManager().uberClassLoader.loadClass(paramValueClassName);
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
     * @param methodName Method key
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
     * Returns true if the specified parameter definition class key has been
     * selected in Jenkins main configuration screen.
     */
    public synchronized boolean isSelected(String paramDefClassName) {
        return maskPasswordsParamDefClasses.contains(paramDefClassName);
    }

    public static MaskPasswordsConfig load() {
        LOGGER.entering(CLASS_NAME, "load");
        try {
            MaskPasswordsConfig file = (MaskPasswordsConfig) getConfigFile().read();
            return (MaskPasswordsConfig) getConfigFile().read();
        }
        catch(FileNotFoundException | NoSuchFileException e) {
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

    static void saveSafeIO(MaskPasswordsConfig config) {
        try {
            save(config);
        } catch(IOException e) {
            LOGGER.warning("Failed to save MaskPasswordsConfig due to IOException: " + e.getMessage());
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("MaskPasswordsConfigFile Regexes:[\n");
        for (Map.Entry<String, VarMaskRegex> entry : this.getGlobalVarMaskRegexesMap().entrySet()) {
            sb.append(entry.getKey() + " : " + entry.getValue().getRegex() + "\n");
        }
        sb.append("]");
        return sb.toString();
    }

    private final static String CLASS_NAME = MaskPasswordsConfig.class.getName();
    private final static Logger LOGGER = Logger.getLogger(CLASS_NAME);

    public static class VarMaskRegexEntry extends AbstractDescribableImpl<VarMaskRegexEntry> implements Cloneable{
        private String key;
        private VarMaskRegex value;

        @DataBoundConstructor
        public VarMaskRegexEntry(String key, String value) {
            this.key = key;
            this.value = new VarMaskRegex(value);
        }

        public VarMaskRegexEntry(String key, VarMaskRegex value) {
            this.key = key;
            this.value = value;
        }

        public VarMaskRegexEntry(VarMaskRegex value) {
            key = "";
            this.value = value;
        }

        public String getName() {
            return key;
        }

        public void setName(String name) {
            this.key = name;
        }

        public VarMaskRegex getRegex() {
            return value;
        }

        public void setRegex(VarMaskRegex regex) {
            this.value = regex;
        }

        public String getKey() {
            return this.getName();
        }

        public void setKey(String key) {
            this.key = key;
        }

        public VarMaskRegex getValue() {
            return this.getRegex();
        }
        public void setValue(VarMaskRegex regex) {
            this.setRegex(regex);
        }

        public String getRegexString() {
            if (this.value == null) {
                return "";
            }
            return this.value.getRegex();
        }

        public String toString() {
            return this.key + ":" + this.value;
        }

        @Override
        @SuppressFBWarnings(value = "CN_IDIOM_NO_SUPER_CALL", justification = "We do not expect anybody to use this class."
                + "If they do, they must override clone() as well")
        public Object clone() {
            return new VarMaskRegexEntry(this.getName(), this.getRegex());
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 67 * hash + (this.key != null ? this.key.hashCode() : 0);
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            } else if (!this.getClass().equals(other.getClass())) {
                return false;
            } else {
                VarMaskRegexEntry otherE = (VarMaskRegexEntry) other;
                return (this.getName().equals(otherE.getName())) && this.getRegex().equals(otherE.getRegex());
            }
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<VarMaskRegexEntry> implements CustomDescribableModel {
            public String getDisplayName() {
                return VarMaskRegexEntry.class.getName();
            }

            @NonNull
            @Override
            public UninstantiatedDescribable customUninstantiate(@NonNull UninstantiatedDescribable step) {
                Map<String, ?> arguments = step.getArguments();
                Map<String, Object> newMap1 = new HashMap<>();
                newMap1.put("name", arguments.get("name"));
                newMap1.put("value", arguments.get("value"));
                return step.withArguments(newMap1);
            }

            @NonNull
            @Override
            public Map<String, Object> customInstantiate(@NonNull Map<String, Object> arguments) {
                Map<String, Object> newMap = new HashMap<>();
                newMap.put("name", arguments.get("name"));
                newMap.put("value", new VarMaskRegex((String)arguments.get("value")));
                return newMap;
            }
        }
    }

}

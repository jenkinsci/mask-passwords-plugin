/*
 * The MIT License
 *
 * Copyright (c) 2010-2012, Manufacture Francaise des Pneumatiques Michelin,
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

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.Secret;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import jenkins.tasks.SimpleBuildWrapper;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Build wrapper that alters the console so that passwords don't get displayed.
 *
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public final class MaskPasswordsBuildWrapper extends SimpleBuildWrapper {

    private final List<VarPasswordPair> varPasswordPairs;
    private final List<VarMaskRegex> varMaskRegexes;

    @DataBoundConstructor
    public MaskPasswordsBuildWrapper(List<VarPasswordPair> varPasswordPairs, List<VarMaskRegex> varMaskRegexes) {
        this.varPasswordPairs = varPasswordPairs;
        this.varMaskRegexes = varMaskRegexes;
    }

    public MaskPasswordsBuildWrapper(List<VarPasswordPair> varPasswordPairs) {
        this.varPasswordPairs = varPasswordPairs;
        this.varMaskRegexes = new ArrayList<VarMaskRegex>();
    }

    @Override
    public ConsoleLogFilter createLoggerDecorator(Run<?, ?> build) {
        List<String> allPasswords = new ArrayList<String>();  // all passwords to be masked
        List<String> allRegexes = new ArrayList<String>(); // all regexes to be masked
        MaskPasswordsConfig config = MaskPasswordsConfig.getInstance();

        // global passwords
        List<VarPasswordPair> globalVarPasswordPairs = config.getGlobalVarPasswordPairs();
        for(VarPasswordPair globalVarPasswordPair: globalVarPasswordPairs) {
            allPasswords.add(globalVarPasswordPair.getPassword());
        }

        // global regexes
        List<VarMaskRegex> globalVarMaskRegexes = config.getGlobalVarMaskRegexes();
        for(VarMaskRegex globalVarMaskRegex: globalVarMaskRegexes) {
            allRegexes.add(globalVarMaskRegex.getRegex());
        }

        // job's passwords
        if(varPasswordPairs != null) {
            for(VarPasswordPair varPasswordPair: varPasswordPairs) {
                String password = varPasswordPair.getPassword();
                if(StringUtils.isNotBlank(password)) {
                    allPasswords.add(password);
                }
            }
        }

        // job's regexes
        if(varMaskRegexes != null) {
            for(VarMaskRegex varMaskRegex: varMaskRegexes) {
                String regex = varMaskRegex.getRegex();
                if(StringUtils.isNotBlank(regex)) {
                    allRegexes.add(regex);
                }
            }
        }

        // find build parameters which are passwords (PasswordParameterValue)
        ParametersAction params = build.getAction(ParametersAction.class);
        if(params != null) {
            for(ParameterValue param : params) {
                if(config.isMasked(param.getClass().getName())) {
                    EnvVars env = new EnvVars();
                    param.buildEnvironment(build, env);
                    String password = env.get(param.getName());
                    if(StringUtils.isNotBlank(password)) {
                        allPasswords.add(password);
                    }
                }
            }
        }

        return new FilterImpl(allPasswords, allRegexes);
    }

    private static final class FilterImpl extends ConsoleLogFilter implements Serializable {

        private static final long serialVersionUID = 1L;

        private final List<Secret> allPasswords;
        private final List<String> allRegexes;

        FilterImpl(List<String> allPasswords, List<String> allRegexes) {
            this.allPasswords = new ArrayList<Secret>();
            this.allRegexes = new ArrayList<String>();
            for (String password : allPasswords) {
                this.allPasswords.add(Secret.fromString(password));
            }
            for (String regex : allRegexes) {
                this.allRegexes.add(regex);
            }
        }

        @SuppressWarnings("rawtypes")
        @Override
        public OutputStream decorateLogger(AbstractBuild _ignore, OutputStream logger) throws IOException, InterruptedException {
            List<String> passwords = new ArrayList<String>();
            List<String> regexes = new ArrayList<String>();
            for (Secret password : allPasswords) {
                passwords.add(password.getPlainText());
            }
            for (String regex : allRegexes) {
                regexes.add(regex);
            }
            return new MaskPasswordsOutputStream(logger, passwords, regexes);
        }

    }

    /**
     * Contributes the passwords defined by the user as variables that can be reused
     * from build steps (and other places).
     */
    @Override
    public void makeBuildVariables(AbstractBuild build, Map<String, String> variables) {
        // global var/password pairs
        MaskPasswordsConfig config = MaskPasswordsConfig.getInstance();
        List<VarPasswordPair> globalVarPasswordPairs = config.getGlobalVarPasswordPairs();
        // we can't use variables.putAll() since passwords are ciphered when in varPasswordPairs
        for(VarPasswordPair globalVarPasswordPair: globalVarPasswordPairs) {
            variables.put(globalVarPasswordPair.getVar(), globalVarPasswordPair.getPassword());
        }

        // job's var/password pairs
        if(varPasswordPairs != null) {
            // cf. comment above
            for(VarPasswordPair varPasswordPair: varPasswordPairs) {
                if(StringUtils.isNotBlank(varPasswordPair.getVar())) {
                    variables.put(varPasswordPair.getVar(), varPasswordPair.getPassword());
                }
            }
        }
    }

    @Override
    public void makeSensitiveBuildVariables(AbstractBuild build, Set<String> sensitiveVariables) {
        final Map<String, String> variables = new TreeMap<String, String>();
        makeBuildVariables(build, variables);
        sensitiveVariables.addAll(variables.keySet());
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        // nothing to do here
    }

    public List<VarPasswordPair> getVarPasswordPairs() {
        return varPasswordPairs;
    }

    public List<VarMaskRegex> getVarMaskRegexes() {
        return varMaskRegexes;
    }

    /**
     * Represents name/password entries defined by users in their jobs.
     * Equality and hashcode are based on {@code var} only, not {@code password}.
     * If the class gets extended, a <code>clone()</code> method must be implemented without <code>super.clone()</code> calls.
     */
    public static class VarPasswordPair implements Cloneable {

        private final String var;
        private final Secret password;

        @DataBoundConstructor
        public VarPasswordPair(String var, String password, boolean fastMethod=false) {
            this.var = var;
            if (fastMethod) {
                /**
                 * Fast method is used when cloning to avoid the performance hit of throwing an exception when attempting to decrypt
                 * an already decrypted string. This is a massive performance hit in some scenarios (e.g. build pipeline, parameterized
                 * trigger
                 */
                this.password = getSecretConstructor().newInstance(password);
            } else {
                this.password = Secret.fromString(password);
            }
        }

        @Override
        @SuppressFBWarnings(value = "CN_IDIOM_NO_SUPER_CALL", justification = "We do not expect anybody to use this class."
                + "If they do, they must override clone() as well")
        public Object clone() {
            return new VarPasswordPair(getVar(), getPassword(), true);
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == null) {
                return false;
            }
            if(getClass() != obj.getClass()) {
                return false;
            }
            final VarPasswordPair other = (VarPasswordPair) obj;
            if((this.var == null) ? (other.var != null) : !this.var.equals(other.var)) {
                return false;
            }
            return true;
        }

        public String getVar() {
            return var;
        }

        public String getPassword() {
            return Secret.toString(password);
        }

        public Secret getPasswordAsSecret() {
            return password;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 67 * hash + (this.var != null ? this.var.hashCode() : 0);
            return hash;
        }
        
        public static Constructor<Secret> getSecretConstructor() {
            if (SECRET_CONSTRUCTOR!=null) {
                SECRET_CONSTRUCTOR = Secret.class.getDeclaredConstructor(String.class);
                SECRET_CONSTRUCTOR.setAccessible(true);
            }
            return SECRET_CONSTRUCTOR;
        }
        
        private static Constructor<Secret> SECRET_CONSTRUCTOR;
    }

    /**
     * Represents regexes defined by users in their jobs.
     * If the class gets extended, a <code>clone()</code> method must be implemented without <code>super.clone()</code> calls.
     */
    public static class VarMaskRegex implements Cloneable {

        private final String regex;

        @DataBoundConstructor
        public VarMaskRegex(String regex) {
            this.regex = regex;
        }

        @Override
        @SuppressFBWarnings(value = "CN_IDIOM_NO_SUPER_CALL", justification = "We do not expect anybody to use this class."
                + "If they do, they must override clone() as well")
        public Object clone() {
            return new VarMaskRegex(getRegex());
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == null) {
                return false;
            }
            if(getClass() != obj.getClass()) {
                return false;
            }
            final VarMaskRegex other = (VarMaskRegex) obj;
            if((this.regex == null) ? (other.regex != null) : !this.regex.equals(other.regex)) {
                return false;
            }
            return true;
        }

        @CheckForNull
        public String getRegex() {
            return regex;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 67 * hash + (this.regex != null ? this.regex.hashCode() : 0);
            return hash;
        }

    }

    @Extension(ordinal = 1000) // JENKINS-12161
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        public DescriptorImpl() {
            super(MaskPasswordsBuildWrapper.class);
        }

        /**
         * @since 2.5
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
            try {
                getConfig().clear();

                LOGGER.fine("Processing the maskedParamDefs and selectedMaskedParamDefs JSON objects");
                JSONObject submittedForm = req.getSubmittedForm();

                // parameter definitions to be automatically masked
                JSONArray paramDefinitions = submittedForm.getJSONArray("maskedParamDefs");
                JSONArray selectedParamDefinitions = submittedForm.getJSONArray("selectedMaskedParamDefs");
                for(int i = 0; i < selectedParamDefinitions.size(); i++) {
                    if(selectedParamDefinitions.getBoolean(i)) {
                        getConfig().addMaskedPasswordParameterDefinition(paramDefinitions.getString(i));
                    }
                }

                // global var/password pairs
                if(submittedForm.has("globalVarPasswordPairs")) {
                    Object o = submittedForm.get("globalVarPasswordPairs");

                    if(o instanceof JSONArray) {
                        JSONArray jsonArray = submittedForm.getJSONArray("globalVarPasswordPairs");
                        for(int i = 0; i < jsonArray.size(); i++) {
                            getConfig().addGlobalVarPasswordPair(new VarPasswordPair(
                                    jsonArray.getJSONObject(i).getString("var"),
                                    jsonArray.getJSONObject(i).getString("password")));
                        }
                    }
                    else if(o instanceof JSONObject) {
                        JSONObject jsonObject = submittedForm.getJSONObject("globalVarPasswordPairs");
                        getConfig().addGlobalVarPasswordPair(new VarPasswordPair(
                                jsonObject.getString("var"),
                                jsonObject.getString("password")));
                    }
                }

                // global regexes
                if(submittedForm.has("globalVarMaskRegexes")) {
                    Object o = submittedForm.get("globalVarMaskRegexes");

                    if(o instanceof JSONArray) {
                        JSONArray jsonArray = submittedForm.getJSONArray("globalVarMaskRegexes");
                        for(int i = 0; i < jsonArray.size(); i++) {
                            getConfig().addGlobalVarMaskRegex(new VarMaskRegex(
                                    jsonArray.getJSONObject(i).getString("regex")));
                        }
                    }
                    else if(o instanceof JSONObject) {
                        JSONObject jsonObject = submittedForm.getJSONObject("globalVarMaskRegexes");
                        getConfig().addGlobalVarMaskRegex(new VarMaskRegex(
                                jsonObject.getString("regex")));
                    }
                }

                // global enable
                if(submittedForm.has("globalVarMaskEnabledGlobally")) {
                  boolean b = submittedForm.getBoolean("globalVarMaskEnabledGlobally");
                  if(b) {
                    getConfig().setGlobalVarEnabledGlobally(true);
                  }
                }

                MaskPasswordsConfig.save(getConfig());

                return true;
            }
            catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to save Mask Passwords plugin configuration", e);
                return false;
            }
        }

        /**
         * @since 2.5
         */
        public MaskPasswordsConfig getConfig() {
            return MaskPasswordsConfig.getInstance();
        }

        @Override
        public String getDisplayName() {
            return new Localizable(ResourceBundleHolder.get(MaskPasswordsBuildWrapper.class), "DisplayName").toString();
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

    }

    /**
     * We need this converter to handle marshalling/unmarshalling of the build
     * wrapper data: Relying on the default mechanism doesn't make it (because
     * {@link Secret} doesn't have the {@code DataBoundConstructor} annotation).
     */
    public static final class ConverterImpl implements Converter {

        private final static String VAR_PASSWORD_PAIRS_NODE = "varPasswordPairs";
        private final static String VAR_PASSWORD_PAIR_NODE = "varPasswordPair";
        private final static String VAR_MASK_REGEXES_NODE = "varMaskRegexes";
        private final static String VAR_MASK_REGEX_NODE = "varMaskRegex";
        private final static String VAR_ATT = "var";
        private final static String PASSWORD_ATT = "password";
        private final static String REGEX_ATT = "regex";

        public boolean canConvert(Class clazz) {
            return clazz.equals(MaskPasswordsBuildWrapper.class);
        }

        public void marshal(Object o, HierarchicalStreamWriter writer, MarshallingContext mc) {
            MaskPasswordsBuildWrapper maskPasswordsBuildWrapper = (MaskPasswordsBuildWrapper) o;

            // varPasswordPairs
            if(maskPasswordsBuildWrapper.getVarPasswordPairs() != null) {
                writer.startNode(VAR_PASSWORD_PAIRS_NODE);
                for(VarPasswordPair varPasswordPair: maskPasswordsBuildWrapper.getVarPasswordPairs()) {
                    // blank passwords are skipped
                    if(StringUtils.isBlank(varPasswordPair.getPassword())) {
                        continue;
                    }
                    writer.startNode(VAR_PASSWORD_PAIR_NODE);
                    writer.addAttribute(VAR_ATT, varPasswordPair.getVar());
                    writer.addAttribute(PASSWORD_ATT, varPasswordPair.getPasswordAsSecret().getEncryptedValue());
                    writer.endNode();
                }
                writer.endNode();
            }
            // varMaskRegexes
            if(maskPasswordsBuildWrapper.getVarMaskRegexes() != null) {
                writer.startNode(VAR_MASK_REGEXES_NODE);
                for(VarMaskRegex varMaskRegex: maskPasswordsBuildWrapper.getVarMaskRegexes()) {
                    // blank passwords are skipped
                    if(StringUtils.isBlank(varMaskRegex.getRegex())) {
                        continue;
                    }
                    writer.startNode(VAR_MASK_REGEX_NODE);
                    writer.addAttribute(REGEX_ATT, varMaskRegex.getRegex());
                    writer.endNode();
                }
                writer.endNode();
            }
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext uc) {
            List<VarPasswordPair> varPasswordPairs = new ArrayList<VarPasswordPair>();
            List<VarMaskRegex> varMaskRegexes = new ArrayList<VarMaskRegex>();

            while(reader.hasMoreChildren()) {
                reader.moveDown();
                if(reader.getNodeName().equals(VAR_PASSWORD_PAIRS_NODE)) {
                    while(reader.hasMoreChildren()) {
                        reader.moveDown();
                        if(reader.getNodeName().equals(VAR_PASSWORD_PAIR_NODE)) {
                            varPasswordPairs.add(new VarPasswordPair(
                                    reader.getAttribute(VAR_ATT),
                                    reader.getAttribute(PASSWORD_ATT)));
                        }
                        else {
                            LOGGER.log(Level.WARNING,
                                    "Encountered incorrect node name: Expected \"" + VAR_PASSWORD_PAIR_NODE + "\", got \"{0}\"",
                                    reader.getNodeName());
                        }
                        reader.moveUp();
                    }
                    reader.moveUp();
                }
                else if(reader.getNodeName().equals(VAR_MASK_REGEXES_NODE)) {
                    while(reader.hasMoreChildren()) {
                        reader.moveDown();
                        if(reader.getNodeName().equals(VAR_MASK_REGEX_NODE)) {
                            varMaskRegexes.add(new VarMaskRegex(
                                    reader.getAttribute(REGEX_ATT)));
                        }
                        else {
                            LOGGER.log(Level.WARNING,
                                    "Encountered incorrect node name: Expected \"" + VAR_MASK_REGEX_NODE + "\", got \"{0}\"",
                                    reader.getNodeName());
                        }
                        reader.moveUp();
                    }
                    reader.moveUp();
                }
                else {
                    LOGGER.log(Level.WARNING,
                            "Encountered incorrect node name: \"{0}\"", reader.getNodeName());
                }
            }

            return new MaskPasswordsBuildWrapper(varPasswordPairs, varMaskRegexes);
        }

    }

    private static final Logger LOGGER = Logger.getLogger(MaskPasswordsBuildWrapper.class.getName());

}

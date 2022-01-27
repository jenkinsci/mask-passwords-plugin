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

import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsConfig.VarMaskRegexEntry;

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.AbstractProject;
import hudson.model.Descriptor;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.Secret;
import jenkins.tasks.SimpleBuildWrapper;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.structs.describable.CustomDescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Build wrapper that alters the console so that passwords don't get displayed.
 *
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public final class MaskPasswordsBuildWrapper extends SimpleBuildWrapper {

    private final List<VarPasswordPair> varPasswordPairs;
    private final List<VarMaskRegexEntry> varMaskRegexes;

    @DataBoundConstructor
    public MaskPasswordsBuildWrapper(List<VarPasswordPair> varPasswordPairs, List<VarMaskRegexEntry> varMaskRegexes) {
        this.varPasswordPairs = varPasswordPairs;
        this.varMaskRegexes = varMaskRegexes;
    }

    public MaskPasswordsBuildWrapper(List<VarPasswordPair> varPasswordPairs) {
        this.varPasswordPairs = varPasswordPairs;
        this.varMaskRegexes = new ArrayList<>();
    }

    @Override
    public ConsoleLogFilter createLoggerDecorator(Run<?, ?> build) {
        List<String> allPasswords = new ArrayList<String>();  // all passwords to be masked
        List<String> allRegexes = new ArrayList<String>(); // all regexes to be masked
        MaskPasswordsConfig config = MaskPasswordsConfig.getInstance();

        // global passwords
        List<VarPasswordPair> globalVarPasswordPairs = config.getGlobalVarPasswordPairs();
        for(VarPasswordPair globalVarPasswordPair: globalVarPasswordPairs) {
            allPasswords.add(globalVarPasswordPair.getPlainTextPassword());
        }

        // global regexes
        List<MaskPasswordsConfig.VarMaskRegexEntry> globalVarMaskRegexes = config.getGlobalVarMaskRegexesU();
        for(MaskPasswordsConfig.VarMaskRegexEntry globalVarMaskRegex: globalVarMaskRegexes) {
            allRegexes.add(globalVarMaskRegex.getValue().getRegex());
        }

        // job's passwords
        if(varPasswordPairs != null) {
            for(VarPasswordPair varPasswordPair: varPasswordPairs) {
                String password = varPasswordPair.getPlainTextPassword();
                if(StringUtils.isNotBlank(password)) {
                    allPasswords.add(password);
                }
            }
        }

        // job's regexes
        if(varMaskRegexes != null) {
            for(VarMaskRegexEntry entry: varMaskRegexes) {
                String regex = entry.getRegexString();
                if(StringUtils.isNotBlank(regex)) {
                    allRegexes.add(regex);
                }
            }
        }

        // find build parameters which are passwords (PasswordParameterValue)
        ParametersAction params = build.getAction(ParametersAction.class);
        if(params != null) {
            for(ParameterValue param : params) {
                if(config.isMasked(param, param.getClass().getName())) {
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

    @Override
    public boolean requiresWorkspace() {
        return false;
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

        @Override
        public OutputStream decorateLogger(Run run, OutputStream logger) {
            LOGGER.info("Decorating Log with RUN.");
            List<String> passwords = new ArrayList<String>();
            List<String> regexes = new ArrayList<String>();
            for (Secret password : allPasswords) {
                passwords.add(password.getPlainText());
            }
            for (String regex : allRegexes) {
                regexes.add(regex);
            }
            String runName = run != null ? run.getFullDisplayName() : "";
            return new MaskPasswordsOutputStream(logger, passwords, regexes, runName);
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
            variables.put(globalVarPasswordPair.getVar(), globalVarPasswordPair.getPlainTextPassword());
        }

        // job's var/password pairs
        if(varPasswordPairs != null) {
            // cf. comment above
            for(VarPasswordPair varPasswordPair: varPasswordPairs) {
                if(StringUtils.isNotBlank(varPasswordPair.getVar())) {
                    variables.put(varPasswordPair.getVar(), varPasswordPair.getPlainTextPassword());
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
    public void setUp(Context context, Run<?, ?> build, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        // nothing to do here
    }

    public List<VarPasswordPair> getVarPasswordPairs() {
        return varPasswordPairs;
    }

    public List<VarMaskRegexEntry> getVarMaskRegexes() {
        return varMaskRegexes;
    }

    /**
     * Represents name/password entries defined by users in their jobs.
     * Equality and hashcode are based on {@code var} only, not {@code password}.
     * If the class gets extended, a <code>clone()</code> method must be implemented without <code>super.clone()</code> calls.
     */
    public static class VarPasswordPair extends AbstractDescribableImpl<VarPasswordPair> implements Cloneable {

        private final String var;
        private final Secret password;

        @DataBoundConstructor
        public VarPasswordPair(String var, Secret password) {
            this.var = var;
            this.password = password;
        }

        @Override
        @SuppressFBWarnings(value = "CN_IDIOM_NO_SUPER_CALL", justification = "We do not expect anybody to use this class."
                + "If they do, they must override clone() as well")
        public Object clone() {
            return new VarPasswordPair(getVar(), password);
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
            return Objects.equals(this.var, other.var);
        }

        public String getVar() {
            return var;
        }

        public Secret getPassword() {
            return password;
        }

        public String getPlainTextPassword() {
            if (password == null || StringUtils.isBlank(password.getPlainText())) {
                return null;
            }

            return password.getPlainText();
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 67 * hash + (this.var != null ? this.var.hashCode() : 0);
            return hash;
        }

        @Extension
        /**
         * {@link CustomDescribableModel} is needed because pipeline doesn't natively support the {@link Secret} class
         * but we need Secret so that data-binding works correctly.
         */
        public static class DescriptorImpl extends Descriptor<VarPasswordPair> implements CustomDescribableModel {
            @Nonnull
            @Override
            public UninstantiatedDescribable customUninstantiate(@Nonnull UninstantiatedDescribable step) {
                Map<String, ?> arguments = step.getArguments();
                Map<String, Object> newMap1 = new HashMap<>();
                newMap1.put("var", arguments.get("var"));
                newMap1.put("password", ((Secret) arguments.get("password")).getPlainText());
                return step.withArguments(newMap1);
            }

            @Nonnull
            @Override
            public Map<String, Object> customInstantiate(@Nonnull Map<String, Object> arguments) {
                Map<String, Object> newMap = new HashMap<>();
                newMap.put("var", arguments.get("var"));
                Object password = arguments.get("password");
                if (password instanceof String) {
                    password = Secret.fromString((String) password);
                }
                newMap.put("password", password);
                return newMap;
            }
        }

    }

    /**
     * Represents regexes defined by users in their jobs.
     * If the class gets extended, a <code>clone()</code> method must be implemented without <code>super.clone()</code> calls.
     */
    public static class VarMaskRegex extends AbstractDescribableImpl<VarMaskRegex> implements Cloneable {

        private final String regex;

        @DataBoundConstructor
        public  VarMaskRegex(String regex) {
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
            return Objects.equals(this.regex, other.regex);
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

        public String toString() {
            return regex;
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<VarMaskRegex> {}

    }

    @Symbol("maskPasswords")
    @Extension(ordinal = 100) // JENKINS-12161, was previously 1000 but that made the system configuration page look weird
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
                JSONObject  submittedForm = req.getSubmittedForm();

                LOGGER.info("JSON Submitted form:\n" + submittedForm);

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
                                    Secret.fromString(jsonArray.getJSONObject(i).getString("password"))));
                        }
                    }
                    else if(o instanceof JSONObject) {
                        JSONObject jsonObject = submittedForm.getJSONObject("globalVarPasswordPairs");
                        getConfig().addGlobalVarPasswordPair(new VarPasswordPair(
                                jsonObject.getString("var"),
                                Secret.fromString(jsonObject.getString("password"))));
                    }
                }

                // global regexes
                if(submittedForm.has("globalVarMaskRegexesU")) {
                    Object o = submittedForm.get("globalVarMaskRegexesU");

                    if(o instanceof JSONArray) {
                        JSONArray jsonArray = submittedForm.getJSONArray("globalVarMaskRegexesU");
                        for(int i = 0; i < jsonArray.size(); i++) {
                            getConfig().addGlobalVarMaskRegex(
                                    jsonArray.getJSONObject(i).getString("key"),
                                    new VarMaskRegex(jsonArray.getJSONObject(i).getString("value")));
                        }
                    }
                    else if(o instanceof JSONObject) {
                        JSONObject jsonObject = submittedForm.getJSONObject("globalVarMaskRegexesU");
                        getConfig().addGlobalVarMaskRegex(
                                jsonObject.getString("key"),
                                new VarMaskRegex(jsonObject.getString("value")));
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

        public List<VarPasswordPair> getGlobalVarPasswordPairs() {
            return getConfig().getGlobalVarPasswordPairs();
        }

        public List<MaskPasswordsConfig.VarMaskRegexEntry> getGlobalVarMaskRegexesU() {
            return getConfig().getGlobalVarMaskRegexesU();
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
        private final static String REGEX_NAME = "name";

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
                    if(varPasswordPair.getPlainTextPassword() == null) {
                        continue;
                    }
                    writer.startNode(VAR_PASSWORD_PAIR_NODE);
                    writer.addAttribute(VAR_ATT, varPasswordPair.getVar());
                    writer.addAttribute(PASSWORD_ATT, varPasswordPair.getPassword().getEncryptedValue());
                    writer.endNode();
                }
                writer.endNode();
            }
            // varMaskRegexes
            if(maskPasswordsBuildWrapper.getVarMaskRegexes() != null) {
                writer.startNode(VAR_MASK_REGEXES_NODE);
                for(VarMaskRegexEntry varMaskRegex: maskPasswordsBuildWrapper.getVarMaskRegexes()) {
                    // blank passwords are skipped
                    if(StringUtils.isBlank(varMaskRegex.getRegexString())) {
                        continue;
                    }
                    writer.startNode(VAR_MASK_REGEX_NODE);
                    writer.addAttribute(REGEX_NAME, varMaskRegex.getName());
                    writer.addAttribute(REGEX_ATT, varMaskRegex.getRegexString());
                    writer.endNode();
                }
                writer.endNode();
            }
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext uc) {
            List<VarPasswordPair> varPasswordPairs = new ArrayList<VarPasswordPair>();
            List<VarMaskRegexEntry> varMaskRegexes = new ArrayList<>();

            while(reader.hasMoreChildren()) {
                reader.moveDown();
                if(reader.getNodeName().equals(VAR_PASSWORD_PAIRS_NODE)) {
                    while(reader.hasMoreChildren()) {
                        reader.moveDown();
                        if(reader.getNodeName().equals(VAR_PASSWORD_PAIR_NODE)) {
                            varPasswordPairs.add(new VarPasswordPair(
                                    reader.getAttribute(VAR_ATT),
                                    Secret.fromString(reader.getAttribute(PASSWORD_ATT))));
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
                            varMaskRegexes.add(new VarMaskRegexEntry(
                                    reader.getAttribute(REGEX_NAME),
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

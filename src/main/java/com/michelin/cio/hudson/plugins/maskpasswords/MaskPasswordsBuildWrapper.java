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
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.CopyOnWriteMap;
import hudson.util.Secret;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Build wrapper that alters the console so that passwords don't get displayed.
 * 
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public final class MaskPasswordsBuildWrapper extends BuildWrapper {

    private final List<VarPasswordPair> varPasswordPairs;

    @DataBoundConstructor
    public MaskPasswordsBuildWrapper(List<VarPasswordPair> varPasswordPairs) {
        this.varPasswordPairs = varPasswordPairs;
    }

    //TODO: Most probably the method is not required after introducing sensitive vars
    /**
     * This method is invoked before {@link #makeBuildVariables()} and {@link
     * #setUp()}.
     */
    @Override
    public OutputStream decorateLogger(AbstractBuild build, OutputStream logger) {
        List<String> allPasswords = new ArrayList<String>();  // all passwords to be masked
        MaskPasswordsConfig config = MaskPasswordsConfig.getInstance();

        // global passwords
        List<VarPasswordPair> globalVarPasswordPairs = config.getGlobalVarPasswordPairs();
        for(VarPasswordPair globalVarPasswordPair: globalVarPasswordPairs) {
            allPasswords.add(globalVarPasswordPair.getPassword());
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

        // find build parameters which are passwords (PasswordParameterValue)
        ParametersAction params = build.getAction(ParametersAction.class);
        if(params != null) {
            for(ParameterValue param : params) {
                if(config.isMasked(param.getClass().getName())) {
                    String password = param.createVariableResolver(build).resolve(param.getName());
                    if(StringUtils.isNotBlank(password)) {
                        allPasswords.add(password);
                    }
                }
            }
        }

        return new MaskPasswordsOutputStream(logger, allPasswords);
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
    public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
        return new Environment() {
            // nothing to tearDown()
        };
    }

    public List<VarPasswordPair> getVarPasswordPairs() {
        return varPasswordPairs;
    }

    /**
     * Represents name/password entries defined by users in their jobs.
     * <p>Equality and hashcode are based on {@code var} only, not
     * {@code password}.</p>
     */
    public static class VarPasswordPair implements Cloneable {

        private final String var;
        private final Secret password;

        @DataBoundConstructor
        public VarPasswordPair(String var, String password) {
            this.var = var;
            this.password = Secret.fromString(password);
        }

        @Override
        public Object clone() {
            return new VarPasswordPair(getVar(), getPassword());
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
        private final static String VAR_ATT = "var";
        private final static String PASSWORD_ATT = "password";

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
        }

        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext uc) {
            List<VarPasswordPair> varPasswordPairs = new ArrayList<VarPasswordPair>();

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
                else {
                    LOGGER.log(Level.WARNING,
                            "Encountered incorrect node name: \"{0}\"", reader.getNodeName());
                }
            }

            return new MaskPasswordsBuildWrapper(varPasswordPairs);
        }

    }

    private static final Logger LOGGER = Logger.getLogger(MaskPasswordsBuildWrapper.class.getName());

}

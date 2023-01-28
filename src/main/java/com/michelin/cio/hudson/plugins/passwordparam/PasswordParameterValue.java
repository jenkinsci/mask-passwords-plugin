/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
 * Copyright (c) 2011-2012, Manufacture Francaise des Pneumatiques Michelin,
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
package com.michelin.cio.hudson.plugins.passwordparam;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.model.Run;
import hudson.util.Secret;
import hudson.util.VariableResolver;
import org.kohsuke.stapler.DataBoundConstructor;

@SuppressFBWarnings(value = "SE_NO_SERIALVERSIONID", justification = "XStream does not need Serial version ID")
public class PasswordParameterValue extends ParameterValue {

    //TODO: Can it even work with Pipeline? And it should be fine to write secrets
    @CheckForNull
    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "The secret must not be stored, so the att has to become transient")
    private final transient Secret value;

    public PasswordParameterValue(String name, Secret value, String description) {
        super(name, description);
        this.value = value;
    }

    @DataBoundConstructor
    public PasswordParameterValue(String name, String value, String description) {
        super(name, description);
        this.value = Secret.fromString(value);
    }

    @Override
    public void buildEnvironment(Run<?,?> build, EnvVars env) {
        env.put(name.toUpperCase(), value != null ? Secret.toString(value) : null);
    }

    @Override
    public VariableResolver<String> createVariableResolver(AbstractBuild<?, ?> build) {
        return new VariableResolver<String>() {
            public String resolve(String name) {
                return PasswordParameterValue.this.name.equals(name) ? (value != null ? Secret.toString(value) : null) : null;
            }
        };
    }

    @Override
    public final boolean isSensitive() {
        return true;
    }
    
    public String getValue() {
        return value != null ? Secret.toString(value) : null;
    }
    
}

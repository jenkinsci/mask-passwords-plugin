/*
 * The MIT License
 *
 * Copyright (c) 2004-2009, Sun Microsystems, Inc.
 * Copyright (c) 2011, Manufacture Française des Pneumatiques Michelin, Romain Seguy
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

import hudson.model.ParameterValue;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.DataBoundConstructor;
import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.util.Secret;
import org.jvnet.localizer.ResourceBundleHolder;
import org.jenkinsci.Symbol;


public class PasswordParameterDefinition extends ParameterDefinition {

    @DataBoundConstructor
    public PasswordParameterDefinition(String name, String description) {
        super(name, description);
    }

    public ParameterValue createValue(Secret password) {
        PasswordParameterValue value = new PasswordParameterValue(getName(), password, getDescription());
        value.setDescription(getDescription());
        return value;
    }

    @Override
    public ParameterValue createValue(StaplerRequest2 req) {
        String[] value = req.getParameterValues(getName());
        if(value == null) {
            return getDefaultParameterValue();
        }
        else if (value.length != 1) {
            throw new IllegalArgumentException("Illegal number of parameter values for " + getName() + ": " + value.length);
        }
        else {
            return createValue(Secret.fromString(value[0]));
        }
    }

    @Override
    public PasswordParameterValue createValue(StaplerRequest2 req, JSONObject formData) {
        PasswordParameterValue value = req.bindJSON(PasswordParameterValue.class, formData);
        value.setDescription(getDescription());
        return value;
    }

    @Extension
    @Symbol("nonStoredPassword")
    public final static class ParameterDescriptorImpl extends ParameterDescriptor {

        @Override
        public String getDisplayName() {
            return ResourceBundleHolder.get(PasswordParameterDefinition.class).format("DisplayName");
        }

        @Override
        public String getHelpFile() {
            return "/help/parameter/string.html";
        }

    }

}

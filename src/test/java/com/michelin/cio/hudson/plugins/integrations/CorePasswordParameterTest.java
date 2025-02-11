/*
 * The MIT License
 *
 * Copyright (c) 2017 Jenkins contributors.
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
package com.michelin.cio.hudson.plugins.integrations;

import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper;
import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsConfig;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterDefinition;
import hudson.model.PasswordParameterValue;
import hudson.model.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.Serial;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link PasswordParameterValue} and {@link PasswordParameterDefinition}.
 *
 * @author bpmarinho
 */
@WithJenkins
class CorePasswordParameterTest {

    private JenkinsRule j;

    @BeforeEach
    void dropCache(JenkinsRule j) {
        this.j = j;
        MaskPasswordsConfig.getInstance().reset();
    }

    @Test
    void shouldMaskPasswordParameterClassByDefault() {
        assertTrue(MaskPasswordsConfig.getInstance().isMasked(PasswordParameterValue.class.getName()),
                PasswordParameterValue.class + " must be masked by default");
    }

    @Test
    void shouldMaskPasswordParameterValueByDefault() {
        PasswordParameterDefinition d = new PasswordParameterDefinition("FOO", "myPassword", "BAR");
        ParameterValue created = d.createValue("hello");

        // We pass the non-existent class name in order to ensure that the Value metadata check is enough
        assertTrue(MaskPasswordsConfig.getInstance().isMasked(created, "nonExistent"),
                PasswordParameterValue.class + " must be masked by default");
    }

    @Test
    void shouldMaskPasswordParameterChildrenValueByValue() {
        ParameterValue created = new MyPasswordParameter();

        // We pass the non-existent class name in order to ensure that the Value metadata check is enough
        assertTrue(MaskPasswordsConfig.getInstance().isMasked(created, "nonExistent"),
                PasswordParameterValue.class + " must be masked by default");
    }

    @Test
    void shouldMaskPasswordParameterChildrenValueByClass() {
        // We pass the non-existent class name in order to ensure that the Value metadata check is enough
        assertTrue(MaskPasswordsConfig.getInstance().isMasked(MyPasswordParameter.class.getName()),
                PasswordParameterValue.class + " must be masked by the class name");
    }

    @Test
    @Issue("JENKINS-41955")
    void passwordParameterShouldBeMaskedInFreestyleProject() throws Exception {
        final String clearTextPassword = "myClearTextPassword";
        final String logWithClearTextPassword = "printed " + clearTextPassword + " oops";
        final String logWithHiddenPassword = "printed ******** oops";

        FreeStyleProject project
                = j.jenkins.createProject(FreeStyleProject.class, "testPasswordParameter");

        PasswordParameterDefinition passwordParameterDefinition
                = new hudson.model.PasswordParameterDefinition("Password1", clearTextPassword, null);
        ParametersDefinitionProperty parametersDefinitionProperty
                = new ParametersDefinitionProperty(passwordParameterDefinition);
        project.addProperty(parametersDefinitionProperty);

        MaskPasswordsBuildWrapper maskPasswordsBuildWrapper
                = new MaskPasswordsBuildWrapper(Collections.emptyList());
        project.getBuildWrappersList().add(maskPasswordsBuildWrapper);

        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                listener.getLogger().println(logWithClearTextPassword);
                build.setResult(Result.SUCCESS);
                return true;
            }
        });

        FreeStyleBuild build = j.buildAndAssertSuccess(project);
        j.assertLogContains(logWithHiddenPassword, build);
        j.assertLogNotContains(logWithClearTextPassword, build);
    }

    private static final class MyPasswordParameter extends hudson.model.PasswordParameterValue {

        @Serial
        private static final long serialVersionUID = 1L;

        public MyPasswordParameter() {
            super("MYPASSWORD", "qwerty123");
        }
    }
}

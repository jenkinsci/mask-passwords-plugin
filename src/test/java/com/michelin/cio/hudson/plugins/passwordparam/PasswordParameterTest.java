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
package com.michelin.cio.hudson.plugins.passwordparam;

import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper;
import com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsConfig;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.util.Secret;
import java.io.IOException;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

/**
 * Tests of {@link PasswordParameterValue} and {@link PasswordParameterDefinition}.
 * @author bpmarinho
 */
public class PasswordParameterTest {
    
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Before
    public void dropCache() {
        MaskPasswordsConfig.getInstance().reset();
    }
    
    @Test
    @Issue("JENKINS-41955")
    public void shouldMaskPasswordParameterClassByDefault() {
        Assert.assertTrue( PasswordParameterValue.class + " must be masked by default",
            MaskPasswordsConfig.getInstance().isMasked(PasswordParameterValue.class.getName()));
    }
    
    @Test
    @Issue("JENKINS-41955")
    public void shouldMaskPasswordParameterValueByDefault() {
        PasswordParameterDefinition d = new PasswordParameterDefinition("FOO", "BAR");
        ParameterValue created = d.createValue(Secret.fromString("hello"));
        
        // We pass the non-existent class name in order to ensure that the Value metadata check is enough
        Assert.assertTrue( PasswordParameterValue.class + " must be masked by default",
            MaskPasswordsConfig.getInstance().isMasked(created, "nonExistent"));
    }
    
    @Test
    @Issue("JENKINS-41955")
    public void passwordParameterShouldBeMaskedInFreestyleProject() throws Exception {
        final String clearTextPassword = "myClearTextPassword";
        final String logWithClearTextPassword = "printed " + clearTextPassword + " oops";
        final String logWithHiddenPassword = "printed ******** oops";

        FreeStyleProject project
                = j.jenkins.createProject(FreeStyleProject.class, "testPasswordParameter");

        PasswordParameterDefinition passwordParameterDefinition = new PasswordParameterDefinition("Password1", null);
        ParametersDefinitionProperty parametersDefinitionProperty
                = new ParametersDefinitionProperty(passwordParameterDefinition);
        project.addProperty(parametersDefinitionProperty);

        MaskPasswordsBuildWrapper maskPasswordsBuildWrapper
                = new MaskPasswordsBuildWrapper(Collections.<MaskPasswordsBuildWrapper.VarPasswordPair>emptyList());
        project.getBuildWrappersList().add(maskPasswordsBuildWrapper);

        project.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                listener.getLogger().println(logWithClearTextPassword);
                build.setResult(Result.SUCCESS);
                return true;
            }
        });

        FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause(), 
                new ParametersAction(passwordParameterDefinition.createValue(Secret.fromString(clearTextPassword))))
                .get();
        j.assertBuildStatusSuccess(j.waitForCompletion(build));
        j.assertLogContains(logWithHiddenPassword, build);
        j.assertLogNotContains(logWithClearTextPassword, build);
    }
}

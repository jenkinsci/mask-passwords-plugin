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
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import java.io.IOException;
import java.util.Collections;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

/**
 * Tests of {@link PasswordParameterValue} and {@link PasswordParameterDefinition}.
 * @author bpmarinho
 */
public class PasswordParameterTest {
    
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();
    
    @Test
    @Issue("JENKINS-41955")
    public void passwordParameterShouldBeMaskedInFreestyleProject() throws Exception {
        story.addStep(new Statement() {
            String clearTextPassword = "myClearTextPassword";
            String logWithClearTextPassword = "printed " + clearTextPassword + " oops";
            String logWithHiddenPassword = "printed ******** oops";
            
            @Override
            public void evaluate() throws Throwable {
                FreeStyleProject project =
                        story.j.jenkins.createProject(FreeStyleProject.class, "testPasswordParameter");
                
                hudson.model.PasswordParameterDefinition passwordParameterDefinition =
                        new hudson.model.PasswordParameterDefinition("Password1", clearTextPassword, null);
                ParametersDefinitionProperty parametersDefinitionProperty =
                        new ParametersDefinitionProperty(passwordParameterDefinition);
                project.addProperty(parametersDefinitionProperty);
                
                MaskPasswordsBuildWrapper maskPasswordsBuildWrapper =
                        new MaskPasswordsBuildWrapper(Collections.<MaskPasswordsBuildWrapper.VarPasswordPair>emptyList());
                project.getBuildWrappersList().add(maskPasswordsBuildWrapper);
                
                project.getBuildersList().add(new TestBuilder() {
                    @Override
                    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                        listener.getLogger().println(logWithClearTextPassword);
                        build.setResult(Result.SUCCESS);
                        return true;
                    }
                });
                
                FreeStyleBuild build = project.scheduleBuild2(0, new Cause.UserIdCause()).get();
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(build));
                story.j.assertLogContains(logWithHiddenPassword, build);
                story.j.assertLogNotContains(logWithClearTextPassword, build);
            }
        });
    }
}

/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.CoreWrapperStep;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

@Issue("JENKINS-27392")
public class MaskPasswordsWorkflowTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test public void configRoundTrip() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                MaskPasswordsBuildWrapper bw1 = new MaskPasswordsBuildWrapper(Collections.singletonList(new MaskPasswordsBuildWrapper.VarPasswordPair("PASSWORD", "s3cr3t")));
                CoreWrapperStep step1 = new CoreWrapperStep(bw1);
                CoreWrapperStep step2 = new StepConfigTester(story.j).configRoundTrip(step1);
                MaskPasswordsBuildWrapper bw2 = (MaskPasswordsBuildWrapper) step2.getDelegate();
                List<MaskPasswordsBuildWrapper.VarPasswordPair> pairs = bw2.getVarPasswordPairs();
                assertEquals(1, pairs.size());
                MaskPasswordsBuildWrapper.VarPasswordPair pair = pairs.get(0);
                assertEquals("PASSWORD", pair.getVar());
                assertEquals("s3cr3t", pair.getPassword());
            }
        });
    }

    @Test public void basics() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("node {wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[var: 'PASSWORD', password: 's3cr3t']]]) {semaphore 'restarting'; echo 'printed s3cr3t oops'}}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("restarting/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                SemaphoreStep.success("restarting/1", null);
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("printed ******** oops", b);
            }
        });
    }

}

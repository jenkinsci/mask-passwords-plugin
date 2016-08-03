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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.io.FileUtils;
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

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                MaskPasswordsBuildWrapper bw1 = new MaskPasswordsBuildWrapper(
                  Collections.singletonList(new MaskPasswordsBuildWrapper.VarPasswordPair("PASSWORD", "s3cr3t")),
                  Collections.singletonList(new MaskPasswordsBuildWrapper.VarMaskRegex("foobar"))
                );
                CoreWrapperStep step1 = new CoreWrapperStep(bw1);
                CoreWrapperStep step2 = new StepConfigTester(story.j).configRoundTrip(step1);
                MaskPasswordsBuildWrapper bw2 = (MaskPasswordsBuildWrapper) step2.getDelegate();
                List<MaskPasswordsBuildWrapper.VarPasswordPair> pairs = bw2.getVarPasswordPairs();
                List<MaskPasswordsBuildWrapper.VarMaskRegex> regexes = bw2.getVarMaskRegexes();
                assertEquals(1, pairs.size());
                assertEquals(1, regexes.size());
                MaskPasswordsBuildWrapper.VarPasswordPair pair = pairs.get(0);
                assertEquals("PASSWORD", pair.getVar());
                assertEquals("s3cr3t", pair.getPassword());
                MaskPasswordsBuildWrapper.VarMaskRegex regex = regexes.get(0);
                assertEquals("foobar", regex.getRegex());
            }
        });
    }

    @Test
    public void basics() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("node {wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[var: 'PASSWORD', password: 's3cr3t']]]) {semaphore 'restarting'; echo 'printed s3cr3t oops'}}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("restarting/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                assertEquals("TODO cannot keep it out of the closure block, but at least outside users cannot see this; withCredentials does better", new HashSet<String>(Arrays.asList("build.xml", "program.dat")), grep(b.getRootDir(), "s3cr3t"));
                SemaphoreStep.success("restarting/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("printed ******** oops", b);
                assertEquals("in build.xml only because it was literally in program text", Collections.singleton("build.xml"), grep(b.getRootDir(), "s3cr3t"));
            }
        });
    }

    @Test
    public void notEnabledGlobally() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                MaskPasswordsConfig config = MaskPasswordsConfig.getInstance();
                config.setGlobalVarEnabledGlobally(false);
                MaskPasswordsConfig.save(config);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p2");
                p.setDefinition(new CpsFlowDefinition("node {semaphore 'restarting'; echo 'printed s3cr3t oops'}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("restarting/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p2", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                assertEquals("TODO cannot keep it out of the closure block, but at least outside users cannot see this; withCredentials does better", new HashSet<String>(Arrays.asList("build.xml", "program.dat")), grep(b.getRootDir(), "s3cr3t"));
                SemaphoreStep.success("restarting/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("printed s3cr3t oops", b);
            }
        });
    }

    @Test
    public void enabledGlobally() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                MaskPasswordsConfig config = MaskPasswordsConfig.getInstance();
                config.setGlobalVarEnabledGlobally(true);
                config.addGlobalVarMaskRegex(new MaskPasswordsBuildWrapper.VarMaskRegex("s\\dcr[0-9]t"));
                MaskPasswordsConfig.save(config);
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p2");
                p.setDefinition(new CpsFlowDefinition("node {semaphore 'restarting'; echo 'printed s3cr3t oops'}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("restarting/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p2", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                assertEquals("TODO cannot keep it out of the closure block, but at least outside users cannot see this; withCredentials does better", new HashSet<String>(Arrays.asList("build.xml", "program.dat")), grep(b.getRootDir(), "s3cr3t"));
                SemaphoreStep.success("restarting/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("printed ******** oops", b);
            }
        });
    }

    // Copied from credentials-binding-plugin; perhaps belongs in JenkinsRule?
    private static Set<String> grep(File dir, String text) throws IOException {
        Set<String> matches = new TreeSet<String>();
        grep(dir, text, "", matches);
        return matches;
    }
    private static void grep(File dir, String text, String prefix, Set<String> matches) throws IOException {
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File kid : kids) {
            String qualifiedName = prefix + kid.getName();
            if (kid.isDirectory()) {
                grep(kid, text, qualifiedName + "/", matches);
            } else if (kid.isFile() && FileUtils.readFileToString(kid).contains(text)) {
                matches.add(qualifiedName);
            }
        }
    }

}

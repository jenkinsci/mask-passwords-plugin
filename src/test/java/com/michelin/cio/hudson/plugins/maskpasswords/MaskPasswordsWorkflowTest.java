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

import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.util.Secret;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.CoreWrapperStep;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;

@Issue("JENKINS-27392")
public class MaskPasswordsWorkflowTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test
    public void configRoundTrip() throws Exception {
        story.then(step -> {
                MaskPasswordsBuildWrapper bw1 = new MaskPasswordsBuildWrapper(
                  Collections.singletonList(new MaskPasswordsBuildWrapper.VarPasswordPair("PASSWORD", Secret.fromString("s3cr3t")))
                );
                CoreWrapperStep step1 = new CoreWrapperStep(bw1);
                CoreWrapperStep step2 = new StepConfigTester(step).configRoundTrip(step1);
                MaskPasswordsBuildWrapper bw2 = (MaskPasswordsBuildWrapper) step2.getDelegate();
                List<MaskPasswordsBuildWrapper.VarPasswordPair> pairs = bw2.getVarPasswordPairs();
                assertEquals(1, pairs.size());
                MaskPasswordsBuildWrapper.VarPasswordPair pair = pairs.get(0);
                assertEquals("PASSWORD", pair.getVar());
                assertEquals("s3cr3t", pair.getPassword().getPlainText());
        });
    }

    @Test
    public void regexConfigRoundTrip() throws Exception {
        story.then(step -> {
                MaskPasswordsBuildWrapper bw1 = new MaskPasswordsBuildWrapper(
                  null,
                  Collections.singletonList(new MaskPasswordsConfig.VarMaskRegexEntry("test", "foobar"))
                );
                CoreWrapperStep step1 = new CoreWrapperStep(bw1);
                CoreWrapperStep step2 = new StepConfigTester(step).configRoundTrip(step1);
                MaskPasswordsBuildWrapper bw2 = (MaskPasswordsBuildWrapper) step2.getDelegate();
                List<MaskPasswordsConfig.VarMaskRegexEntry> regexes = bw2.getVarMaskRegexes();
                assertEquals(1, regexes.size());
                MaskPasswordsConfig.VarMaskRegexEntry regex = regexes.get(0);
                assertEquals("foobar", regex.getRegexString());
                assertEquals("test", regex.getKey());
        });
    }

    @Test
    public void basics() throws Exception {
        story.then(step -> {
            WorkflowJob p = step.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("node {wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [[var: 'PASSWORD', password: 's3cr3t']]]) {semaphore 'restarting'; echo 'printed s3cr3t oops'}}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("restarting/1", b);
        });

        story.then(step -> {
            WorkflowJob p = step.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getLastBuild();
            Set<String> expected = new HashSet<>(Arrays.asList("build.xml", "program.dat", "workflow/5.xml"));
            if (!isWindows()) {
                // Skip assertion on Windows, temporary files contaminate content frequently
                assertEquals("TODO cannot keep it out of the closure block, but at least outside users cannot see this; withCredentials does better", expected, grep(b.getRootDir(), "s3cr3t"));
            }
            SemaphoreStep.success("restarting/1", null);
            step.assertBuildStatusSuccess(step.waitForCompletion(b));
            step.assertLogContains("printed ******** oops", b);
            step.assertLogNotContains("printed s3cr3t oops", b);
            expected = new HashSet<>(Arrays.asList("build.xml", "workflow/5.xml", "workflow/8.xml"));
            if (!isWindows()) {
                // Skip assertion on Windows, temporary files contaminate content frequently
                assertEquals("in build.xml only because it was literally in program text", expected, grep(b.getRootDir(), "s3cr3t"));
            }
        });
    }

    @Test
    public void basicsRegex() throws Exception {
        story.then(step -> {
            WorkflowJob p = step.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("node {wrap([$class: 'MaskPasswordsBuildWrapper', varMaskRegexes: [[key: 'REGEX', value: 's3cr3t']]]) {semaphore 'restarting'; echo 'printed s3cr3t oops'}}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("restarting/1", b);
        });

        story.then(step -> {
            WorkflowJob p = step.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getLastBuild();
            Set<String> expected = new HashSet<>(Arrays.asList("build.xml", "program.dat", "workflow/5.xml"));
            if (!isWindows()) {
                // Skip assertion on Windows, temporary files contaminate content frequently
                assertEquals("TODO cannot keep it out of the closure block, but at least outside users cannot see this; withCredentials does better", expected, grep(b.getRootDir(), "s3cr3t"));
            }
            SemaphoreStep.success("restarting/1", null);
            step.assertBuildStatusSuccess(step.waitForCompletion(b));
            step.assertLogContains("printed ******** oops", b);
            step.assertLogNotContains("printed s3cr3t oops", b);
            expected = new HashSet<>(Arrays.asList("build.xml", "workflow/5.xml", "workflow/8.xml"));
            if (!isWindows()) {
                // Skip assertion on Windows, temporary files contaminate content frequently
                assertEquals("in build.xml only because it was literally in program text", expected, grep(b.getRootDir(), "s3cr3t"));
            }
        });
    }

    @Test
    public void noWorkspaceRequired() {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("maskPasswords(varPasswordPairs: [[var: 'PASSWORD', password: 's3cr3t']]) {echo 'printed s3cr3t oops'}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
            r.assertLogContains("printed ******** oops", b);
            r.assertLogNotContains("printed s3cr3t oops", b);
        });
    }

    @Test
    public void noWorkspaceRequiredRegex() {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("maskPasswords(varMaskRegexes: [[key: 'REGEX', value: 's3cr3t']]) {echo 'printed s3cr3t oops'}", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
            r.assertLogContains("printed ******** oops", b);
            r.assertLogNotContains("printed s3cr3t oops", b);
        });
    }

    // test to ensure that when the ConsoleLogFilter isn't enabled globally,
    // it doesn't change the output (i.e. it respects the config setting).
    // Note that per JENKINS-30777, this does not work with Pipeline jobs
    // we would need to implement a TaskListenerDecorator for that to work
    @Test
    public void notEnabledGlobally() throws Exception {

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                MaskPasswordsConfig config = MaskPasswordsConfig.getInstance();
                config.setGlobalVarEnabledGlobally(false);
                config.addGlobalVarMaskRegex(new MaskPasswordsBuildWrapper.VarMaskRegex("s\\dcr[0-9]t"));
                MaskPasswordsConfig.save(config);
                FreeStyleProject p = story.j.jenkins.createProject(FreeStyleProject.class, "p2");
                p.getBuildersList().add( new TestBuilder() {
                    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                        listener.getLogger().println("printed s3cr3t oops");
                        build.setResult(Result.SUCCESS);
                        return true;
                    }
                });
                FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause()).get();
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("printed s3cr3t oops", b);
            }
        });
    }

    // Test to ensure that when the plugin/ConsoleLogFilter **is** enabled globally,
    // it actually suppresses the log output. Note that per JENKINS-30777,
    // this does not work with Pipeline jobs
    // we would need to implement a TaskListenerDecorator for that to work
    @Test
    public void enabledGlobally() throws Exception {

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                MaskPasswordsConfig config = MaskPasswordsConfig.getInstance();
                config.setGlobalVarEnabledGlobally(true);
                config.addGlobalVarMaskRegex(new MaskPasswordsBuildWrapper.VarMaskRegex("s\\dcr[0-9]t"));
                MaskPasswordsConfig.save(config);
                FreeStyleProject p = story.j.jenkins.createProject(FreeStyleProject.class, "p2");
                p.getBuildersList().add( new TestBuilder() {
                    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                        listener.getLogger().println("printed s3cr3t oops");
                        build.setResult(Result.SUCCESS);
                        return true;
                    }
                });
                FreeStyleBuild b = p.scheduleBuild2(0, new Cause.UserIdCause()).get();
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("printed ******** oops", b);
                story.j.assertLogNotContains("printed s3cr3t oops", b);
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

    private static boolean isWindows() {
        return File.pathSeparatorChar == ';';
    }
}

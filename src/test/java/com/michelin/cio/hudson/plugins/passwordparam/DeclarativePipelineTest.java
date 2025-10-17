package com.michelin.cio.hudson.plugins.passwordparam;

import hudson.model.ParametersAction;
import hudson.util.Secret;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class DeclarativePipelineTest {

    private static final String SCRIPT_WITH_NON_STORED_PASSWORD =
            """
                pipeline {
                    agent none
                    parameters {
                        nonStoredPassword(name: 'MY_PASSWORD', description: 'Test password parameter')
                    }
                    stages {
                        stage('Test') {
                            steps {
                                echo 'Testing nonStoredPassword parameter'
                            }
                        }
                    }
                }
            """;

    private JenkinsRule j;

    @BeforeEach
    void beforeEach(JenkinsRule j) throws Exception {
        this.j = j;
    }

    @Test
    void test_nonStoredPassword_symbol_in_declarative_pipeline() throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, "test-nonStoredPassword");
        job.setDefinition(new CpsFlowDefinition(SCRIPT_WITH_NON_STORED_PASSWORD, true));
        
        // First run: defines the parameter for the job
        WorkflowRun run1 = job.scheduleBuild2(0).get();
        j.waitForCompletion(run1);
        j.assertBuildStatusSuccess(run1);
        
        // Second run: accepts the parameter value
        PasswordParameterValue paramValue = new PasswordParameterValue("MY_PASSWORD", Secret.fromString("testPassword123"), "Test password parameter");
        WorkflowRun run2 = job.scheduleBuild2(0, new ParametersAction(paramValue)).get();
        j.waitForCompletion(run2);
        j.assertBuildStatusSuccess(run2);
    }
}
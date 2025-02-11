/*
 * The MIT License
 *
 * Copyright (c) 2017 CloudBees, Inc.
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

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of {@link MaskPasswordsConfig}.
 * These tests do not depend on the caching being done in the Jenkins instance.
 *
 * @author Oleg Nenashev
 */
@WithJenkins
class MaskPasswordConfigTests {

    @Test
    void shouldConsiderAsMasked_cmchppPasswordParameterValue(JenkinsRule j) {
        assertIsMasked(com.michelin.cio.hudson.plugins.passwordparam.PasswordParameterValue.class);
    }

    @Test
    void shouldConsiderAsMasked_hmPasswordParameterValue(JenkinsRule j) {
        assertIsMasked(hudson.model.PasswordParameterValue.class);
    }

    @Test
    void shouldNotMaskTheBaseClass(JenkinsRule j) {
        assertIsNotMasked(hudson.model.ParameterValue.class);
    }

    @Test
    void shouldNotMaskTheBasicParameterTypes(JenkinsRule j) {
        assertIsNotMasked(hudson.model.StringParameterValue.class);
        assertIsNotMasked(hudson.model.FileParameterValue.class);
    }

    @Test
    void shouldReloadCorrectly_fromMissingFile(JenkinsRule j) {
        // Initialize caches
        MaskPasswordsConfig instance = MaskPasswordsConfig.getInstance();
        assertIsNotMasked(instance, hudson.model.StringParameterValue.class);
        assertIsNotMasked(instance, hudson.model.FileParameterValue.class);

        MaskPasswordsConfig loaded = MaskPasswordsConfig.load();
        assertIsNotMasked(loaded, hudson.model.StringParameterValue.class);
        assertIsNotMasked(loaded, hudson.model.FileParameterValue.class);
    }

    @Test
    @Issue("JENKINS-43504")
    void shouldReloadCorrectly_fromFile(JenkinsRule j) throws Exception {
        // Initialize caches
        MaskPasswordsConfig instance = MaskPasswordsConfig.getInstance();
        assertIsNotMasked(instance, hudson.model.StringParameterValue.class);
        assertIsNotMasked(instance, hudson.model.FileParameterValue.class);
        MaskPasswordsConfig.save(instance);

        MaskPasswordsConfig loaded = MaskPasswordsConfig.load();
        assertIsNotMasked(loaded, hudson.model.StringParameterValue.class);
        assertIsNotMasked(loaded, hudson.model.FileParameterValue.class);
    }

    private static void assertIsMasked(Class<?> clazz) {
        MaskPasswordsConfig instance = MaskPasswordsConfig.getInstance();
        assertIsMasked(instance, clazz);
    }

    private static void assertIsMasked(MaskPasswordsConfig config, Class<?> clazz) {
        config.invalidatePasswordValueClassCaches();
        assertTrue(config.guessIfShouldMask(clazz.getName()), "Expected that the class is masked: " + clazz);
    }

    private static void assertIsNotMasked(Class<?> clazz) {
        MaskPasswordsConfig instance = MaskPasswordsConfig.getInstance();
        assertIsNotMasked(instance, clazz);
    }

    private static void assertIsNotMasked(MaskPasswordsConfig config, Class<?> clazz) {
        config.invalidatePasswordValueClassCaches();
        assertFalse(config.guessIfShouldMask(clazz.getName()), "Expected that the class is not masked: " + clazz);
    }
}

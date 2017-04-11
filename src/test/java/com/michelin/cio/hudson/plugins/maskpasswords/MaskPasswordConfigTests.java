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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Tests of {@link MaskPasswordsConfig}.
 * These tests do not depend on the caching being done in the Jenkins instance.
 * @author Oleg Nenashev
 */
public class MaskPasswordConfigTests {
    
    // The logic inside needs the classloader
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    @Test
    public void shouldConsiderAsMasked_cmchppPasswordParameterValue() {
        assertIsMasked(com.michelin.cio.hudson.plugins.passwordparam.PasswordParameterValue.class);   
    }
    
    @Test
    public void shouldConsiderAsMasked_hmPasswordParameterValue() {
        assertIsMasked(hudson.model.PasswordParameterValue.class);
    }
    
    @Test
    public void shouldNotMaskTheBaseClass() {
        assertIsNotMasked(hudson.model.ParameterValue.class);
    }
    
    @Test
    public void shouldNotMaskTheBasicParameterTypes() {
        assertIsNotMasked(hudson.model.StringParameterValue.class);
        assertIsNotMasked(hudson.model.FileParameterValue.class);
    }
    
    @Test
    public void shouldReloadCorrectly_fromMissingFile() {
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
    public void shouldReloadCorrectly_fromFile() throws Exception {
        // Initialize caches
        MaskPasswordsConfig instance = MaskPasswordsConfig.getInstance();
        assertIsNotMasked(instance, hudson.model.StringParameterValue.class);
        assertIsNotMasked(instance, hudson.model.FileParameterValue.class);
        MaskPasswordsConfig.save(instance);
        
        MaskPasswordsConfig loaded = MaskPasswordsConfig.load();
        assertIsNotMasked(loaded, hudson.model.StringParameterValue.class);
        assertIsNotMasked(loaded, hudson.model.FileParameterValue.class);
    }
    
    private void assertIsMasked(Class<?> clazz) {
        MaskPasswordsConfig instance = MaskPasswordsConfig.getInstance();
        assertIsMasked(instance, clazz);
    }
    
    private void assertIsMasked(MaskPasswordsConfig config, Class<?> clazz) {
        config.invalidatePasswordValueClassCaches();
        assertTrue("Expected that the class is masked: " + clazz, config.guessIfShouldMask(clazz.getName()));
    }
    
    private void assertIsNotMasked(Class<?> clazz) {
        MaskPasswordsConfig instance = MaskPasswordsConfig.getInstance();
        assertIsNotMasked(instance, clazz);
    }
    
    private void assertIsNotMasked(MaskPasswordsConfig config, Class<?> clazz) {
        config.invalidatePasswordValueClassCaches();
        assertFalse("Expected that the class is not masked: " + clazz, config.guessIfShouldMask(clazz.getName()));
    }
}

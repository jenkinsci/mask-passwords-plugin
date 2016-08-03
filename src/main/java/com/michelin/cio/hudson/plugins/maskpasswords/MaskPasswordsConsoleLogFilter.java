/*
 * The MIT License
 *
 * Copyright (c) 2010-2012, Manufacture Francaise des Pneumatiques Michelin,
 * Romain Seguy
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

import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.console.ConsoleLogFilter;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.Secret;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.tasks.SimpleBuildWrapper;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * GLOBAL Console Log Filter that alters the console so that passwords don't
 * get displayed.
 *
 * @author Jason Antman <jason@jasonantman.com>
 */
@Extension
public class MaskPasswordsConsoleLogFilter extends ConsoleLogFilter {

  public MaskPasswordsConsoleLogFilter() {
    // nothing to do here; this object lives for the lifetime of Jenkins,
    // so if we don't want to have to restart to detect config changes,
    // we need to get the config in each run.
  }

  @SuppressWarnings("rawtypes")
  @Override
  public OutputStream decorateLogger(AbstractBuild _ignore, OutputStream logger) throws IOException, InterruptedException {
      // check the config
      MaskPasswordsConfig config = MaskPasswordsConfig.getInstance();
      if(! config.isEnabledGlobally()) {
        LOGGER.log(Level.FINE, "MaskPasswords not enabled globally; not decorating logger");
        return logger;
      }
      LOGGER.log(Level.FINE, "MaskPasswords IS enabled globally; decorating logger");

      // build our config
      List<String> passwords = new ArrayList<String>();
      List<String> regexes = new ArrayList<String>();

      // global passwords
      List<MaskPasswordsBuildWrapper.VarPasswordPair> globalVarPasswordPairs = config.getGlobalVarPasswordPairs();
      for(MaskPasswordsBuildWrapper.VarPasswordPair globalVarPasswordPair: globalVarPasswordPairs) {
          passwords.add(globalVarPasswordPair.getPassword());
      }

      // global regexes
      List<MaskPasswordsBuildWrapper.VarMaskRegex> globalVarMaskRegexes = config.getGlobalVarMaskRegexes();
      for(MaskPasswordsBuildWrapper.VarMaskRegex globalVarMaskRegex: globalVarMaskRegexes) {
          regexes.add(globalVarMaskRegex.getRegex());
      }
      return new MaskPasswordsOutputStream(logger, passwords, regexes);
  }

  private static final Logger LOGGER = Logger.getLogger(MaskPasswordsConsoleLogFilter.class.getName());

}

/*
 * The MIT License
 *
 * Copyright (c) 2016 Cox Automotive, Inc./Manheim, Jason Antman.
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

import hudson.Extension;
import hudson.console.ConsoleLogFilter;
import hudson.model.Run;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GLOBAL Console Log Filter that alters the console so that passwords don't
 * get displayed.
 *
 * @author Jason Antman jason@jasonantman.com
 */
@Extension
public class MaskPasswordsConsoleLogFilter extends ConsoleLogFilter  implements Serializable {

  private static final long serialVersionUID = 1L;

  public MaskPasswordsConsoleLogFilter() {
    // nothing to do here; this object lives for the lifetime of Jenkins,
    // so if we don't want to have to restart to detect config changes,
    // we need to get the config in each run.
  }

  @SuppressWarnings("rawtypes")
  @Override
  public OutputStream decorateLogger(Run _ignore, OutputStream logger) throws IOException, InterruptedException {
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
          passwords.add(globalVarPasswordPair.getPlainTextPassword());
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

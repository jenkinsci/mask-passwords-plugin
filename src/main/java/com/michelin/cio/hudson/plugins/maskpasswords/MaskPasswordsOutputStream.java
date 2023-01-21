/*
 * The MIT License
 *
 * Copyright (c) 2010-2011, Manufacture Francaise des Pneumatiques Michelin,
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.console.LineTransformationOutputStream;
import org.apache.commons.lang.StringUtils;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import static com.michelin.cio.hudson.plugins.util.MaskPasswordsUtil.secretsMaskPatterns;

//TODO: UTF-8 hardcoding is not a perfect solution
/**
 * Custom output stream which masks a predefined set of passwords.
 *
 * @author Romain Seguy (http://openromain.blogspot.com)
 */
public class MaskPasswordsOutputStream extends LineTransformationOutputStream {

    private final OutputStream logger;
    private final List<Pattern> passwordsAsPatterns;
    private final String runName;

    /**
     * @param logger The output stream to which this {@link MaskPasswordsOutputStream}
     *               will write to
     * @param passwords A collection of {@link String}s to be masked
     * @param regexes A collection of Regular Expression {@link String}s to be masked
     * @param runName A string representation of the Run/Build the output stream logger is associated with. Used for logging purposes.
     */
    public MaskPasswordsOutputStream(OutputStream logger, @CheckForNull Collection<String> passwords, @CheckForNull Collection<String> regexes, String runName) {
        this.logger = logger;
        this.runName = (runName != null) ? runName : "";
        passwordsAsPatterns = new ArrayList<>();

        if (passwords != null) {
            // Passwords aggregated into single regex which is compiled as a pattern for efficiency
            StringBuilder pwRegex = new StringBuilder().append('(');
            int pwCount = 0;
            for (String pw : passwords) {
                if (StringUtils.isNotEmpty(pw)) {
                    pwCount++;
                    pwRegex.append(Pattern.quote(pw));
                    pwRegex.append('|');
                    try {
                        String encodedPassword = URLEncoder.encode(pw, "UTF-8");
                        if (!encodedPassword.equals(pw)) {
                            pwRegex.append(Pattern.quote(encodedPassword));
                            pwRegex.append('|');
                        }
                    } catch (UnsupportedEncodingException e) {
                        // ignore any encoding problem => status quo
                    }
                }
            }
            if (pwCount > 0) {
                pwRegex.deleteCharAt(pwRegex.length()-1); // removes the last unuseful pipe
                pwRegex.append(')');
                passwordsAsPatterns.add(Pattern.compile(pwRegex.toString()));
            }
        }
        if (regexes != null) {
            for (String r: regexes) {
                passwordsAsPatterns.add(Pattern.compile(r));
            }
        }

    }

    /**
     * @param logger The output stream to which this {@link MaskPasswordsOutputStream}
     *               will write to
     * @param passwords A collection of {@link String}s to be masked
     */
    public MaskPasswordsOutputStream(OutputStream logger, @CheckForNull Collection<String> passwords) {
        this(logger, passwords, null);
    }

    public MaskPasswordsOutputStream(OutputStream logger, @CheckForNull Collection<String> passwords, @CheckForNull Collection<String> regexes) {
        this(logger, passwords, regexes, "");
    }

    // TODO: The logic relies on the default encoding, which may cause issues when master and agent have different encodings
    @SuppressFBWarnings(value = "DM_DEFAULT_ENCODING", justification = "Open TODO item for wider rework")
    @Override
    protected void eol(byte[] bytes, int len) throws IOException {
        String line = new String(bytes, 0, len);
        if(passwordsAsPatterns != null && line != null) {
            line = secretsMaskPatterns(passwordsAsPatterns, line, runName);
        }
        logger.write(line.getBytes());
    }

    /**
     * {@inheritDoc}
     * @throws IOException on error
     */
    @Override
    public void close() throws IOException {
        super.close();
        logger.close();
    }

    /**
     * {@inheritDoc}
     * @throws IOException on error
     */
    @Override
    public void flush() throws IOException {
        super.flush();
        logger.flush();
    }
}

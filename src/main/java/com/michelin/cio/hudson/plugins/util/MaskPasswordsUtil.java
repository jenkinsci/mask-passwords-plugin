package com.michelin.cio.hudson.plugins.util;

import org.apache.commons.lang.StringUtils;

import javax.annotation.CheckForNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaskPasswordsUtil {
    private static final Logger LOGGER = Logger.getLogger(MaskPasswordsUtil.class.getName());
    public final static String MASKED_STRING = "********";

    public static List<String> patternMatch(List<Pattern> ps, String s) {
        List<String> ret = new ArrayList<>();
        for (Pattern p: ps) {
            Matcher m = p.matcher(s);
            while (m.find()) { // Regex matches
                if (m.groupCount() > 0) { // Regex contains group(s)
                    for (int i = 1; i <= m.groupCount(); i++) {
                        String toAdd = m.group(i);
                        if (toAdd != null) {
                            ret.add(toAdd);
                        }
                    }
                } else { // Regex doesn't contain groups, match entire Regex string
                    ret.add(m.group(0));
                }
            }
        }
        return ret;
    }

    public static List<String> patternMatch(Pattern p, String s) {
        return patternMatch(Arrays.asList(p), s);
    }

    public static String secretsMask(List<String> secrets, String s, String runName) {
        if (secrets != null && secrets.size() > 0) {
            for (String secret: secrets) {
                s = s.replaceAll(Pattern.quote(secret), MASKED_STRING);
            }
            LOGGER.info(String.format("Masking Run[%s]'s line: %s", runName, StringUtils.strip(s)));
        }
        return s;
    }

    public static String secretsMaskPattern(Pattern p, String s) {
        return StringUtils.isNotBlank(s) ? secretsMask(patternMatch(p, s), s, "") : s;
    }

    public static String secretsMaskPatterns(List<Pattern> ps, String s, String runName) {
        return StringUtils.isNotBlank(s) ? secretsMask(patternMatch(ps, s), s, runName) : s;
    }

    public static List<Pattern> passwordRegexCombiner(@CheckForNull Collection<String> passwords, @CheckForNull Collection<String> regexes) {
        List<Pattern> passwordsAsPatterns = new ArrayList<>();

        if (passwords != null) {
            for (String pw : passwords) {
                passwordsAsPatterns.add(Pattern.compile(pw));
            }
        }
        if (regexes != null) {
            for (String r: regexes) {
                passwordsAsPatterns.add(Pattern.compile(r));
            }
        }

        return passwordsAsPatterns;
    }
}

package com.michelin.cio.hudson.plugins.util;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static com.michelin.cio.hudson.plugins.maskpasswords.MaskPasswordsBuildWrapper.VarMaskRegex;
import static com.michelin.cio.hudson.plugins.util.MaskPasswordsUtil.passwordRegexCombiner;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MaskPasswordsUtilTest {

    @Test
    void testAwsMaskJson() throws IOException {
        String input = FileUtils.readFileToString(new File(getClass().getResource("echoAwsJson.txt").getFile()), "UTF-8");
        String maskedString = FileUtils.readFileToString(new File(getClass().getResource("echoAwsJsonMasked.txt").getFile()), "UTF-8");

        List<String> regexes = new ArrayList<>();
        List<VarMaskRegex> rs = new ArrayList<>();
        rs.add(new VarMaskRegex("['\"]+(?:(?i:SecretAccessKey)|(?i:AccessKeyId)|(?i:SessionToken))['\"]+:[']?\\s*['\"]+\\s*([a-zA-Z0-9\\/=+]*)['\"]?"));
        for (VarMaskRegex r : rs) {
            regexes.add(r.getRegex());
        }

        String output = MaskPasswordsUtil.secretsMaskPatterns(passwordRegexCombiner(null, regexes), input, "");

        assertEquals(maskedString, output);
    }

    @Test
    void testSimpleString() {
        String input = "test1 too12 test123 too1234 secret12345 secret1234";
        String expected = "t********1 t********12 t********123 t********1234 ********12345 ********1234";

        List<String> regexes = List.of("(?:t)(est|oo)(?:1)");
        List<String> passwords = List.of("secret");
        String output = MaskPasswordsUtil.secretsMaskPatterns(passwordRegexCombiner(passwords, regexes), input, "");

        assertEquals(expected, output);
    }

    // Test usecase where Regex doesn't contain grouping and want to match entire string
    @Test
    void testEntireRegex() {
        String expectStr = "AWS_SECRET_ACCESS_KEY=4KJOMHUs8BHcILmZ4KlLfKLjuIuSINfExPy4oZIC";
        String input = "export " + expectStr;
        List<String> expect = new ArrayList<>(List.of(expectStr));
        Pattern p = Pattern.compile("AWS_SECRET_ACCESS_KEY=[\\S]+");
        assertEquals(expect, MaskPasswordsUtil.patternMatch(p, input));
    }

    //
    // Test where Regex pattern has multiple matches in a single String
    @Test
    void testMultipleMatches() {
        String expect1 = "1234";
        String expect2 = "5678";
        List<String> expect = new ArrayList<>(Arrays.asList(expect1, expect2));
        String input = String.format("Secret = %s, Secret = %s", expect1, expect2);
        Pattern p = Pattern.compile("Secret = ([(0-9]*)");
        assertEquals(expect, MaskPasswordsUtil.patternMatch(p, input));
    }
}

/*
 * $Id$
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.tiles.locale;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

/**
 * Tests {@link LocaleSanitizer} (CVE-2023-49735).
 *
 * @version $Rev$ $Date$
 */
public class LocaleSanitizerTest {

    /**
     * Locales that must never be used to build a resource path: traversal,
     * absolute paths, URL-encoded traversal, URLs (SSRF), wildcards, control
     * characters, non-ASCII and oversized values.
     *
     * @return The unsafe locales.
     */
    public static List<Locale> unsafeLocales() {
        return Arrays.asList(
                new Locale("../../etc/passwd"),
                new Locale(".."),
                new Locale("en", "../x"),
                new Locale("en", "US", "../../x"),
                new Locale("en", "US", "a/b"),
                new Locale("en", "US", "..\\..\\x"),
                new Locale("..\\..\\windows\\win"),
                new Locale("%2e%2e%2f"),
                new Locale("en", "%2e%2e%2f"),
                new Locale("en", "US", "%2e%2e%2fetc%2fpasswd"),
                new Locale("/etc/passwd"),
                new Locale("en", "/etc/passwd"),
                new Locale("c:\\windows"),
                new Locale("file:///etc/passwd"),
                new Locale("http://169.254.169.254/latest/meta-data"),
                new Locale("en", "US", "*"),
                new Locale("en", "US", "a.b"),
                new Locale("en", "US", "x\0"),
                new Locale("en", "US", "x\r\nforged-log-line"),
                new Locale("en", "US", "x y"),
                new Locale("\u00e9n"),
                new Locale("languagetoolong"),
                new Locale("en", "COUNTRYTOOLONG"),
                new Locale("en", "US", "v1234567890123456789012345678901234567890123456789012345678901234"));
    }

    /**
     * Valid locales that must keep working exactly as before.
     *
     * @return The valid locales.
     */
    public static List<Locale> validLocales() {
        return Arrays.asList(
                Locale.ROOT,
                Locale.ENGLISH,
                Locale.US,
                Locale.CANADA_FRENCH,
                Locale.GERMAN,
                new Locale("es", "ES", "TRADITIONAL"),
                new Locale("es", "ES", "Traditional_WIN"),
                new Locale("ja", "JP", "JP"),
                new Locale("th", "TH", "TH"),
                Locale.forLanguageTag("de-DE-1996-fonipa"),
                Locale.forLanguageTag("sr-Latn-RS"),
                Locale.forLanguageTag("es-419"));
    }

    /**
     * Tests {@link LocaleSanitizer#isSafe(Locale)} with valid locales.
     */
    @Test
    public void testIsSafeAcceptsValidLocales() {
        for (Locale locale : validLocales()) {
            assertTrue("Valid locale rejected: " + locale, LocaleSanitizer.isSafe(locale));
        }
        for (Locale locale : Locale.getAvailableLocales()) {
            assertTrue("JDK locale rejected: " + locale, LocaleSanitizer.isSafe(locale));
        }
    }

    /**
     * Tests {@link LocaleSanitizer#isSafe(Locale)} with unsafe locales.
     */
    @Test
    public void testIsSafeRejectsUnsafeLocales() {
        assertFalse(LocaleSanitizer.isSafe(null));
        for (Locale locale : unsafeLocales()) {
            assertFalse("Unsafe locale accepted: " + locale, LocaleSanitizer.isSafe(locale));
        }
    }

    /**
     * Tests {@link LocaleSanitizer#safeOrRoot(Locale)}.
     */
    @Test
    public void testSafeOrRoot() {
        assertSame(Locale.ROOT, LocaleSanitizer.safeOrRoot(null));
        for (Locale locale : validLocales()) {
            assertSame(locale, LocaleSanitizer.safeOrRoot(locale));
        }
        for (Locale locale : unsafeLocales()) {
            assertSame(Locale.ROOT, LocaleSanitizer.safeOrRoot(locale));
        }
    }

    /**
     * Tests {@link LocaleSanitizer#toLogString(Object)}: untrusted values are
     * escaped and truncated, so they cannot forge log lines.
     */
    @Test
    public void testToLogString() {
        assertEquals("en_US", LocaleSanitizer.toLogString(Locale.US));
        assertEquals("\\u002e\\u002e\\u002fx", LocaleSanitizer.toLogString(new Locale("../x")));
        String escaped = LocaleSanitizer.toLogString(new Locale("en", "US", "x\r\nforged-log-line"));
        assertFalse(escaped.contains("\r"));
        assertFalse(escaped.contains("\n"));
        assertEquals("en_US_x\\u000d\\u000aforged-log-line", escaped);
        assertEquals("null", LocaleSanitizer.toLogString(null));
        StringBuilder longValue = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longValue.append('a');
        }
        String truncated = LocaleSanitizer.toLogString(longValue.toString());
        assertEquals(64 + "...".length(), truncated.length());
        assertTrue(truncated.endsWith("..."));
    }
}

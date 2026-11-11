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

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * Validates locales before they are used to locate localized definition files
 * such as <code>/WEB-INF/tiles_&lt;language&gt;_&lt;country&gt;_&lt;variant&gt;.xml</code>.
 * </p>
 * <p>
 * {@link Locale} instances created with the public constructors accept
 * arbitrary strings, e.g. <code>new Locale("../../x")</code>. If such a locale
 * reaches the code that builds a localized resource path, it allows path
 * traversal and, from there, SSRF/XXE (CVE-2023-49735). Every locale that may
 * end up in a resource path must therefore be checked with
 * {@link #isSafe(Locale)}.
 * </p>
 * <p>
 * A locale is safe when its language, script and country contain only ASCII
 * letters and digits (at most {@value #MAX_SUBTAG_LENGTH} characters each) and
 * its variant contains only ASCII letters, digits, <code>'_'</code> and
 * <code>'-'</code> (at most {@value #MAX_VARIANT_LENGTH} characters). Every
 * locale built by the JDK from a well-formed language tag, every locale
 * returned by {@link Locale#getAvailableLocales()}, legacy variants such as
 * <code>es_ES_TRADITIONAL</code> or <code>ja_JP_JP</code>, and
 * {@link Locale#ROOT} are safe.
 * </p>
 *
 * @version $Rev$ $Date$
 * @since 4.0.1
 */
public final class LocaleSanitizer {

    /**
     * Maximum length of the language, script and country parts.
     */
    public static final int MAX_SUBTAG_LENGTH = 8;

    /**
     * Maximum length of the variant part. Variants may legitimately contain
     * several subtags, e.g. <code>Traditional_WIN</code> or
     * <code>1996_fonipa</code>.
     */
    public static final int MAX_VARIANT_LENGTH = 64;

    /**
     * Maximum number of characters of an untrusted value written to the log.
     */
    private static final int MAX_LOGGED_LENGTH = 64;

    /**
     * The logging object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(LocaleSanitizer.class);

    /**
     * Private constructor to avoid instantiation.
     */
    private LocaleSanitizer() {
    }

    /**
     * Checks whether a locale is safe to be used to build a localized resource
     * path.
     *
     * @param locale The locale to check.
     * @return <code>true</code> if the locale is not <code>null</code> and only
     * contains allowed characters.
     */
    public static boolean isSafe(Locale locale) {
        if (locale == null) {
            return false;
        }
        return isSafePart(locale.getLanguage(), MAX_SUBTAG_LENGTH, false)
                && isSafePart(locale.getScript(), MAX_SUBTAG_LENGTH, false)
                && isSafePart(locale.getCountry(), MAX_SUBTAG_LENGTH, false)
                && isSafePart(locale.getVariant(), MAX_VARIANT_LENGTH, true);
    }

    /**
     * Returns a locale that can safely be used as a customization key to load
     * localized definitions: <code>null</code> and unsafe locales are replaced
     * by {@link Locale#ROOT}, i.e. the non-localized (default) definitions.
     *
     * @param locale The locale to check. It may be <code>null</code>.
     * @return The locale itself if it is safe, {@link Locale#ROOT} otherwise.
     */
    public static Locale safeOrRoot(Locale locale) {
        if (locale == null) {
            return Locale.ROOT;
        }
        if (!isSafe(locale)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Ignoring unsafe locale \"" + toLogString(locale)
                        + "\" while loading localized definitions; using the default definitions instead.");
            }
            return Locale.ROOT;
        }
        return locale;
    }

    /**
     * Renders an untrusted value so that it can be written to a log safely:
     * every character other than ASCII letters, digits, <code>'_'</code> and
     * <code>'-'</code> is escaped as <code>\\uXXXX</code>, and the result is
     * truncated to 64 characters.
     *
     * @param value The value to render. It may be <code>null</code>.
     * @return The escaped representation.
     */
    public static String toLogString(Object value) {
        String string = String.valueOf(value);
        int length = Math.min(string.length(), MAX_LOGGED_LENGTH);
        StringBuilder builder = new StringBuilder(length + 16);
        for (int i = 0; i < length; i++) {
            char ch = string.charAt(i);
            if (isAsciiLetterOrDigit(ch) || ch == '_' || ch == '-') {
                builder.append(ch);
            } else {
                builder.append(String.format("\\u%04x", (int) ch));
            }
        }
        if (string.length() > MAX_LOGGED_LENGTH) {
            builder.append("...");
        }
        return builder.toString();
    }

    /**
     * Checks a single part of a locale.
     *
     * @param part The part to check. <code>null</code> is treated as empty.
     * @param maxLength The maximum allowed length.
     * @param allowSeparators Whether <code>'_'</code> and <code>'-'</code> are
     * allowed.
     * @return <code>true</code> if the part only contains allowed characters.
     */
    private static boolean isSafePart(String part, int maxLength, boolean allowSeparators) {
        if (part == null) {
            return true;
        }
        int length = part.length();
        if (length > maxLength) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            char ch = part.charAt(i);
            if (!isAsciiLetterOrDigit(ch) && !(allowSeparators && (ch == '_' || ch == '-'))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether a character is an ASCII letter or digit.
     *
     * @param ch The character.
     * @return <code>true</code> if it is in <code>[A-Za-z0-9]</code>.
     */
    private static boolean isAsciiLetterOrDigit(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9');
    }
}

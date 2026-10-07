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
package org.apache.tiles.locale.impl;

import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.tiles.locale.LocaleResolver;
import org.apache.tiles.locale.LocaleSanitizerTest;
import org.apache.tiles.request.Request;
import org.junit.Test;

/**
 * Tests {@link DefaultLocaleResolver}.
 *
 * @version $Rev$ $Date$
 */
public class DefaultLocaleResolverTest {

    /**
     * Test method for {@link DefaultLocaleResolver#resolveLocale(Request)}.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testResolveLocale() {
        Request request = createMock(Request.class);
        Map<String, Object> sessionScope = createMock(Map.class);
        Locale locale = Locale.ITALY;

        expect(request.getContext("session")).andReturn(sessionScope);
        expect(sessionScope.get(DefaultLocaleResolver.LOCALE_KEY)).andReturn(null);
        expect(request.getRequestLocale()).andReturn(locale);

        replay(request, sessionScope);
        DefaultLocaleResolver resolver = new DefaultLocaleResolver();
        assertSame(locale, resolver.resolveLocale(request));
        verify(request, sessionScope);
    }

    /**
     * Without a session the request locale is used.
     */
    @Test
    public void testResolveLocaleWithoutSession() {
        Request request = createMock(Request.class);
        expect(request.getContext("session")).andReturn(null);
        expect(request.getRequestLocale()).andReturn(Locale.US);

        replay(request);
        assertSame(Locale.US, new DefaultLocaleResolver().resolveLocale(request));
        verify(request);
    }

    /**
     * Valid session locales are returned unchanged and the request locale is
     * not even consulted, exactly as before.
     */
    @Test
    public void testResolveLocaleFromSessionWithValidLocales() {
        DefaultLocaleResolver resolver = new DefaultLocaleResolver();
        for (Locale locale : LocaleSanitizerTest.validLocales()) {
            Request request = createMock(Request.class);
            Map<String, Object> sessionScope = new HashMap<String, Object>();
            sessionScope.put(DefaultLocaleResolver.LOCALE_KEY, locale);
            expect(request.getContext("session")).andReturn(sessionScope);

            replay(request);
            assertSame("Valid session locale not used: " + locale, locale, resolver.resolveLocale(request));
            verify(request);
        }
    }

    /**
     * Malicious session locales (CVE-2023-49735) are ignored and the request
     * locale is used, exactly as if the attribute were absent.
     */
    @Test
    public void testResolveLocaleIgnoresUnsafeSessionLocales() {
        DefaultLocaleResolver resolver = new DefaultLocaleResolver();
        for (Locale locale : LocaleSanitizerTest.unsafeLocales()) {
            Request request = createMock(Request.class);
            Map<String, Object> sessionScope = new HashMap<String, Object>();
            sessionScope.put(DefaultLocaleResolver.LOCALE_KEY, locale);
            expect(request.getContext("session")).andReturn(sessionScope);
            expect(request.getRequestLocale()).andReturn(Locale.CANADA_FRENCH);

            replay(request);
            assertSame("Unsafe session locale not rejected: " + locale, Locale.CANADA_FRENCH,
                    resolver.resolveLocale(request));
            verify(request);
        }
    }

    /**
     * A session attribute that is not a {@link Locale} is ignored (it used to
     * cause a {@link ClassCastException}) and the request locale is used.
     */
    @Test
    public void testResolveLocaleIgnoresNonLocaleSessionAttribute() {
        DefaultLocaleResolver resolver = new DefaultLocaleResolver();
        Object[] values = new Object[] {"../../etc/passwd", "en_US", Integer.valueOf(1), new Object()};
        for (Object value : values) {
            Request request = createMock(Request.class);
            Map<String, Object> sessionScope = new HashMap<String, Object>();
            sessionScope.put(DefaultLocaleResolver.LOCALE_KEY, value);
            expect(request.getContext("session")).andReturn(sessionScope);
            expect(request.getRequestLocale()).andReturn(Locale.GERMAN);

            replay(request);
            assertSame("Non-Locale session attribute not rejected: " + value.getClass(), Locale.GERMAN,
                    resolver.resolveLocale(request));
            verify(request);
        }
    }

    /**
     * The extension point used by applications (e.g. a Spring-based resolver
     * that extends {@link DefaultLocaleResolver}, overrides
     * {@link DefaultLocaleResolver#resolveLocale(Request)} and falls back to
     * <code>super.resolveLocale(request)</code>) is unchanged, and the
     * fallback is protected too.
     */
    @Test
    public void testSubclassFallingBackToSuper() {
        LocaleResolver resolver = new DefaultLocaleResolver() {
            @Override
            public Locale resolveLocale(Request request) {
                return super.resolveLocale(request);
            }
        };
        Request request = createMock(Request.class);
        Map<String, Object> sessionScope = new HashMap<String, Object>();
        sessionScope.put(DefaultLocaleResolver.LOCALE_KEY, new Locale("en", "US", "../../x"));
        expect(request.getContext("session")).andReturn(sessionScope);
        expect(request.getRequestLocale()).andReturn(Locale.US);

        replay(request);
        assertSame(Locale.US, resolver.resolveLocale(request));
        verify(request);
    }
}

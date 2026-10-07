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
package org.apache.tiles.definition.dao;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.tiles.Definition;
import org.apache.tiles.definition.LocaleDefinitionsFactory;
import org.apache.tiles.definition.digester.DigesterDefinitionsReader;
import org.apache.tiles.definition.pattern.BasicPatternDefinitionResolver;
import org.apache.tiles.definition.pattern.wildcard.WildcardDefinitionPatternMatcherFactory;
import org.apache.tiles.locale.LocaleResolver;
import org.apache.tiles.locale.LocaleSanitizerTest;
import org.apache.tiles.locale.impl.DefaultLocaleResolver;
import org.apache.tiles.request.ApplicationContext;
import org.apache.tiles.request.ApplicationResource;
import org.apache.tiles.request.Request;
import org.apache.tiles.request.locale.URLApplicationResource;
import org.junit.Test;

/**
 * Tests that the locale-based definition DAOs never build a localized
 * definition path from an unsafe locale (CVE-2023-49735), and that valid
 * locales keep loading the same localized files, in the same order, as before.
 *
 * @version $Rev$ $Date$
 */
public class LocalizedDefinitionsPathTraversalTest {

    /**
     * The base (non-localized) definitions file.
     */
    private static final String BASE_PATH = "org/apache/tiles/config/defs1.xml";

    /**
     * Prefix of the localized definitions files.
     */
    private static final String PATH_PREFIX = "org/apache/tiles/config/defs1";

    /**
     * The DAO implementations under test.
     */
    private enum DaoType {
        /** {@link LocaleUrlDefinitionDAO}. */
        LOCALE_URL,
        /** {@link CachingLocaleUrlDefinitionDAO}. */
        CACHING,
        /** {@link ResolvingLocaleUrlDefinitionDAO}. */
        RESOLVING
    }

    /**
     * An application context that loads resources from the test class path
     * and records every localized resource that is requested.
     */
    private static final class RecordingApplicationContext implements ApplicationContext {

        /** Locales passed to {@link #getResource(ApplicationResource, Locale)}. */
        private final List<Locale> requestedLocales = new ArrayList<Locale>();

        /** Localized paths built from those locales. */
        private final List<String> requestedPaths = new ArrayList<String>();

        /** {@inheritDoc} */
        public Object getContext() {
            return null;
        }

        /** {@inheritDoc} */
        public Map<String, Object> getApplicationScope() {
            return Collections.emptyMap();
        }

        /** {@inheritDoc} */
        public Map<String, String> getInitParams() {
            return Collections.emptyMap();
        }

        /** {@inheritDoc} */
        public ApplicationResource getResource(String localePath) {
            URL url = getClass().getClassLoader().getResource(localePath);
            return url == null ? null : new URLApplicationResource(localePath, url);
        }

        /** {@inheritDoc} */
        public ApplicationResource getResource(ApplicationResource base, Locale locale) {
            requestedLocales.add(locale);
            String localePath = base.getLocalePath(locale);
            requestedPaths.add(localePath);
            URL url = getClass().getClassLoader().getResource(localePath);
            return url == null ? null : new URLApplicationResource(base.getPath(), locale, url);
        }

        /** {@inheritDoc} */
        public Collection<ApplicationResource> getResources(String path) {
            ApplicationResource resource = getResource(path);
            if (resource == null) {
                return Collections.emptyList();
            }
            return Collections.singletonList(resource);
        }
    }

    /**
     * Creates a DAO that reads {@link #BASE_PATH}.
     *
     * @param type The DAO implementation.
     * @param context The application context.
     * @return The DAO.
     */
    private static DefinitionDAO<Locale> createDao(DaoType type, ApplicationContext context) {
        BaseLocaleUrlDefinitionDAO dao;
        switch (type) {
        case LOCALE_URL:
            dao = new LocaleUrlDefinitionDAO(context);
            break;
        case CACHING:
            dao = new CachingLocaleUrlDefinitionDAO(context);
            break;
        default:
            dao = new ResolvingLocaleUrlDefinitionDAO(context);
            break;
        }
        if (dao instanceof CachingLocaleUrlDefinitionDAO) {
            WildcardDefinitionPatternMatcherFactory factory = new WildcardDefinitionPatternMatcherFactory();
            ((CachingLocaleUrlDefinitionDAO) dao).setPatternDefinitionResolver(
                    new BasicPatternDefinitionResolver<Locale>(factory, factory));
        }
        ApplicationResource base = context.getResource(BASE_PATH);
        assertNotNull("Could not load " + BASE_PATH, base);
        dao.setSources(Collections.singletonList(base));
        dao.setReader(new DigesterDefinitionsReader());
        return dao;
    }

    /**
     * Returns the expected localized paths for a list of postfixes.
     *
     * @param postfixes The postfixes, e.g. "_en".
     * @return The paths.
     */
    private static List<String> paths(String... postfixes) {
        List<String> paths = new ArrayList<String>();
        for (String postfix : postfixes) {
            paths.add(PATH_PREFIX + postfix + ".xml");
        }
        return paths;
    }

    /**
     * Returns the value of the "country" attribute of a definition.
     *
     * @param definition The definition.
     * @return The country.
     */
    private static Object country(Definition definition) {
        assertNotNull("Definition not found", definition);
        return definition.getAttribute("country").getValue();
    }

    /**
     * Asserts that a requested path cannot escape the definitions directory.
     *
     * @param path The path.
     */
    private static void assertSafePath(String path) {
        assertTrue("Unexpected path: " + path, path.startsWith(PATH_PREFIX));
        assertTrue("Unexpected path: " + path, path.endsWith(".xml"));
        assertFalse("Traversal path built: " + path, path.contains(".."));
        assertFalse("Traversal path built: " + path, path.substring(PATH_PREFIX.length()).contains("/"));
        assertFalse("Traversal path built: " + path, path.contains("\\"));
        assertFalse("Encoded path built: " + path, path.contains("%"));
        assertFalse("URL built: " + path, path.contains(":"));
    }

    /**
     * Loads <code>test.def1</code> with the given locale, with a fresh DAO.
     *
     * @param type The DAO implementation.
     * @param context The application context.
     * @param locale The locale.
     * @return The definition.
     */
    private static Definition loadDef1(DaoType type, RecordingApplicationContext context, Locale locale) {
        DefinitionDAO<Locale> dao = createDao(type, context);
        Definition definition = dao.getDefinition("test.def1", locale);
        for (String path : context.requestedPaths) {
            assertSafePath(path);
        }
        return definition;
    }

    /**
     * Valid locales still load the localized files along the fallback chain
     * (e.g. en_US, then en, then the default), in the same order as before.
     */
    @Test
    public void testValidLocalesFollowTheFallbackChain() {
        Object[][] cases = new Object[][] {
            {null, paths(""), "default"},
            {Locale.ROOT, paths(""), "default"},
            {Locale.ENGLISH, paths("", "_en"), "default"},
            {Locale.US, paths("", "_en", "_en_US"), "US"},
            {Locale.FRENCH, paths("", "_fr"), "France"},
            {Locale.CANADA_FRENCH, paths("", "_fr", "_fr_CA"), "Canada"},
            {Locale.GERMAN, paths("", "_de"), "default"},
            {new Locale("es", "ES", "TRADITIONAL"),
                paths("", "_es", "_es_ES", "_es_ES_TRADITIONAL"), "default"},
        };
        for (DaoType type : DaoType.values()) {
            for (Object[] testCase : cases) {
                Locale locale = (Locale) testCase[0];
                RecordingApplicationContext context = new RecordingApplicationContext();
                Definition definition = loadDef1(type, context, locale);
                String message = type + " / " + locale;
                assertEquals(message, testCase[1], context.requestedPaths);
                assertEquals(message, testCase[2], country(definition));
            }
        }
    }

    /**
     * Definitions that only exist in a parent locale file are inherited by
     * the child locale (fr_CA falls back to fr).
     */
    @Test
    public void testParentLocaleDefinitionsAreInherited() {
        for (DaoType type : DaoType.values()) {
            DefinitionDAO<Locale> dao = createDao(type, new RecordingApplicationContext());
            assertEquals(type.toString(), "France",
                    country(dao.getDefinition("test.common.french", Locale.CANADA_FRENCH)));
            assertNull(type.toString(), dao.getDefinition("test.common.french", Locale.US));
        }
    }

    /**
     * Unsafe locales only load the default definitions: no localized path is
     * ever built or requested from them.
     */
    @Test
    public void testUnsafeLocalesOnlyLoadDefaultDefinitions() {
        for (DaoType type : DaoType.values()) {
            for (Locale locale : LocaleSanitizerTest.unsafeLocales()) {
                RecordingApplicationContext context = new RecordingApplicationContext();
                Definition definition = loadDef1(type, context, locale);
                String message = type + " / " + locale;
                assertEquals(message, Collections.singletonList(Locale.ROOT), context.requestedLocales);
                assertEquals(message, paths(""), context.requestedPaths);
                assertEquals(message, "default", country(definition));
            }
        }
    }

    /**
     * {@link DefinitionDAO#getDefinitions(Object)} is protected as well.
     */
    @Test
    public void testGetDefinitionsWithUnsafeLocales() {
        for (DaoType type : DaoType.values()) {
            for (Locale locale : LocaleSanitizerTest.unsafeLocales()) {
                RecordingApplicationContext context = new RecordingApplicationContext();
                Map<String, Definition> definitions = createDao(type, context).getDefinitions(locale);
                String message = type + " / " + locale;
                assertEquals(message, paths(""), context.requestedPaths);
                assertEquals(message, "default", country(definitions.get("test.def1")));
            }
        }
    }

    /**
     * End to end: a malicious locale stored in the session is ignored by
     * {@link DefaultLocaleResolver}, and the request locale is used to load
     * the localized definitions.
     */
    @Test
    public void testDefinitionsFactoryIgnoresUnsafeSessionLocale() {
        List<Object> sessionValues = new ArrayList<Object>(LocaleSanitizerTest.unsafeLocales());
        sessionValues.addAll(Arrays.asList("../../etc/passwd", Integer.valueOf(1)));
        for (Object sessionValue : sessionValues) {
            RecordingApplicationContext context = new RecordingApplicationContext();
            LocaleDefinitionsFactory factory = new LocaleDefinitionsFactory();
            factory.setLocaleResolver(new DefaultLocaleResolver());
            factory.setDefinitionDAO(createDao(DaoType.RESOLVING, context));

            Request request = createMock(Request.class);
            Map<String, Object> session = new HashMap<String, Object>();
            session.put(DefaultLocaleResolver.LOCALE_KEY, sessionValue);
            expect(request.getContext("session")).andReturn(session).anyTimes();
            expect(request.getRequestLocale()).andReturn(Locale.US).anyTimes();
            replay(request);

            Definition definition = factory.getDefinition("test.def1", request);
            String message = String.valueOf(sessionValue);
            assertEquals(message, "US", country(definition));
            assertEquals(message, paths("", "_en", "_en_US"), context.requestedPaths);
            verify(request);
        }
    }

    /**
     * A {@link LocaleResolver} that does not go through the session check
     * (e.g. one that returns a locale taken from a cookie or a request
     * parameter) cannot make the DAO build a traversal path either.
     */
    @Test
    public void testDefinitionsFactoryWithCustomResolverReturningUnsafeLocale() {
        for (final Locale locale : LocaleSanitizerTest.unsafeLocales()) {
            RecordingApplicationContext context = new RecordingApplicationContext();
            LocaleDefinitionsFactory factory = new LocaleDefinitionsFactory();
            factory.setLocaleResolver(new DefaultLocaleResolver() {
                @Override
                public Locale resolveLocale(Request request) {
                    return locale;
                }
            });
            factory.setDefinitionDAO(createDao(DaoType.RESOLVING, context));

            Request request = createMock(Request.class);
            replay(request);

            Definition definition = factory.getDefinition("test.def1", request);
            String message = locale.toString();
            assertEquals(message, "default", country(definition));
            assertEquals(message, paths(""), context.requestedPaths);
        }
    }
}

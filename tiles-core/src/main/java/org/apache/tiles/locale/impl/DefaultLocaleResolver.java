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

import java.util.Locale;
import java.util.Map;

import org.apache.tiles.locale.LocaleResolver;
import org.apache.tiles.locale.LocaleSanitizer;
import org.apache.tiles.request.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of <code>LocaleResolver</code><br>
 * It tries to take the locale from the session-scoped attribute
 * {@link DefaultLocaleResolver#LOCALE_KEY}. If it is not found, it returns the
 * locale included in the request.
 * <p>
 * The session attribute is only used if it is a {@link Locale} that is safe to
 * use in a resource path (see {@link LocaleSanitizer#isSafe(Locale)}).
 * Otherwise it is ignored, exactly as if it were absent, so that it cannot be
 * used to load definition files from arbitrary locations (CVE-2023-49735).
 * </p>
 *
 * @version $Rev$ $Date$
 */
public class DefaultLocaleResolver implements LocaleResolver {

    /**
     * The attribute name that is used to store the current locale.
     */
    public static final String LOCALE_KEY = "org.apache.tiles.LOCALE";

    /**
     * The logging object.
     */
    private static final Logger LOG = LoggerFactory.getLogger(DefaultLocaleResolver.class);

    /** {@inheritDoc} */
    public Locale resolveLocale(Request request) {
        Locale retValue = null;
        Map<String, Object> session = request.getContext("session");
        if (session != null) {
            retValue = toSafeLocale(session.get(DefaultLocaleResolver.LOCALE_KEY));
        }
        if (retValue == null) {
            retValue = request.getRequestLocale();
        }

        return retValue;
    }

    /**
     * Validates the value of the {@link #LOCALE_KEY} session attribute.
     *
     * @param value The attribute value.
     * @return The value, if it is a safe {@link Locale}, <code>null</code>
     * otherwise.
     */
    private static Locale toSafeLocale(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Locale)) {
            if (LOG.isWarnEnabled()) {
                // only the type is logged, never the (untrusted) value
                LOG.warn("Ignoring session attribute " + LOCALE_KEY + ": it is a "
                        + value.getClass().getName()
                        + ", not a java.util.Locale; using the request locale instead.");
            }
            return null;
        }
        Locale locale = (Locale) value;
        if (!LocaleSanitizer.isSafe(locale)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Ignoring session attribute " + LOCALE_KEY + ": locale \""
                        + LocaleSanitizer.toLogString(locale)
                        + "\" contains characters that are not allowed; using the request locale instead.");
            }
            return null;
        }
        return locale;
    }
}

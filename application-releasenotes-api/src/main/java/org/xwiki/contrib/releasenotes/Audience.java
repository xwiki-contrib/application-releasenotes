/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.releasenotes;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.stability.Unstable;

/**
 * The audience a change is written for, which decides the section of the release note it is displayed in.
 *
 * @version $Id$
 * @since 2.7
 */
@Unstable
public enum Audience
{
    /** A change an end user of the product needs to know about. */
    USER,

    /** A change an administrator of the product needs to know about. */
    ADMINISTRATOR,

    /** A change a developer building on the product needs to know about. */
    DEVELOPER;

    /**
     * @return the value the {@code audience} property of a change holds for this audience
     */
    public String getStoredValue()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param storedValue the value held by the {@code audience} property of a change
     * @return the matching audience, or {@code null} when the passed value is not one of them
     */
    public static Audience fromStoredValue(String storedValue)
    {
        for (Audience audience : values()) {
            if (audience.getStoredValue().equals(StringUtils.lowerCase(storedValue, Locale.ROOT))) {
                return audience;
            }
        }

        return null;
    }
}

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

import org.xwiki.stability.Unstable;

/**
 * How important a change is, which decides the order the changes of a release note are displayed in and, for the
 * developer section, whether a change is displayed among the main ones or among the miscellaneous ones.
 *
 * @version $Id$
 * @since 2.7
 */
@Unstable
public enum Importance
{
    /** A change of little consequence. */
    LOW(0),

    /** A change worth reading. */
    MEDIUM(1),

    /** A change nobody upgrading can afford to miss. */
    HIGH(2);

    private final int level;

    Importance(int level)
    {
        this.level = level;
    }

    /**
     * @return the value the {@code importance} property of a change holds for this importance, which is a number so
     *         that the database can order the changes by it
     */
    public String getStoredValue()
    {
        return String.valueOf(this.level);
    }

    /**
     * @return how this importance is written by a person or a client, which is the lower cased name of the value
     *         (e.g. {@code high}) and not the number it is stored as
     * @since 2.8
     */
    public String getName()
    {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param name the name of an importance, whatever its case (e.g. {@code high} or {@code HIGH})
     * @return the matching importance, or {@code null} when the passed name is not the name of one of them
     * @since 2.8
     */
    public static Importance fromName(String name)
    {
        for (Importance importance : values()) {
            if (importance.name().equalsIgnoreCase(name)) {
                return importance;
            }
        }

        return null;
    }

    /**
     * @param storedValue the value held by the {@code importance} property of a change
     * @return the matching importance, or {@code null} when the passed value is not one of them
     */
    public static Importance fromStoredValue(String storedValue)
    {
        for (Importance importance : values()) {
            if (importance.getStoredValue().equals(storedValue)) {
                return importance;
            }
        }

        return null;
    }
}

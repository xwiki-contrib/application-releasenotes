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

import org.apache.commons.lang3.StringUtils;
import org.xwiki.stability.Unstable;

/**
 * What an entry of a release note that is a change stands for, which decides the part of the release note it is
 * displayed in.
 *
 * @version $Id$
 * @since 2.8
 */
@Unstable
public enum ChangeType
{
    /** Something the version brings, displayed among the new and noteworthy changes of the release note. */
    CHANGE("Change"),

    /**
     * Something to do when upgrading to the version, because it breaks backward compatibility or needs a migration
     * step, displayed among the backward compatibility and migration notes of the release note.
     */
    MIGRATION("Migration");

    private final String storedValue;

    ChangeType(String storedValue)
    {
        this.storedValue = storedValue;
    }

    /**
     * @return the value the {@code type} property of the entry of a change holds for this type
     */
    public String getStoredValue()
    {
        return this.storedValue;
    }

    /**
     * @param storedValue the value held by the {@code type} property of the entry of a change, whatever its case
     * @return the matching type, or {@code null} when the passed value is not one of them
     */
    public static ChangeType fromStoredValue(String storedValue)
    {
        for (ChangeType type : values()) {
            if (type.getStoredValue().equalsIgnoreCase(StringUtils.trim(storedValue))) {
                return type;
            }
        }

        return null;
    }
}

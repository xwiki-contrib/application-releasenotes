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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link Importance}.
 * <p>
 * The values asserted here are the values the {@code importance} property of a change holds, which the queries of
 * the application filter and order the changes on: changing one of them hides every change already written.
 *
 * @version $Id$
 */
class ImportanceTest
{
    /**
     * The importance is stored as a number, and not as its name, so that the database can order the changes of a
     * release note by it.
     */
    @Test
    void everyImportanceIsStoredAsItsRank()
    {
        assertEquals("0", Importance.LOW.getStoredValue());
        assertEquals("1", Importance.MEDIUM.getStoredValue());
        assertEquals("2", Importance.HIGH.getStoredValue());
    }

    @Test
    void everyStoredValueIsReadBackAsItsImportance()
    {
        for (Importance importance : Importance.values()) {
            assertEquals(importance, Importance.fromStoredValue(importance.getStoredValue()));
        }
    }

    @Test
    void aValueThatIsNoImportanceIsReadBackAsNone()
    {
        assertNull(Importance.fromStoredValue(""));
        assertNull(Importance.fromStoredValue(null));
        assertNull(Importance.fromStoredValue("high"));
    }

    /**
     * An importance is written by its lower cased name wherever a person or a client writes one: a filter of a
     * report, or a change posted over REST.
     */
    @Test
    void everyImportanceIsNamedInLowerCase()
    {
        assertEquals("low", Importance.LOW.getName());
        assertEquals("medium", Importance.MEDIUM.getName());
        assertEquals("high", Importance.HIGH.getName());
    }

    @Test
    void anImportanceIsFoundByItsNameWhateverItsCase()
    {
        for (Importance importance : Importance.values()) {
            assertEquals(importance, Importance.fromName(importance.getName()));
        }

        assertEquals(Importance.HIGH, Importance.fromName("HIGH"));
        assertEquals(Importance.MEDIUM, Importance.fromName("Medium"));
    }

    /**
     * The number an importance is stored as is not its name, so that a caller can tell the two apart and accept both.
     */
    @Test
    void aValueThatIsNoImportanceNameIsFoundAsNone()
    {
        assertNull(Importance.fromName("2"));
        assertNull(Importance.fromName(""));
        assertNull(Importance.fromName(null));
        assertNull(Importance.fromName("highest"));
    }
}

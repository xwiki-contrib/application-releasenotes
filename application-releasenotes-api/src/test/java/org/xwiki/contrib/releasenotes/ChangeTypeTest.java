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
 * Unit tests for {@link ChangeType}.
 * <p>
 * The values asserted here are the values the {@code type} property of an entry holds, which the queries of the
 * application tell the changes and the migration notes apart with: changing one of them hides every entry already
 * written.
 *
 * @version $Id$
 */
class ChangeTypeTest
{
    @Test
    void everyTypeIsStoredUnderTheValueTheEntryClassDeclares()
    {
        assertEquals("Change", ChangeType.CHANGE.getStoredValue());
        assertEquals("Migration", ChangeType.MIGRATION.getStoredValue());
    }

    /**
     * A type is read back whatever the case it is written with, since the REST API and the macros write it lower
     * cased.
     */
    @Test
    void aStoredValueIsReadBackWhateverItsCase()
    {
        assertEquals(ChangeType.MIGRATION, ChangeType.fromStoredValue("Migration"));
        assertEquals(ChangeType.MIGRATION, ChangeType.fromStoredValue("migration"));
        assertEquals(ChangeType.CHANGE, ChangeType.fromStoredValue(" CHANGE "));
    }

    /**
     * The contributors of a release note are an entry too, but not a change of any type.
     */
    @Test
    void aValueThatIsNoTypeIsReadBackAsNone()
    {
        assertNull(ChangeType.fromStoredValue("Contributors"));
        assertNull(ChangeType.fromStoredValue(""));
        assertNull(ChangeType.fromStoredValue(null));
    }
}

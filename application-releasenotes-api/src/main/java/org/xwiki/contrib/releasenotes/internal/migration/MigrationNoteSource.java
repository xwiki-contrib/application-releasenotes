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
package org.xwiki.contrib.releasenotes.internal.migration;

/**
 * One note of the "Issues specific" subsection of a release note, as the import turns it into a migration note entry.
 *
 * @version $Id$
 * @since 2.8
 */
public class MigrationNoteSource
{
    private final String title;

    private final String description;

    /**
     * @param title the title of the note, as plain text
     * @param description the body of the note, in the wiki syntax it was written in
     */
    public MigrationNoteSource(String title, String description)
    {
        this.title = title;
        this.description = description;
    }

    /**
     * @return the title of the note, as plain text
     */
    public String getTitle()
    {
        return this.title;
    }

    /**
     * @return the body of the note, in the wiki syntax it was written in
     */
    public String getDescription()
    {
        return this.description;
    }
}

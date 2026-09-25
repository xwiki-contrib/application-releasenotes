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

import java.util.ArrayList;
import java.util.List;

/**
 * What the backward compatibility and migration notes section of one release note holds, as the import reads it.
 *
 * @version $Id$
 * @since 2.8
 */
public class MigrationSection
{
    private boolean alreadyMigrated;

    private int issuesStart = -1;

    private int issuesEnd = -1;

    private String issuesHeading = "";

    private String keptSource = "";

    private final List<MigrationNoteSource> notes = new ArrayList<>();

    private final List<MigrationLeftover> leftovers = new ArrayList<>();

    /**
     * @return whether the section already displays the migration note entries of the release note, which is what a
     *         section the import rewrote does
     */
    public boolean isAlreadyMigrated()
    {
        return this.alreadyMigrated;
    }

    void setAlreadyMigrated(boolean alreadyMigrated)
    {
        this.alreadyMigrated = alreadyMigrated;
    }

    /**
     * @return whether the section has an "Issues specific" subsection, which is the only part the import rewrites
     */
    public boolean hasIssues()
    {
        return this.issuesStart >= 0;
    }

    int getIssuesStart()
    {
        return this.issuesStart;
    }

    int getIssuesEnd()
    {
        return this.issuesEnd;
    }

    String getIssuesHeading()
    {
        return this.issuesHeading;
    }

    String getKeptSource()
    {
        return this.keptSource;
    }

    void setIssues(int start, int end, String heading, String kept)
    {
        this.issuesStart = start;
        this.issuesEnd = end;
        this.issuesHeading = heading;
        this.keptSource = kept;
    }

    /**
     * @return the notes the import turns into migration note entries, in the order they are written in
     */
    public List<MigrationNoteSource> getNotes()
    {
        return this.notes;
    }

    /**
     * @return what the import leaves in place, for someone to migrate by hand
     */
    public List<MigrationLeftover> getLeftovers()
    {
        return this.leftovers;
    }
}

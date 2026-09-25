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

import org.xwiki.model.reference.DocumentReference;

/**
 * What the import does, or would do, with the backward compatibility and migration notes of one release note.
 *
 * @version $Id$
 * @since 2.8
 */
public class ReleaseNoteImport
{
    private final DocumentReference reference;

    private final String version;

    private MigrationSection section;

    private final List<MigrationNoteSource> notes = new ArrayList<>();

    private final List<String> duplicates = new ArrayList<>();

    private final List<DocumentReference> createdEntries = new ArrayList<>();

    private boolean rewritten;

    private String error;

    /**
     * @param reference the page of the release note
     * @param version the version of the release note
     */
    public ReleaseNoteImport(DocumentReference reference, String version)
    {
        this.reference = reference;
        this.version = version;
    }

    /**
     * @return the page of the release note
     */
    public DocumentReference getReference()
    {
        return this.reference;
    }

    /**
     * @return the version of the release note
     */
    public String getVersion()
    {
        return this.version;
    }

    MigrationSection getSection()
    {
        return this.section;
    }

    void setSection(MigrationSection section)
    {
        this.section = section;
    }

    /**
     * @return whether the release note was already migrated, which leaves it alone
     */
    public boolean isAlreadyMigrated()
    {
        return this.section != null && this.section.isAlreadyMigrated();
    }

    /**
     * @return the notes that become migration note entries of this release note, in the order they are written in
     */
    public List<MigrationNoteSource> getNotes()
    {
        return this.notes;
    }

    /**
     * @return the titles of the notes left out because a milestone or a release candidate of the same version holds
     *         them already, and the release note displays the entries of those
     */
    public List<String> getDuplicates()
    {
        return this.duplicates;
    }

    /**
     * @return what the import leaves in place, for someone to migrate by hand
     */
    public List<MigrationLeftover> getLeftovers()
    {
        return this.section == null ? List.of() : this.section.getLeftovers();
    }

    /**
     * @return whether the release note gets rewritten: it does when at least one of its notes is moved to an entry or
     *         left out as a duplicate
     */
    public boolean isToRewrite()
    {
        return !isAlreadyMigrated() && (!this.notes.isEmpty() || !this.duplicates.isEmpty());
    }

    /**
     * @return whether the import has something to say about this release note
     */
    public boolean isReported()
    {
        return isToRewrite() || !getLeftovers().isEmpty() || this.error != null;
    }

    /**
     * @return the entries the import created, once it ran
     */
    public List<DocumentReference> getCreatedEntries()
    {
        return this.createdEntries;
    }

    /**
     * @return whether the import rewrote the release note, once it ran
     */
    public boolean isRewritten()
    {
        return this.rewritten;
    }

    void setRewritten(boolean rewritten)
    {
        this.rewritten = rewritten;
    }

    /**
     * @return why the import failed on this release note, or {@code null} when it did not
     */
    public String getError()
    {
        return this.error;
    }

    void setError(String error)
    {
        this.error = error;
    }
}

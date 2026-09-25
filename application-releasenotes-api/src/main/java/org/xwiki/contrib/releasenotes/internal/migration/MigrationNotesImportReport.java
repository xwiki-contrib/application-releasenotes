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

import java.util.List;

/**
 * What the import does, or would do, with the backward compatibility and migration notes of the release notes of a
 * product.
 *
 * @version $Id$
 * @since 2.8
 */
public class MigrationNotesImportReport
{
    private final String product;

    private final List<ReleaseNoteImport> releaseNotes;

    private final int alreadyMigrated;

    /**
     * @param product the product whose release notes were read
     * @param releaseNotes the release notes the import has something to say about, oldest first
     * @param alreadyMigrated how many release notes were already migrated
     */
    public MigrationNotesImportReport(String product, List<ReleaseNoteImport> releaseNotes, int alreadyMigrated)
    {
        this.product = product;
        this.releaseNotes = releaseNotes;
        this.alreadyMigrated = alreadyMigrated;
    }

    /**
     * @return the product whose release notes were read
     */
    public String getProduct()
    {
        return this.product;
    }

    /**
     * @return the release notes the import has something to say about, oldest first
     */
    public List<ReleaseNoteImport> getReleaseNotes()
    {
        return this.releaseNotes;
    }

    /**
     * @return how many release notes were already migrated, and are left alone
     */
    public int getAlreadyMigrated()
    {
        return this.alreadyMigrated;
    }

    /**
     * @return how many notes become migration note entries
     */
    public int getNoteCount()
    {
        return this.releaseNotes.stream().mapToInt(note -> note.getNotes().size()).sum();
    }

    /**
     * @return how many release notes get rewritten
     */
    public int getRewriteCount()
    {
        return (int) this.releaseNotes.stream().filter(ReleaseNoteImport::isToRewrite).count();
    }
}

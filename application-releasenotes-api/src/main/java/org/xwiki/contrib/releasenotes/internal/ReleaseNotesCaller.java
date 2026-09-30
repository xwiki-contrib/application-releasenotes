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
package org.xwiki.contrib.releasenotes.internal;

import org.xwiki.model.reference.DocumentReference;

/**
 * Who a call entering the application is made for, which is whose rights {@link ReleaseNotesEntryPoint} checks: the
 * current user always, and the author of the calling script when there is one.
 *
 * @version $Id$
 * @since 2.8
 */
public final class ReleaseNotesCaller
{
    private static final ReleaseNotesCaller CURRENT_USER = new ReleaseNotesCaller(false, null);

    private final boolean scripted;

    private final DocumentReference author;

    private ReleaseNotesCaller(boolean scripted, DocumentReference author)
    {
        this.scripted = scripted;
        this.author = author;
    }

    /**
     * @return a caller that runs no script, such as a REST request, whose current user is the only one whose rights
     *         there are to check
     */
    public static ReleaseNotesCaller currentUser()
    {
        return CURRENT_USER;
    }

    /**
     * @param author the author of the calling script, or {@code null} when that script has no author, which is then
     *            checked as the guest user
     * @return a caller that is a script, whose author is checked along with the current user
     */
    public static ReleaseNotesCaller currentUserAndAuthor(DocumentReference author)
    {
        return new ReleaseNotesCaller(true, author);
    }

    /**
     * @return whether the author of a calling script is to be checked along with the current user
     */
    public boolean isScripted()
    {
        return this.scripted;
    }

    /**
     * @return the author of the calling script, or {@code null} when there is none, or when the author is the guest
     *         user
     * @see #isScripted()
     */
    public DocumentReference getAuthor()
    {
        return this.author;
    }
}

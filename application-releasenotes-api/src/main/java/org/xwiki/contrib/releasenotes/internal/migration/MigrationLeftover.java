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
 * Something of the backward compatibility and migration notes section of a release note that the import leaves in
 * place, for someone to migrate by hand.
 *
 * @version $Id$
 * @since 2.8
 */
public class MigrationLeftover
{
    /**
     * Why the import leaves something in place.
     *
     * @version $Id$
     */
    public enum Kind
    {
        /** Text of the "Issues specific" subsection that is under no note title, so there is no title to give it. */
        UNTITLED_TEXT,

        /** A subsection that is none of the ones the release notes are written with, so it is no note. */
        NON_STANDARD_HEADING,

        /**
         * A note running a script macro, which would not run on the page of an entry, whose template declares no
         * required right.
         */
        SCRIPT_MACRO,

        /** A note linking to its own release note in a way the import does not know how to point elsewhere. */
        UNRESOLVED_LINK,

        /** A note whose title is empty once rendered as plain text, e.g. a title that is only a macro. */
        EMPTY_TITLE
    }

    private final Kind kind;

    private final String text;

    /**
     * @param kind why it is left in place
     * @param text what is left in place: a note title, a heading, or the beginning of the text
     */
    public MigrationLeftover(Kind kind, String text)
    {
        this.kind = kind;
        this.text = text;
    }

    /**
     * @return why it is left in place
     */
    public Kind getKind()
    {
        return this.kind;
    }

    /**
     * @return what is left in place: a note title, a heading, or the beginning of the text
     */
    public String getText()
    {
        return this.text;
    }
}

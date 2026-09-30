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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;

/**
 * The pages of the application the release notes and the changes are made of. The application only works installed
 * in the {@code ReleaseNotes} space: every one of its own pages names that space too, in its queries, its page
 * references and its stylesheet identifiers.
 *
 * @version $Id$
 * @since 2.7
 */
public final class ReleaseNotesReferences
{
    /**
     * The top-level space of the application, which is the only place it works installed in.
     */
    public static final String TOP_SPACE = "ReleaseNotes";

    /**
     * The space holding the code of the application.
     */
    public static final List<String> CODE_SPACE = List.of(TOP_SPACE, "Code");

    /**
     * The space the release notes are created under.
     */
    public static final List<String> DATA_SPACE = List.of(TOP_SPACE, "Data");

    /**
     * The version of a milestone is written with this letter in a page name, e.g. {@code 8.3M1} for
     * {@code 8.3-milestone-1}.
     */
    public static final String MILESTONE_LETTER = "M";

    /**
     * The version of a release candidate is written with these letters in a page name, e.g. {@code 8.3RC1} for
     * {@code 8.3-rc-1}.
     */
    public static final String RELEASE_CANDIDATE_LETTERS = "RC";

    /**
     * The class carried by the page of a release note.
     */
    public static final LocalDocumentReference RELEASE_NOTE_CLASS =
        new LocalDocumentReference(CODE_SPACE, "ReleaseNoteClass");

    /**
     * The class that identifies and locates the entries of a release note, carried by every one of them.
     */
    public static final LocalDocumentReference ENTRY_CLASS = new LocalDocumentReference(CODE_SPACE, "EntryClass");

    /**
     * The class holding a change, carried by the entries whose type is {@code Change}.
     */
    public static final LocalDocumentReference CHANGE_CLASS =
        new LocalDocumentReference(changeSpace(), "ChangeClass");

    /**
     * The page the entries holding a change are created from.
     */
    public static final LocalDocumentReference CHANGE_TEMPLATE =
        new LocalDocumentReference(changeSpace(), "ChangeTemplate");

    /**
     * The page holding the release note defaults of the wiki.
     */
    public static final LocalDocumentReference CONFIGURATION =
        new LocalDocumentReference(CODE_SPACE, "ReleaseNotesConfig");

    /**
     * The class those defaults are held in.
     */
    public static final LocalDocumentReference CONFIGURATION_CLASS =
        new LocalDocumentReference(CODE_SPACE, "ReleaseNotesConfigClass");

    /**
     * The class a page declares the rights its content needs with.
     */
    public static final LocalDocumentReference REQUIRED_RIGHT_CLASS =
        new LocalDocumentReference("XWiki", "RequiredRightClass");

    private ReleaseNotesReferences()
    {
        // Utility class, and thus no public constructor.
    }

    /**
     * @param wikiId the wiki the release note is in
     * @param product the product the release note is about
     * @param version the version the release note is about, in its long form
     * @return the page of that release note, whether it exists or not
     * @throws IllegalArgumentException when the product or the version is blank, which locates no release note
     */
    public static DocumentReference releaseNote(String wikiId, String product, String version)
    {
        if (StringUtils.isBlank(product) || StringUtils.isBlank(version)) {
            throw new IllegalArgumentException(String.format(
                "A release note is located by a product and a version, and got the product [%s] and the version "
                    + "[%s].", product, version));
        }

        List<String> spaces = new ArrayList<>(DATA_SPACE);
        spaces.add(product);
        spaces.add(getShortVersion(version));

        return new DocumentReference(wikiId, spaces, "WebHome");
    }

    /**
     * Gives the form a version is written in the page name of a release note: the separators taken out and the word
     * "milestone" shortened, so that {@code 8.3-milestone-1} is written {@code 8.3M1}.
     *
     * @param version the version in its long form
     * @return that version in its short form
     */
    private static String getShortVersion(String version)
    {
        return StringUtils.upperCase(StringUtils.replaceChars(version, "-", "")).replace("MILESTONE",
            MILESTONE_LETTER);
    }

    /**
     * @return the space holding the code of the changes, which is nested under the code of the application
     */
    private static List<String> changeSpace()
    {
        List<String> spaces = new ArrayList<>(CODE_SPACE);
        spaces.add("Change");

        return spaces;
    }
}

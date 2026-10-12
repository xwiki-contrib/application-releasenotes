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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.document.DocumentAuthors;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.user.GuestUserReference;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Unit tests for {@link ReleaseNotesDocumentStore}.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class ReleaseNotesDocumentStoreTest
{
    private static final DocumentReference RELEASE_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", "XWiki", "8.3"), "WebHome");

    private static final DocumentReference USER = new DocumentReference("xwiki", "XWiki", "User");

    private static final DocumentReference OTHER_USER = new DocumentReference("xwiki", "XWiki", "OtherUser");

    @InjectMockComponents
    private ReleaseNotesDocumentStore store;

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    @BeforeEach
    void setUp()
    {
        this.oldcore.getXWikiContext().setUserReference(USER);
    }

    /**
     * The author and the content author are set here rather than left to the save, which sets neither: the content
     * of a release note is copied from a template that scripts, and the content author is who that script runs as.
     */
    @Test
    void savingAPageRecordsTheCurrentUserAsItsAuthorAndAsItsContentAuthor() throws Exception
    {
        this.store.save(page(), "New Release note");

        XWikiDocument saved = load();
        assertFalse(saved.isNew(), "Expected the page to have been saved.");
        DocumentAuthors authors = saved.getAuthors();
        assertNotNull(authors.getEffectiveMetadataAuthor());
        assertNotSame(GuestUserReference.INSTANCE, authors.getEffectiveMetadataAuthor(),
            "A page whose content author is nobody has content that cannot execute.");
        assertEquals(authors.getEffectiveMetadataAuthor(), authors.getContentAuthor());
        assertEquals(authors.getEffectiveMetadataAuthor(), authors.getCreator());
        assertEquals("New Release note", saved.getComment());
    }

    /**
     * The content author is who the scripts in the content of a page run as, so a save that does not write that
     * content must not move it: otherwise a user who only writes the objects of a page, e.g. to mark a release note
     * released, would become the author of a script someone else wrote, and run it with their own rights.
     */
    @Test
    void savingAPageWithoutWritingItsContentKeepsItsContentAuthor() throws Exception
    {
        XWikiDocument page = page();
        page.setContent("{{velocity}}$xcontext.user{{/velocity}}");
        this.store.save(page, "New Release note");
        DocumentAuthors authorsBefore = load().getAuthors();

        this.oldcore.getXWikiContext().setUserReference(OTHER_USER);
        XWikiDocument update = load().clone();
        update.newXObject(ReleaseNotesReferences.RELEASE_NOTE_CLASS, this.oldcore.getXWikiContext())
            .setIntValue("released", 1);
        this.store.save(update, "Updated release note");

        DocumentAuthors authors = load().getAuthors();
        assertNotEquals(authorsBefore.getEffectiveMetadataAuthor(), authors.getEffectiveMetadataAuthor(),
            "Expected the page to have been saved by the other user.");
        assertEquals(authorsBefore.getContentAuthor(), authors.getContentAuthor(),
            "A save that does not write the content must keep its content author.");
    }

    /**
     * A user who writes the content of a page is its content author, as on any other save.
     */
    @Test
    void savingAPageWritingItsContentRecordsTheCurrentUserAsItsContentAuthor() throws Exception
    {
        XWikiDocument page = page();
        page.setContent("first");
        this.store.save(page, "New Release note");

        this.oldcore.getXWikiContext().setUserReference(OTHER_USER);
        XWikiDocument update = load().clone();
        update.setContent("second");
        this.store.save(update, "Updated release note");

        DocumentAuthors authors = load().getAuthors();
        assertEquals(authors.getEffectiveMetadataAuthor(), authors.getContentAuthor());
    }

    private XWikiDocument page() throws Exception
    {
        return load();
    }

    private XWikiDocument load() throws Exception
    {
        return this.oldcore.getSpyXWiki().getDocument(RELEASE_NOTE, this.oldcore.getXWikiContext());
    }
}

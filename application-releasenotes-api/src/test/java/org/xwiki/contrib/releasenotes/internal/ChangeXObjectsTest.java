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
import org.xwiki.contrib.releasenotes.Audience;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;
import org.xwiki.contrib.releasenotes.internal.ChangeXObjects.WriteMode;
import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.test.MockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.InjectMockitoOldcore;
import com.xpn.xwiki.test.junit5.mockito.OldcoreTest;
import com.xpn.xwiki.test.reference.ReferenceComponentList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ChangeXObjects}.
 * <p>
 * The stored values asserted here are what the wiki pages of the application read and query: changing one of them
 * hides every change already written.
 *
 * @version $Id$
 */
@OldcoreTest
@ReferenceComponentList
class ChangeXObjectsTest
{
    private static final DocumentReference PAGE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "8.3M1", "Entry001"), "WebHome");

    @InjectMockitoOldcore
    private MockitoOldcore oldcore;

    private XWikiContext xcontext;

    private XWikiDocument document;

    @BeforeEach
    void setUp() throws Exception
    {
        ReleaseNotesXClasses.install(this.oldcore);
        this.xcontext = this.oldcore.getXWikiContext();
        this.document = new XWikiDocument(PAGE);
    }

    @Test
    void aChangeIsReadBackAsItWasWritten() throws Exception
    {
        ChangeXObjects.writeEntry(this.document, "XWiki", "8.3-milestone-1", this.xcontext);
        ChangeXObjects.writeChange(this.document, fullChange(), WriteMode.OVER_TEMPLATE, this.xcontext);

        Change read = ChangeXObjects.read(this.document);
        assertEquals("XWiki", read.getProduct());
        assertEquals("8.3-milestone-1", read.getVersion());
        assertEquals("Faster startup", read.getTitle());
        assertEquals("Starting a wiki now takes half the time.", read.getSummary());
        assertEquals("The long story.", read.getDescription());
        assertEquals(Audience.DEVELOPER, read.getAudience());
        assertEquals(Importance.HIGH, read.getImportance());
        assertEquals("Performance", read.getCategory());
        assertEquals(List.of("before.png", "after.png"), read.getScreenshots());
    }

    /**
     * The entry is what every query looking for changes filters on, and its type is what keeps the contributors of a
     * release note from being counted among its changes.
     */
    @Test
    void aChangeIsStoredAsAnEntryOfTypeChange() throws Exception
    {
        ChangeXObjects.writeEntry(this.document, "XWiki", "8.3-milestone-1", this.xcontext);

        BaseObject entry = this.document.getXObject(ReleaseNotesReferences.ENTRY_CLASS);
        assertEquals("Change", entry.getStringValue("type"));
        assertEquals("XWiki", entry.getStringValue("product"));
        assertEquals("8.3-milestone-1", entry.getStringValue("version"));
    }

    /**
     * Audience and importance are stored as the values the class lists, and the media as one comma-separated value,
     * which is what the displayers read.
     */
    @Test
    void theValuesOfAChangeAreStoredTheWayThePagesReadThem() throws Exception
    {
        ChangeXObjects.writeChange(this.document, fullChange(), WriteMode.OVER_TEMPLATE, this.xcontext);

        BaseObject change = this.document.getXObject(ReleaseNotesReferences.CHANGE_CLASS);
        assertEquals("developer", change.getStringValue("audience"));
        assertEquals("2", change.getStringValue("importance"));
        assertEquals("before.png,after.png", change.getStringValue("screenshots"));
    }

    @Test
    void theTitleIsStoredTrimmed() throws Exception
    {
        Change change = new Change();
        change.setTitle("  Faster startup ");

        ChangeXObjects.writeChange(this.document, change, WriteMode.OVER_TEMPLATE, this.xcontext);

        assertEquals("Faster startup",
            this.document.getXObject(ReleaseNotesReferences.CHANGE_CLASS).getStringValue("title"));
    }

    /**
     * Writing over a template leaves the values the change does not carry to the template.
     */
    @Test
    void writingOverTheTemplateKeepsTheValuesTheChangeLeavesOut() throws Exception
    {
        ChangeXObjects.writeEntry(this.document, "XWiki", "8.3", this.xcontext);
        BaseObject template = this.document.newXObject(ReleaseNotesReferences.CHANGE_CLASS, this.xcontext);
        template.setStringValue("audience", "user");
        template.setLargeStringValue("summary", "To be written.");

        Change change = new Change();
        change.setTitle("Faster startup");
        ChangeXObjects.writeChange(this.document, change, WriteMode.OVER_TEMPLATE, this.xcontext);

        Change read = ChangeXObjects.read(this.document);
        assertEquals("Faster startup", read.getTitle());
        assertEquals(Audience.USER, read.getAudience());
        assertEquals("To be written.", read.getSummary());
    }

    /**
     * Replacing a change empties what the replacement leaves out rather than keeping it.
     */
    @Test
    void replacingAChangeEmptiesTheValuesItLeavesOut() throws Exception
    {
        ChangeXObjects.writeEntry(this.document, "XWiki", "8.3", this.xcontext);
        ChangeXObjects.writeChange(this.document, fullChange(), WriteMode.OVER_TEMPLATE, this.xcontext);

        Change replacement = new Change();
        replacement.setTitle("Even faster startup");
        ChangeXObjects.writeChange(this.document, replacement, WriteMode.REPLACE, this.xcontext);

        Change read = ChangeXObjects.read(this.document);
        assertEquals("Even faster startup", read.getTitle());
        assertEquals("", read.getSummary());
        assertEquals("", read.getDescription());
        assertEquals("", read.getCategory());
        assertNull(read.getAudience());
        assertNull(read.getImportance());
        assertTrue(read.getScreenshots().isEmpty());
        assertEquals("XWiki", read.getProduct(), "Expected the entry to be left as it was.");
    }

    /**
     * A page carrying only one of the two objects is not a change: the contributors of a release note are an entry
     * with no change object, and a change object with no entry is invisible to every query.
     */
    @Test
    void aPageMissingEitherObjectHoldsNoChange() throws Exception
    {
        ReleaseNotesNotFoundException exception =
            assertThrows(ReleaseNotesNotFoundException.class, () -> ChangeXObjects.read(this.document));
        assertEquals(PAGE, exception.getReference());
        assertEquals("The page [xwiki:ReleaseNotes.Data.XWiki.8\\.3M1.Entry001.WebHome] holds no change.",
            exception.getMessage());

        this.document.newXObject(ReleaseNotesReferences.CHANGE_CLASS, this.xcontext);
        assertThrows(ReleaseNotesNotFoundException.class, () -> ChangeXObjects.read(this.document));
    }

    @Test
    void aChangeIsNotReplacedOnAPageHoldingNone() throws Exception
    {
        ChangeXObjects.writeEntry(this.document, "XWiki", "8.3", this.xcontext);
        Change change = fullChange();

        assertThrows(ReleaseNotesNotFoundException.class,
            () -> ChangeXObjects.writeChange(this.document, change, WriteMode.REPLACE, this.xcontext));
        assertNull(this.document.getXObject(ReleaseNotesReferences.CHANGE_CLASS));
    }

    /**
     * The spaces around the commas separating the media are ignored, and so are empty names, the way the screenshot
     * displayer reads them.
     */
    @Test
    void theMediaAreReadBackAsATrimmedList() throws Exception
    {
        ChangeXObjects.writeEntry(this.document, "XWiki", "8.3", this.xcontext);
        this.document.newXObject(ReleaseNotesReferences.CHANGE_CLASS, this.xcontext)
            .setStringValue("screenshots", "one.png ,  two.mp4 ,");

        assertEquals(List.of("one.png", "two.mp4"), ChangeXObjects.read(this.document).getScreenshots());
    }

    private Change fullChange()
    {
        Change change = new Change();
        change.setTitle("Faster startup");
        change.setSummary("Starting a wiki now takes half the time.");
        change.setDescription("The long story.");
        change.setAudience(Audience.DEVELOPER);
        change.setImportance(Importance.HIGH);
        change.setCategory("Performance");
        change.setScreenshots(List.of("before.png", "after.png"));

        return change;
    }
}

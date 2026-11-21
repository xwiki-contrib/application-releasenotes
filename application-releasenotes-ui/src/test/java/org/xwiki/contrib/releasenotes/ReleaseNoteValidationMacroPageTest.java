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

import java.text.SimpleDateFormat;
import java.util.List;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.rendering.wikimacro.internal.WikiMacroFactoryComponentClass;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.WikiMacroSetup;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Page test for the {@code releasenotevalidation} wiki macro, defined in
 * {@code ReleaseNotes.Code.ReleaseNoteValidationMacro}.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
@WikiMacroFactoryComponentClass
class ReleaseNoteValidationMacroPageTest extends PageTest
{
    private static final DocumentReference MACRO =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "ReleaseNoteValidationMacro");

    private static final DocumentReference RELEASE_NOTE_CLASS =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Code"), "ReleaseNoteClass");

    private static final DocumentReference RELEASE_NOTE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "8.3"), "WebHome");

    private static final String BADGE = ".rn-qa-badge";

    @BeforeEach
    void setUp() throws Exception
    {
        // The macro emits its badge from a clean=false HTML block, which its (privileged) author is allowed to do.
        when(this.oldcore.getMockRightService().hasProgrammingRights(any())).thenReturn(true);

        WikiMacroSetup.loadWikiMacro(this, this.componentManager, MACRO);
        loadPage(RELEASE_NOTE_CLASS);
    }

    /**
     * A version Manual QA has not tested displays no badge: neither does a release note created before the
     * validation existed, which holds no value for it.
     */
    @Test
    void aVersionNotTestedDisplaysNoBadge() throws Exception
    {
        assertNull(render("notTested", null).selectFirst(BADGE));
        assertNull(render("", null).selectFirst(BADGE));
    }

    /**
     * The tooltip of a validated version says on which day Manual QA validated it, in the language of the reader. A
     * page test registers no translation bundle, so a translated string renders as its own key followed by its
     * parameters.
     */
    @Test
    void aValidatedVersionDisplaysTheValidatedBadgeWithTheDayOfItsValidation() throws Exception
    {
        Element badge = render("validated", "20/09/2016").selectFirst(BADGE);

        assertNotNull(badge, "A validated version must display a badge.");
        assertEquals("rn-qa-badge rn-qa-badge-validated", badge.className());
        assertEquals("releasenotes.qaValidation.validated.dateTooltip [Sep 20, 2016]", badge.attr("title"));
        assertEquals("releasenotes.qaValidation.label", badge.selectFirst(".rn-qa-badge-label").text());
        assertEquals("releasenotes.qaValidation.validated", badge.selectFirst(".rn-qa-badge-status").text());
        // Screen readers do not read the title, so the meaning of the badge is also given to them as text.
        assertEquals("releasenotes.qaValidation.label: releasenotes.qaValidation.validated. "
            + "releasenotes.qaValidation.validated.dateTooltip [Sep 20, 2016]", badge.selectFirst(".sr-only").text());
    }

    @Test
    void aValidatedVersionWithoutAValidationDayDisplaysTheTooltipWithoutDay() throws Exception
    {
        Element badge = render("validated", null).selectFirst(BADGE);

        assertNotNull(badge, "A validated version must display a badge, even without its validation day.");
        assertEquals("releasenotes.qaValidation.validated.tooltip", badge.attr("title"));
    }

    @Test
    void aVersionNotValidatedDisplaysTheNotValidatedBadge() throws Exception
    {
        Element badge = render("notValidated", "20/09/2016").selectFirst(BADGE);

        assertNotNull(badge, "A version Manual QA did not validate must display a badge.");
        assertEquals("rn-qa-badge rn-qa-badge-notValidated", badge.className());
        assertEquals("releasenotes.qaValidation.notValidated.tooltip", badge.attr("title"));
        assertEquals("releasenotes.qaValidation.notValidated", badge.selectFirst(".rn-qa-badge-status").text());
    }

    private Document render(String validation, String validationDay) throws Exception
    {
        XWikiDocument releaseNote = this.xwiki.getDocument(RELEASE_NOTE, this.context);
        releaseNote.setSyntax(Syntax.XWIKI_2_1);
        releaseNote.setContent("{{releasenotevalidation/}}");
        BaseObject object = releaseNote.newXObject(RELEASE_NOTE_CLASS, this.context);
        object.setStringValue("product", "XWiki");
        object.setStringValue("version", "8.3");
        if (!validation.isEmpty()) {
            object.setStringValue("qaValidation", validation);
        }
        if (validationDay != null) {
            object.setDateValue("qaValidationDate", new SimpleDateFormat("dd/MM/yyyy").parse(validationDay));
        }
        this.xwiki.saveDocument(releaseNote, this.context);
        this.context.setDoc(releaseNote);

        return renderHTMLPage(releaseNote);
    }
}

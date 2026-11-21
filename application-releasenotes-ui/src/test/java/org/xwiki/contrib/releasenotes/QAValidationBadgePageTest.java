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
import org.xwiki.localization.script.LocalizationScriptService;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.script.service.ScriptService;
import org.xwiki.test.page.HTML50ComponentList;
import org.xwiki.test.page.PageTest;
import org.xwiki.test.page.XWikiSyntax21ComponentList;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Page test for the Manual QA validation badge, the UI extension defined in
 * {@code ReleaseNotes.Code.QAValidationBadge}. A page test registers no UI extension, so the content of the extension
 * is rendered as the content of the page it is displayed on, which is what it sees once registered: the page being
 * viewed is the current document.
 *
 * @version $Id$
 */
@HTML50ComponentList
@XWikiSyntax21ComponentList
class QAValidationBadgePageTest extends PageTest
{
    private static final List<String> CODE_SPACES = List.of("ReleaseNotes", "Code");

    private static final DocumentReference BADGE = new DocumentReference("xwiki", CODE_SPACES, "QAValidationBadge");

    private static final DocumentReference RELEASE_NOTE_CLASS =
        new DocumentReference("xwiki", CODE_SPACES, "ReleaseNoteClass");

    private static final DocumentReference CONFIGURATION =
        new DocumentReference("xwiki", CODE_SPACES, "ReleaseNotesConfig");

    private static final DocumentReference CONFIGURATION_CLASS =
        new DocumentReference("xwiki", CODE_SPACES, "ReleaseNotesConfigClass");

    private static final DocumentReference RELEASE_NOTE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "XWiki", "8.3"), "WebHome");

    private static final String BADGE_SELECTOR = ".rn-qa-badge";

    private static final String DISPLAY = "displayQAValidation";

    private static final String VALIDATED = "validated";

    private static final String DAY = "20/09/2016";

    private String badgeContent;

    @BeforeEach
    void setUp() throws Exception
    {
        // The badge is emitted from a clean=false HTML block, which its (privileged) author is allowed to do.
        when(this.oldcore.getMockRightService().hasProgrammingRights(any())).thenReturn(true);

        loadPage(RELEASE_NOTE_CLASS);
        loadPage(CONFIGURATION_CLASS);
        XWikiDocument badgePage = loadPage(BADGE);
        this.badgeContent = badgePage.getXObject(new DocumentReference("xwiki", "XWiki", "UIExtensionClass"))
            .getLargeStringValue("content");

        // The application ships with the badge turned off, which a wiki using it turns on in its administration.
        XWikiDocument configuration = loadPage(CONFIGURATION);
        assertEquals(0, configuration.getXObject(CONFIGURATION_CLASS).getIntValue(DISPLAY),
            "The badge must be turned off until an administrator turns it on.");
        setDisplayed(true);
    }

    @Test
    void aValidatedVersionDisplaysTheValidatedBadgeWithTheDayOfItsValidation() throws Exception
    {
        Element badge = render(VALIDATED, DAY).selectFirst(BADGE_SELECTOR);

        assertNotNull(badge, "A validated version must display a badge.");
        assertEquals("rn-qa-badge rn-qa-badge-validated", badge.className());
        // A page test registers no translation bundle, so a translated string renders as its own key followed by
        // its parameters. The day is formatted in the language of the reader, English in a page test.
        String tooltip = "releasenotes.qaValidation.validated.dateTooltip [Sep 20, 2016]";
        Element label = badge.selectFirst(".rn-qa-badge-label");
        Element status = badge.selectFirst(".rn-qa-badge-status");
        assertEquals("releasenotes.qaValidation.label", label.text());
        assertEquals("releasenotes.qaValidation.validated", status.text());
        assertEquals(tooltip, label.attr("title"));
        assertEquals(tooltip, status.attr("title"));
        // Assistive technologies get the meaning of the badge once, from the hidden text, and not from the parts
        // carrying the tooltip.
        assertEquals("true", label.attr("aria-hidden"));
        assertEquals("true", status.attr("aria-hidden"));
        assertEquals("releasenotes.qaValidation.label: releasenotes.qaValidation.validated. " + tooltip,
            badge.selectFirst(".sr-only").text());
    }

    @Test
    void aValidatedVersionWithoutAValidationDayDisplaysTheTooltipWithoutDay() throws Exception
    {
        Element badge = render(VALIDATED, null).selectFirst(BADGE_SELECTOR);

        assertNotNull(badge, "A validated version must display a badge, even without its validation day.");
        assertEquals("releasenotes.qaValidation.validated.tooltip",
            badge.selectFirst(".rn-qa-badge-status").attr("title"));
    }

    @Test
    void aVersionNotValidatedDisplaysTheNotValidatedBadge() throws Exception
    {
        Element badge = render("notValidated", DAY).selectFirst(BADGE_SELECTOR);

        assertNotNull(badge, "A version Manual QA did not validate must display a badge.");
        assertEquals("rn-qa-badge rn-qa-badge-notValidated", badge.className());
        assertEquals("releasenotes.qaValidation.notValidated", badge.selectFirst(".rn-qa-badge-status").text());
        assertEquals("releasenotes.qaValidation.notValidated.tooltip",
            badge.selectFirst(".rn-qa-badge-status").attr("title"));
    }

    /**
     * A version Manual QA has not tested displays no badge: neither does a release note created before the
     * validation existed, which holds no value for it.
     */
    @Test
    void aVersionNotTestedDisplaysNoBadge() throws Exception
    {
        assertNull(render("notTested", null).selectFirst(BADGE_SELECTOR));
        assertNull(render("", null).selectFirst(BADGE_SELECTOR));
    }

    @Test
    void noBadgeIsDisplayedUnlessTheAdministrationTurnsItOn() throws Exception
    {
        setDisplayed(false);

        assertNull(render(VALIDATED, DAY).selectFirst(BADGE_SELECTOR));
    }

    /**
     * The extension is displayed on every page of the wiki, and only a release note has a badge.
     */
    @Test
    void aPageThatIsNoReleaseNoteDisplaysNothing() throws Exception
    {
        XWikiDocument page =
            this.xwiki.getDocument(new DocumentReference("xwiki", "Main", "WebHome"), this.context);
        page.setSyntax(Syntax.XWIKI_2_1);
        page.setContent(this.badgeContent);
        this.xwiki.saveDocument(page, this.context);
        this.context.setDoc(page);

        assertEquals("", renderHTMLPage(page).body().text());
    }

    /**
     * A translation is written by whoever may edit a translation page, so the badge displays it as text: neither
     * markup nor a wiki macro in it may reach the page.
     */
    @Test
    void theTranslationsAreDisplayedAsText() throws Exception
    {
        String hostile = "<b class=\"injected\">QA</b>\"{{/html}}{{velocity}}$xcontext{{/velocity}}";
        LocalizationScriptService localization = this.componentManager.getInstance(ScriptService.class,
            "localization");
        when(localization.render("releasenotes.qaValidation.label")).thenReturn(hostile);
        when(localization.render("releasenotes.qaValidation.validated.tooltip")).thenReturn(hostile);

        Document html = render(VALIDATED, null);

        assertTrue(html.select(".injected").isEmpty(), "A translation must not inject markup into the page.");
        Element badge = html.selectFirst(BADGE_SELECTOR);
        assertNotNull(badge, "The badge must still be displayed.");
        assertEquals(hostile, badge.selectFirst(".rn-qa-badge-label").text());
        assertEquals(hostile, badge.selectFirst(".rn-qa-badge-label").attr("title"));
        assertTrue(badge.selectFirst(".sr-only").text().startsWith(hostile + ": "));
    }

    private void setDisplayed(boolean displayed) throws Exception
    {
        XWikiDocument configuration = this.xwiki.getDocument(CONFIGURATION, this.context);
        configuration.getXObject(CONFIGURATION_CLASS).setIntValue(DISPLAY, displayed ? 1 : 0);
        this.xwiki.saveDocument(configuration, this.context);
    }

    private Document render(String validation, String validationDay) throws Exception
    {
        XWikiDocument releaseNote = this.xwiki.getDocument(RELEASE_NOTE, this.context);
        releaseNote.setSyntax(Syntax.XWIKI_2_1);
        releaseNote.setContent(this.badgeContent);
        BaseObject object = releaseNote.getXObject(RELEASE_NOTE_CLASS, true, this.context);
        object.setStringValue("product", "XWiki");
        object.setStringValue("version", "8.3");
        object.setStringValue("qaValidation", validation);
        if (validationDay != null) {
            object.setDateValue("qaValidationDate", new SimpleDateFormat("dd/MM/yyyy").parse(validationDay));
        } else {
            object.removeField("qaValidationDate");
        }
        this.xwiki.saveDocument(releaseNote, this.context);
        this.context.setDoc(releaseNote);

        return renderHTMLPage(releaseNote);
    }
}

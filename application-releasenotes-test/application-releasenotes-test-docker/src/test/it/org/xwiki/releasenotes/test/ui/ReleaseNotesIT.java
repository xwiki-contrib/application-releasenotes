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
package org.xwiki.releasenotes.test.ui;

import java.util.List;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.ViewPage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests for the Release Notes Application.
 *
 * @version $Id$
 */
@UITest
class ReleaseNotesIT
{
    /**
     * Builds a release note page (with the contributors macro) plus, optionally, its ContributorsList child entry,
     * then asserts the macro shows a warning when the list is absent and renders the names when it is present.
     */
    @Test
    @Order(1)
    void contributorsList(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "TestProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "= Credits =\n\n{{releasenotecontributors/}}", "RN 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass", "product", "TestProduct", "version", "1.0");

        // Before any contributors list exists, the macro shows the warning.
        ViewPage beforePage = setup.gotoPage(releaseNote);
        assertTrue(beforePage.getContent().contains("The list of contributors has not been generated yet."),
            "Expected the not-generated-yet warning before the contributors list exists.");

        // Create the deterministic ContributorsList child entry with two names.
        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "TestProduct", "1.0", "ContributorsList"), "WebHome");
        setup.rest().delete(contributorsEntry);
        setup.createPage(contributorsEntry, "", "Contributors");
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.EntryClass",
            "product", "TestProduct", "type", "ContributorsList", "version", "1.0");
        // Store the names unsorted and with mixed case to exercise the case-insensitive alphabetical ordering.
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.ContributorsListClass",
            "contributors", "bob jones\nAlice Smith\nCarol Nguyen");

        // Now the macro renders the names, sorted alphabetically ignoring case, and drops the warning.
        ViewPage afterPage = setup.gotoPage(releaseNote);
        String content = afterPage.getContent();
        assertTrue(content.contains("Alice Smith"), "Expected the first contributor to be rendered.");
        assertTrue(content.contains("bob jones"), "Expected the second contributor to be rendered.");
        assertTrue(content.contains("Carol Nguyen"), "Expected the third contributor to be rendered.");
        assertTrue(content.indexOf("Alice Smith") < content.indexOf("bob jones")
            && content.indexOf("bob jones") < content.indexOf("Carol Nguyen"),
            "Contributors must be sorted alphabetically ignoring case, got: " + content);
        assertFalse(content.contains("has not been generated yet"),
            "Warning must disappear once the contributors list exists.");
    }

    /**
     * Exercises the aligned edit UX: the macro shows an "Add contributors" button when the list is absent, the button
     * opens the separate ContributorsList page in inline edit mode, and saving there renders the names back on the
     * release note and drops the warning.
     */
    @Test
    @Order(2)
    void contributorsListEditFlow(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "EditProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "EditProduct", "1.0", "ContributorsList"), "WebHome");
        setup.rest().delete(contributorsEntry);

        setup.createPage(releaseNote, "= Credits =\n\n{{releasenotecontributors/}}", "RN Edit 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", "EditProduct", "version", "1.0");

        // Absent: warning is shown and the "Add contributors" button is available to an editor.
        ViewPage beforePage = setup.gotoPage(releaseNote);
        assertTrue(beforePage.getContent().contains("The list of contributors has not been generated yet."),
            "Expected the not-generated-yet warning before the contributors list exists.");
        WebElement addButton = setup.getDriver().findElementWithoutWaiting(
            By.cssSelector("input.button[value='Add contributors']"));

        // Click "Add contributors": lands on the ContributorsList page in inline edit mode.
        addButton.click();
        WebElement textarea = setup.getDriver().findElement(By.cssSelector("textarea"));
        textarea.clear();
        textarea.sendKeys("Alice Smith\nBob Jones");
        setup.getDriver().findElement(By.cssSelector("input[name='action_save']")).click();

        // Back on the release note: both names render and the warning is gone.
        ViewPage afterPage = setup.gotoPage(releaseNote);
        String content = afterPage.getContent();
        assertTrue(content.contains("Alice Smith"), "Expected the first contributor to be rendered.");
        assertTrue(content.contains("Bob Jones"), "Expected the second contributor to be rendered.");
        assertFalse(content.contains("The list of contributors has not been generated yet."),
            "Warning must disappear once the contributors list has been saved.");

        // The ContributorsList page created from the macro is a technical child page: it must be hidden.
        Page savedEntry = setup.rest().get(contributorsEntry);
        assertTrue(savedEntry.isHidden(), "The ContributorsList child page must be created as a hidden page.");
    }

    /**
     * A contributor name containing wiki syntax must be rendered literally, not interpreted (injection guard for the
     * $services.rendering.escape call in the macro).
     */
    @Test
    @Order(3)
    void contributorsListEscapesWikiSyntax(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "EscProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "{{releasenotecontributors/}}", "RN Esc 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass", "product", "EscProduct", "version", "1.0");

        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "EscProduct", "1.0", "ContributorsList"), "WebHome");
        setup.rest().delete(contributorsEntry);
        setup.createPage(contributorsEntry, "", "Contributors");
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.EntryClass",
            "product", "EscProduct", "type", "ContributorsList", "version", "1.0");
        // A name carrying bold wiki syntax: if escaped, the asterisks survive in the rendered text; if interpreted,
        // the text would render as bold and the asterisks would be gone.
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.ContributorsListClass",
            "contributors", "**Robert Tables**");

        String content = setup.gotoPage(releaseNote).getContent();
        assertTrue(content.contains("**Robert Tables**"),
            "Wiki syntax in a contributor name must be escaped and rendered literally, got: " + content);
    }

    /**
     * Adding the first change to a version that already has a ContributorsList entry must still number the new change
     * Entry001 (regression guard: the ContributorsList entry must not be counted by the change-numbering query).
     */
    @Test
    @Order(4)
    void changeNumberingIgnoresContributorsList(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();

        DocumentReference releaseNote =
            new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", "NumProduct", "1.0"), "WebHome");
        setup.rest().delete(releaseNote);
        setup.createPage(releaseNote, "{{releasenotechanges/}}", "RN Num 1.0");
        setup.addObject(releaseNote, "ReleaseNotes.Code.ReleaseNoteClass",
            "product", "NumProduct", "version", "1.0", "released", "0");

        // A ContributorsList entry exists in the version space, but no change entry does yet.
        DocumentReference contributorsEntry = new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", "NumProduct", "1.0", "ContributorsList"), "WebHome");
        setup.rest().delete(contributorsEntry);
        setup.createPage(contributorsEntry, "", "Contributors");
        setup.addObject(contributorsEntry, "ReleaseNotes.Code.EntryClass",
            "product", "NumProduct", "type", "ContributorsList", "version", "1.0");

        // Trigger "Add User Change": handleAddAction computes the next Entry name and redirects to its inline editor.
        setup.gotoPage(releaseNote, "view",
            "action=useradd&template=ReleaseNotes.Code.Change.ChangeTemplate&product=NumProduct&version=1.0"
                + "&audience=user");
        String currentUrl = setup.getDriver().getCurrentUrl();
        assertTrue(currentUrl.contains("Entry001"),
            "The new change must be numbered Entry001 despite the ContributorsList entry, landed on: " + currentUrl);
    }
}

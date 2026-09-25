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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.contrib.releasenotes.internal.migration.MigrationLeftover.Kind;
import org.xwiki.rendering.wiki.WikiModel;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectComponentManager;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MigrationSectionParser}, mostly on release notes of xwiki.org as they are written there, which
 * is the layout the import reads.
 *
 * @version $Id$
 */
@ComponentTest
@MigrationSyntaxComponentList
class MigrationSectionParserTest
{
    private static final String REFERENCE = "ReleaseNotes.Data.XWiki.17\\.4\\.0RC1.WebHome";

    private static final String ISSUES_HEADING = "== Issues specific to {{velocity}}$product $version{{/velocity}} ==";

    private static final String API_BREAKAGES_HEADING = "== API Breakages ==";

    @InjectComponentManager
    private MockitoComponentManager componentManager;

    /**
     * Only puts the parser in wiki mode, which is the mode it reads the references of the links in on a wiki.
     */
    @MockComponent
    private WikiModel wikiModel;

    private MigrationSectionParser parser;

    @BeforeComponent
    void registerContextComponentManager() throws Exception
    {
        this.componentManager.registerComponent(ComponentManager.class, "context", this.componentManager);
    }

    @BeforeEach
    void setUp() throws Exception
    {
        this.parser = this.componentManager.getInstance(MigrationSectionParser.class);
    }

    /**
     * Each note of the "Issues specific" subsection becomes one note, titled after its heading and holding its body.
     */
    @Test
    void theNotesOfTheIssuesSubsectionAreRead() throws Exception
    {
        MigrationSection section = this.parser.parse(load("xwiki-17.4.0RC1"), REFERENCE);

        assertFalse(section.isAlreadyMigrated());
        assertEquals(List.of("Live Data Vue Components registration", "Removed Mail Plugin", "New meta.vm template"),
            titles(section));
        assertTrue(section.getLeftovers().isEmpty(), "Nothing is left in place in a regular release note.");
        String description = section.getNotes().get(1).getDescription();
        assertFalse(description.startsWith("="), "The heading is the title, not part of the description.");
        assertFalse(description.endsWith("\n"), description);
    }

    /**
     * A note linking to an anchor of its release note does so with an empty reference, which would point to the page
     * of the entry the note is moved to: the link is made to point to the release note.
     */
    @Test
    void aLinkToAnAnchorOfTheReleaseNotePointsToTheReleaseNote() throws Exception
    {
        String description = this.parser.parse(load("xwiki-17.4.0RC1"), REFERENCE).getNotes().get(0).getDescription();

        assertTrue(description.contains("[[Live Data Component store>>doc:" + REFERENCE
            + "||anchor=\"HLiveDatacomponentsstore\"]]"), description);
        assertFalse(description.contains(">>||"), description);
    }

    /**
     * A rewrite replaces the "Issues specific" subsection with the macro displaying the migration note entries, and
     * leaves the rest of the release note exactly as it was written, down to the last character.
     */
    @Test
    void aRewriteOnlyReplacesTheIssuesSubsection() throws Exception
    {
        String content = load("xwiki-17.4.0RC1");
        String rewritten = this.parser.rewrite(content, this.parser.parse(content, REFERENCE));

        String before = content.substring(0, content.indexOf(ISSUES_HEADING));
        String after = content.substring(content.indexOf(API_BREAKAGES_HEADING));
        assertEquals(before + MigrationSectionParser.MIGRATION_NOTES_MACRO + "\n\n" + after, rewritten);
    }

    /**
     * The section of a rewritten release note displays the migration note entries, which is what tells a release
     * note already migrated from one to migrate, so that running the import again does nothing.
     */
    @Test
    void aRewrittenReleaseNoteIsAlreadyMigrated() throws Exception
    {
        String content = load("xwiki-17.4.0RC1");
        MigrationSection section =
            this.parser.parse(this.parser.rewrite(content, this.parser.parse(content, REFERENCE)), REFERENCE);

        assertTrue(section.isAlreadyMigrated());
        assertTrue(section.getNotes().isEmpty());
    }

    /**
     * The title of an entry is plain text, displayed escaped, so the wiki syntax of a heading is rendered away rather
     * than kept.
     */
    @Test
    void aTitleIsReadAsPlainText() throws Exception
    {
        assertEquals(List.of("Removal of Formula Macro", "Escaping of { in XML context"),
            titles(this.parser.parse(load("xwiki-14.7"), REFERENCE)));
    }

    /**
     * xwiki.org stores its pages with Windows line breaks, which the rewrite keeps, so that the history of a page only
     * shows the lines the import actually rewrote.
     */
    @Test
    void aRewriteKeepsTheLineBreaksOfThePage() throws Exception
    {
        String content = load("xwiki-15.0RC1");
        String rewritten = this.parser.rewrite(content, this.parser.parse(content, REFERENCE));

        assertTrue(rewritten.contains(MigrationSectionParser.MIGRATION_NOTES_MACRO + "\r\n\r\n"));
        assertTrue(rewritten.endsWith(content.substring(content.indexOf(API_BREAKAGES_HEADING))));
    }

    /**
     * A script macro written inside a code macro is code shown to the reader, not a script the note runs, so it does
     * not keep the note from being moved.
     */
    @Test
    void aScriptShownInACodeMacroDoesNotKeepANoteInPlace() throws Exception
    {
        MigrationSection section = this.parser.parse(load("xwiki-15.0RC1"), REFERENCE);

        assertTrue(titles(section).contains("Change of macro priorities"), titles(section).toString());
        assertTrue(section.getLeftovers().isEmpty(), "Got: " + kinds(section));
    }

    /**
     * Text under no note title has no title to give an entry, so it is left in place and reported, and the rewrite
     * keeps it in its subsection, above the macro displaying the entries.
     */
    @Test
    void textUnderNoTitleIsLeftInPlace() throws Exception
    {
        String content = load("xwiki-16.1.0");
        MigrationSection section = this.parser.parse(content, REFERENCE);

        assertEquals(List.of(Kind.UNTITLED_TEXT), kinds(section));
        assertTrue(section.getLeftovers().get(0).getText().startsWith("* ##eventstream.uselocalstore##"));

        String rewritten = this.parser.rewrite(content, section);
        String issues =
            rewritten.substring(rewritten.indexOf(ISSUES_HEADING), rewritten.indexOf(API_BREAKAGES_HEADING));
        assertTrue(issues.contains("##eventstream.uselocalstore##"), issues);
        assertTrue(issues.endsWith(MigrationSectionParser.MIGRATION_NOTES_MACRO + "\n\n"), issues);
    }

    /**
     * A note some release notes write as a subsection of its own is none of the subsections the notes are read from,
     * so it is left in place and reported.
     */
    @Test
    void aSubsectionOfItsOwnIsLeftInPlace() throws Exception
    {
        MigrationSection section = this.parser.parse(load("xwiki-9.8"), REFERENCE);

        assertEquals(List.of("Database List Property Values", "Changes in the way the $docextra variable works"),
            section.getLeftovers().stream().filter(leftover -> leftover.getKind() == Kind.NON_STANDARD_HEADING)
                .map(MigrationLeftover::getText).collect(Collectors.toList()));
    }

    @Test
    void aReleaseNoteWithNoSectionHasNothingToMigrate() throws Exception
    {
        assertNull(this.parser.parse("= New and Noteworthy =\n\nSomething new.\n", REFERENCE));
    }

    /**
     * A note running a script would not run it once moved to an entry, whose page declares no required right, so it
     * is left in place, and the rewrite keeps it above the macro.
     */
    @Test
    void aNoteRunningAScriptIsLeftInPlace() throws Exception
    {
        String content = section("=== A scripted note ===\n\n{{velocity}}$xwiki.version{{/velocity}}\n\n"
            + "=== A plain note ===\n\nDo this.\n\n");
        MigrationSection section = this.parser.parse(content, REFERENCE);

        assertEquals(List.of("A plain note"), titles(section));
        assertEquals(List.of(Kind.SCRIPT_MACRO), kinds(section));
        String rewritten = this.parser.rewrite(content, section);
        assertTrue(rewritten.contains("=== A scripted note ===\n\n{{velocity}}$xwiki.version{{/velocity}}\n\n"
            + MigrationSectionParser.MIGRATION_NOTES_MACRO), rewritten);
        assertFalse(rewritten.contains("A plain note"), rewritten);
    }

    /**
     * The placeholder the release note template leaves in a subsection nobody filled, and a word saying there is
     * nothing to say, are nothing a reader would miss.
     */
    @Test
    void aPlaceholderIsNothingToMigrate() throws Exception
    {
        assertTrue(this.parser.parse(section("{{comment}}\n<issues specific to the project>\n{{/comment}}\n\n"),
            REFERENCE).getLeftovers().isEmpty());
        assertTrue(this.parser.parse(section("None.\n\n"), REFERENCE).getLeftovers().isEmpty());
    }

    /**
     * A title that is empty once rendered as plain text gives an entry no title, which an entry needs.
     */
    @Test
    void aNoteWithAnEmptyTitleIsLeftInPlace() throws Exception
    {
        MigrationSection section = this.parser.parse(section("=== {{info}}x{{/info}} ===\n\nDo this.\n\n"),
            REFERENCE);

        assertTrue(section.getNotes().isEmpty());
        assertEquals(List.of(Kind.EMPTY_TITLE), kinds(section));
    }

    /**
     * The headings are the ones the parser reads, so a line that only looks like one, inside a code macro, is not
     * taken for the section.
     */
    @Test
    void aHeadingShownInACodeMacroIsNoHeading() throws Exception
    {
        MigrationSection section = this.parser.parse("{{code}}\n= Backward Compatibility and Migration Notes =\n"
            + ISSUES_HEADING + "\n=== Not a note ===\n{{/code}}\n\n"
            + section("=== A note ===\n\nDo this.\n\n"), REFERENCE);

        assertEquals(List.of("A note"), titles(section));
    }

    private static String section(String issues)
    {
        return "= Backward Compatibility and Migration Notes =\n\n== General Notes ==\n\n* Back up.\n\n"
            + ISSUES_HEADING + "\n\n" + issues + API_BREAKAGES_HEADING + "\n\nNone.\n\n= Credits =\n\n* Someone\n";
    }

    private static List<String> titles(MigrationSection section)
    {
        return section.getNotes().stream().map(MigrationNoteSource::getTitle).collect(Collectors.toList());
    }

    private static List<Kind> kinds(MigrationSection section)
    {
        return section.getLeftovers().stream().map(MigrationLeftover::getKind).collect(Collectors.toList());
    }

    /**
     * @param name the name of a release note of xwiki.org held by the test resources
     * @return its content, as xwiki.org stores it, line breaks included
     */
    private String load(String name) throws Exception
    {
        try (InputStream stream = getClass().getResourceAsStream("/migration/" + name + ".txt")) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.internal.migration.MigrationLeftover.Kind;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.EmptyLinesBlock;
import org.xwiki.rendering.block.HeaderBlock;
import org.xwiki.rendering.block.LinkBlock;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.block.NewLineBlock;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.renderer.BlockRenderer;
import org.xwiki.rendering.renderer.printer.DefaultWikiPrinter;

/**
 * Reads the backward compatibility and migration notes section of a release note written the way the release notes
 * of xwiki.org are, and rewrites it once its notes are migration note entries.
 * <p>
 * The structure is read by the wiki syntax parser, which is what tells a heading from a line inside a macro, but the
 * notes and the rewritten page are cut out of the source itself: rendering a parsed page back to wiki syntax does not
 * give the source it was parsed from, and a rewrite would otherwise change parts of the page it has no business
 * changing.
 *
 * @version $Id$
 * @since 2.8
 */
@Component(roles = MigrationSectionParser.class)
@Singleton
public class MigrationSectionParser
{
    /**
     * The macro call the "Issues specific" subsection of a migrated release note is replaced with.
     */
    public static final String MIGRATION_NOTES_MACRO = "{{releasenotechanges migrationNotes=\"true\"/}}";

    private static final String SECTION_TITLE = "Backward Compatibility";

    private static final String ISSUES_TITLE = "Issues specific";

    private static final List<String> STANDARD_TITLES = List.of("General Notes", "API Breakages", ISSUES_TITLE);

    /**
     * The macros a note cannot be moved with, since the page of an entry declares no required right and would not
     * run them.
     */
    private static final Set<String> SCRIPT_MACROS = Set.of("velocity", "groovy", "python", "script");

    /**
     * How a link to an anchor of the page it is written in starts, once its label is written: an empty reference
     * followed by its parameters.
     */
    private static final String SAME_PAGE_LINK = ">>||";

    private static final int EXCERPT_LENGTH = 200;

    /**
     * The line break xwiki.org stores its pages with, which a rewrite keeps, so that the lines it does not rewrite
     * stay as they were.
     */
    private static final String WINDOWS_LINE_BREAK = "\r\n";

    /**
     * The text of a subsection that says it holds nothing, which some release notes write instead of leaving the
     * subsection empty.
     */
    private static final Pattern NOTHING_TO_SAY = Pattern.compile("(?i)\\s*(none|n/a)\\s*[.!]*\\s*");

    @Inject
    @Named("xwiki/2.1")
    private Parser parser;

    @Inject
    @Named("plain/1.0")
    private BlockRenderer plainRenderer;

    /**
     * @param content the content of a release note
     * @param releaseNoteReference the reference of the page of that release note, which the links of a note to an
     *            anchor of that page are made to point to
     * @return the backward compatibility and migration notes section of that release note, or {@code null} when it
     *         has none
     * @throws ReleaseNotesException when the content could not be read
     */
    public MigrationSection parse(String content, String releaseNoteReference) throws ReleaseNotesException
    {
        List<Heading> headings = locateHeadings(content);
        int sectionIndex = -1;

        for (int index = 0; index < headings.size() && sectionIndex < 0; index++) {
            if (headings.get(index).isTitled(1, SECTION_TITLE)) {
                sectionIndex = index;
            }
        }

        if (sectionIndex < 0) {
            return null;
        }

        int sectionEnd = findEnd(headings, sectionIndex, 1, content.length());
        MigrationSection section = new MigrationSection();
        section.setAlreadyMigrated(
            containsMigrationNotesMacro(content.substring(headings.get(sectionIndex).getBodyStart(), sectionEnd)));

        if (!section.isAlreadyMigrated()) {
            List<Heading> inSection = new ArrayList<>();

            for (Heading heading : headings.subList(sectionIndex + 1, headings.size())) {
                if (heading.getStart() < sectionEnd) {
                    inSection.add(heading);
                }
            }

            int issuesIndex = readSubsections(inSection, section);

            if (issuesIndex >= 0) {
                readIssues(content, inSection, issuesIndex, sectionEnd, releaseNoteReference, section);
            }
        }

        return section;
    }

    /**
     * Reads the subsections of the section, and reports the ones that are none of the subsections the release notes
     * are written with.
     *
     * @return the index of the "Issues specific" subsection among the headings of the section, or {@code -1} when
     *         there is none
     */
    private int readSubsections(List<Heading> inSection, MigrationSection section)
    {
        int issuesIndex = -1;

        for (int index = 0; index < inSection.size(); index++) {
            Heading heading = inSection.get(index);

            if (heading.getLevel() != 2) {
                continue;
            }

            if (heading.isTitled(2, ISSUES_TITLE) && issuesIndex < 0) {
                issuesIndex = index;
            } else if (STANDARD_TITLES.stream()
                .noneMatch(title -> StringUtils.startsWithIgnoreCase(heading.getText(), title))) {
                section.getLeftovers().add(new MigrationLeftover(Kind.NON_STANDARD_HEADING, heading.getText()));
            }
        }

        return issuesIndex;
    }

    /**
     * @param content the content of a release note
     * @param section the backward compatibility and migration notes section of that release note
     * @return the content of that release note once the notes of its "Issues specific" subsection are migration note
     *         entries: that subsection is replaced with the macro displaying those entries, and is kept, holding only
     *         what the import left in place, when it left something
     */
    public String rewrite(String content, MigrationSection section)
    {
        String lineBreak = content.contains(WINDOWS_LINE_BREAK) ? WINDOWS_LINE_BREAK : "\n";
        StringBuilder replacement = new StringBuilder();

        if (StringUtils.isNotBlank(section.getKeptSource())) {
            replacement.append(section.getIssuesHeading())
                .append(StringUtils.stripEnd(section.getKeptSource(), null)).append(lineBreak).append(lineBreak);
        }

        replacement.append(MIGRATION_NOTES_MACRO).append(lineBreak).append(lineBreak);

        return content.substring(0, section.getIssuesStart()) + replacement + content.substring(section.getIssuesEnd());
    }

    private void readIssues(String content, List<Heading> inSection, int issuesIndex, int sectionEnd,
        String releaseNoteReference, MigrationSection section) throws ReleaseNotesException
    {
        Heading issues = inSection.get(issuesIndex);
        int issuesEnd = findEnd(inSection, issuesIndex, 2, sectionEnd);
        List<Integer> noteIndexes = new ArrayList<>();

        for (int index = issuesIndex + 1; index < inSection.size(); index++) {
            if (inSection.get(index).getStart() < issuesEnd && inSection.get(index).getLevel() == 3) {
                noteIndexes.add(index);
            }
        }

        StringBuilder kept = new StringBuilder();
        int preambleEnd = noteIndexes.isEmpty() ? issuesEnd : inSection.get(noteIndexes.get(0)).getStart();
        String preamble = content.substring(issues.getBodyStart(), preambleEnd);

        if (!isIgnorable(preamble)) {
            section.getLeftovers().add(new MigrationLeftover(Kind.UNTITLED_TEXT, excerpt(preamble)));
            kept.append(preamble);
        }

        for (int noteIndex : noteIndexes) {
            Heading note = inSection.get(noteIndex);
            int noteEnd = findEnd(inSection, noteIndex, 3, issuesEnd);
            MigrationLeftover leftover = readNote(content, note, noteEnd, releaseNoteReference, section);

            if (leftover != null) {
                section.getLeftovers().add(leftover);
                kept.append(content, note.getStart(), noteEnd);
            }
        }

        section.setIssues(issues.getStart(), issuesEnd, content.substring(issues.getStart(), issues.getBodyStart()),
            kept.toString());
    }

    /**
     * Reads one note, and adds it to the notes of the section when it can be moved to an entry.
     *
     * @return why the note is left in place, or {@code null} when it is moved
     */
    private MigrationLeftover readNote(String content, Heading note, int noteEnd, String releaseNoteReference,
        MigrationSection section) throws ReleaseNotesException
    {
        if (StringUtils.isBlank(note.getText())) {
            return new MigrationLeftover(Kind.EMPTY_TITLE, content.substring(note.getStart(), note.getBodyStart()));
        }

        String body = content.substring(note.getBodyStart(), noteEnd);
        XDOM bodyXdom = parse(body);

        if (bodyXdom.getFirstBlock(block -> block instanceof MacroBlock
            && SCRIPT_MACROS.contains(((MacroBlock) block).getId()), Block.Axes.DESCENDANT) != null) {
            return new MigrationLeftover(Kind.SCRIPT_MACRO, note.getText());
        }

        long samePageLinks = bodyXdom.<LinkBlock>getBlocks(new ClassBlockMatcher(LinkBlock.class),
            Block.Axes.DESCENDANT).stream().filter(link -> StringUtils.isEmpty(link.getReference().getReference()))
            .count();
        String description = body;

        if (samePageLinks > 0) {
            // A link to an anchor of the release note is written with an empty reference, which would point to the
            // page of the entry once the note lives there: it is made to point to the release note instead. The
            // parser tells how many such links the note holds, and the source is only rewritten when each of them
            // is written the one way the rewrite knows.
            if (StringUtils.countMatches(body, SAME_PAGE_LINK) != samePageLinks) {
                return new MigrationLeftover(Kind.UNRESOLVED_LINK, note.getText());
            }

            description = body.replace(SAME_PAGE_LINK, ">>doc:" + releaseNoteReference + "||");
        }

        section.getNotes().add(new MigrationNoteSource(note.getText(), description.strip()));

        return null;
    }

    /**
     * @return whether the passed text holds nothing a reader would miss: blank lines and comments, such as the
     *         placeholder the release note template leaves in a subsection nobody filled, or a word saying there is
     *         nothing to say, such as "None."
     */
    private boolean isIgnorable(String text) throws ReleaseNotesException
    {
        XDOM xdom = parse(text);
        boolean blank = true;

        for (Block block : xdom.getChildren()) {
            boolean comment = block instanceof MacroBlock && "comment".equals(((MacroBlock) block).getId());

            if (!comment && !(block instanceof EmptyLinesBlock) && !(block instanceof NewLineBlock)) {
                blank = false;
            }
        }

        return blank || NOTHING_TO_SAY.matcher(toPlainText(xdom.getChildren())).matches();
    }

    private boolean containsMigrationNotesMacro(String text) throws ReleaseNotesException
    {
        return parse(text).getFirstBlock(block -> block instanceof MacroBlock
            && "releasenotechanges".equals(((MacroBlock) block).getId())
            && "true".equals(((MacroBlock) block).getParameter("migrationNotes")), Block.Axes.DESCENDANT) != null;
    }

    /**
     * @return where the part starting with the heading at the passed index ends: at the next heading of the same
     *         level or of a higher one, or at the passed end when there is none
     */
    private static int findEnd(List<Heading> headings, int index, int level, int end)
    {
        for (Heading heading : headings.subList(index + 1, headings.size())) {
            if (heading.getLevel() <= level && heading.getStart() < end) {
                return heading.getStart();
            }
        }

        return end;
    }

    /**
     * Gives the headings of the content with where each of them is written. The headings are the ones the parser
     * reads, so that a line that only looks like a heading, e.g. inside a code macro, is not one; each of them is then
     * matched, in order, to the first line after the previous one that is a heading of the same level and text.
     */
    private List<Heading> locateHeadings(String content) throws ReleaseNotesException
    {
        List<Heading> candidates = new ArrayList<>();
        int start = 0;

        while (start < content.length()) {
            int lineEnd = content.indexOf('\n', start);
            int next = lineEnd < 0 ? content.length() : lineEnd + 1;
            String line = content.substring(start, lineEnd < 0 ? content.length() : lineEnd);

            if (line.stripLeading().startsWith("=")) {
                // A heading is parsed wrapped in the section it opens.
                HeaderBlock header =
                    parse(line).getFirstBlock(new ClassBlockMatcher(HeaderBlock.class), Block.Axes.DESCENDANT);

                if (header != null) {
                    candidates.add(new Heading(header.getLevel().getAsInt(), toPlainText(header), start, next));
                }
            }

            start = next;
        }

        List<Heading> headings = new ArrayList<>();
        int candidate = 0;

        for (HeaderBlock header : parse(content).<HeaderBlock>getBlocks(new ClassBlockMatcher(HeaderBlock.class),
            Block.Axes.DESCENDANT)) {
            int level = header.getLevel().getAsInt();
            String text = toPlainText(header);

            while (candidate < candidates.size() && !candidates.get(candidate).isTitled(level, text, true)) {
                candidate++;
            }

            if (candidate == candidates.size()) {
                throw new ReleaseNotesException(
                    String.format("Failed to locate the heading [%s] in the source.", text));
            }

            headings.add(candidates.get(candidate++));
        }

        return headings;
    }

    private String toPlainText(HeaderBlock header)
    {
        return toPlainText(header.getChildren());
    }

    private String toPlainText(List<Block> blocks)
    {
        DefaultWikiPrinter printer = new DefaultWikiPrinter();
        this.plainRenderer.render(blocks, printer);

        return StringUtils.normalizeSpace(printer.toString());
    }

    private XDOM parse(String text) throws ReleaseNotesException
    {
        try {
            return this.parser.parse(new StringReader(text));
        } catch (ParseException e) {
            throw new ReleaseNotesException("Failed to parse the content of the release note.", e);
        }
    }

    private static String excerpt(String text)
    {
        return StringUtils.abbreviate(StringUtils.normalizeSpace(text), EXCERPT_LENGTH);
    }

    /**
     * A heading of the content, with where it is written.
     */
    private static final class Heading
    {
        private final int level;

        private final String text;

        private final int start;

        private final int bodyStart;

        Heading(int level, String text, int start, int bodyStart)
        {
            this.level = level;
            this.text = text;
            this.start = start;
            this.bodyStart = bodyStart;
        }

        int getLevel()
        {
            return this.level;
        }

        String getText()
        {
            return this.text;
        }

        /**
         * @return where the line of the heading starts
         */
        int getStart()
        {
            return this.start;
        }

        /**
         * @return where the line after the heading starts
         */
        int getBodyStart()
        {
            return this.bodyStart;
        }

        /**
         * @return whether the heading is of the passed level and starts with the passed text, whatever its case, since
         *         the release notes do not all write their titles with the same case
         */
        boolean isTitled(int expectedLevel, String prefix)
        {
            return isTitled(expectedLevel, prefix, false);
        }

        boolean isTitled(int expectedLevel, String expectedText, boolean exactly)
        {
            if (this.level != expectedLevel) {
                return false;
            }

            return exactly ? this.text.equals(expectedText) : StringUtils.startsWithIgnoreCase(this.text, expectedText);
        }
    }
}

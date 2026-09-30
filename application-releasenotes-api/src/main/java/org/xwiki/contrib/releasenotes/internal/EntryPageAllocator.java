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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.model.reference.SpaceReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Hands out the page a new entry of a release note lives in.
 *
 * @version $Id$
 * @since 2.7
 */
@Component(roles = EntryPageAllocator.class)
@Singleton
public class EntryPageAllocator
{
    /**
     * The name of the page an entry of a release note lives in, whose number is zero-padded so that the entries of a
     * release note are displayed in the order they were added in.
     */
    private static final String ENTRY_NAME_FORMAT = "Entry%03d";

    /**
     * The pages of the entries of a release note, which is what the number of a new entry is derived from.
     */
    private static final Pattern ENTRY_NAME_PATTERN = Pattern.compile("Entry(\\d+)");

    /**
     * How many page names are tried before giving up. Every page of the release note has just been looked at, so the
     * page of the next number is free unless another author took it in the meantime, which is why more than one is
     * tried.
     */
    private static final int CANDIDATE_COUNT = 10;

    @Inject
    private ReleaseNotesDocumentStore documentStore;

    @Inject
    private QueryManager queryManager;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    /**
     * Gives the page of the next entry of a release note, loaded and not yet saved, so that the caller decides what
     * that page holds when it is saved.
     *
     * @param product the product of the release note to add an entry to
     * @param version the version of the release note to add an entry to, in its long form
     * @param xcontext the context to load that page with
     * @return that page, or {@code null} when no page name was free
     * @throws ReleaseNotesException when the entries the release note already holds could not be looked up
     */
    public XWikiDocument takeNextEntryPage(String product, String version, XWikiContext xcontext)
        throws ReleaseNotesException
    {
        DocumentReference noteReference = ReleaseNotesReferences.releaseNote(xcontext.getWikiId(), product, version);
        SpaceReference versionSpace = noteReference.getLastSpaceReference();
        int highestNumber = getHighestEntryNumber(versionSpace);

        for (int number = highestNumber + 1; number <= highestNumber + CANDIDATE_COUNT; number++) {
            DocumentReference candidate =
                ReleaseNotesReferences.entry(noteReference, String.format(ENTRY_NAME_FORMAT, number));
            XWikiDocument document = this.documentStore.load(candidate);

            if (document.isNew()) {
                return document;
            }
        }

        return null;
    }

    /**
     * Gives the highest number an entry of the passed release note is using.
     * <p>
     * Every page of the release note is looked at, and not only the entries that hold a change, so that a page taken
     * by an author who has not saved their change yet is seen as taken. The highest number is computed here rather
     * than asked of the query, because a query can only order the page names as strings, which sorts
     * {@code Entry999} above {@code Entry1000} and hands the number 1000 out over and over.
     */
    private int getHighestEntryNumber(SpaceReference versionSpace) throws ReleaseNotesException
    {
        String spaceName = this.localEntityReferenceSerializer.serialize(versionSpace);
        // MySQL uses "\" as an escape character, and that character is what separates the spaces of a space
        // reference whose space names hold a dot, so another escape character is asked for.
        String spaceLike = spaceName.replaceAll("([%_!])", "!$1") + ".%";
        List<String> pages;

        try {
            Query query = this.queryManager.createQuery("where doc.space like :space escape '!'", Query.XWQL);
            pages = query.bindValue("space", spaceLike).execute();
        } catch (QueryException e) {
            throw new ReleaseNotesException(
                String.format("Failed to look up the entries of the release note [%s].", spaceName), e);
        }

        int highestNumber = 0;

        for (String page : pages) {
            String entryName =
                this.documentReferenceResolver.resolve(page).getLastSpaceReference().getName();
            Matcher matcher = ENTRY_NAME_PATTERN.matcher(entryName);

            if (matcher.matches()) {
                highestNumber = Math.max(highestNumber, Integer.parseInt(matcher.group(1)));
            }
        }

        return highestNumber;
    }
}

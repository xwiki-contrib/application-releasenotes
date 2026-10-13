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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.math.NumberUtils;
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

    /**
     * The highest number an entry can have for the numbers of the candidates that follow it to be counted. A page
     * named after a higher number, or after a number too long to be counted at all, was not numbered here but named by
     * hand, and is left out: counting it would make the creation of every new entry of the release note fail.
     */
    private static final int HIGHEST_ENTRY_NUMBER = Integer.MAX_VALUE - CANDIDATE_COUNT;

    /**
     * How many locks the release notes share. Two release notes sharing a lock only means their entries are not
     * created at the same time, which costs little, while a lock per release note would never be freed.
     */
    private static final int LOCK_COUNT = 64;

    /**
     * The entries of a release note are created one at a time, so that requests adding an entry to the same release
     * note at the same time are not all handed the same number and left to retry until some of them give up.
     */
    private final Lock[] locks = IntStream.range(0, LOCK_COUNT).mapToObj(i -> new ReentrantLock()).toArray(Lock[]::new);

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
     * What the page of a new entry is filled with before it is saved.
     */
    @FunctionalInterface
    public interface EntryPageWriter
    {
        /**
         * @param document the page of the new entry, not yet saved, to write into
         * @throws ReleaseNotesException when that page could not be filled
         */
        void write(XWikiDocument document) throws ReleaseNotesException;
    }

    /**
     * Saves the page of the next entry of a release note, as the current user, with what the passed writer fills it
     * with.
     * <p>
     * The entries of a release note are created one at a time on this wiki instance. Two instances of a cluster adding
     * an entry to the same release note at the same time can still be handed the same number: the first save creates
     * the page, and the second fails because the page now exists, so that one moves on to the next number and is
     * filled and saved again, rather than overwrite the first one or fail.
     *
     * @param product the product of the release note to add an entry to
     * @param version the version of the release note to add an entry to, in its long form
     * @param writer what fills the page before it is saved
     * @param comment the comment to record the new page with
     * @param xcontext the context to resolve the wiki of the release note with
     * @return the reference of the saved page, or {@code null} when no page name was free
     * @throws ReleaseNotesException when the entries the release note already holds could not be looked up, or the
     *     page could not be filled or saved
     */
    public DocumentReference saveNextEntryPage(String product, String version, EntryPageWriter writer, String comment,
        XWikiContext xcontext) throws ReleaseNotesException
    {
        DocumentReference noteReference = ReleaseNotesReferences.releaseNote(xcontext.getWikiId(), product, version);
        Lock lock = this.locks[Math.floorMod(noteReference.hashCode(), LOCK_COUNT)];

        lock.lock();
        try {
            // The lock only covers this wiki instance: in a cluster, another instance can still create a page of the
            // same name in the meantime, which is what the retry is for.
            for (int attempt = 0; attempt < CANDIDATE_COUNT; attempt++) {
                XWikiDocument document = takeNextEntryPage(noteReference);

                if (document == null) {
                    return null;
                }

                writer.write(document);

                try {
                    this.documentStore.save(document, comment);

                    return document.getDocumentReference();
                } catch (ReleaseNotesException e) {
                    if (this.documentStore.load(document.getDocumentReference()).isNew()) {
                        throw e;
                    }
                    // Another author created a page of that name since it was looked up, which is why the save
                    // failed: the next number is tried.
                }
            }

            return null;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Gives the page of the next entry of a release note, loaded and not yet saved.
     */
    private XWikiDocument takeNextEntryPage(DocumentReference noteReference) throws ReleaseNotesException
    {
        SpaceReference versionSpace = noteReference.getLastSpaceReference();
        int highestNumber = getHighestEntryNumber(versionSpace);

        for (int number = highestNumber + 1; number <= highestNumber + CANDIDATE_COUNT; number++) {
            DocumentReference candidate =
                ReleaseNotesReferences.entry(noteReference, String.format(ENTRY_NAME_FORMAT, number));
            XWikiDocument document = this.documentStore.load(candidate);

            if (document.isNew()) {
                // Requests loading a page that does not exist at the same time can be handed the same instance, so
                // what is written into is a copy, or the authors of those requests would fill each other's page.
                return document.clone();
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
                int number = NumberUtils.toInt(matcher.group(1), -1);

                if (number <= HIGHEST_ENTRY_NUMBER) {
                    highestNumber = Math.max(highestNumber, number);
                }
            }
        }

        return highestNumber;
    }
}

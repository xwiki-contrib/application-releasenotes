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
import java.util.function.Predicate;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteAlreadyExistsException;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.stability.Unstable;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Default implementation of {@link ReleaseNoteManager}, which holds the release notes in the pages of the
 * {@code ReleaseNotes.Data} space.
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Singleton
@Unstable
public class DefaultReleaseNoteManager implements ReleaseNoteManager
{
    private static final String PRODUCT = "product";

    private static final String VERSION = "version";

    private static final String DATE = "date";

    private static final String RELEASED = "released";

    private static final String LEVEL = "level";

    /**
     * The key of the title a release note is given, which names the product and the version it is about.
     */
    private static final String TITLE_KEY = "releasenotes.releasenote.title";

    /**
     * What a caller is told about a page it asked to read or to replace the release note of, and that holds none.
     */
    private static final String NO_RELEASE_NOTE = "The page [%s] holds no release note.";

    /**
     * What a release note with no version is refused with, both when it is created and when it is replaced: the
     * version is what its page is named after.
     */
    private static final String NO_VERSION = "A release note needs the version it is about.";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private ReleaseNotesConfiguration configuration;

    @Inject
    private ProductResolver productResolver;

    @Inject
    private ReleaseNotesDocumentStore documentStore;

    @Inject
    private QueryManager queryManager;

    @Inject
    private ContextualLocalizationManager localization;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    @Override
    public DocumentReference createReleaseNote(ReleaseNote note) throws ReleaseNotesException
    {
        String version = StringUtils.trimToNull(note.getVersion());

        if (version == null) {
            throw new ReleaseNotesException(NO_VERSION);
        }

        String product = this.productResolver.resolve(note.getProduct());
        DocumentReference reference = getReleaseNoteReference(product, version);

        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document = this.documentStore.load(reference);

        if (!document.isNew()) {
            throw new ReleaseNoteAlreadyExistsException(reference);
        }

        DocumentReference template =
            note.getTemplate() != null ? note.getTemplate() : this.configuration.getDefaultTemplate();

        try {
            applyTemplate(document, template, xcontext);
            document.setTitle(getTitle(product, version));

            BaseObject object = document.newXObject(ReleaseNotesReferences.RELEASE_NOTE_CLASS, xcontext);
            object.set(PRODUCT, product, xcontext);
            object.set(VERSION, version, xcontext);
            setReleaseState(object, note, xcontext);
        } catch (XWikiException e) {
            throw new ReleaseNotesException(
                String.format("Failed to fill the page [%s] of the new release note.", reference), e);
        }

        this.documentStore.save(document, "New Release note");

        return reference;
    }

    @Override
    public ReleaseNote updateReleaseNote(ReleaseNote note) throws ReleaseNotesException
    {
        String version = StringUtils.trimToNull(note.getVersion());

        if (version == null) {
            throw new ReleaseNotesException(NO_VERSION);
        }

        DocumentReference reference =
            getReleaseNoteReference(this.productResolver.resolve(note.getProduct()), version);
        XWikiContext xcontext = this.xcontextProvider.get();
        // The page exists, and the instance the store answers with for a page that exists is the one it holds in its
        // cache, which a caller must not write into: what is modified here is a copy of it, and the save is what
        // makes the wiki hold it.
        XWikiDocument document = this.documentStore.load(reference).clone();
        BaseObject object = document.getXObject(ReleaseNotesReferences.RELEASE_NOTE_CLASS);

        if (object == null) {
            throw new ReleaseNotesNotFoundException(String.format(NO_RELEASE_NOTE, reference), reference);
        }

        try {
            // Both properties are written, and not only the ones the passed release note carries: this replaces the
            // release note, so a date the caller left out is emptied rather than kept.
            setReleaseState(object, note, xcontext);
        } catch (XWikiException e) {
            throw new ReleaseNotesException(
                String.format("Failed to write the release note of the page [%s].", reference), e);
        }

        this.documentStore.save(document, "Updated release note");

        return toReleaseNote(object);
    }

    @Override
    public DocumentReference getReleaseNoteReference(String product, String version)
    {
        return ReleaseNotesReferences.releaseNote(this.xcontextProvider.get().getWikiId(), product, version);
    }

    @Override
    public ReleaseNote getReleaseNote(DocumentReference reference) throws ReleaseNotesException
    {
        BaseObject object = this.documentStore.load(reference).getXObject(ReleaseNotesReferences.RELEASE_NOTE_CLASS);

        if (object == null) {
            throw new ReleaseNotesNotFoundException(String.format(NO_RELEASE_NOTE, reference), reference);
        }

        return toReleaseNote(object);
    }

    @Override
    public List<ReleaseNote> getReleaseNotes(String product, Predicate<DocumentReference> filter)
        throws ReleaseNotesException
    {
        String className =
            this.localEntityReferenceSerializer.serialize(ReleaseNotesReferences.RELEASE_NOTE_CLASS);
        StringBuilder statement = new StringBuilder("from doc.object(").append(className).append(") as note");

        if (StringUtils.isNotBlank(product)) {
            statement.append(" where note.product = :product");
        }

        // The pages of the release notes are named after their product and their version, so ordering them by page
        // is what gives the release notes of one product together, in version order.
        statement.append(" order by doc.fullName");

        List<String> pages;

        try {
            Query query = this.queryManager.createQuery(statement.toString(), Query.XWQL);

            if (StringUtils.isNotBlank(product)) {
                query.bindValue(PRODUCT, product);
            }

            pages = query.execute();
        } catch (QueryException e) {
            throw new ReleaseNotesException("Failed to look up the release notes of this wiki.", e);
        }

        List<ReleaseNote> notes = new ArrayList<>(pages.size());

        for (String page : pages) {
            DocumentReference reference = this.documentReferenceResolver.resolve(page);

            if (filter.test(reference)) {
                notes.add(getReleaseNote(reference));
            }
        }

        return notes;
    }

    @Override
    public List<String> getAggregatedVersions(DocumentReference noteReference)
    {
        String shortVersion = noteReference.getLastSpaceReference().getName();
        int position = shortVersion.indexOf(ReleaseNotesReferences.MILESTONE_LETTER);

        if (position > -1) {
            return List.of(shortVersion.substring(0, position) + "-milestone-"
                + shortVersion.substring(position + ReleaseNotesReferences.MILESTONE_LETTER.length()));
        }

        position = shortVersion.indexOf(ReleaseNotesReferences.RELEASE_CANDIDATE_LETTERS);

        if (position > -1) {
            return List.of(shortVersion.substring(0, position) + "-rc-"
                + shortVersion.substring(position + ReleaseNotesReferences.RELEASE_CANDIDATE_LETTERS.length()));
        }

        // A final version also displays the changes of its milestones and of its release candidates, which are
        // matched by pattern since their numbers are not known here.
        return List.of(shortVersion, shortVersion + "-milestone%", shortVersion + "-rc%");
    }

    /**
     * Writes the two properties of a release note that change over its life: the day the version is released, and
     * whether it has been.
     *
     * @param object the release note object to write them to
     * @param note the release note holding them
     * @param xcontext the context to write them with
     * @throws XWikiException when they could not be written
     */
    private void setReleaseState(BaseObject object, ReleaseNote note, XWikiContext xcontext) throws XWikiException
    {
        object.set(RELEASED, note.isReleased() ? "1" : "0", xcontext);

        if (note.getDate() == null) {
            // An empty release date, and not no release date at all: the Live Data listing the release notes sorts
            // them on their date and leaves out the ones that have no value for it.
            object.set(DATE, "", xcontext);
        } else {
            object.set(DATE, note.getDate(), xcontext);
        }
    }

    /**
     * @param object the release note object of a page
     * @return the release note that object holds
     */
    private ReleaseNote toReleaseNote(BaseObject object)
    {
        ReleaseNote note = new ReleaseNote();
        note.setProduct(object.getStringValue(PRODUCT));
        note.setVersion(object.getStringValue(VERSION));
        note.setDate(object.getDateValue(DATE));
        note.setReleased(object.getIntValue(RELEASED) == 1);

        return note;
    }

    /**
     * @param product the product the release note is about
     * @param version the version the release note is about
     * @return the title to give that release note, in the language of the user creating it
     */
    private String getTitle(String product, String version)
    {
        String title = this.localization.getTranslationPlain(TITLE_KEY, product, version);

        // The bundle holding that translation is a page of the application, so a wiki running the jar alone has no
        // value for it, and a release note is still better off with a title than with none.
        return title != null ? title : String.format("Release Notes for %s %s", product, version);
    }

    /**
     * Copies into the new release note what its template holds: the content, and the rights that content needs,
     * since content copied without them would not execute. The title is not copied but built, by
     * {@link #getTitle(String, String)}: a release note that took a title written in Velocity would need the script
     * right to display its own title.
     */
    private void applyTemplate(XWikiDocument document, DocumentReference templateReference, XWikiContext xcontext)
        throws XWikiException, ReleaseNotesException
    {
        if (templateReference == null) {
            return;
        }

        XWikiDocument template = this.documentStore.load(templateReference);

        if (template.isNew()) {
            throw new ReleaseNotesException(
                String.format("The release note template [%s] does not exist.", templateReference));
        }

        document.setContent(template.getContent());
        document.setSyntax(template.getSyntax());

        for (BaseObject requiredRight : template.getXObjects(ReleaseNotesReferences.REQUIRED_RIGHT_CLASS)) {
            if (requiredRight != null) {
                document.newXObject(ReleaseNotesReferences.REQUIRED_RIGHT_CLASS, xcontext)
                    .set(LEVEL, requiredRight.getStringValue(LEVEL), xcontext);
            }
        }

        document.setEnforceRequiredRights(template.isEnforceRequiredRights());
    }
}

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

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.Audience;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeQuery;
import org.xwiki.contrib.releasenotes.ChangeSearchResult;
import org.xwiki.contrib.releasenotes.ChangeType;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.stability.Unstable;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Default implementation of {@link ChangeManager}, which holds the changes of a release note in the {@code Entry###}
 * pages under it.
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Singleton
@Unstable
public class DefaultChangeManager implements ChangeManager
{
    /**
     * The property of an entry holding whether it is a change, a migration note or the contributors of the release
     * note.
     */
    private static final String TYPE = "type";

    /**
     * The media of a change are stored as one comma-separated value, so a media name holding a comma cannot be
     * stored.
     */
    private static final String SCREENSHOT_SEPARATOR = ",";

    private static final String PRODUCT = "product";

    private static final String VERSION = "version";

    private static final String TITLE = "title";

    private static final String SUMMARY = "summary";

    private static final String DESCRIPTION = "description";

    private static final String AUDIENCE = "audience";

    private static final String IMPORTANCE = "importance";

    private static final String CATEGORY = "category";

    private static final String SCREENSHOTS = "screenshots";

    /**
     * What a caller is told about a page it asked to read or to replace the change of, and that holds none.
     */
    private static final String NO_CHANGE = "The page [%s] holds no change.";

    /**
     * What a change with no title is refused with, both when it is created and when it is replaced: a change with no
     * title is displayed as an empty line by every displayer, which makes it look like the change is missing rather
     * than like its title is.
     */
    private static final String NO_TITLE = "A change needs a title.";

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    @Inject
    private ProductResolver productResolver;

    @Inject
    private ReleaseNotesDocumentWriter documentWriter;

    @Inject
    private EntryPageAllocator entryPageAllocator;

    @Inject
    private ChangeSearcher changeSearcher;

    @Override
    public DocumentReference createChange(Change change) throws ReleaseNotesException
    {
        String version = StringUtils.trimToNull(change.getVersion());

        if (version == null) {
            throw new ReleaseNotesException("A change needs the version it was made in.");
        }

        String title = StringUtils.trimToNull(change.getTitle());

        if (title == null) {
            throw new ReleaseNotesException(NO_TITLE);
        }

        String product = this.productResolver.resolve(change.getProduct());
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document = this.entryPageAllocator.takeNextEntryPage(product, version, xcontext);

        if (document == null) {
            throw new ReleaseNotesException(
                String.format("No page was free for a new change of the version [%s] of [%s].", version, product));
        }

        try {
            // The objects of a change are created by the change template, and not here, so that a template an
            // administrator has customised is what a change is made of, whichever way it was created.
            DocumentReference templateReference = new DocumentReference(ReleaseNotesReferences.CHANGE_TEMPLATE,
                document.getDocumentReference().getWikiReference());
            document.readFromTemplate(templateReference, xcontext);
            // A change enforces its required rights when its template does. That is set here because applying a
            // template does not carry the setting over on every XWiki version the application supports.
            document.setEnforceRequiredRights(
                loadDocument(templateReference, xcontext).isEnforceRequiredRights());

            BaseObject entry = document.getXObject(ReleaseNotesReferences.ENTRY_CLASS, true, xcontext);
            entry.set(PRODUCT, product, xcontext);
            entry.set(VERSION, version, xcontext);
            entry.set(TYPE, getStoredType(change), xcontext);

            BaseObject changeObject = document.getXObject(ReleaseNotesReferences.CHANGE_CLASS, true, xcontext);
            changeObject.set(TITLE, title, xcontext);
            fillNewChange(changeObject, change, xcontext);
        } catch (XWikiException e) {
            throw new ReleaseNotesException(
                String.format("Failed to fill the page [%s] of the new change.", document.getDocumentReference()), e);
        }

        this.documentWriter.save(document, "New change");

        return document.getDocumentReference();
    }

    /**
     * Writes the values of a new change on top of the ones its template gave it. Only the values the change carries
     * are set, so that the ones it leaves out keep the default the template gives them, which is what a template is
     * for.
     */
    private void fillNewChange(BaseObject changeObject, Change change, XWikiContext xcontext) throws XWikiException
    {
        setIfNotNull(changeObject, SUMMARY, change.getSummary(), xcontext);
        setIfNotNull(changeObject, DESCRIPTION, change.getDescription(), xcontext);
        setIfNotNull(changeObject, CATEGORY, change.getCategory(), xcontext);

        if (change.getAudience() != null) {
            changeObject.set(AUDIENCE, change.getAudience().getStoredValue(), xcontext);
        }

        if (change.getImportance() != null) {
            changeObject.set(IMPORTANCE, change.getImportance().getStoredValue(), xcontext);
        }

        if (change.getScreenshots() != null) {
            changeObject.set(SCREENSHOTS, String.join(SCREENSHOT_SEPARATOR, change.getScreenshots()), xcontext);
        }
    }

    @Override
    public Change updateChange(DocumentReference reference, Change change) throws ReleaseNotesException
    {
        String title = StringUtils.trimToNull(change.getTitle());

        if (title == null) {
            throw new ReleaseNotesException(NO_TITLE);
        }

        XWikiContext xcontext = this.xcontextProvider.get();
        // The page exists, and the instance the store answers with for a page that exists is the one it holds in its
        // cache, which a caller must not write into: what is modified here is a copy of it, and the save is what
        // makes the wiki hold it.
        XWikiDocument document = loadDocument(reference, xcontext).clone();
        BaseObject entry = document.getXObject(ReleaseNotesReferences.ENTRY_CLASS);
        BaseObject changeObject = document.getXObject(ReleaseNotesReferences.CHANGE_CLASS);

        if (entry == null || changeObject == null) {
            throw new ReleaseNotesNotFoundException(String.format(NO_CHANGE, reference), reference);
        }

        try {
            // Every property is written, and not only the ones the passed change carries: this replaces the change,
            // so what the caller left out is emptied rather than kept. The change template has no say here either,
            // for the same reason.
            changeObject.set(TITLE, title, xcontext);
            changeObject.set(SUMMARY, StringUtils.defaultString(change.getSummary()), xcontext);
            changeObject.set(DESCRIPTION, StringUtils.defaultString(change.getDescription()), xcontext);
            changeObject.set(CATEGORY, StringUtils.defaultString(change.getCategory()), xcontext);
            changeObject.set(AUDIENCE,
                change.getAudience() == null ? "" : change.getAudience().getStoredValue(), xcontext);
            changeObject.set(IMPORTANCE,
                change.getImportance() == null ? "" : change.getImportance().getStoredValue(), xcontext);
            changeObject.set(SCREENSHOTS, change.getScreenshots() == null ? ""
                : String.join(SCREENSHOT_SEPARATOR, change.getScreenshots()), xcontext);
            // A type left out is the one of a plain change rather than emptied, since an entry holding no type is
            // neither a change nor a migration note.
            entry.set(TYPE, getStoredType(change), xcontext);
        } catch (XWikiException e) {
            throw new ReleaseNotesException(
                String.format("Failed to write the change of the page [%s].", reference), e);
        }

        this.documentWriter.save(document, "Updated change");

        return toChange(entry, changeObject);
    }

    @Override
    public DocumentReference reserveNextEntry(String product, String version) throws ReleaseNotesException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document = this.entryPageAllocator
            .takeNextEntryPage(this.productResolver.resolve(product), version, xcontext);

        if (document == null) {
            return null;
        }

        this.documentWriter.save(document, "Take the page of a new release note entry");

        return document.getDocumentReference();
    }

    @Override
    public Change getChange(DocumentReference reference) throws ReleaseNotesException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        XWikiDocument document = loadDocument(reference, xcontext);
        BaseObject entry = document.getXObject(ReleaseNotesReferences.ENTRY_CLASS);
        BaseObject changeObject = document.getXObject(ReleaseNotesReferences.CHANGE_CLASS);

        if (entry == null || changeObject == null) {
            throw new ReleaseNotesNotFoundException(String.format(NO_CHANGE, reference), reference);
        }

        return toChange(entry, changeObject);
    }

    @Override
    public ChangeSearchResult search(ChangeQuery query) throws ReleaseNotesException
    {
        return this.changeSearcher.search(query);
    }

    /**
     * @param entry the entry object of the page of a change, which says which release note it belongs to
     * @param changeObject the change object of that page, which says what the change is
     * @return the change those two objects hold
     */
    private Change toChange(BaseObject entry, BaseObject changeObject)
    {
        Change change = new Change();
        change.setProduct(entry.getStringValue(PRODUCT));
        change.setVersion(entry.getStringValue(VERSION));
        change.setTitle(changeObject.getStringValue(TITLE));
        change.setSummary(changeObject.getLargeStringValue(SUMMARY));
        change.setDescription(changeObject.getLargeStringValue(DESCRIPTION));
        change.setType(ChangeType.fromStoredValue(entry.getStringValue(TYPE)));
        change.setAudience(Audience.fromStoredValue(changeObject.getStringValue(AUDIENCE)));
        change.setImportance(Importance.fromStoredValue(changeObject.getStringValue(IMPORTANCE)));
        change.setCategory(changeObject.getStringValue(CATEGORY));
        change.setScreenshots(splitScreenshots(changeObject.getStringValue(SCREENSHOTS)));

        return change;
    }

    /**
     * @return the value the entry of the passed change holds as its type, which is the one of a plain change when the
     *         change does not say, since that is what a change was before it could be a migration note
     */
    private static String getStoredType(Change change)
    {
        return change.getType() == null ? ChangeType.CHANGE.getStoredValue() : change.getType().getStoredValue();
    }

    private List<String> splitScreenshots(String screenshots)
    {
        List<String> mediaNames = new ArrayList<>();

        for (String mediaName : StringUtils.split(StringUtils.defaultString(screenshots), SCREENSHOT_SEPARATOR)) {
            if (StringUtils.isNotBlank(mediaName)) {
                mediaNames.add(mediaName.trim());
            }
        }

        return mediaNames;
    }

    private void setIfNotNull(BaseObject object, String property, String value, XWikiContext xcontext)
        throws XWikiException
    {
        if (value != null) {
            object.set(property, value, xcontext);
        }
    }

    private XWikiDocument loadDocument(DocumentReference reference, XWikiContext xcontext)
        throws ReleaseNotesException
    {
        try {
            return xcontext.getWiki().getDocument(reference, xcontext);
        } catch (XWikiException e) {
            throw new ReleaseNotesException(String.format("Failed to load the page [%s].", reference), e);
        }
    }
}

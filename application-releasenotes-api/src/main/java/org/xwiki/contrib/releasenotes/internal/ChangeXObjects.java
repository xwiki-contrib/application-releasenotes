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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.contrib.releasenotes.Audience;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.contrib.releasenotes.ReleaseNotesNotFoundException;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * The one place that knows how a {@link Change} is stored: the two objects the page of a change carries, an
 * {@code EntryClass} object saying which release note the change belongs to and a {@code ChangeClass} object saying
 * what the change is, and the names of their properties, which the queries looking for changes use too.
 *
 * @version $Id$
 * @since 2.8
 */
public final class ChangeXObjects
{
    /**
     * The property of the entry object holding the product of the release note the entry belongs to.
     */
    public static final String PRODUCT = "product";

    /**
     * The property of the entry object holding the version of the release note the entry belongs to.
     */
    public static final String VERSION = "version";

    /**
     * The property of the entry object saying whether the entry is a change or the contributors of the release note.
     */
    public static final String TYPE = "type";

    /**
     * The value the {@link #TYPE} property of an entry holds when that entry is a change, and not the contributors of
     * the release note.
     */
    public static final String CHANGE_TYPE = "Change";

    /**
     * The property of the change object holding its title.
     */
    public static final String TITLE = "title";

    /**
     * The property of the change object holding its summary.
     */
    public static final String SUMMARY = "summary";

    /**
     * The property of the change object holding its description.
     */
    public static final String DESCRIPTION = "description";

    /**
     * The property of the change object holding who it is for, as {@link Audience#getStoredValue()}.
     */
    public static final String AUDIENCE = "audience";

    /**
     * The property of the change object holding how much it matters, as {@link Importance#getStoredValue()}.
     */
    public static final String IMPORTANCE = "importance";

    /**
     * The property of the change object holding its category.
     */
    public static final String CATEGORY = "category";

    /**
     * The property of the change object holding the names of its media, as one comma-separated value.
     */
    public static final String SCREENSHOTS = "screenshots";

    /**
     * The media of a change are stored as one comma-separated value, so a media name holding a comma cannot be
     * stored.
     */
    private static final String SCREENSHOT_SEPARATOR = ",";

    /**
     * How the values of a change are written into the change object of its page.
     */
    public enum WriteMode
    {
        /**
         * The object is created when the page does not carry it yet, and only the values the change carries are
         * written, so that the ones it leaves out keep the default the change template gave them, which is what a
         * template is for.
         */
        OVER_TEMPLATE,

        /**
         * Both objects of a change must exist, and every value is written, so that what the change leaves out is
         * emptied rather than kept: the change replaces the one the page held, and the template has no say.
         */
        REPLACE
    }

    private ChangeXObjects()
    {
        // Utility class, and thus no public constructor.
    }

    /**
     * @param document the page of a change
     * @return the change that page holds
     * @throws ReleaseNotesNotFoundException when the page does not carry both objects of a change
     */
    public static Change read(XWikiDocument document) throws ReleaseNotesNotFoundException
    {
        BaseObject entry = getEntryObject(document);
        BaseObject changeObject = getChangeObject(document);

        Change change = new Change();
        change.setProduct(entry.getStringValue(PRODUCT));
        change.setVersion(entry.getStringValue(VERSION));
        change.setTitle(changeObject.getStringValue(TITLE));
        change.setSummary(changeObject.getLargeStringValue(SUMMARY));
        change.setDescription(changeObject.getLargeStringValue(DESCRIPTION));
        change.setAudience(Audience.fromStoredValue(changeObject.getStringValue(AUDIENCE)));
        change.setImportance(Importance.fromStoredValue(changeObject.getStringValue(IMPORTANCE)));
        change.setCategory(changeObject.getStringValue(CATEGORY));
        change.setScreenshots(splitScreenshots(changeObject.getStringValue(SCREENSHOTS)));

        return change;
    }

    /**
     * Makes the passed page an entry of the passed release note that is a change, creating its entry object when it
     * carries none.
     *
     * @param document the page of the change
     * @param product the product of the release note the change belongs to
     * @param version the version of the release note the change belongs to
     * @param xcontext the context the object is created in
     * @throws XWikiException when the entry object could not be created
     */
    public static void writeEntry(XWikiDocument document, String product, String version, XWikiContext xcontext)
        throws XWikiException
    {
        BaseObject entry = document.getXObject(ReleaseNotesReferences.ENTRY_CLASS, true, xcontext);
        entry.set(PRODUCT, product, xcontext);
        entry.set(VERSION, version, xcontext);
        entry.set(TYPE, CHANGE_TYPE, xcontext);
    }

    /**
     * Writes what the passed change says into the change object of the passed page. The title is written trimmed;
     * checking that there is one is up to the caller.
     *
     * @param document the page of the change
     * @param change the change to write
     * @param mode whether the values the change leaves out are kept or emptied
     * @param xcontext the context the values are written in
     * @throws XWikiException when a value could not be written
     * @throws ReleaseNotesNotFoundException when a change is replaced on a page that does not carry both objects of
     *             a change
     */
    public static void writeChange(XWikiDocument document, Change change, WriteMode mode, XWikiContext xcontext)
        throws XWikiException, ReleaseNotesNotFoundException
    {
        BaseObject changeObject;

        if (mode == WriteMode.REPLACE) {
            getEntryObject(document);
            changeObject = getChangeObject(document);
        } else {
            changeObject = document.getXObject(ReleaseNotesReferences.CHANGE_CLASS, true, xcontext);
        }

        Audience audience = change.getAudience();
        Importance importance = change.getImportance();
        List<String> screenshots = change.getScreenshots();

        changeObject.set(TITLE, StringUtils.trim(change.getTitle()), xcontext);
        write(changeObject, SUMMARY, change.getSummary(), mode, xcontext);
        write(changeObject, DESCRIPTION, change.getDescription(), mode, xcontext);
        write(changeObject, CATEGORY, change.getCategory(), mode, xcontext);
        write(changeObject, AUDIENCE, audience == null ? null : audience.getStoredValue(), mode, xcontext);
        write(changeObject, IMPORTANCE, importance == null ? null : importance.getStoredValue(), mode, xcontext);
        write(changeObject, SCREENSHOTS,
            screenshots == null ? null : String.join(SCREENSHOT_SEPARATOR, screenshots), mode, xcontext);
    }

    private static void write(BaseObject object, String property, String value, WriteMode mode,
        XWikiContext xcontext) throws XWikiException
    {
        if (value != null) {
            object.set(property, value, xcontext);
        } else if (mode == WriteMode.REPLACE) {
            object.set(property, "", xcontext);
        }
    }

    private static BaseObject getEntryObject(XWikiDocument document) throws ReleaseNotesNotFoundException
    {
        return require(document.getXObject(ReleaseNotesReferences.ENTRY_CLASS), document);
    }

    private static BaseObject getChangeObject(XWikiDocument document) throws ReleaseNotesNotFoundException
    {
        return require(document.getXObject(ReleaseNotesReferences.CHANGE_CLASS), document);
    }

    private static BaseObject require(BaseObject object, XWikiDocument document) throws ReleaseNotesNotFoundException
    {
        if (object == null) {
            throw new ReleaseNotesNotFoundException(
                String.format("The page [%s] holds no change.", document.getDocumentReference()),
                document.getDocumentReference());
        }

        return object;
    }

    private static List<String> splitScreenshots(String screenshots)
    {
        return Stream.of(StringUtils.split(StringUtils.defaultString(screenshots), SCREENSHOT_SEPARATOR))
            .filter(StringUtils::isNotBlank).map(String::trim).collect(Collectors.toCollection(ArrayList::new));
    }
}

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

import java.util.Date;

import org.xwiki.contrib.releasenotes.QAValidation;
import org.xwiki.contrib.releasenotes.ReleaseNote;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.objects.BaseObject;

/**
 * The one place that knows how a {@link ReleaseNote} is stored: the properties of the {@code ReleaseNoteClass} object
 * its page carries.
 *
 * @version $Id$
 * @since 2.9
 */
public final class ReleaseNoteXObjects
{
    private static final String PRODUCT = "product";

    private static final String VERSION = "version";

    private static final String DATE = "date";

    private static final String RELEASED = "released";

    private static final String QA_VALIDATION = "qaValidation";

    private static final String QA_VALIDATION_DATE = "qaValidationDate";

    private ReleaseNoteXObjects()
    {
        // Utility class.
    }

    /**
     * @param object the release note object of a page
     * @return the release note that object holds
     */
    public static ReleaseNote read(BaseObject object)
    {
        ReleaseNote note = new ReleaseNote();
        note.setProduct(object.getStringValue(PRODUCT));
        note.setVersion(object.getStringValue(VERSION));
        note.setDate(object.getDateValue(DATE));
        note.setReleased(object.getIntValue(RELEASED) == 1);
        // A release note created before the validation existed holds no value for it, which reads as not tested.
        note.setQaValidation(QAValidation.fromValue(object.getStringValue(QA_VALIDATION)));
        note.setQaValidationDate(object.getDateValue(QA_VALIDATION_DATE));

        return note;
    }

    /**
     * Writes the properties of a release note that change over its life: the day the version is released, whether it
     * has been, and whether Manual QA has validated it and on which day. All of them are written, and not only the
     * ones the release note carries, so a value it leaves out is emptied.
     *
     * @param object the release note object to write them to
     * @param note the release note holding them
     * @param xcontext the context to write them with
     * @throws XWikiException when they could not be written
     */
    public static void writeState(BaseObject object, ReleaseNote note, XWikiContext xcontext) throws XWikiException
    {
        object.set(RELEASED, note.isReleased() ? "1" : "0", xcontext);
        setDate(object, DATE, note.getDate(), xcontext);
        object.set(QA_VALIDATION, note.getQaValidation().getStoredValue(), xcontext);
        setDate(object, QA_VALIDATION_DATE, note.getQaValidationDate(), xcontext);
    }

    /**
     * Writes a date, and an empty value rather than no value at all when there is none: the Live Data listing the
     * release notes sorts them on their release date and leaves out the ones that have no value for it. The Manual QA
     * validation date is written the same way, so that both dates of a release note are stored alike.
     */
    private static void setDate(BaseObject object, String property, Date date, XWikiContext xcontext)
        throws XWikiException
    {
        if (date == null) {
            object.set(property, "", xcontext);
        } else {
            object.set(property, date, xcontext);
        }
    }
}

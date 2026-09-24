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

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;

import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.test.MockitoOldcore;

/**
 * Installs, in the wiki of a test, the classes the release notes and the changes are stored in.
 * <p>
 * They are needed as classes and not only as names: a value set on an xobject whose class does not declare the
 * property is silently dropped, so a test writing against classes that do not exist would assert on nothing.
 *
 * @version $Id$
 */
final class ReleaseNotesXClasses
{
    private ReleaseNotesXClasses()
    {
        // Utility class, and thus no public constructor.
    }

    /**
     * @param oldcore the wiki of the test to install the classes in
     */
    static void install(MockitoOldcore oldcore) throws Exception
    {
        BaseClass releaseNoteClass = createClass(oldcore, ReleaseNotesReferences.RELEASE_NOTE_CLASS);
        releaseNoteClass.addTextField("product", "Product", 30);
        releaseNoteClass.addTextField("version", "Version", 30);
        // The date is declared not to default to today, as the shipped class does, so that the empty value a
        // release note is created with stays empty.
        releaseNoteClass.addDateField("date", "Release Date", "dd/MM/yyyy", 0);
        releaseNoteClass.addBooleanField("released", "Released", "yesno");
        saveClass(oldcore, releaseNoteClass);

        BaseClass entryClass = createClass(oldcore, ReleaseNotesReferences.ENTRY_CLASS);
        entryClass.addDBListField("product", "Product", "");
        entryClass.addDBListField("version", "Version", "");
        entryClass.addStaticListField("type", "Type", "Change|Contributors|Migration");
        saveClass(oldcore, entryClass);

        BaseClass changeClass = createClass(oldcore, ReleaseNotesReferences.CHANGE_CLASS);
        changeClass.addTextField("title", "Title", 80);
        changeClass.addTextAreaField("summary", "Summary", 80, 5);
        changeClass.addTextAreaField("description", "Description", 80, 10);
        changeClass.addStaticListField("audience", "Target Audience",
            "user=User|administrator=Administrator|developer=Developer");
        changeClass.addStaticListField("importance", "Importance", "2=High|1=Medium|0=Low");
        changeClass.addDBListField("category", "Category", "");
        changeClass.addTextField("screenshots", "Screenshots", 80);
        saveClass(oldcore, changeClass);

        BaseClass configurationClass = createClass(oldcore, ReleaseNotesReferences.CONFIGURATION_CLASS);
        configurationClass.addTextField("product", "Product", 30);
        configurationClass.addTextField("template", "Template", 60);
        saveClass(oldcore, configurationClass);

        BaseClass requiredRightClass = createClass(oldcore, ReleaseNotesReferences.REQUIRED_RIGHT_CLASS);
        requiredRightClass.addStaticListField("level", "Level", "edit|script|wiki_admin|programming");
        saveClass(oldcore, requiredRightClass);
    }

    private static BaseClass createClass(MockitoOldcore oldcore, LocalDocumentReference reference)
    {
        XWikiDocument document = new XWikiDocument(reference(oldcore, reference));
        BaseClass xclass = document.getXClass();
        xclass.setOwnerDocument(document);

        return xclass;
    }

    private static void saveClass(MockitoOldcore oldcore, BaseClass xclass) throws Exception
    {
        oldcore.getSpyXWiki().saveDocument(xclass.getOwnerDocument(), oldcore.getXWikiContext());
    }

    /**
     * @param oldcore the wiki of the test
     * @param reference a page of the application
     * @return that page in the wiki of the test
     */
    static DocumentReference reference(MockitoOldcore oldcore, LocalDocumentReference reference)
    {
        return new DocumentReference(reference,
            new WikiReference(oldcore.getXWikiContext().getWikiId()));
    }
}

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
package org.xwiki.contrib.releasenotes.test.ui;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.xwiki.contrib.releasenotes.rest.model.ChangeRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ChangesRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ErrorRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNoteRepresentation;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.ObjectPropertyReference;
import org.xwiki.model.reference.ObjectReference;
import org.xwiki.rest.model.jaxb.Page;
import org.xwiki.rest.model.jaxb.Property;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests for the REST endpoints of the Release Notes Application.
 *
 * @version $Id$
 */
@UITest
class ReleaseNotesRestIT
{
    private static final String PRODUCT = "RestProduct";

    private static final String VERSION = "1.0-milestone-1";

    private static final DocumentReference RELEASE_NOTE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", PRODUCT, "1.0M1"), "WebHome");

    private static final DocumentReference FIRST_CHANGE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", PRODUCT, "1.0M1", "Entry001"), "WebHome");

    private static final DocumentReference THIRD_CHANGE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", PRODUCT, "1.0M1", "Entry003"), "WebHome");

    /**
     * The release notes a test replaces are those of a product of its own, so that the entries the other test
     * numbers are not the entries this one replaces.
     */
    private static final String UPDATE_PRODUCT = "RestUpdateProduct";

    private static final DocumentReference UPDATED_RELEASE_NOTE =
        new DocumentReference("xwiki", List.of("ReleaseNotes", "Data", UPDATE_PRODUCT, "1.0M1"), "WebHome");

    private static final DocumentReference UPDATED_CHANGE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", UPDATE_PRODUCT, "1.0M1", "Entry001"), "WebHome");

    private static final DocumentReference MIGRATION_NOTE = new DocumentReference("xwiki",
        List.of("ReleaseNotes", "Data", UPDATE_PRODUCT, "1.0M1", "Entry002"), "WebHome");

    /**
     * Walks what the endpoints exist for: one call creates the release note of a version, in the page its version
     * says, and one call per change fills it, each change page carrying both of the objects that make an entry
     * visible to the application. Then the release note is listed back, its changes are read back, and creating it
     * again is refused with the page it already lives in.
     */
    @Test
    void createAndListReleaseNotesAndChanges(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        setup.rest().delete(FIRST_CHANGE);
        setup.rest().delete(new DocumentReference("xwiki",
            List.of("ReleaseNotes", "Data", PRODUCT, "1.0M1", "Entry002"), "WebHome"));
        // An entry left behind would be counted when the next one is numbered, so every page this test creates is
        // deleted before it runs again.
        setup.rest().delete(THIRD_CHANGE);
        setup.rest().delete(RELEASE_NOTE);

        ReleaseNotesRestClient client = new ReleaseNotesRestClient(setup);

        // Creating the release note: the page it lands in is named after its product and its short version, which is
        // the naming the release note pages of the application are found and aggregated by.
        ReleaseNoteRepresentation posted = new ReleaseNoteRepresentation();
        posted.setProduct(PRODUCT);
        posted.setVersion(VERSION);
        posted.setDate("2026-09-09");

        JsonResponse created = client.post("/releasenotes", posted);

        assertEquals(201, created.getStatus(), created.getBody());
        assertTrue(created.getLocation()
            .endsWith("/wikis/xwiki/spaces/ReleaseNotes/spaces/Data/spaces/RestProduct/spaces/1.0M1/pages/WebHome"),
            "Expected the created release note to be pointed at through the page resource, got: "
                + created.getLocation());
        assertEquals("ReleaseNotes.Data.RestProduct.1\\.0M1.WebHome",
            created.as(ReleaseNoteRepresentation.class).getReference());

        // The release note is a release note of the wiki: it carries the class the application locates them by, and
        // the content of the template, which is what makes the page show its changes.
        assertEquals(VERSION, propertyValue(setup, RELEASE_NOTE, "ReleaseNotes.Code.ReleaseNoteClass", "version"));
        assertEquals(PRODUCT, propertyValue(setup, RELEASE_NOTE, "ReleaseNotes.Code.ReleaseNoteClass", "product"));
        assertTrue(setup.rest().<Page>get(RELEASE_NOTE).getContent().contains("releasenotechanges"),
            "Expected the release note to be created from the template.");

        // Creating a change: one call, and the page of the new entry carries both the entry class that locates it
        // and the change class that holds what it says. An entry without the first is invisible to every query the
        // application runs, which is the trap these endpoints exist to avoid.
        ChangeRepresentation change = new ChangeRepresentation();
        change.setTitle("The first change");
        change.setSummary("What it does.");
        change.setAudience("user");
        change.setImportance("high");
        change.setCategory("Performance");

        JsonResponse firstChange = client.post(changesPath(), change);

        assertEquals(201, firstChange.getStatus(), firstChange.getBody());
        assertEquals("ReleaseNotes.Data.RestProduct.1\\.0M1.Entry001.WebHome",
            firstChange.as(ChangeRepresentation.class).getReference());
        assertEquals("Change", propertyValue(setup, FIRST_CHANGE, "ReleaseNotes.Code.EntryClass", "type"));
        assertEquals(VERSION, propertyValue(setup, FIRST_CHANGE, "ReleaseNotes.Code.EntryClass", "version"));
        assertEquals("The first change",
            propertyValue(setup, FIRST_CHANGE, "ReleaseNotes.Code.Change.ChangeClass", "title"));
        assertEquals("2", propertyValue(setup, FIRST_CHANGE, "ReleaseNotes.Code.Change.ChangeClass", "importance"));

        // A second change: the page of the new entry is numbered after the ones that already exist, which is the
        // other reason a client does not create these pages itself.
        change.setTitle("The second change");
        change.setImportance("low");

        JsonResponse secondChange = client.post(changesPath(), change);

        assertEquals(201, secondChange.getStatus(), secondChange.getBody());
        assertEquals("ReleaseNotes.Data.RestProduct.1\\.0M1.Entry002.WebHome",
            secondChange.as(ChangeRepresentation.class).getReference());

        // Reading the changes back, which is how a client that may be re-running finds out what it already added.
        // The most important ones come first.
        ChangesRepresentation changes =
            client.get(changesPath()).as(ChangesRepresentation.class);

        assertEquals(2, changes.getChanges().size(), "Expected both changes to be listed.");
        assertEquals("The first change", changes.getChanges().get(0).getTitle());
        assertEquals("high", changes.getChanges().get(0).getImportance());
        assertEquals("user", changes.getChanges().get(0).getAudience());
        assertEquals("Performance", changes.getChanges().get(0).getCategory());
        assertEquals("The second change", changes.getChanges().get(1).getTitle());

        // Filtering is the same language as the macro the pages of the application use.
        ChangesRepresentation important =
            client.get(changesPath() + "?importance=high").as(ChangesRepresentation.class);

        assertEquals(1, important.getChanges().size(), "Expected only the important change to be listed.");
        assertEquals("The first change", important.getChanges().get(0).getTitle());

        // Listing the release notes of the product, each with the page it lives in.
        JsonResponse listed = client.get("/releasenotes?product=" + PRODUCT);

        assertEquals(200, listed.getStatus(), listed.getBody());
        assertTrue(listed.getBody().contains("ReleaseNotes.Data.RestProduct.1\\\\.0M1.WebHome"),
            "Expected the release note to be listed with the page it lives in, got: " + listed.getBody());
        assertTrue(listed.getBody().contains("2026-09-09"),
            "Expected the release note to be listed with its release date, got: " + listed.getBody());

        // Creating it again is refused with the page it already lives in, rather than silently answered with a
        // release note the client did not create: that is what tells a client at its first call that it has run
        // before.
        JsonResponse conflict = client.post("/releasenotes", posted);

        assertEquals(409, conflict.getStatus(), conflict.getBody());
        assertEquals("ReleaseNotes.Data.RestProduct.1\\.0M1.WebHome",
            conflict.as(ErrorRepresentation.class).getReference());

        // A change is answered as it was stored and not as it was posted: creation is template-driven, so the
        // importance this one leaves out is the one the change template gives it, and a client that recorded what it
        // posted would hold no importance at all.
        ChangeRepresentation withoutImportance = new ChangeRepresentation();
        withoutImportance.setTitle("A change with no importance");

        JsonResponse defaulted = client.post(changesPath(), withoutImportance);

        assertEquals(201, defaulted.getStatus(), defaulted.getBody());
        assertEquals("medium", defaulted.as(ChangeRepresentation.class).getImportance());
        assertEquals("1", propertyValue(setup, THIRD_CHANGE, "ReleaseNotes.Code.Change.ChangeClass", "importance"));

        // A change posted to a release note that does not exist would land in a page tree no release note gathers.
        JsonResponse notFound = client.post("/releasenotes/" + PRODUCT + "/9.9/changes", change);

        assertEquals(404, notFound.getStatus(), notFound.getBody());
        assertNotNull(notFound.as(ErrorRepresentation.class).getMessage());
    }

    /**
     * Walks what a client does to illustrate a change, which is the one thing a write-once API made impossible: the
     * media of a change name attachments of its own page, and that page does not exist until the change has been
     * created. So the change is created, its image is attached to the page the creation answered, and the change is
     * replaced with the name it now has. Then the version is marked released, which was write-once too.
     */
    @Test
    void aChangeIsIllustratedAndAVersionIsMarkedReleased(TestUtils setup) throws Exception
    {
        setup.loginAsSuperAdmin();
        // An entry left behind would be counted when the next one is numbered, so every page this test creates is
        // deleted before it runs again.
        setup.rest().delete(UPDATED_CHANGE);
        setup.rest().delete(MIGRATION_NOTE);
        setup.rest().delete(UPDATED_RELEASE_NOTE);

        ReleaseNotesRestClient client = new ReleaseNotesRestClient(setup);

        ReleaseNoteRepresentation note = new ReleaseNoteRepresentation();
        note.setProduct(UPDATE_PRODUCT);
        note.setVersion(VERSION);

        assertEquals(201, client.post("/releasenotes", note).getStatus());

        ChangeRepresentation change = new ChangeRepresentation();
        change.setTitle("A change worth a screenshot");
        change.setSummary("What it does.");

        JsonResponse created = client.post(updatePath() + "/changes", change);

        assertEquals(201, created.getStatus(), created.getBody());
        // The entry the creation answers is what addresses the change from now on, so a client never has to work out
        // the name of the page the wiki allocated.
        assertEquals("Entry001", created.as(ChangeRepresentation.class).getEntry());

        // The image can only be attached now: it goes to the page the creation allocated, through the attachment
        // resource of the wiki, since this API stores no attachment of its own.
        setup.rest().attachFile(new AttachmentReference("shot.png", UPDATED_CHANGE),
            getClass().getResourceAsStream("/screenshot.png"), true);

        change.setScreenshots(List.of("shot.png"));

        JsonResponse illustrated = client.put(changePath("Entry001"), change);

        assertEquals(200, illustrated.getStatus(), illustrated.getBody());
        assertEquals(List.of("shot.png"), illustrated.as(ChangeRepresentation.class).getScreenshots());
        assertEquals("shot.png",
            propertyValue(setup, UPDATED_CHANGE, "ReleaseNotes.Code.Change.ChangeClass", "screenshots"));

        // The change is read back at the URL it was replaced at, which is what makes that URL a resource rather than
        // a write-only address.
        ChangeRepresentation read = client.get(changePath("Entry001")).as(ChangeRepresentation.class);

        assertEquals("A change worth a screenshot", read.getTitle());
        assertEquals(List.of("shot.png"), read.getScreenshots());

        assertEquals("change", read.getType(), "A change posted with no type is a plain change.");

        // A migration note is posted as an entry of its own, of the migration type, and each type is listed apart.
        ChangeRepresentation migrationNote = new ChangeRepresentation();
        migrationNote.setType("migration");
        migrationNote.setTitle("Clear the cache before upgrading");
        migrationNote.setAudience("administrator");

        JsonResponse createdNote = client.post(updatePath() + "/changes", migrationNote);

        assertEquals(201, createdNote.getStatus(), createdNote.getBody());
        assertEquals("migration", createdNote.as(ChangeRepresentation.class).getType());
        assertEquals("Migration",
            propertyValue(setup, MIGRATION_NOTE, "ReleaseNotes.Code.EntryClass", "type"));
        assertEquals(List.of("Entry002"), entriesOf(client.get(updatePath() + "/changes?type=migration")));
        assertEquals(List.of("Entry001"), entriesOf(client.get(updatePath() + "/changes?type=change")));

        // The entries of a version that is not released yet are not released entries.
        assertEquals(List.of(), entriesOf(client.get(updatePath() + "/changes?released=true")));

        // A replacement replaces: the summary this one leaves out is emptied rather than kept, which is what a
        // client asking for the whole change to be stored asked for.
        ChangeRepresentation withoutSummary = new ChangeRepresentation();
        withoutSummary.setTitle("A change worth a screenshot");

        assertEquals(200, client.put(changePath("Entry001"), withoutSummary).getStatus());
        assertEquals("", propertyValue(setup, UPDATED_CHANGE, "ReleaseNotes.Code.Change.ChangeClass", "summary"));

        // Marking the version released on the day it ships, which a release note could not be told either once it
        // existed.
        note.setReleased(true);
        note.setDate("2026-09-10");

        JsonResponse released = client.put(updatePath(), note);

        assertEquals(200, released.getStatus(), released.getBody());
        assertEquals("2026-09-10", released.as(ReleaseNoteRepresentation.class).getDate());
        assertEquals(Boolean.TRUE, released.as(ReleaseNoteRepresentation.class).getReleased());
        assertEquals("1", propertyValue(setup, UPDATED_RELEASE_NOTE, "ReleaseNotes.Code.ReleaseNoteClass",
            "released"));

        // Once their version is released, its entries are released entries. They are listed the most important
        // first, and the migration note holds the importance its template gives it while the change had its own
        // emptied by the replacement above.
        assertEquals(List.of("Entry002", "Entry001"), entriesOf(client.get(updatePath() + "/changes?released=true")));
        assertEquals(List.of(), entriesOf(client.get(updatePath() + "/changes?released=false")));

        // The release note is read back at the URL it was replaced at, as the change was.
        ReleaseNoteRepresentation readNote = client.get(updatePath()).as(ReleaseNoteRepresentation.class);

        assertEquals(Boolean.TRUE, readNote.getReleased());
        assertEquals("2026-09-10", readNote.getDate());
        assertEquals("ReleaseNotes.Data.RestUpdateProduct.1\\.0M1.WebHome", readNote.getReference());

        // An entry that holds no change, and a version that has no release note, are answered as what they are:
        // nothing there. A replacement never creates, since the page of a change is the wiki's to allocate.
        JsonResponse noEntry = client.put(changePath("Entry999"), change);

        assertEquals(404, noEntry.getStatus(), noEntry.getBody());
        assertNotNull(noEntry.as(ErrorRepresentation.class).getMessage());
        assertEquals(404, client.put("/releasenotes/" + UPDATE_PRODUCT + "/9.9", note).getStatus());
        assertEquals(404, client.get("/releasenotes/" + UPDATE_PRODUCT + "/9.9").getStatus());
    }

    /**
     * @return the entries of the changes the passed listing answered, in the order it answered them
     */
    private static List<String> entriesOf(JsonResponse listing) throws Exception
    {
        assertEquals(200, listing.getStatus(), listing.getBody());

        return listing.as(ChangesRepresentation.class).getChanges().stream().map(ChangeRepresentation::getEntry)
            .collect(Collectors.toList());
    }

    private static String changesPath()
    {
        return "/releasenotes/" + PRODUCT + "/" + VERSION + "/changes";
    }

    private static String updatePath()
    {
        return "/releasenotes/" + UPDATE_PRODUCT + "/" + VERSION;
    }

    private static String changePath(String entry)
    {
        return updatePath() + "/changes/" + entry;
    }

    private String propertyValue(TestUtils setup, DocumentReference page, String className, String property)
        throws Exception
    {
        Property value = setup.rest()
            .get(new ObjectPropertyReference(property, new ObjectReference(className + "[0]", page)));

        return value.getValue();
    }
}

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.ChangeManager;
import org.xwiki.contrib.releasenotes.ChangeType;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.internal.ProductResolver;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesDocumentWriter;
import org.xwiki.extension.version.internal.DefaultVersion;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Moves the backward compatibility and migration notes written in the content of the release notes of a product, the
 * way the release notes of xwiki.org are written, to migration note entries.
 * <p>
 * A note holds a title and a description, and the release note is rewritten to display its migration note entries
 * where its notes were written. What cannot be moved is left in place and reported, for someone to migrate by hand.
 *
 * @version $Id$
 * @since 2.8
 */
@Component(roles = MigrationNotesImporter.class)
@Singleton
public class MigrationNotesImporter
{
    /**
     * The comment of the save of a rewritten release note, which tells in its history why its content changed.
     */
    static final String SAVE_COMMENT = "Migrated the backward compatibility and migration notes to migration note "
        + "entries";

    @Inject
    private ReleaseNoteManager releaseNoteManager;

    @Inject
    private ChangeManager changeManager;

    @Inject
    private ProductResolver productResolver;

    @Inject
    private MigrationSectionParser sectionParser;

    @Inject
    private ReleaseNotesDocumentWriter documentWriter;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    @Inject
    private Provider<XWikiContext> xcontextProvider;

    /**
     * Tells what the import would do, without doing it.
     *
     * @param product the product to read the release notes of, or {@code null} for the product configured for the
     *            wiki
     * @return what the import would do
     * @throws ReleaseNotesException when the release notes could not be read
     */
    public MigrationNotesImportReport preview(String product) throws ReleaseNotesException
    {
        return analyse(this.productResolver.resolve(product));
    }

    /**
     * Moves the notes to migration note entries and rewrites the release notes holding them. A release note that
     * fails is reported as such, and the others are still migrated.
     *
     * @param product the product to migrate the release notes of, or {@code null} for the product configured for
     *            the wiki
     * @return what the import did
     * @throws ReleaseNotesException when the release notes could not be read
     */
    public MigrationNotesImportReport importNotes(String product) throws ReleaseNotesException
    {
        MigrationNotesImportReport report = analyse(this.productResolver.resolve(product));

        for (ReleaseNoteImport item : report.getReleaseNotes()) {
            if (item.isToRewrite()) {
                try {
                    migrate(report.getProduct(), item);
                } catch (ReleaseNotesException e) {
                    item.setError(e.getMessage());
                }
            }
        }

        return report;
    }

    private MigrationNotesImportReport analyse(String product) throws ReleaseNotesException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        List<ReleaseNoteImport> items = new ArrayList<>();

        for (ReleaseNote note : this.releaseNoteManager.getReleaseNotes(product)) {
            if (StringUtils.isBlank(note.getVersion())) {
                continue;
            }

            DocumentReference reference = this.releaseNoteManager.getReleaseNoteReference(product, note.getVersion());
            ReleaseNoteImport item = new ReleaseNoteImport(reference, note.getVersion());

            try {
                item.setSection(this.sectionParser.parse(loadDocument(reference, xcontext).getContent(),
                    this.localSerializer.serialize(reference)));
            } catch (ReleaseNotesException e) {
                item.setError(e.getMessage());
            }

            items.add(item);
        }

        items.sort(Comparator.comparing(item -> new DefaultVersion(item.getVersion())));
        sortOutDuplicates(items);

        int alreadyMigrated = (int) items.stream().filter(ReleaseNoteImport::isAlreadyMigrated).count();
        List<ReleaseNoteImport> reported = new ArrayList<>();

        for (ReleaseNoteImport item : items) {
            if (item.isReported()) {
                reported.add(item);
            }
        }

        return new MigrationNotesImportReport(product, reported, alreadyMigrated);
    }

    /**
     * Splits the notes of each release note between the ones becoming entries and the duplicates. A final release
     * note displays the entries of its milestones and of its release candidates, so a note it repeats from one of
     * them would be displayed twice: only the earliest of the release notes of a version keeps it. A note repeated in
     * the release note of another version is kept, since it is about another upgrade.
     *
     * @param items the release notes, oldest first
     */
    private void sortOutDuplicates(List<ReleaseNoteImport> items)
    {
        Map<String, Set<String>> titlesByVersion = new HashMap<>();

        for (ReleaseNoteImport item : items) {
            if (item.getSection() == null || item.isAlreadyMigrated()) {
                continue;
            }

            // The milestones and the release candidates of a version are named after it, e.g. 8.3-rc-1 for 8.3.
            Set<String> titles = titlesByVersion.computeIfAbsent(StringUtils.substringBefore(item.getVersion(), '-'),
                version -> new HashSet<>());

            for (MigrationNoteSource note : item.getSection().getNotes()) {
                if (titles.add(note.getTitle().toLowerCase(Locale.ROOT))) {
                    item.getNotes().add(note);
                } else {
                    item.getDuplicates().add(note.getTitle());
                }
            }
        }
    }

    private void migrate(String product, ReleaseNoteImport item) throws ReleaseNotesException
    {
        XWikiContext xcontext = this.xcontextProvider.get();
        // The rights are checked before any entry is created, so that a release note the user cannot rewrite is not
        // left with entries its content does not display.
        this.documentWriter.checkEditRight(item.getReference());

        for (MigrationNoteSource note : item.getNotes()) {
            Change change = new Change();
            change.setProduct(product);
            change.setVersion(item.getVersion());
            change.setType(ChangeType.MIGRATION);
            change.setTitle(note.getTitle());
            change.setDescription(note.getDescription());
            item.getCreatedEntries().add(this.changeManager.createChange(change));
        }

        // The page exists, and the instance the store answers with for a page that exists is the one it holds in its
        // cache, which a caller must not write into.
        XWikiDocument document = loadDocument(item.getReference(), xcontext).clone();
        document.setContent(this.sectionParser.rewrite(document.getContent(), item.getSection()));
        this.documentWriter.save(document, SAVE_COMMENT);
        item.setRewritten(true);
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

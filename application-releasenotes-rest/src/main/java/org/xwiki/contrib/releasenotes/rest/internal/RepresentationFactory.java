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
package org.xwiki.contrib.releasenotes.rest.internal;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.Audience;
import org.xwiki.contrib.releasenotes.Change;
import org.xwiki.contrib.releasenotes.Importance;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.rest.model.ChangeRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNoteRepresentation;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

/**
 * Reads what a client posts into the model of the application, and writes that model back into what a client reads.
 *
 * @version $Id$
 * @since 2.7
 */
@Component(roles = RepresentationFactory.class)
@Singleton
public class RepresentationFactory
{
    /**
     * How a date is written, both ways: the release date of a version is a day, which is the granularity the release
     * notes are listed and sorted by.
     */
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> documentReferenceResolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localEntityReferenceSerializer;

    /**
     * @param representation the release note a client posted
     * @return that release note, as the application models it
     * @throws IllegalArgumentException when the date is not a day, or a day followed by a time
     */
    public ReleaseNote toReleaseNote(ReleaseNoteRepresentation representation)
    {
        ReleaseNote note = new ReleaseNote();
        note.setProduct(representation.getProduct());
        note.setVersion(representation.getVersion());
        note.setDate(toDate(representation.getDate()));
        note.setReleased(Boolean.TRUE.equals(representation.getReleased()));

        if (StringUtils.isNotBlank(representation.getTemplate())) {
            note.setTemplate(this.documentReferenceResolver.resolve(representation.getTemplate()));
        }

        return note;
    }

    /**
     * @param representation the release note a client posted, or {@code null}
     * @param product the product of the release note the URL names, which is what locates it rather than whatever
     *            the posted release note says
     * @param version the version of that release note, in its long form
     * @return that release note, as the application models it
     * @throws IllegalArgumentException when the date is not a day, or a day followed by a time
     */
    public ReleaseNote toReleaseNote(ReleaseNoteRepresentation representation, String product, String version)
    {
        ReleaseNote note = toReleaseNote(representation);
        note.setProduct(product);
        note.setVersion(version);

        return note;
    }

    /**
     * @param note a release note of the wiki
     * @param reference the page it lives in, or {@code null} when it holds no product or no version and therefore
     *            lives nowhere this API can name
     * @return that release note, as a client reads it
     */
    public ReleaseNoteRepresentation toRepresentation(ReleaseNote note, DocumentReference reference)
    {
        ReleaseNoteRepresentation representation = new ReleaseNoteRepresentation();
        representation.setProduct(note.getProduct());
        representation.setVersion(note.getVersion());
        representation.setDate(toDayString(note.getDate()));
        representation.setReleased(note.isReleased());
        representation.setReference(serialize(reference));

        return representation;
    }

    /**
     * @param representation the change a client posted
     * @param product the product of the release note it was posted to, which is what a change is stored against
     *            rather than whatever the posted change says
     * @param version the version of that release note, in its long form
     * @return that change, as the application models it
     * @throws IllegalArgumentException when the audience or the importance is not one of the values it accepts
     */
    public Change toChange(ChangeRepresentation representation, String product, String version)
    {
        Change change = new Change();
        change.setProduct(product);
        change.setVersion(version);
        change.setTitle(representation.getTitle());
        change.setSummary(representation.getSummary());
        change.setDescription(representation.getDescription());
        change.setMigrationNotes(representation.getMigrationNotes());
        change.setAudience(toAudience(representation.getAudience()));
        change.setImportance(toImportance(representation.getImportance()));
        change.setCategory(representation.getCategory());
        change.setScreenshots(representation.getScreenshots());

        return change;
    }

    /**
     * @param change a change of the wiki
     * @param reference the page it lives in
     * @return that change, as a client reads it
     */
    public ChangeRepresentation toRepresentation(Change change, DocumentReference reference)
    {
        ChangeRepresentation representation = new ChangeRepresentation();
        representation.setProduct(change.getProduct());
        representation.setVersion(change.getVersion());
        representation.setTitle(change.getTitle());
        representation.setSummary(change.getSummary());
        representation.setDescription(change.getDescription());
        representation.setMigrationNotes(change.getMigrationNotes());
        representation.setAudience(change.getAudience() == null ? null : change.getAudience().getStoredValue());
        representation.setImportance(change.getImportance() == null ? null : toName(change.getImportance()));
        representation.setCategory(change.getCategory());
        representation.setScreenshots(change.getScreenshots());
        representation.setEntry(reference == null ? null : reference.getLastSpaceReference().getName());
        representation.setReference(serialize(reference));

        return representation;
    }

    /**
     * @param reference a page of the wiki, or {@code null}
     * @return that page, without the name of its wiki since that is part of the URL it was reached by
     */
    private String serialize(DocumentReference reference)
    {
        return reference == null ? null : this.localEntityReferenceSerializer.serialize(reference);
    }

    /**
     * @param date a day, written {@link #DATE_FORMAT}, optionally followed by a time this drops
     * @return that day, at the start of it, or {@code null} when no date was given
     */
    private Date toDate(String date)
    {
        String day = StringUtils.trimToNull(date);

        if (day == null) {
            return null;
        }

        // A client that has a full date and time at hand, such as one reading the date of a build, gets its time of
        // day dropped rather than a refusal.
        if (day.length() > DATE_FORMAT.length()) {
            day = day.substring(0, DATE_FORMAT.length());
        }

        try {
            return Date.from(LocalDate.parse(day).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                String.format("The date [%s] is not a day written %s.", date, DATE_FORMAT), e);
        }
    }

    /**
     * @param date the release date a release note holds
     * @return the day of that date, or {@code null} when the release note holds no date, which is what a release
     *         note whose day is not decided yet holds
     */
    private String toDayString(Date date)
    {
        if (date == null) {
            return null;
        }

        return LocalDate.ofInstant(date.toInstant(), ZoneId.systemDefault()).toString();
    }

    private Audience toAudience(String audience)
    {
        if (StringUtils.isBlank(audience)) {
            return null;
        }

        Audience resolved = Audience.fromStoredValue(audience);

        if (resolved == null) {
            throw new IllegalArgumentException(String.format("The audience [%s] is none of [%s].", audience,
                names(Stream.of(Audience.values()).map(Audience::getStoredValue))));
        }

        return resolved;
    }

    private Importance toImportance(String importance)
    {
        if (StringUtils.isBlank(importance)) {
            return null;
        }

        for (Importance candidate : Importance.values()) {
            if (candidate.name().equalsIgnoreCase(importance)) {
                return candidate;
            }
        }

        // The numbers an importance is stored as are accepted too, so that a client reading a change out of the wiki
        // and posting it to another one does not have to translate them.
        Importance resolved = Importance.fromStoredValue(importance);

        if (resolved == null) {
            throw new IllegalArgumentException(String.format("The importance [%s] is none of [%s].", importance,
                names(Stream.of(Importance.values()).map(RepresentationFactory::toName))));
        }

        return resolved;
    }

    /**
     * @return how an importance is written in a representation, which is the lower cased name of the value and not
     *         the number it is stored as: a client says {@code high}, the database orders on {@code 2}
     */
    private static String toName(Importance importance)
    {
        return importance.name().toLowerCase(Locale.ROOT);
    }

    /**
     * @return the values a property accepts, listed for whoever passed one it does not
     */
    private static String names(Stream<String> values)
    {
        return values.collect(Collectors.joining(", "));
    }
}

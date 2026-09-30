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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.rest.ReleaseNotesResource;
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNoteRepresentation;
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNotesRepresentation;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rest.XWikiRestComponent;

/**
 * Default implementation of {@link ReleaseNotesResource}.
 *
 * @version $Id$
 * @since 2.7
 */
@Component
@Named("org.xwiki.contrib.releasenotes.rest.internal.DefaultReleaseNotesResource")
@Singleton
public class DefaultReleaseNotesResource extends AbstractReleaseNotesResource
    implements ReleaseNotesResource, XWikiRestComponent
{
    @Inject
    private ReleaseNoteManager releaseNoteManager;

    @Inject
    private RepresentationFactory representationFactory;

    @Inject
    private ReleaseNotesConfiguration configuration;

    @Override
    public ReleaseNotesRepresentation getReleaseNotes(String wikiName, String product) throws ReleaseNotesException
    {
        return inWiki(wikiName, () -> {
            ReleaseNotesRepresentation representation = new ReleaseNotesRepresentation();

            for (ReleaseNote note : this.entryPoint.getReleaseNotes(product, getCaller())) {
                representation.getReleaseNotes()
                    .add(this.representationFactory.toRepresentation(note, getReference(note)));
            }

            return representation;
        });
    }

    @Override
    public Response createReleaseNote(UriInfo uriInfo, String wikiName, ReleaseNoteRepresentation note)
        throws ReleaseNotesException
    {
        return inWiki(wikiName, () -> {
            if (note == null || StringUtils.isBlank(note.getVersion())) {
                return refuse(Response.Status.BAD_REQUEST, "A release note needs the version it is about.");
            }

            if (StringUtils.isBlank(note.getProduct())
                && StringUtils.isBlank(this.configuration.getDefaultProduct())) {
                return refuse(Response.Status.BAD_REQUEST,
                    "A release note needs the product it is about, since this wiki has no default product "
                        + "configured.");
            }

            ReleaseNote created;

            try {
                created = this.representationFactory.toReleaseNote(note);
            } catch (IllegalArgumentException e) {
                return refuse(Response.Status.BAD_REQUEST, e.getMessage());
            }

            // A release note that already exists, a page that may not be edited and a save that failed are answered
            // by the exception mapper, which is where every endpoint of the application turns a failure into a status
            // code.
            DocumentReference reference = this.entryPoint.createReleaseNote(created, getCaller());

            // The release note is read back rather than echoed, because the values the request left out are supplied
            // during the creation: a release note posted without a product holds the one configured for the wiki.
            return Response.created(getPageUri(uriInfo, reference))
                .entity(this.representationFactory.toRepresentation(this.releaseNoteManager.getReleaseNote(reference),
                    reference))
                .build();
        });
    }

    /**
     * @param note a release note of the wiki
     * @return the page it lives in, which is named after its product and its version, or {@code null} when it holds
     *         neither and therefore lives nowhere this API can name
     */
    private DocumentReference getReference(ReleaseNote note)
    {
        if (StringUtils.isBlank(note.getProduct()) || StringUtils.isBlank(note.getVersion())) {
            return null;
        }

        return this.releaseNoteManager.getReleaseNoteReference(note.getProduct(), note.getVersion());
    }
}

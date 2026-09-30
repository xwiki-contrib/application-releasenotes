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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.ReleaseNote;
import org.xwiki.contrib.releasenotes.ReleaseNoteManager;
import org.xwiki.contrib.releasenotes.ReleaseNotesException;
import org.xwiki.contrib.releasenotes.rest.ReleaseNoteResource;
import org.xwiki.contrib.releasenotes.rest.model.ReleaseNoteRepresentation;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rest.XWikiRestComponent;

/**
 * Default implementation of {@link ReleaseNoteResource}.
 *
 * @version $Id$
 * @since 2.8
 */
@Component
@Named("org.xwiki.contrib.releasenotes.rest.internal.DefaultReleaseNoteResource")
@Singleton
public class DefaultReleaseNoteResource extends AbstractReleaseNotesResource
    implements ReleaseNoteResource, XWikiRestComponent
{
    @Inject
    private ReleaseNoteManager releaseNoteManager;

    @Inject
    private RepresentationFactory representationFactory;

    @Override
    public ReleaseNoteRepresentation getReleaseNote(String wikiName, String product, String version)
        throws ReleaseNotesException
    {
        return inWiki(wikiName, () -> {
            if (!StringUtils.isNoneBlank(product, version)) {
                throw new WebApplicationException(refuse(Response.Status.BAD_REQUEST, NO_RELEASE_NOTE_IN_URL));
            }

            DocumentReference reference = this.releaseNoteManager.getReleaseNoteReference(product, version);

            return this.representationFactory.toRepresentation(this.entryPoint.getReleaseNote(reference, getCaller()),
                reference);
        });
    }

    @Override
    public Response updateReleaseNote(String wikiName, String product, String version,
        ReleaseNoteRepresentation note) throws ReleaseNotesException
    {
        return inWiki(wikiName, () -> {
            if (!StringUtils.isNoneBlank(product, version)) {
                return refuse(Response.Status.BAD_REQUEST, NO_RELEASE_NOTE_IN_URL);
            }

            if (note == null) {
                // The properties a replacement leaves out are emptied, so an empty release note is a meaningful
                // request and no release note at all is not.
                return refuse(Response.Status.BAD_REQUEST,
                    "A release note is replaced by the release note to store, and the request carries none.");
            }

            ReleaseNote replacement;

            try {
                replacement = this.representationFactory.toReleaseNote(note, product, version);
            } catch (IllegalArgumentException e) {
                return refuse(Response.Status.BAD_REQUEST, e.getMessage());
            }

            // A product and a version that have no release note, and a page that may not be edited, are answered by
            // the exception mapper, which is where every endpoint of the application turns a failure into a status
            // code.
            DocumentReference reference = this.releaseNoteManager.getReleaseNoteReference(product, version);

            return Response.ok(this.representationFactory
                .toRepresentation(this.entryPoint.updateReleaseNote(replacement, getCaller()), reference)).build();
        });
    }
}

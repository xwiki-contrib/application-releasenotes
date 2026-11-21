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
package org.xwiki.contrib.releasenotes.rest.model;

import org.xwiki.stability.Unstable;

/**
 * A release note, as a client posts it and as the endpoints return it.
 *
 * @version $Id$
 * @since 2.7
 */
@Unstable
public class ReleaseNoteRepresentation
{
    private String product;

    private String version;

    private String date;

    private Boolean released;

    private String qaValidation;

    private String qaValidationDate;

    private String template;

    private String reference;

    /**
     * @return the product the release note is about, e.g. {@code XWiki}, which a client may leave out to use the
     *         product configured for the wiki
     */
    public String getProduct()
    {
        return this.product;
    }

    /**
     * @param product see {@link #getProduct()}
     */
    public void setProduct(String product)
    {
        this.product = product;
    }

    /**
     * @return the version the release note is about, in its long form, e.g. {@code 8.3-milestone-1}
     */
    public String getVersion()
    {
        return this.version;
    }

    /**
     * @param version see {@link #getVersion()}
     */
    public void setVersion(String version)
    {
        this.version = version;
    }

    /**
     * @return the day the version is released, written {@code yyyy-MM-dd}, or {@code null} when that day is not
     *         decided yet. A day is the granularity the release notes are listed and sorted by, so a client posting
     *         a full date and time has its time of day dropped.
     */
    public String getDate()
    {
        return this.date;
    }

    /**
     * @param date see {@link #getDate()}
     */
    public void setDate(String date)
    {
        this.date = date;
    }

    /**
     * @return whether the version has been released, which closes the release note to new changes, or {@code null}
     *         on a posted release note that does not say, which stands for not released yet
     */
    public Boolean getReleased()
    {
        return this.released;
    }

    /**
     * @param released see {@link #getReleased()}
     */
    public void setReleased(Boolean released)
    {
        this.released = released;
    }

    /**
     * @return whether Manual QA has validated the version: {@code notTested}, {@code validated} or
     *         {@code notValidated}, or {@code null} on a posted release note that does not say, which stands for
     *         {@code notTested}
     * @since 2.9
     */
    public String getQaValidation()
    {
        return this.qaValidation;
    }

    /**
     * @param qaValidation see {@link #getQaValidation()}
     * @since 2.9
     */
    public void setQaValidation(String qaValidation)
    {
        this.qaValidation = qaValidation;
    }

    /**
     * @return the day Manual QA finished testing the version, written {@code yyyy-MM-dd}, or {@code null} when it has
     *         not
     * @since 2.9
     */
    public String getQaValidationDate()
    {
        return this.qaValidationDate;
    }

    /**
     * @param qaValidationDate see {@link #getQaValidationDate()}
     * @since 2.9
     */
    public void setQaValidationDate(String qaValidationDate)
    {
        this.qaValidationDate = qaValidationDate;
    }

    /**
     * @return the page the content and the title of the release note are copied from, which a client may leave out to
     *         use the template configured for the wiki. Only read on a posted release note: the template a release
     *         note was created from is not kept.
     */
    public String getTemplate()
    {
        return this.template;
    }

    /**
     * @param template see {@link #getTemplate()}
     */
    public void setTemplate(String template)
    {
        this.template = template;
    }

    /**
     * @return the page the release note lives in, without the name of its wiki since that is part of the URL the
     *         release note was reached by. Only returned, and ignored on a posted release note: the page is named
     *         after the product and the version.
     */
    public String getReference()
    {
        return this.reference;
    }

    /**
     * @param reference see {@link #getReference()}
     */
    public void setReference(String reference)
    {
        this.reference = reference;
    }
}

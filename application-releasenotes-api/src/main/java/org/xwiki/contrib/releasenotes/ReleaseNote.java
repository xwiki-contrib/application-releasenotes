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
package org.xwiki.contrib.releasenotes;

import java.util.Date;

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyMandatory;
import org.xwiki.stability.Unstable;

/**
 * The release note of one version of one product: what {@link ReleaseNoteManager#createReleaseNote(ReleaseNote)}
 * creates, and what {@link ReleaseNoteManager#getReleaseNote(DocumentReference)} reads back.
 * <p>
 * The page the release note lives in is not part of it: it is derived from the product and the version, and it is
 * what the creation returns.
 *
 * @version $Id$
 * @since 2.7
 */
@Unstable
public class ReleaseNote
{
    private String product;

    private String version;

    private Date date;

    private boolean released;

    private QAValidation qaValidation = QAValidation.NOT_TESTED;

    private Date qaValidationDate;

    private DocumentReference template;

    /**
     * @return the product the release note is about, which defaults to the product configured for the wiki
     */
    public String getProduct()
    {
        return this.product;
    }

    /**
     * @param product see {@link #getProduct()}
     */
    @PropertyDescription("The product the release note is about, e.g. \"XWiki\". Defaults to the product configured "
        + "for the wiki.")
    public void setProduct(String product)
    {
        this.product = product;
    }

    /**
     * @return the version of the product the release note is about, in its long form, e.g. {@code 8.3-milestone-1}
     */
    public String getVersion()
    {
        return this.version;
    }

    /**
     * @param version see {@link #getVersion()}
     */
    @PropertyMandatory
    @PropertyDescription("The version the release note is about, e.g. \"8.3-milestone-1\". The page of the release "
        + "note is named after it.")
    public void setVersion(String version)
    {
        this.version = version;
    }

    /**
     * @return the day the version was, or is to be, released, or {@code null} when that day is not decided yet
     */
    public Date getDate()
    {
        return this.date;
    }

    /**
     * @param date see {@link #getDate()}
     */
    @PropertyDescription("The day the version is released. May be left out until that day is decided.")
    public void setDate(Date date)
    {
        this.date = date;
    }

    /**
     * @return whether the version has been released, which is what closes the release note to new changes
     */
    public boolean isReleased()
    {
        return this.released;
    }

    /**
     * @param released see {@link #isReleased()}
     */
    @PropertyDescription("Whether the version has been released, which closes the release note to new changes.")
    public void setReleased(boolean released)
    {
        this.released = released;
    }

    /**
     * @return whether Manual QA has validated the version, which is {@link QAValidation#NOT_TESTED} until it says
     * @since 2.9
     */
    public QAValidation getQaValidation()
    {
        return this.qaValidation;
    }

    /**
     * @param qaValidation see {@link #getQaValidation()}; {@code null} stands for {@link QAValidation#NOT_TESTED}
     * @since 2.9
     */
    @PropertyDescription("Whether manual QA has validated the version: notTested (the default), validated or "
        + "notValidated.")
    public void setQaValidation(QAValidation qaValidation)
    {
        this.qaValidation = qaValidation == null ? QAValidation.NOT_TESTED : qaValidation;
    }

    /**
     * @return the day Manual QA finished testing the version, or {@code null} when it has not
     * @since 2.9
     */
    public Date getQaValidationDate()
    {
        return this.qaValidationDate;
    }

    /**
     * @param qaValidationDate see {@link #getQaValidationDate()}
     * @since 2.9
     */
    @PropertyDescription("The day Manual QA finished testing the version.")
    public void setQaValidationDate(Date qaValidationDate)
    {
        this.qaValidationDate = qaValidationDate;
    }

    /**
     * @return the page the content, the title and the required rights of the release note are copied from, or
     *         {@code null} to use the template configured for the wiki
     */
    public DocumentReference getTemplate()
    {
        return this.template;
    }

    /**
     * @param template see {@link #getTemplate()}
     */
    @PropertyDescription("The page the content and the title of the release note are copied from. Defaults to the "
        + "template configured for the wiki.")
    public void setTemplate(DocumentReference template)
    {
        this.template = template;
    }
}

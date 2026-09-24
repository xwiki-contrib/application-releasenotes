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

import java.util.List;

import org.xwiki.stability.Unstable;

/**
 * A change of a release note, as a client posts it and as the endpoints return it.
 *
 * @version $Id$
 * @since 2.7
 */
@Unstable
public class ChangeRepresentation
{
    private String title;

    private String summary;

    private String description;

    private String migrationNotes;

    private String audience;

    private String importance;

    private String category;

    private List<String> screenshots;

    private String product;

    private String version;

    private String entry;

    private String reference;

    /**
     * @return what the change is, in one line, which is the only part of a change that every way of displaying it
     *         shows
     */
    public String getTitle()
    {
        return this.title;
    }

    /**
     * @param title see {@link #getTitle()}
     */
    public void setTitle(String title)
    {
        this.title = title;
    }

    /**
     * @return a few sentences about the change, displayed under its title
     */
    public String getSummary()
    {
        return this.summary;
    }

    /**
     * @param summary see {@link #getSummary()}
     */
    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    /**
     * @return the full description of the change, displayed on the page of the change only
     */
    public String getDescription()
    {
        return this.description;
    }

    /**
     * @param description see {@link #getDescription()}
     */
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * @return what has to be done when upgrading to the version of the change, in wiki syntax, or {@code null} when
     *         the change needs nothing of the kind
     * @since 2.8
     */
    public String getMigrationNotes()
    {
        return this.migrationNotes;
    }

    /**
     * @param migrationNotes see {@link #getMigrationNotes()}
     * @since 2.8
     */
    public void setMigrationNotes(String migrationNotes)
    {
        this.migrationNotes = migrationNotes;
    }

    /**
     * @return who the change is written for, one of {@code user}, {@code administrator} and {@code developer}, which
     *         decides the section of the release note it appears in
     */
    public String getAudience()
    {
        return this.audience;
    }

    /**
     * @param audience see {@link #getAudience()}
     */
    public void setAudience(String audience)
    {
        this.audience = audience;
    }

    /**
     * @return how important the change is, one of {@code low}, {@code medium} and {@code high}
     */
    public String getImportance()
    {
        return this.importance;
    }

    /**
     * @param importance see {@link #getImportance()}
     */
    public void setImportance(String importance)
    {
        this.importance = importance;
    }

    /**
     * @return the category of the change, which the reports group the changes by. Free text: the categories already
     *         in use are what the forms of the application suggest, and any other one is accepted.
     */
    public String getCategory()
    {
        return this.category;
    }

    /**
     * @param category see {@link #getCategory()}
     */
    public void setCategory(String category)
    {
        this.category = category;
    }

    /**
     * @return the names of the images and the videos illustrating the change, as they are attached to its page. A
     *         name carrying a comma cannot be stored, since the names are stored comma-separated.
     */
    public List<String> getScreenshots()
    {
        return this.screenshots;
    }

    /**
     * @param screenshots see {@link #getScreenshots()}
     */
    public void setScreenshots(List<String> screenshots)
    {
        this.screenshots = screenshots;
    }

    /**
     * @return the product the change was made to. Only returned, and ignored on a posted change: the release note a
     *         change belongs to is the one named by the URL it is posted to.
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
     * @return the version the change was made in, in its long form. Only returned, and ignored on a posted change,
     *         for the same reason as the product.
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
     * @return the name of the entry the change lives in, e.g. {@code Entry001}, which is what addresses it: it is
     *         the last path segment of the URL a change is read from and replaced at. Only returned: the entry of a
     *         new change is allocated by the wiki.
     * @since 2.8
     */
    public String getEntry()
    {
        return this.entry;
    }

    /**
     * @param entry see {@link #getEntry()}
     * @since 2.8
     */
    public void setEntry(String entry)
    {
        this.entry = entry;
    }

    /**
     * @return the page the change lives in, without the name of its wiki since that is part of the URL the change
     *         was reached by. Only returned: the page of a new change is allocated by the wiki.
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

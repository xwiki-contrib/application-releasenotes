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

import java.util.List;

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.properties.annotation.PropertyDescription;
import org.xwiki.properties.annotation.PropertyMandatory;
import org.xwiki.stability.Unstable;

/**
 * One change of one version of one product: what {@link ChangeManager#createChange(Change)} creates, and what
 * {@link ChangeManager#getChange(DocumentReference)} reads back.
 * <p>
 * The page the change lives in is not part of it: it is allocated by the creation, which returns it.
 *
 * @version $Id$
 * @since 2.7
 */
@Unstable
public class Change
{
    private String product;

    private String version;

    private ChangeType type;

    private String title;

    private String summary;

    private String description;

    private Audience audience;

    private Importance importance;

    private String category;

    private List<String> screenshots;

    /**
     * @return the product the change was made to, which defaults to the product configured for the wiki
     */
    public String getProduct()
    {
        return this.product;
    }

    /**
     * @param product see {@link #getProduct()}
     */
    @PropertyDescription("The product the change was made to, e.g. \"XWiki\". Defaults to the product configured for "
        + "the wiki.")
    public void setProduct(String product)
    {
        this.product = product;
    }

    /**
     * @return the version of the product the change was made in, in its long form, e.g. {@code 8.3-milestone-1}
     */
    public String getVersion()
    {
        return this.version;
    }

    /**
     * @param version see {@link #getVersion()}
     */
    @PropertyMandatory
    @PropertyDescription("The version the change was made in, e.g. \"8.3-milestone-1\". It decides the release note "
        + "the change belongs to.")
    public void setVersion(String version)
    {
        this.version = version;
    }

    /**
     * @return whether the change is something the version brings or something to do when upgrading to it, which
     *         decides the part of the release note it is displayed in, or {@code null} when it is not said, which a
     *         new change takes as a {@link ChangeType#CHANGE}
     * @since 2.8
     */
    public ChangeType getType()
    {
        return this.type;
    }

    /**
     * @param type see {@link #getType()}
     * @since 2.8
     */
    @PropertyDescription("What the change stands for: \"change\" for something the version brings, displayed among "
        + "the new and noteworthy changes, or \"migration\" for something to do when upgrading to the version, "
        + "displayed among the backward compatibility and migration notes. Defaults to \"change\".")
    public void setType(ChangeType type)
    {
        this.type = type;
    }

    /**
     * @return what the change is, in one line, which is the only part of it every displayer shows
     */
    public String getTitle()
    {
        return this.title;
    }

    /**
     * @param title see {@link #getTitle()}
     */
    @PropertyMandatory
    @PropertyDescription("What the change is, in one line. It is the only part of a change that every way of "
        + "displaying it shows.")
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
    @PropertyDescription("A few sentences about the change, displayed under its title.")
    public void setSummary(String summary)
    {
        this.summary = summary;
    }

    /**
     * @return the full description of the change, displayed on its own page only
     */
    public String getDescription()
    {
        return this.description;
    }

    /**
     * @param description see {@link #getDescription()}
     */
    @PropertyDescription("The full description of the change, displayed on the page of the change only.")
    public void setDescription(String description)
    {
        this.description = description;
    }

    /**
     * @return who the change is written for, which decides the section of the release note it appears in
     */
    public Audience getAudience()
    {
        return this.audience;
    }

    /**
     * @param audience see {@link #getAudience()}
     */
    @PropertyDescription("Who the change is written for: \"user\", \"administrator\" or \"developer\". It decides "
        + "the section of the release note the change appears in.")
    public void setAudience(Audience audience)
    {
        this.audience = audience;
    }

    /**
     * @return how important the change is
     */
    public Importance getImportance()
    {
        return this.importance;
    }

    /**
     * @param importance see {@link #getImportance()}
     */
    @PropertyDescription("How important the change is: \"low\", \"medium\" or \"high\".")
    public void setImportance(Importance importance)
    {
        this.importance = importance;
    }

    /**
     * @return the free-text category of the change, which the reports group the changes by
     */
    public String getCategory()
    {
        return this.category;
    }

    /**
     * @param category see {@link #getCategory()}
     */
    @PropertyDescription("The category of the change, which the reports group the changes by. Free text: the "
        + "categories already in use are suggested, but any other one is accepted.")
    public void setCategory(String category)
    {
        this.category = category;
    }

    /**
     * @return the names of the images and videos illustrating the change, as they are attached to its page
     */
    public List<String> getScreenshots()
    {
        return this.screenshots;
    }

    /**
     * @param screenshots see {@link #getScreenshots()}
     */
    @PropertyDescription("The names of the images and videos illustrating the change, as they are attached to its "
        + "page. A name carrying a comma cannot be stored, since the names are stored comma-separated.")
    public void setScreenshots(List<String> screenshots)
    {
        this.screenshots = screenshots;
    }
}

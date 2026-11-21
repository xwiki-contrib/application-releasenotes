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
package org.xwiki.contrib.releasenotes.test.ui.po;

import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.xwiki.administration.test.po.AdministrationSectionPage;

/**
 * Represents the administration section of the Release Notes application, the one the {@code ConfigurableClass}
 * xobject of the configuration page registers, holding the instance-level defaults of the application.
 *
 * @version $Id$
 */
public class ReleaseNotesAdministrationSectionPage extends AdministrationSectionPage
{
    /**
     * The identifier of the section, i.e. the {@code displayInSection} value of the {@code ConfigurableClass} xobject.
     */
    private static final String SECTION_ID = "releasenotes";

    /**
     * The page holding the {@code ConfigurableClass} xobject: the form of the section is identified by its full name.
     */
    private static final String CONFIGURATION_PAGE = "ReleaseNotes.Code.ReleaseNotesConfig";

    /**
     * The class of the configuration xobject: the fields of the form are named after it.
     */
    private static final String CONFIGURATION_CLASS = "ReleaseNotes.Code.ReleaseNotesConfigClass";

    /**
     * Default constructor.
     */
    public ReleaseNotesAdministrationSectionPage()
    {
        super(SECTION_ID);
    }

    /**
     * Navigates to the section.
     *
     * @return the section, displayed
     */
    public static ReleaseNotesAdministrationSectionPage gotoSection()
    {
        getUtil().gotoPage(getURL(SECTION_ID));
        return new ReleaseNotesAdministrationSectionPage();
    }

    /**
     * @return the value displayed in the product field
     */
    public String getProduct()
    {
        return getFieldValue("product");
    }

    /**
     * @return the value displayed in the template field
     */
    public String getTemplate()
    {
        return getFieldValue("template");
    }

    /**
     * @return {@code true} if the option displaying the Manual QA validation badge on the release notes is ticked
     */
    public boolean isQAValidationDisplayed()
    {
        return getDriver().findElementWithoutWaiting(By.cssSelector(
            String.format("input[type='checkbox'][name='%s_0_displayQAValidation']", CONFIGURATION_CLASS)))
            .isSelected();
    }

    /**
     * Ticks or unticks the option displaying the Manual QA validation badge on the release notes, and saves the
     * section.
     *
     * @param displayed whether the badge is to be displayed
     */
    public void setQAValidationDisplayed(boolean displayed)
    {
        WebElement option = getDriver().findElementWithoutWaiting(By.cssSelector(
            String.format("input[type='checkbox'][name='%s_0_displayQAValidation']", CONFIGURATION_CLASS)));
        if (option.isSelected() != displayed) {
            option.click();
        }
        clickSave();
    }

    /**
     * @return the hint displayed under each field of the section, in the order the fields are displayed
     */
    public List<String> getHints()
    {
        return getDriver().findElementsWithoutWaiting(By.cssSelector("#admin-page-content .xHint")).stream()
            .map(WebElement::getText)
            .toList();
    }

    /**
     * @param categoryId the identifier of a category of the administration menu, such as {@code other}
     * @return {@code true} if the section is listed in that category of the administration menu, {@code false}
     *         otherwise
     */
    public boolean isListedInCategory(String categoryId)
    {
        return !getDriver().findElementsWithoutWaiting(
                By.cssSelector(String.format("#panel-body-%s a[data-id='%s']", categoryId, SECTION_ID)))
            .isEmpty();
    }

    private String getFieldValue(String propertyName)
    {
        return getFormContainerElementForClass(CONFIGURATION_PAGE)
            .getFieldValue(By.name(String.format("%s_0_%s", CONFIGURATION_CLASS, propertyName)));
    }
}

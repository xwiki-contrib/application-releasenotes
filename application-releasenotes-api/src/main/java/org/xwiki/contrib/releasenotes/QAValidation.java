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

import org.xwiki.stability.Unstable;

/**
 * The outcome of the manual QA of the version a release note is about: whether the version was manually tested, and
 * whether that testing validated it. What validating a version takes is up to whoever tests it.
 *
 * @version $Id$
 * @since 2.9
 */
@Unstable
public enum QAValidation
{
    /** The version has not been manually tested, or its testing is not over. */
    NOT_TESTED("notTested"),

    /** Manual QA validated the version. */
    VALIDATED("validated"),

    /** Manual QA did not validate the version. */
    NOT_VALIDATED("notValidated");

    private final String storedValue;

    QAValidation(String storedValue)
    {
        this.storedValue = storedValue;
    }

    /**
     * @return the value the {@code qaValidation} property of a release note holds for this validation, which is also
     *         how a client writes it (e.g. {@code notValidated})
     */
    public String getStoredValue()
    {
        return this.storedValue;
    }

    /**
     * @return the stored value of this validation (e.g. {@code notValidated}), so that a script displaying it shows
     *         the value it would write
     */
    @Override
    public String toString()
    {
        return this.storedValue;
    }

    /**
     * @param value the stored value of a validation (e.g. {@code notValidated}) or the name of its constant (e.g.
     *            {@code NOT_VALIDATED}), whatever its case
     * @return the matching validation, or {@code null} when the passed value is neither
     */
    public static QAValidation fromValue(String value)
    {
        for (QAValidation validation : values()) {
            if (validation.storedValue.equalsIgnoreCase(value) || validation.name().equalsIgnoreCase(value)) {
                return validation;
            }
        }

        return null;
    }
}

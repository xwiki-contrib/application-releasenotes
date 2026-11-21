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
 * Whether Manual QA has validated the version a release note is about: whether it ran its test plan on that version
 * and found no blockers. It is a snapshot taken on the day of the validation, and not a promise that the version has
 * no bugs.
 *
 * @version $Id$
 * @since 2.9
 */
@Unstable
public enum QAValidation
{
    /** Manual QA has not tested the version, or not finished testing it. */
    NOT_TESTED("notTested"),

    /** Manual QA tested the version and found no blockers. */
    VALIDATED("validated"),

    /** Manual QA tested the version and found blockers, or a blocker found later makes the version unfit to use. */
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

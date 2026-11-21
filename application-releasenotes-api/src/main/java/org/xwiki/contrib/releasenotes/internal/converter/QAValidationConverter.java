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
package org.xwiki.contrib.releasenotes.internal.converter;

import java.lang.reflect.Type;

import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.releasenotes.QAValidation;
import org.xwiki.properties.converter.AbstractConverter;
import org.xwiki.properties.converter.ConversionException;

/**
 * Reads a {@link QAValidation} from the value a script writes, which is the value a release note stores and a REST
 * client reads (e.g. {@code notValidated}), so that a script can pass on what it read. The name of the constant (e.g.
 * {@code NOT_VALIDATED}) is accepted too, and an empty value stands for {@link QAValidation#NOT_TESTED}, which is what
 * an empty form field holds.
 *
 * @version $Id$
 * @since 2.9
 */
@Component
@Singleton
public class QAValidationConverter extends AbstractConverter<QAValidation>
{
    @Override
    protected <G extends QAValidation> G convertToType(Type targetType, Object value)
    {
        if (value == null) {
            return null;
        }

        String stringValue = value.toString();

        if (StringUtils.isBlank(stringValue)) {
            return (G) QAValidation.NOT_TESTED;
        }

        QAValidation validation = QAValidation.fromValue(stringValue.trim());

        if (validation == null) {
            throw new ConversionException(String.format("The Manual QA validation [%s] is none of [%s].", stringValue,
                StringUtils.join(QAValidation.values(), ", ")));
        }

        return (G) validation;
    }

    @Override
    protected String convertToString(QAValidation value)
    {
        return value.getStoredValue();
    }
}

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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.xwiki.contrib.releasenotes.internal.ChangeSearcher;
import org.xwiki.contrib.releasenotes.internal.DefaultChangeManager;
import org.xwiki.contrib.releasenotes.internal.DefaultChangeQueryParser;
import org.xwiki.contrib.releasenotes.internal.DefaultReleaseNoteManager;
import org.xwiki.contrib.releasenotes.internal.DefaultReleaseNotesConfiguration;
import org.xwiki.contrib.releasenotes.internal.EntryPageAllocator;
import org.xwiki.contrib.releasenotes.internal.ProductResolver;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesDocumentStore;
import org.xwiki.contrib.releasenotes.internal.ReleaseNotesEntryPoint;
import org.xwiki.contrib.releasenotes.internal.converter.ChangeConverter;
import org.xwiki.contrib.releasenotes.internal.converter.ReleaseNoteConverter;
import org.xwiki.contrib.releasenotes.script.ReleaseNotesScriptService;
import org.xwiki.test.annotation.ComponentList;

/**
 * The components behind {@code $services.releasenotes}, which the pages creating a release note or a change reach
 * the application's Java API through. No page test of this application registers every component of the platform,
 * so the ones a page needs are named, and this is the set that page needs.
 *
 * @version $Id$
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ComponentList({
    ReleaseNotesScriptService.class,
    ReleaseNotesEntryPoint.class,
    DefaultReleaseNoteManager.class,
    DefaultChangeManager.class,
    DefaultChangeQueryParser.class,
    ChangeSearcher.class,
    DefaultReleaseNotesConfiguration.class,
    EntryPageAllocator.class,
    ProductResolver.class,
    ReleaseNotesDocumentStore.class,
    ChangeConverter.class,
    ReleaseNoteConverter.class
})
@Inherited
public @interface ReleaseNotesApiComponentList
{
}

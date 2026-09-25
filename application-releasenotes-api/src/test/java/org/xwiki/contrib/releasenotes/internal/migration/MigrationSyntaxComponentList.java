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
package org.xwiki.contrib.releasenotes.internal.migration;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.xwiki.rendering.internal.parser.reference.DefaultImageReferenceParser;
import org.xwiki.rendering.internal.parser.reference.DefaultLinkReferenceParser;
import org.xwiki.rendering.internal.parser.reference.DefaultResourceReferenceParser;
import org.xwiki.rendering.internal.parser.reference.DefaultUntypedImageReferenceParser;
import org.xwiki.rendering.internal.parser.reference.DefaultUntypedLinkReferenceParser;
import org.xwiki.rendering.internal.parser.reference.type.AttachmentResourceReferenceTypeParser;
import org.xwiki.rendering.internal.parser.reference.type.DocumentResourceReferenceTypeParser;
import org.xwiki.rendering.internal.parser.reference.type.InterWikiResourceReferenceTypeParser;
import org.xwiki.rendering.internal.parser.reference.type.MailtoResourceReferenceTypeParser;
import org.xwiki.rendering.internal.parser.reference.type.PageAttachmentResourceReferenceTypeParser;
import org.xwiki.rendering.internal.parser.reference.type.PageResourceReferenceTypeParser;
import org.xwiki.rendering.internal.parser.reference.type.PathResourceReferenceTypeParser;
import org.xwiki.rendering.internal.parser.reference.type.SpaceResourceReferenceTypeParser;
import org.xwiki.rendering.internal.parser.reference.type.UNCResourceReferenceTypeParser;
import org.xwiki.rendering.internal.parser.reference.type.URLResourceReferenceTypeParser;
import org.xwiki.rendering.internal.parser.wikimodel.WikiModelParserListenerBuilder;
import org.xwiki.rendering.internal.parser.xwiki21.XWiki21Parser;
import org.xwiki.rendering.internal.renderer.DefaultLinkLabelGenerator;
import org.xwiki.rendering.internal.renderer.plain.PlainTextBlockRenderer;
import org.xwiki.rendering.internal.renderer.plain.PlainTextRenderer;
import org.xwiki.rendering.internal.renderer.plain.PlainTextRendererFactory;
import org.xwiki.rendering.internal.listener.ListenerRegistry;
import org.xwiki.rendering.internal.syntax.DefaultSyntaxRegistry;
import org.xwiki.rendering.internal.xwiki21.XWiki21SyntaxProvider;
import org.xwiki.rendering.internal.plain.Plain10SyntaxProvider;
import org.xwiki.test.annotation.ComponentList;

/**
 * The components the import reads the content of a release note with: the parser of the wiki syntax the release
 * notes are written in, with the parsers of the references of its links and images, and the plain text renderer the
 * titles of the notes are rendered with.
 *
 * @version $Id$
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ComponentList({
    XWiki21Parser.class,
    WikiModelParserListenerBuilder.class,
    DefaultLinkReferenceParser.class,
    DefaultImageReferenceParser.class,
    DefaultResourceReferenceParser.class,
    DefaultUntypedLinkReferenceParser.class,
    DefaultUntypedImageReferenceParser.class,
    AttachmentResourceReferenceTypeParser.class,
    DocumentResourceReferenceTypeParser.class,
    InterWikiResourceReferenceTypeParser.class,
    MailtoResourceReferenceTypeParser.class,
    PageAttachmentResourceReferenceTypeParser.class,
    PageResourceReferenceTypeParser.class,
    PathResourceReferenceTypeParser.class,
    SpaceResourceReferenceTypeParser.class,
    UNCResourceReferenceTypeParser.class,
    URLResourceReferenceTypeParser.class,
    PlainTextBlockRenderer.class,
    PlainTextRenderer.class,
    PlainTextRendererFactory.class,
    DefaultLinkLabelGenerator.class,
    ListenerRegistry.class,
    DefaultSyntaxRegistry.class,
    XWiki21SyntaxProvider.class,
    Plain10SyntaxProvider.class,
    MigrationSectionParser.class
})
@Inherited
public @interface MigrationSyntaxComponentList
{
}

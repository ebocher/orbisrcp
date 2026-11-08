/*
 * Groovy Editor (GE) is a library that brings a groovy console to the Eclipse RCP.
 * GE is developed by CNRS http://www.cnrs.fr/.
 *
 * GE is part of the OrbisGIS project. GE is free software;
 * you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation;
 * version 3.0 of the License.
 *
 * GE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details http://www.gnu.org/licenses.
 *
 *
 *For more information, please consult: http://www.orbisgis.org
 *or contact directly: info_at_orbisgis.org
 */
package org.orbisgis.ui.editors.groovy.syntax;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.rules.EndOfLineRule;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.jface.text.rules.IPredicateRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.MultiLineRule;
import org.eclipse.jface.text.rules.RuleBasedPartitionScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;

/**
 * Partitions of a Groovy script: comments and strings are found first, so they are colored as a whole, even
 * when they span several lines, and the code rules only apply to the code.
 */
public final class GroovyPartitions {

    /** Name of the Groovy partitioning of the documents */
    public static final String PARTITIONING = "__groovy_partitioning";

    public static final String COMMENT = "__groovy_comment";
    public static final String DOC_COMMENT = "__groovy_doc_comment";
    /** Single quoted strings: no interpolation */
    public static final String STRING = "__groovy_string";
    /** Double quoted strings (GStrings): ${...} and $name are interpolated */
    public static final String GSTRING = "__groovy_gstring";

    /** All the partition types, including the code ({@link IDocument#DEFAULT_CONTENT_TYPE}) */
    public static final String[] CONTENT_TYPES = {IDocument.DEFAULT_CONTENT_TYPE, COMMENT, DOC_COMMENT, STRING, GSTRING};

    private GroovyPartitions() {
    }

    /**
     * Scanner finding the comments and strings.
     */
    public static RuleBasedPartitionScanner createScanner() {
        IToken comment = new Token(COMMENT);
        IToken docComment = new Token(DOC_COMMENT);
        IToken string = new Token(STRING);
        IToken gstring = new Token(GSTRING);
        RuleBasedPartitionScanner scanner = new RuleBasedPartitionScanner();
        scanner.setPredicateRules(new IPredicateRule[]{
                new EndOfLineRule("//", comment),
                // An unterminated block comment or triple quoted string colors the end of the script, as in the
                // other editors: the end is usually being typed
                new EmptyCommentRule(comment),
                new MultiLineRule("/**", "*/", docComment, (char) 0, true),
                new MultiLineRule("/*", "*/", comment, (char) 0, true),
                new MultiLineRule("\"\"\"", "\"\"\"", gstring, '\\', true),
                new MultiLineRule("'''", "'''", string, '\\', true),
                new SingleLineRule("\"", "\"", gstring, '\\'),
                new SingleLineRule("'", "'", string, '\\'),
        });
        return scanner;
    }

    /**
     * Install the Groovy partitioner on a document, if it does not have one.
     */
    public static void setup(IDocument document) {
        if (document instanceof IDocumentExtension3 extension) {
            if (extension.getDocumentPartitioner(PARTITIONING) == null) {
                IDocumentPartitioner partitioner = new FastPartitioner(createScanner(), CONTENT_TYPES);
                extension.setDocumentPartitioner(PARTITIONING, partitioner);
                partitioner.connect(document);
            }
        }
    }

    /**
     * {@code /**}{@code /} is an empty block comment, not the start of a documentation comment.
     */
    private static final class EmptyCommentRule extends MultiLineRule {
        EmptyCommentRule(IToken token) {
            super("/**/", "", token);
        }

        @Override
        protected boolean endSequenceDetected(org.eclipse.jface.text.rules.ICharacterScanner scanner) {
            return true;
        }
    }
}

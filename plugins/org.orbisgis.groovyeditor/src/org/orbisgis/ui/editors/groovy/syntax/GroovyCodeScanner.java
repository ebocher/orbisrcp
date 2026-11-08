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

import org.eclipse.jface.text.rules.ICharacterScanner;
import org.eclipse.jface.text.rules.IRule;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.IWordDetector;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.rules.SingleLineRule;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.rules.WhitespaceRule;
import org.eclipse.jface.text.rules.WordRule;

import java.util.List;

/**
 * Coloring of the Groovy code (outside the comments and strings, see {@link GroovyPartitions}).
 *
 * The identifiers are read as a whole: a keyword or a digit inside a name ({@code h2gis}, {@code my_if}) is not
 * colored.
 */
public final class GroovyCodeScanner {

    /** Keywords, reserved words and literals of Groovy */
    static final List<String> KEYWORDS = List.of("abstract", "as", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "def", "default", "do", "double", "else", "enum",
            "extends", "false", "final", "finally", "float", "for", "goto", "if", "implements", "import", "in",
            "instanceof", "int", "interface", "long", "native", "new", "null", "package", "permits",
            "private", "protected", "public", "record", "return", "sealed", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "threadsafe", "throw", "throws", "trait", "transient", "true", "try",
            "var", "void", "volatile", "while", "yield");

    private GroovyCodeScanner() {
    }

    /**
     * Scanner of the code partitions.
     */
    public static RuleBasedScanner createCodeScanner(GroovyColors colors) {
        // The default token must have data: a text attribute without color uses the editor default
        IToken defaultToken = new Token(new org.eclipse.jface.text.TextAttribute(null));
        IToken keyword = new Token(colors.keyword);

        WordRule words = new WordRule(new IdentifierDetector(), defaultToken);
        for (String word : KEYWORDS) {
            words.addWord(word, keyword);
        }
        RuleBasedScanner scanner = new RuleBasedScanner();
        scanner.setRules(new IRule[]{
                new WhitespaceRule(Character::isWhitespace),
                new AnnotationRule(new Token(colors.annotation)),
                words,
                new NumberRule(new Token(colors.number)),
        });
        scanner.setDefaultReturnToken(defaultToken);
        return scanner;
    }

    /**
     * Scanner of the single quoted strings.
     */
    public static RuleBasedScanner createStringScanner(GroovyColors colors) {
        RuleBasedScanner scanner = new RuleBasedScanner();
        scanner.setDefaultReturnToken(new Token(colors.string));
        return scanner;
    }

    /**
     * Scanner of the double quoted strings: {@code ${expression}} and {@code $name.property} are colored.
     */
    public static RuleBasedScanner createGStringScanner(GroovyColors colors) {
        IToken interpolation = new Token(colors.interpolation);
        RuleBasedScanner scanner = new RuleBasedScanner();
        scanner.setRules(new IRule[]{
                new EscapeRule(new Token(colors.string)),
                new SingleLineRule("${", "}", interpolation),
                new DollarIdentifierRule(interpolation),
        });
        scanner.setDefaultReturnToken(new Token(colors.string));
        return scanner;
    }

    /**
     * Scanner of the comments.
     */
    public static RuleBasedScanner createCommentScanner(org.eclipse.jface.text.TextAttribute attribute) {
        RuleBasedScanner scanner = new RuleBasedScanner();
        scanner.setDefaultReturnToken(new Token(attribute));
        return scanner;
    }

    static final class IdentifierDetector implements IWordDetector {
        @Override
        public boolean isWordStart(char c) {
            return Character.isJavaIdentifierStart(c);
        }

        @Override
        public boolean isWordPart(char c) {
            return Character.isJavaIdentifierPart(c);
        }
    }

    /**
     * {@code @Name} or {@code @qualified.Name}: only the name of the annotation is colored, not its arguments.
     */
    static final class AnnotationRule implements IRule {
        private final IToken token;

        AnnotationRule(IToken token) {
            this.token = token;
        }

        @Override
        public IToken evaluate(ICharacterScanner scanner) {
            if (scanner.read() != '@') {
                scanner.unread();
                return Token.UNDEFINED;
            }
            int c = scanner.read();
            if (c == ICharacterScanner.EOF || !Character.isJavaIdentifierStart((char) c)) {
                scanner.unread();
                scanner.unread();
                return Token.UNDEFINED;
            }
            do {
                c = scanner.read();
            } while (c != ICharacterScanner.EOF && (Character.isJavaIdentifierPart((char) c) || c == '.'));
            scanner.unread();
            return token;
        }
    }

    /**
     * Number literals: {@code 12}, {@code 1_000}, {@code 3.14}, {@code 1e-3}, {@code 0xFF}, {@code 10L},
     * {@code 2.5G}... In {@code 0..10}, the range operator is not part of the number.
     */
    static final class NumberRule implements IRule {
        private final IToken token;

        NumberRule(IToken token) {
            this.token = token;
        }

        @Override
        public IToken evaluate(ICharacterScanner scanner) {
            int c = scanner.read();
            if (c == ICharacterScanner.EOF || !Character.isDigit((char) c)) {
                scanner.unread();
                return Token.UNDEFINED;
            }
            if (c == '0') {
                int x = scanner.read();
                if (x == 'x' || x == 'X' || x == 'b' || x == 'B') {
                    readWhile(scanner, "0123456789abcdefABCDEF_");
                    readSuffix(scanner);
                    return token;
                }
                scanner.unread();
            }
            readWhile(scanner, "0123456789_");
            // Decimal part, only if a digit follows the point (0..10 is a range, 1.abs() a method call)
            if (peek(scanner) == '.') {
                scanner.read();
                if (Character.isDigit((char) peek(scanner))) {
                    readWhile(scanner, "0123456789_");
                } else {
                    scanner.unread();
                }
            }
            // Exponent
            int e = peek(scanner);
            if (e == 'e' || e == 'E') {
                scanner.read();
                int sign = scanner.read();
                int consumed = 2;
                if (sign != '+' && sign != '-') {
                    scanner.unread();
                    consumed = 1;
                }
                if (Character.isDigit((char) peek(scanner))) {
                    readWhile(scanner, "0123456789_");
                } else {
                    for (int i = 0; i < consumed; i++) {
                        scanner.unread();
                    }
                }
            }
            readSuffix(scanner);
            return token;
        }

        private static void readSuffix(ICharacterScanner scanner) {
            int s = peek(scanner);
            if ("lLiIgGfFdD".indexOf(s) >= 0 && s != ICharacterScanner.EOF) {
                scanner.read();
            }
        }

        private static void readWhile(ICharacterScanner scanner, String chars) {
            int c;
            do {
                c = scanner.read();
            } while (c != ICharacterScanner.EOF && chars.indexOf(c) >= 0);
            scanner.unread();
        }

        private static int peek(ICharacterScanner scanner) {
            int c = scanner.read();
            scanner.unread();
            return c;
        }
    }

    /**
     * Escaped characters of a string ({@code \$}, {@code \"}...), so an escaped dollar is not interpolated.
     */
    static final class EscapeRule implements IRule {
        private final IToken token;

        EscapeRule(IToken token) {
            this.token = token;
        }

        @Override
        public IToken evaluate(ICharacterScanner scanner) {
            if (scanner.read() != '\\') {
                scanner.unread();
                return Token.UNDEFINED;
            }
            if (scanner.read() == ICharacterScanner.EOF) {
                scanner.unread();
            }
            return token;
        }
    }

    /**
     * {@code $name} or {@code $name.property} in a GString.
     */
    static final class DollarIdentifierRule implements IRule {
        private final IToken token;

        DollarIdentifierRule(IToken token) {
            this.token = token;
        }

        @Override
        public IToken evaluate(ICharacterScanner scanner) {
            if (scanner.read() != '$') {
                scanner.unread();
                return Token.UNDEFINED;
            }
            int c = scanner.read();
            if (c == ICharacterScanner.EOF || !Character.isJavaIdentifierStart((char) c) || c == '$') {
                scanner.unread();
                scanner.unread();
                return Token.UNDEFINED;
            }
            while (true) {
                c = scanner.read();
                if (c != ICharacterScanner.EOF && Character.isJavaIdentifierPart((char) c) && c != '$') {
                    continue;
                }
                // $a.b: the property is part of the expression, but not a final point ("$name.")
                if (c == '.') {
                    int next = scanner.read();
                    if (next != ICharacterScanner.EOF && Character.isJavaIdentifierStart((char) next) && next != '$') {
                        continue;
                    }
                    scanner.unread();
                }
                scanner.unread();
                return token;
            }
        }
    }
}

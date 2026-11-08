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
package org.orbisgis.ui.editors.groovy.completion;

import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.orbisgis.core.logger.Logger;
import org.orbisgis.ui.editors.groovy.session.GroovySession;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Completion of the Groovy editor, based on the live context of the editor (see {@link GroovySession}):
 * <ul>
 *     <li>{@code variable.}: methods and properties of the object stored in the variable (including the
 *     Groovy extension methods: each, collect...);</li>
 *     <li>{@code ClassName.} or {@code java.time.LocalDate.}: static methods and fields;</li>
 *     <li>otherwise: variables of the context, imported classes, keywords and the words of the script.</li>
 * </ul>
 * Only the objects already in the context are inspected: nothing is evaluated, so the completion has no
 * side effect. Run the script (or the current line) to make its variables available to the completion.
 */
public class GroovyCompletionProcessor implements IContentAssistProcessor {

    private static final Logger LOGGER = new Logger(GroovyCompletionProcessor.class);

    private static final int MAX_PROPOSALS = 500;

    private static final List<String> KEYWORDS = List.of("abstract", "as", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "continue", "def", "default", "do", "double", "else", "enum",
            "extends", "false", "final", "finally", "float", "for", "if", "implements", "import", "in",
            "instanceof", "int", "interface", "long", "new", "null", "package", "private", "protected", "public",
            "return", "short", "static", "super", "switch", "this", "throw", "throws", "trait", "true", "try",
            "var", "void", "while");

    /** Packages imported by default in Groovy */
    private static final List<String> DEFAULT_PACKAGES = List.of("java.lang", "java.util", "java.io", "java.net",
            "java.math", "groovy.lang", "groovy.util");

    private static final Pattern WORD = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{2,}");

    private final Supplier<GroovySession> session;

    /**
     * @param session gives the Groovy context of the edited script
     */
    public GroovyCompletionProcessor(Supplier<GroovySession> session) {
        this.session = session;
    }

    @Override
    public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
        try {
            return computeProposals(viewer.getDocument().get(), offset).toArray(new ICompletionProposal[0]);
        } catch (Exception | LinkageError e) {
            LOGGER.warn("Unable to compute the completion proposals.", e);
            return new ICompletionProposal[0];
        }
    }

    /**
     * Compute the proposals for the given text and cursor offset.
     */
    public List<ComplProposal> computeProposals(String text, int offset) {
        int start = offset;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        String prefix = text.substring(start, offset);
        int replaceLength = identifierEnd(text, offset) - start;
        GroovySession context = session.get();

        List<ComplProposal> proposals = new ArrayList<>();
        if (start > 0 && text.charAt(start - 1) == '.') {
            Optional<ReceiverTypes.Resolved> receiver = context == null ? Optional.empty()
                    : resolveReceiver(context, text, start - 1);
            if (receiver.isPresent()) {
                addMemberProposals(proposals, receiver.get(), prefix, start, replaceLength);
            }
        } else {
            addContextProposals(proposals, context, text, prefix, start, replaceLength);
        }
        proposals.sort(Comparator.comparing(ComplProposal::getKind)
                .thenComparing(p -> p.getDisplayString().toLowerCase()));
        return proposals.size() > MAX_PROPOSALS ? new ArrayList<>(proposals.subList(0, MAX_PROPOSALS)) : proposals;
    }

    /**
     * @return the dotted identifier ending at {@code dotOffset} ({@code a.b.c} in {@code a.b.c.}), or an empty
     * string if the receiver is not a plain identifier (method call, literal...)
     */
    static String receiverBefore(String text, int dotOffset) {
        int start = dotOffset;
        while (start > 0) {
            char c = text.charAt(start - 1);
            if (Character.isJavaIdentifierPart(c) || c == '.') {
                start--;
            } else {
                break;
            }
        }
        String receiver = text.substring(start, dotOffset);
        if (receiver.isEmpty() || receiver.startsWith(".") || receiver.endsWith(".") || receiver.contains("..")
                || !Character.isJavaIdentifierStart(receiver.charAt(0))) {
            return "";
        }
        return receiver;
    }

    private static int identifierEnd(String text, int offset) {
        int end = offset;
        while (end < text.length() && Character.isJavaIdentifierPart(text.charAt(end))) {
            end++;
        }
        return end;
    }

    /**
     * @param text   the script
     * @param offset a position after a point
     * @return the class of the object written before the point, if it is known ({@code void} for the result of a
     * method returning nothing, {@code Class} for a class name)
     */
    public Optional<Class<?>> receiverType(String text, int offset) {
        int start = offset;
        while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        GroovySession context = session.get();
        if (start == 0 || text.charAt(start - 1) != '.' || context == null) {
            return Optional.empty();
        }
        return resolveReceiver(context, text, start - 1).map(receiver -> receiver.isStatic() ? Class.class
                : receiver.value() != null ? receiver.value().getClass() : receiver.type());
    }

    private Optional<ReceiverTypes.Resolved> resolveReceiver(GroovySession context, String text, int dotOffset) {
        try {
            return ReceiverTypes.resolve(text, dotOffset, context.getVariables(), name -> resolveClass(context, name));
        } catch (RuntimeException | LinkageError e) {
            LOGGER.trace("Unable to resolve the object before the point", e);
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- receiver.member

    private void addMemberProposals(List<ComplProposal> proposals, ReceiverTypes.Resolved receiver, String prefix,
                                    int start, int replaceLength) {
        if (receiver.isVoid()) {
            // the method returns nothing
            return;
        }
        if (receiver.isStatic()) {
            addStaticMembers(proposals, receiver.type(), prefix, start, replaceLength);
        } else if (receiver.value() != null) {
            if (receiver.value() instanceof Map<?, ?> map) {
                // the keys of a map are read as properties: spec.encoding
                for (Object key : map.keySet()) {
                    String name = String.valueOf(key);
                    if (key instanceof String && isIdentifier(name) && matches(name, prefix)) {
                        Object value = map.get(key);
                        proposals.add(new ComplProposal(start, replaceLength, name, name.length(), name,
                                ": " + (value == null ? "null" : simpleName(value.getClass())),
                                ComplProposal.Kind.PROPERTY, prefix.length()));
                    }
                }
            }
            addInstanceMembers(proposals, InvokerHelper.getMetaClass(receiver.value()),
                    Receivers.isIterable(receiver.value()), null, prefix, start, replaceLength);
        } else {
            addInstanceMembers(proposals, GroovySystem.getMetaClassRegistry().getMetaClass(receiver.type()),
                    Receivers.isIterable(receiver.type()), receiver.type(), prefix, start, replaceLength);
        }
    }

    private static boolean isIdentifier(String name) {
        return !name.isEmpty() && Character.isJavaIdentifierStart(name.charAt(0))
                && name.chars().allMatch(Character::isJavaIdentifierPart);
    }

    /**
     * @param type the class of the object when only its class is known: its methods are also read by reflection,
     *             since the meta class of an interface (List, Connection...) has not its methods
     */
    private void addInstanceMembers(List<ComplProposal> proposals, MetaClass metaClass, boolean iterable,
                                    Class<?> type, String prefix, int start, int replaceLength) {
        Set<String> seen = new TreeSet<>();
        for (MetaProperty property : metaClass.getProperties()) {
            String name = property.getName();
            if (matches(name, prefix) && seen.add("p:" + name)) {
                proposals.add(new ComplProposal(start, replaceLength, name, name.length(), name,
                        ": " + simpleName(property.getType()), ComplProposal.Kind.PROPERTY, prefix.length()));
            }
        }
        List<MetaMethod> methods = new ArrayList<>(metaClass.getMethods());
        methods.addAll(metaClass.getMetaMethods());
        for (MetaMethod method : methods) {
            if (!method.isPublic() || method.getName().contains("$") || method.getName().startsWith("<")) {
                continue;
            }
            // each, collect... added by Groovy to all the objects: only on the objects which can be iterated
            if (!iterable && method.getDeclaringClass().getTheClass() == Object.class
                    && Receivers.ITERATION_METHODS.contains(method.getName())) {
                continue;
            }
            addMethod(proposals, seen, method.getName(), method.getNativeParameterTypes(), method.getReturnType(),
                    prefix, start, replaceLength);
        }
        if (type != null) {
            for (Method method : type.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    addMethod(proposals, seen, method.getName(), method.getParameterTypes(), method.getReturnType(),
                            prefix, start, replaceLength);
                }
            }
        }
    }

    private void addStaticMembers(List<ComplProposal> proposals, Class<?> clazz, String prefix, int start,
                                  int replaceLength) {
        Set<String> seen = new TreeSet<>();
        for (Field field : clazz.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && matches(field.getName(), prefix)
                    && seen.add("p:" + field.getName())) {
                proposals.add(new ComplProposal(start, replaceLength, field.getName(), field.getName().length(),
                        field.getName(), ": " + simpleName(field.getType()), ComplProposal.Kind.PROPERTY,
                        prefix.length()));
            }
        }
        for (Method method : clazz.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                addMethod(proposals, seen, method.getName(), method.getParameterTypes(), method.getReturnType(),
                        prefix, start, replaceLength);
            }
        }
        if (matches("class", prefix)) {
            proposals.add(new ComplProposal(start, replaceLength, "class", 5, "class", ": Class",
                    ComplProposal.Kind.PROPERTY, prefix.length()));
        }
    }

    private static void addMethod(List<ComplProposal> proposals, Set<String> seen, String name,
                                  Class<?>[] parameters, Class<?> returnType, String prefix, int start,
                                  int replaceLength) {
        if (!matches(name, prefix)) {
            return;
        }
        StringBuilder signature = new StringBuilder(name).append('(');
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                signature.append(", ");
            }
            signature.append(simpleName(parameters[i]));
        }
        signature.append(')');
        if (!seen.add("m:" + signature)) {
            return;
        }
        String replacement = name + "()";
        int cursor = parameters.length == 0 ? replacement.length() : name.length() + 1;
        proposals.add(new ComplProposal(start, replaceLength, replacement, cursor, signature.toString(),
                ": " + simpleName(returnType), ComplProposal.Kind.METHOD, prefix.length()));
    }

    // ---------------------------------------------------------------- identifiers

    private void addContextProposals(List<ComplProposal> proposals, GroovySession context, String text,
                                     String prefix, int start, int replaceLength) {
        Set<String> seen = new TreeSet<>();
        if (context != null) {
            for (Map.Entry<String, Object> variable : context.getVariables().entrySet()) {
                String name = variable.getKey();
                if (matches(name, prefix) && seen.add(name)) {
                    Object value = variable.getValue();
                    proposals.add(new ComplProposal(start, replaceLength, name, name.length(), name,
                            value == null ? null : ": " + simpleName(value.getClass()), ComplProposal.Kind.VARIABLE,
                            prefix.length()));
                }
            }
            for (Map.Entry<String, String> imported : context.getImportedClasses().entrySet()) {
                String name = imported.getKey();
                if (matches(name, prefix) && seen.add(name)) {
                    proposals.add(new ComplProposal(start, replaceLength, name, name.length(), name,
                            "- " + imported.getValue(), ComplProposal.Kind.CLASS, prefix.length()));
                }
            }
        }
        if (prefix.isEmpty()) {
            return;
        }
        for (String keyword : KEYWORDS) {
            if (matches(keyword, prefix) && seen.add(keyword)) {
                proposals.add(new ComplProposal(start, replaceLength, keyword, keyword.length(), keyword, null,
                        ComplProposal.Kind.KEYWORD, prefix.length()));
            }
        }
        // Words of the script: useful before the script has been run
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            String word = matcher.group();
            if (matcher.start() != start && matches(word, prefix) && !word.equals(prefix) && seen.add(word)) {
                proposals.add(new ComplProposal(start, replaceLength, word, word.length(), word, null,
                        ComplProposal.Kind.WORD, prefix.length()));
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Resolve a class name written in the script: fully qualified name, imported class, class of a package
     * imported in the context or by default.
     */
    static Class<?> resolveClass(GroovySession context, String name) {
        List<String> candidates = new ArrayList<>();
        if (name.contains(".")) {
            candidates.add(name);
        } else {
            String imported = context.getImportedClasses().get(name);
            if (imported != null) {
                candidates.add(imported);
            }
            for (String pkg : context.getImportedPackages()) {
                candidates.add(pkg + "." + name);
            }
            for (String pkg : DEFAULT_PACKAGES) {
                candidates.add(pkg + "." + name);
            }
        }
        ClassLoader loader = context.getClassLoader();
        for (String candidate : candidates) {
            if (!Character.isUpperCase(candidate.charAt(candidate.lastIndexOf('.') + 1))) {
                continue;
            }
            try {
                return Class.forName(candidate, false, loader);
            } catch (ClassNotFoundException | LinkageError e) {
                // try the next one
            }
        }
        return null;
    }

    private static boolean matches(String name, String prefix) {
        return prefix.isEmpty() || name.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String simpleName(Class<?> type) {
        if (type == null) {
            return "Object";
        }
        return type.isArray() ? simpleName(type.getComponentType()) + "[]" : type.getSimpleName();
    }

    @Override
    public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
        return null;
    }

    @Override
    public char[] getCompletionProposalAutoActivationCharacters() {
        return new char[]{'.'};
    }

    @Override
    public char[] getContextInformationAutoActivationCharacters() {
        return null;
    }

    @Override
    public String getErrorMessage() {
        return null;
    }

    @Override
    public IContextInformationValidator getContextInformationValidator() {
        return null;
    }
}

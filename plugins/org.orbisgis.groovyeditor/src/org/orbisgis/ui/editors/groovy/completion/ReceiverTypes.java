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

import groovy.lang.Closure;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import org.eclipse.jface.text.Document;
import org.orbisgis.ui.editors.groovy.syntax.CodeKinds;
import org.orbisgis.ui.editors.groovy.syntax.GroovyPartitions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Type of the object written before a point, without evaluating anything: a variable of the context, a class, or
 * a chain of method calls and properties from them ({@code dbeaver.query("db", "...").}), followed with the
 * declared return types of the methods and the types of the properties.
 */
final class ReceiverTypes {

    /**
     * The object before a point.
     *
     * @param type      its class ({@code void.class} for the result of a method returning nothing)
     * @param value     its value when it is exactly known (a variable of the context), else null
     * @param isStatic  true for a class name: its static members
     */
    record Resolved(Class<?> type, Object value, boolean isStatic) {

        boolean isVoid() {
            return type == void.class || type == Void.class;
        }
    }

    /** A part of the chain: a name, with the number of arguments for a method call (-1 for a property) */
    private record Segment(String name, int arguments, boolean constructor) {
    }

    private ReceiverTypes() {
    }

    /**
     * @param text         the script
     * @param dotOffset    offset of the point
     * @param variables    the variables of the context
     * @param classes      resolves a class name written in the script, or null
     * @return the object before the point, or empty if it is unknown
     */
    static Optional<Resolved> resolve(String text, int dotOffset, Map<String, Object> variables,
                                      Function<String, Class<?>> classes) {
        List<Segment> segments = parse(text, dotOffset);
        if (segments == null || segments.isEmpty()) {
            return Optional.empty();
        }
        // The start of the chain: a variable, a class (maybe qualified: java.time.LocalDate), new X(), f(...)
        Resolved current = null;
        int next = 1;
        Segment first = segments.get(0);
        if (first.constructor()) {
            Class<?> type = classes.apply(first.name());
            current = type == null ? null : new Resolved(type, null, false);
        } else if (first.arguments() < 0 && variables.containsKey(first.name())) {
            Object value = variables.get(first.name());
            if (value instanceof Class<?> type) {
                current = new Resolved(type, null, true);
            } else if (value != null) {
                current = new Resolved(value.getClass(), value, false);
            }
        } else if (first.arguments() >= 0 && variables.get(first.name()) instanceof Closure<?> closure) {
            // a function of the context: vegalite(...)
            current = resolved(callReturnType(closure.getClass(), "doCall", first.arguments(), false));
        } else if (first.arguments() < 0) {
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < segments.size() && segments.get(i).arguments() < 0; i++) {
                name.append(i == 0 ? "" : ".").append(segments.get(i).name());
                Class<?> type = classes.apply(name.toString());
                if (type != null) {
                    current = new Resolved(type, null, true);
                    next = i + 1;
                    break;
                }
            }
        }
        if (current == null) {
            return Optional.empty();
        }
        for (int i = next; i < segments.size(); i++) {
            if (current.isVoid()) {
                return Optional.empty();
            }
            current = step(current, segments.get(i));
            if (current == null) {
                return Optional.empty();
            }
        }
        if (!current.isStatic() && current.value() == null && current.type() == Object.class) {
            return Optional.empty();
        }
        return Optional.of(current);
    }

    private static Resolved step(Resolved current, Segment segment) {
        if (segment.arguments() >= 0) {
            return resolved(callReturnType(current.type(), segment.name(), segment.arguments(), current.isStatic()));
        }
        if (current.isStatic()) {
            if (segment.name().equals("class")) {
                return new Resolved(Class.class, current.type(), false);
            }
            try {
                Field field = current.type().getField(segment.name());
                return Modifier.isStatic(field.getModifiers()) ? new Resolved(field.getType(), null, false) : null;
            } catch (NoSuchFieldException e) {
                return null;
            }
        }
        // An entry of a map of the context can be read without side effect: spec.encoding.
        if (current.value() instanceof Map<?, ?> map) {
            Object value = map.get(segment.name());
            return value == null ? null : new Resolved(value.getClass(), value, false);
        }
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(current.type());
        MetaProperty property = metaClass.getMetaProperty(segment.name());
        return property == null ? null : resolved(property.getType());
    }

    private static Resolved resolved(Class<?> type) {
        return type == null ? null : new Resolved(type, null, false);
    }

    /**
     * Return type of the methods of a class with a name, preferably the ones with the given number of
     * arguments (a closure after the parentheses is an argument).
     */
    private static Class<?> callReturnType(Class<?> type, String name, int arguments, boolean isStatic) {
        List<Class<?>[]> parameters = new ArrayList<>();
        List<Class<?>> returns = new ArrayList<>();
        if (isStatic) {
            for (Method method : type.getMethods()) {
                if (Modifier.isStatic(method.getModifiers()) && method.getName().equals(name)) {
                    parameters.add(method.getParameterTypes());
                    returns.add(method.getReturnType());
                }
            }
        } else {
            MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(type);
            List<MetaMethod> methods = new ArrayList<>(metaClass.getMethods());
            methods.addAll(metaClass.getMetaMethods());
            for (MetaMethod method : methods) {
                if (method.getName().equals(name) && method.isPublic()) {
                    parameters.add(method.getNativeParameterTypes());
                    returns.add(method.getReturnType());
                }
            }
            // The meta class of an interface (List, Connection...) has not its methods
            for (Method method : type.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && method.getName().equals(name)) {
                    parameters.add(method.getParameterTypes());
                    returns.add(method.getReturnType());
                }
            }
        }
        Set<Class<?>> exact = new LinkedHashSet<>();
        Set<Class<?>> all = new LinkedHashSet<>(returns);
        for (int i = 0; i < returns.size(); i++) {
            if (parameters.get(i).length == arguments) {
                exact.add(returns.get(i));
            }
        }
        Set<Class<?>> candidates = exact.isEmpty() ? all : exact;
        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        // Several return types: the first one other than Object
        for (Class<?> candidate : candidates) {
            if (candidate != Object.class) {
                return candidate;
            }
        }
        return Object.class;
    }

    // ---------------------------------------------------------------- parsing

    /**
     * The chain before a point, from its start: {@code a.b(x, y).c} gives a, b(2), c. Null if it is not a chain
     * of names and calls (a literal, an operator...).
     */
    private static List<Segment> parse(String text, int dotOffset) {
        Document document = new Document(text);
        GroovyPartitions.setup(document);
        byte[] kinds = CodeKinds.of(document);
        List<Segment> segments = new ArrayList<>();
        int i = dotOffset - 1;
        while (true) {
            i = skipBlanks(text, kinds, i);
            if (i < 0) {
                return null;
            }
            int arguments = -1;
            if (text.charAt(i) == '}' && CodeKinds.kind(kinds, i) == CodeKinds.CODE) {
                int open = matchingOpener(text, kinds, i);
                if (open < 0) {
                    return null;
                }
                arguments = 1;
                i = skipBlanks(text, kinds, open - 1);
                if (i < 0) {
                    return null;
                }
            }
            if (text.charAt(i) == ')' && CodeKinds.kind(kinds, i) == CodeKinds.CODE) {
                int open = matchingOpener(text, kinds, i);
                if (open < 0) {
                    return null;
                }
                arguments = Math.max(arguments, 0) + countArguments(text, kinds, open + 1, i);
                i = skipBlanks(text, kinds, open - 1);
                if (i < 0) {
                    return null;
                }
            }
            if (CodeKinds.kind(kinds, i) != CodeKinds.CODE || !Character.isJavaIdentifierPart(text.charAt(i))) {
                return null;
            }
            int end = i + 1;
            while (i > 0 && Character.isJavaIdentifierPart(text.charAt(i - 1))
                    && CodeKinds.kind(kinds, i - 1) == CodeKinds.CODE) {
                i--;
            }
            String name = text.substring(i, end);
            if (!Character.isJavaIdentifierStart(name.charAt(0))) {
                return null;
            }
            int before = skipBlanks(text, kinds, i - 1);
            boolean constructor = arguments >= 0 && before >= 2 && text.startsWith("new", before - 2)
                    && (before < 3 || !Character.isJavaIdentifierPart(text.charAt(before - 3)));
            segments.add(0, new Segment(name, arguments, constructor));
            if (constructor || before < 0 || text.charAt(before) != '.') {
                return segments;
            }
            i = before - 1;
            if (i >= 0 && (text.charAt(i) == '?' || text.charAt(i) == '*' || text.charAt(i) == '&')) {
                i--;
            }
        }
    }

    private static int skipBlanks(String text, byte[] kinds, int from) {
        int i = from;
        while (i >= 0 && (CodeKinds.kind(kinds, i) == CodeKinds.COMMENT
                || (CodeKinds.kind(kinds, i) == CodeKinds.CODE && Character.isWhitespace(text.charAt(i))))) {
            i--;
        }
        return i;
    }

    private static int matchingOpener(String text, byte[] kinds, int closer) {
        int depth = 0;
        for (int i = closer; i >= 0; i--) {
            if (CodeKinds.kind(kinds, i) != CodeKinds.CODE) {
                continue;
            }
            char c = text.charAt(i);
            if (c == ')' || c == ']' || c == '}') {
                depth++;
            } else if (c == '(' || c == '[' || c == '{') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Number of arguments between parentheses; named arguments ({@code f(a: 1, b: 2)}) are one map.
     */
    private static int countArguments(String text, byte[] kinds, int start, int end) {
        int depth = 0;
        int commas = 0;
        boolean named = false;
        boolean empty = true;
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (CodeKinds.kind(kinds, i) != CodeKinds.CODE) {
                if (CodeKinds.kind(kinds, i) == CodeKinds.STRING) {
                    empty = false;
                }
                continue;
            }
            if (!Character.isWhitespace(c)) {
                empty = false;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (depth == 0 && c == ',') {
                commas++;
            } else if (depth == 0 && c == ':' && (i == 0 || text.charAt(i - 1) != '?')
                    && (i + 1 >= text.length() || text.charAt(i + 1) != ':')) {
                named = true;
            }
        }
        if (empty) {
            return 0;
        }
        return named ? 1 : commas + 1;
    }
}

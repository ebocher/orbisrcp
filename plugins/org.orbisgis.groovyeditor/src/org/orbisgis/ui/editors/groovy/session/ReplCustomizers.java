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
package org.orbisgis.ui.editors.groovy.session;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ImportNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.EmptyExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Compilation customizers turning the successive executions of an editor into a REPL session.
 */
final class ReplCustomizers {

    private ReplCustomizers() {
    }

    /**
     * Turns the top-level declarations of a script ({@code def x = 1}, {@code String s = "a"}, {@code var v = 2})
     * into assignments of the script binding, so the variables are still available in the next executions.
     *
     * Declarations inside blocks (closures, loops, if...), multiple assignments ({@code def (a, b) = ...})
     * and annotated declarations ({@code @Field}...) are left unchanged and stay local to the execution.
     */
    static class ScriptVariablesCustomizer extends CompilationCustomizer {

        ScriptVariablesCustomizer() {
            super(CompilePhase.CONVERSION);
        }

        @Override
        public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
            if (!classNode.isScript()) {
                return;
            }
            MethodNode run = classNode.getMethod("run", Parameter.EMPTY_ARRAY);
            if (run == null || !(run.getCode() instanceof BlockStatement)) {
                return;
            }
            for (Statement statement : ((BlockStatement) run.getCode()).getStatements()) {
                if (!(statement instanceof ExpressionStatement)) {
                    continue;
                }
                ExpressionStatement expressionStatement = (ExpressionStatement) statement;
                if (!(expressionStatement.getExpression() instanceof DeclarationExpression)) {
                    continue;
                }
                DeclarationExpression declaration = (DeclarationExpression) expressionStatement.getExpression();
                if (declaration.isMultipleAssignmentDeclaration() || !declaration.getAnnotations().isEmpty()) {
                    continue;
                }
                VariableExpression variable = declaration.getVariableExpression();
                Expression value = declaration.getRightExpression();
                if (value instanceof EmptyExpression) {
                    value = ConstantExpression.NULL;
                }
                VariableExpression target = new VariableExpression(variable.getName());
                target.setSourcePosition(variable);
                Token assign = Token.newSymbol(Types.ASSIGN,
                        declaration.getOperation().getStartLine(), declaration.getOperation().getStartColumn());
                BinaryExpression assignment = new BinaryExpression(target, assign, value);
                assignment.setSourcePosition(declaration);
                expressionStatement.setExpression(assignment);
            }
        }
    }

    /**
     * Records the imports of a script. Once the script is compiled, the session adds them to the
     * {@link ImportCustomizer} shared by all the executions, so an import does not need to be repeated.
     */
    static class ImportCollector extends CompilationCustomizer {

        /** An import read in a script */
        private record PendingImport(String kind, String alias, String className, String field) {

            void addTo(ImportCustomizer customizer) {
                switch (kind) {
                    case "regular" -> customizer.addImport(alias, className);
                    case "star" -> customizer.addStarImports(className);
                    case "static" -> customizer.addStaticImport(alias, className, field);
                    case "staticStar" -> customizer.addStaticStars(className);
                    default -> throw new IllegalStateException(kind);
                }
            }
        }

        private final List<PendingImport> pending = new ArrayList<>();

        ImportCollector() {
            super(CompilePhase.CONVERSION);
        }

        @Override
        public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
            ModuleNode module = source.getAST();
            if (module == null) {
                return;
            }
            synchronized (pending) {
                for (ImportNode node : module.getImports()) {
                    pending.add(new PendingImport("regular", node.getAlias(), node.getClassName(), null));
                }
                for (ImportNode node : module.getStarImports()) {
                    String packageName = node.getPackageName();
                    if (packageName.endsWith(".")) {
                        packageName = packageName.substring(0, packageName.length() - 1);
                    }
                    pending.add(new PendingImport("star", null, packageName, null));
                }
                module.getStaticImports().forEach((alias, node) ->
                        pending.add(new PendingImport("static", alias, node.getClassName(), node.getFieldName())));
                module.getStaticStarImports().forEach((alias, node) ->
                        pending.add(new PendingImport("staticStar", null, node.getClassName(), null)));
            }
        }

        /**
         * Forget the imports of a script which failed to compile.
         */
        void discard() {
            synchronized (pending) {
                pending.clear();
            }
        }

        /**
         * Add the imports of the last compiled script to the import customizer of the session.
         *
         * @param target the import customizer of the session
         * @param known  the imports already added, used to avoid duplicates
         */
        void commit(ImportCustomizer target, Set<Object> known) {
            List<PendingImport> imports;
            synchronized (pending) {
                imports = new ArrayList<>(pending);
                pending.clear();
            }
            for (PendingImport pendingImport : imports) {
                if (known.add(pendingImport)) {
                    pendingImport.addTo(target);
                }
            }
        }

        /**
         * @return the simple names (or aliases) of the classes imported in the session
         */
        static Map<String, String> importedClasses(Set<Object> known) {
            Map<String, String> classes = new TreeMap<>();
            for (Object o : known) {
                if (o instanceof PendingImport p && "regular".equals(p.kind())) {
                    classes.put(p.alias(), p.className());
                }
            }
            return classes;
        }

        /**
         * @return the packages imported with a star in the session
         */
        static List<String> importedPackages(Set<Object> known) {
            List<String> packages = new ArrayList<>();
            for (Object o : known) {
                if (o instanceof PendingImport p && "star".equals(p.kind())) {
                    packages.add(p.className());
                }
            }
            return packages;
        }
    }
}

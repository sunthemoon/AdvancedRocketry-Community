package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Attributed, call-site-bound source audit, including forwarded callbacks and fixture methods. */
class GameTestSchedulingSourceTest {
    private static final String FRAMEWORK = "net.minecraft.gametest.framework.";
    private static final Set<String> REGISTRATIONS = Set.of("onEachTick", "runAtTickTime", "runAfterDelay",
            "failIfEver", "succeedWhen", "succeedOnTickWhen", "succeedOnTick", "succeedIf", "failIf",
            "startSequence", "createSequence", "setRunAtTickTime");
    @TempDir Path temporary;

    @Test
    void scheduledCallbacksCannotReachAnotherVanillaRegistration() throws IOException {
        List<Path> sources;
        try (var files = Files.walk(Path.of("src/main/java"))) {
            sources = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("gametest.framework")).sorted().toList();
        }
        assertTrue(sources.size() > 100, "The audit must cover the complete GameTest source tree");
        var violations = audit(sources);
        assertEquals(Set.of(), violations, "Nested GameTest registrations:\n" + String.join("\n", violations));
    }

    @Test
    void rejectsDirectNestingAndRecursivePolling() throws IOException {
        assertTrue(check("h.onEachTick(() -> h.runAfterDelay(1, () -> {}));", "").stream()
                .anyMatch(line -> line.endsWith("runAfterDelay")));
        assertTrue(check("poll(h);", "void poll(GameTestHelper h) { h.runAfterDelay(1, () -> poll(h)); }")
                .stream().anyMatch(line -> line.endsWith("runAfterDelay")));
    }

    @Test
    void coversEveryHelperRegistrationFamily() throws IOException {
        for (var registration : List.of("h.onEachTick(() -> {})", "h.runAtTickTime(2, () -> {})",
                "h.runAfterDelay(1, () -> {})", "h.failIfEver(() -> {})", "h.succeedWhen(() -> {})",
                "h.succeedOnTickWhen(2, () -> {})", "h.succeedIf(() -> {})", "h.failIf(() -> {})",
                "h.startSequence()")) {
            assertTrue(!check("h.runAfterDelay(1, () -> { " + registration + "; });", "").isEmpty(),
                    "Missed registration: " + registration);
        }
    }

    @Test
    void rejectsForwardedCallbacksAndMethodReferences() throws IOException {
        assertTrue(check("waitFor(h, () -> h.onEachTick(() -> {}));",
                "void waitFor(GameTestHelper h, Runnable action) { h.runAfterDelay(1, action); }")
                .stream().anyMatch(line -> line.endsWith("onEachTick")));
        assertTrue(check("waitFor(h, () -> guard(() -> waitFor(h, () -> guard(() -> later(h)))));",
                "void waitFor(GameTestHelper h, Runnable action) { h.runAfterDelay(1, action); }"
                + " void guard(Runnable action) { action.run(); }"
                + " void later(GameTestHelper h) { h.onEachTick(() -> {}); }")
                .stream().anyMatch(line -> line.endsWith("onEachTick")));
        assertTrue(check("waitFor(h, () -> waitFor(h, () -> h.onEachTick(() -> {})));",
                "void waitFor(GameTestHelper h, Runnable action) { h.runAfterDelay(1, action); }")
                .stream().anyMatch(line -> line.endsWith("onEachTick")));
        assertTrue(check("h.onEachTick(this::later);",
                "void later() { h.runAfterDelay(1, () -> {}); }").stream()
                .anyMatch(line -> line.endsWith("runAfterDelay")));
    }

    @Test
    void rejectsStoredCallbacksAndRegistrationInsideConstructors() throws IOException {
        assertTrue(check("Runnable action = () -> h.runAfterDelay(1, () -> {}); h.onEachTick(action);", "")
                .stream().anyMatch(line -> line.endsWith("runAfterDelay")));
        assertTrue(check("h.onEachTick(() -> new Fixture(h));",
                "class Fixture { Fixture(GameTestHelper h) { h.onEachTick(() -> {}); } }")
                .stream().anyMatch(line -> line.endsWith("onEachTick")));
        assertTrue(check("h.onEachTick(new Runnable() { public void run() { h.runAfterDelay(1, () -> {}); } });", "")
                .stream().anyMatch(line -> line.endsWith("runAfterDelay")));
    }

    @Test
    void sequenceCallbacksAlsoCannotRegisterMoreWork() throws IOException {
        assertTrue(check("h.startSequence().thenExecute(() -> h.runAfterDelay(1, () -> {}));", "")
                .stream().anyMatch(line -> line.endsWith("runAfterDelay")));
    }

    @Test
    void synchronousGuardedSetupDoesNotBecomeAScheduledCallback() throws IOException {
        assertEquals(Set.of(), check("guard(() -> h.onEachTick(() -> guard(() -> {})));",
                "void guard(Runnable action) { action.run(); }"));
    }

    @Test
    void tickDriverTransitionsDoNotRegisterAnotherVanillaCallback() throws IOException {
        assertEquals(Set.of(), check("var driver = new "
                + "io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickDriver(h);"
                + " driver.after(1, () -> driver.after(2, () -> {}));", ""));
    }

    @Test
    void permitsIndependentSetupTimersAndIgnoresCommentsAndStrings() throws IOException {
        assertEquals(Set.of(), check("h.runAfterDelay(1, () -> {}); h.runAtTickTime(2, () -> {});"
                + " String text = \"h.onEachTick(() -> h.runAfterDelay(1, x))\";"
                + " /* h.onEachTick(() -> h.runAfterDelay(1, x)) */", ""));
    }

    private Set<String> check(String body, String helpers) throws IOException {
        Path source = temporary.resolve("AuditCase.java");
        Files.writeString(source, "import net.minecraft.gametest.framework.*; class AuditCase {"
                + " GameTestHelper h; @net.minecraft.gametest.framework.GameTest(template=\"empty\") void test(GameTestHelper h) { this.h=h; "
                + body + " } " + helpers + " }", StandardCharsets.UTF_8);
        return audit(List.of(source));
    }

    private static String read(Path path) {
        try { return Files.readString(path); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    static Set<String> audit(List<Path> sources) throws IOException {
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "The scheduling audit requires the configured JDK 17, not a JRE");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            String classpath = System.getProperty("arce.apiPlatformClasspath") + java.io.File.pathSeparator
                    + Path.of("build/classes/java/main").toAbsolutePath();
            var task = (JavacTask) compiler.getTask(null, files, diagnostics,
                    List.of("-proc:none", "-implicit:none", "--release", "17", "-classpath", classpath),
                    null, files.getJavaFileObjectsFromPaths(sources));
            var units = new ArrayList<CompilationUnitTree>();
            task.parse().forEach(units::add);
            task.analyze();
            assertTrue(diagnostics.getDiagnostics().stream().noneMatch(d -> d.getKind() == Diagnostic.Kind.ERROR),
                    () -> "Cannot attribute GameTest sources: " + diagnostics.getDiagnostics());
            var trees = Trees.instance(task);
            var methods = new HashMap<Element, TreePath>();
            var calls = new HashMap<Element, Set<Element>>();
            var roots = new ArrayList<TreePath>();
            for (var unit : units) {
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitMethod(MethodTree method, Void unused) {
                        methods.put(trees.getElement(getCurrentPath()), getCurrentPath());
                        if (method.getModifiers().getAnnotations().stream()
                                .anyMatch(a -> a.getAnnotationType().toString().equals("GameTest")
                                        || a.getAnnotationType().toString().equals(FRAMEWORK + "GameTest"))) {
                            roots.add(getCurrentPath());
                        }
                        return super.visitMethod(method, unused);
                    }

                    @Override
                    public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                        TreePath enclosing = getCurrentPath();
                        while (enclosing != null && !(enclosing.getLeaf() instanceof MethodTree)) {
                            enclosing = enclosing.getParentPath();
                        }
                        Element target = trees.getElement(getCurrentPath());
                        if (enclosing != null && target != null) {
                            calls.computeIfAbsent(trees.getElement(enclosing), key -> new HashSet<>()).add(target);
                        }
                        return super.visitMethodInvocation(call, unused);
                    }
                }.scan(unit, null);
            }
            assertTrue(!roots.isEmpty(), "The audit found no GameTest entry points");
            var deferred = new HashSet<Element>();
            boolean changed;
            do {
                changed = false;
                for (var entry : calls.entrySet()) {
                    if (entry.getValue().stream().anyMatch(target -> deferred.contains(target)
                            || target.getEnclosingElement().toString().startsWith(FRAMEWORK)
                                && REGISTRATIONS.contains(target.getSimpleName().toString())
                            || target.getEnclosingElement().toString().endsWith(".GameTestTickDriver")
                                && Set.of("after", "at").contains(target.getSimpleName().toString()))) {
                        changed |= deferred.add(entry.getKey());
                    }
                }
            } while (changed);
            var violations = new TreeSet<String>();
            for (var root : roots) {
                new Scanner(trees, methods, deferred, violations, false, new HashMap<>(), new HashSet<>())
                        .scan(new TreePath(root, ((MethodTree) root.getLeaf()).getBody()), null);
            }
            return violations;
        }
    }

    private record Binding(TreePath value, Map<Element, Binding> environment) { }
    private record Context(Element method, boolean scheduled) { }

    private static final class Scanner extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final Map<Element, TreePath> methods;
        private final Set<Element> deferred;
        private final Set<String> violations;
        private final boolean scheduled;
        private final Map<Element, Binding> environment;
        private final Set<Context> active;

        private Scanner(Trees trees, Map<Element, TreePath> methods, Set<Element> deferred,
                Set<String> violations, boolean scheduled,
                Map<Element, Binding> environment, Set<Context> active) {
            this.trees = trees; this.methods = methods; this.deferred = deferred;
            this.violations = violations; this.scheduled = scheduled;
            this.environment = environment; this.active = active;
        }

        private void execute(TreePath value, Map<Element, Binding> bindings, boolean inCallback) {
            Tree leaf = value.getLeaf();
            if (leaf instanceof IdentifierTree) {
                Binding binding = bindings.get(trees.getElement(value));
                if (binding != null) { execute(binding.value(), binding.environment(), inCallback); }
            } else if (leaf instanceof LambdaExpressionTree lambda) {
                // Walk lexical continuations as well: repeated helper invocations can introduce deeper lambdas
                // while the call-graph recursion bound is active. Never merge different callers' bindings.
                if (inCallback) { inspectLexical(new TreePath(value, lambda.getBody())); }
                new Scanner(trees, methods, deferred, violations, inCallback, new HashMap<>(bindings), active)
                        .scan(new TreePath(value, lambda.getBody()), null);
            } else if (leaf instanceof MemberReferenceTree) {
                invoke(trees.getElement(value), value, List.of(), bindings, inCallback);
            } else if (leaf instanceof ParenthesizedTree parentheses) {
                execute(new TreePath(value, parentheses.getExpression()), bindings, inCallback);
            } else if (leaf instanceof TypeCastTree cast) {
                execute(new TreePath(value, cast.getExpression()), bindings, inCallback);
            } else if (leaf instanceof NewClassTree anonymous && anonymous.getClassBody() != null) {
                var declaration = new TreePath(value, anonymous.getClassBody());
                for (var member : anonymous.getClassBody().getMembers()) {
                    if (member instanceof MethodTree method && method.getBody() != null
                            && !method.getName().contentEquals("<init>")) {
                        new Scanner(trees, methods, deferred, violations, inCallback, new HashMap<>(bindings), active)
                                .scan(new TreePath(new TreePath(declaration, method), method.getBody()), null);
                    }
                }
            }
        }

        private void inspectLexical(TreePath body) {
            inspectLexical(body, new HashSet<>());
        }

        private void inspectLexical(TreePath body, Set<Element> visited) {
            new TreePathScanner<Void, Void>() {
                private void inspect(Element target) {
                    if (isRegistration(target)) { record(getCurrentPath(), target); }
                    else if (methods.containsKey(target) && visited.add(target)
                            && !target.getEnclosingElement().toString().endsWith(".GameTestTickDriver")) {
                        var declaration = methods.get(target);
                        var method = (MethodTree) declaration.getLeaf();
                        if (method.getBody() != null) {
                            inspectLexical(new TreePath(declaration, method.getBody()), visited);
                        }
                    }
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
                    inspect(trees.getElement(getCurrentPath()));
                    return super.visitMethodInvocation(call, unused);
                }

                @Override
                public Void visitMemberReference(MemberReferenceTree reference, Void unused) {
                    inspect(trees.getElement(getCurrentPath()));
                    return super.visitMemberReference(reference, unused);
                }
            }.scan(body, null);
        }

        private boolean isRegistration(Element target) {
            return target != null && target.getEnclosingElement().toString().startsWith(FRAMEWORK)
                    && REGISTRATIONS.contains(target.getSimpleName().toString());
        }

        private void record(TreePath call, Element target) {
            var unit = call.getCompilationUnit();
            long offset = trees.getSourcePositions().getStartPosition(unit, call.getLeaf());
            violations.add(Path.of(unit.getSourceFile().toUri()).getFileName() + ":"
                    + unit.getLineMap().getLineNumber(offset) + " " + target.getSimpleName());
        }

        private void invoke(Element target, TreePath call, List<? extends ExpressionTree> arguments,
                Map<Element, Binding> bindings, boolean inCallback) {
            if (target == null) { return; }
            String owner = target.getEnclosingElement().toString();
            String name = target.getSimpleName().toString();
            boolean registration = isRegistration(target);
            if (registration && inCallback) {
                record(call, target);
            }
            if (registration || owner.equals(FRAMEWORK + "GameTestSequence")
                    || owner.endsWith(".GameTestTickDriver") && Set.of("after", "at").contains(name)) {
                for (var argument : arguments) { execute(new TreePath(call, argument), bindings, true); }
                return;
            }
            if (deferred.contains(target)) {
                // A helper can run its continuation on a later tick even when its first call is test setup.
                // Check the actual callback arguments before bounding recursive helper-body traversal.
                for (var argument : arguments) { execute(new TreePath(call, argument), bindings, true); }
            }
            TreePath declaration = methods.get(target);
            var context = new Context(target, inCallback);
            if (declaration == null || !active.add(context)) { return; }
            try {
                var next = new HashMap<>(bindings);
                var parameters = ((ExecutableElement) target).getParameters();
                for (int i = 0; i < Math.min(parameters.size(), arguments.size()); i++) {
                    next.put(parameters.get(i), new Binding(new TreePath(call, arguments.get(i)),
                            new HashMap<>(bindings)));
                }
                var body = ((MethodTree) declaration.getLeaf()).getBody();
                if (body != null) {
                    new Scanner(trees, methods, deferred, violations, inCallback, next, active)
                            .scan(new TreePath(declaration, body), null);
                }
            } finally { active.remove(context); }
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
            Element target = trees.getElement(getCurrentPath());
            if (call.getMethodSelect() instanceof MemberSelectTree select && target != null
                    && (target.getEnclosingElement().toString().equals("java.lang.Runnable")
                        || target.getEnclosingElement().toString().startsWith("java.util.function."))) {
                execute(new TreePath(new TreePath(getCurrentPath(), select), select.getExpression()), environment, scheduled);
            } else { invoke(target, getCurrentPath(), call.getArguments(), environment, scheduled); }
            return super.visitMethodInvocation(call, unused);
        }

        @Override
        public Void visitNewClass(NewClassTree constructor, Void unused) {
            invoke(trees.getElement(getCurrentPath()), getCurrentPath(), constructor.getArguments(), environment, scheduled);
            return super.visitNewClass(constructor, unused);
        }

        @Override
        public Void visitVariable(VariableTree variable, Void unused) {
            if (variable.getInitializer() != null) {
                environment.put(trees.getElement(getCurrentPath()), new Binding(
                        new TreePath(getCurrentPath(), variable.getInitializer()), new HashMap<>(environment)));
            }
            return super.visitVariable(variable, unused);
        }

        @Override
        public Void visitAssignment(AssignmentTree assignment, Void unused) {
            environment.put(trees.getElement(new TreePath(getCurrentPath(), assignment.getVariable())),
                    new Binding(new TreePath(getCurrentPath(), assignment.getExpression()), new HashMap<>(environment)));
            return super.visitAssignment(assignment, unused);
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree lambda, Void unused) { return null; }
    }
}

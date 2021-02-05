package io.github.liambloom.tests;

import java.io.*;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Supplier;
import java.lang.Runnable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.fusesource.jansi.AnsiConsole;
import static org.fusesource.jansi.Ansi.*;
import static org.fusesource.jansi.Ansi.Color.*;

public class Tester implements Closeable {
    public static enum Policy {
        RunLast, RunAll, RunUntilFailure;
    }

    private static class BoundMethod {
        public final Method method;
        public final Object[] args;

        public BoundMethod(Method method, Object... args) {
            this.method = method;
            this.args = args;
        }

        public Object invoke(Object target) throws IllegalAccessException, InvocationTargetException {
            return method.invoke(target, args);
        }
    }

    private static class RelayStream extends OutputStream {
        private final Queue<BoundMethod> queue = new LinkedList<>();

        public void close() {
            try { 
                queue.add(new BoundMethod(OutputStream.class.getMethod("close"))); 
            }
            catch (NoSuchMethodException e) { 
                // This should never happen
            }
        }

        public void flush() {
            try { 
                queue.add(new BoundMethod(OutputStream.class.getMethod("flush"))); 
            }
            catch (NoSuchMethodException e) { 
                // This should never happen
            }
        }

        public void write(byte[] b) {
            try {
                queue.add(new BoundMethod(OutputStream.class.getMethod("write", byte[].class), b));
            } catch (NoSuchMethodException e) {
                // This should never happen
            }
        }

        public void write(byte[] b, int off, int len) {
            try {
                queue.add(new BoundMethod(OutputStream.class.getMethod("write", byte[].class, int.class, int.class), b, off, len));
            } catch (NoSuchMethodException e) {
                // This should never happen
            }
        }

        public void write(int b) {
            try {
                queue.add(new BoundMethod(OutputStream.class.getMethod("write", int.class), b));
            } catch (NoSuchMethodException e) {
                // This should never happen
            }
        }

        public void relay(OutputStream target) {
            try {
                while (!queue.isEmpty()) {
                    queue.remove().invoke(target);
                }
            }
            catch (Exception e) {
                // This should never happen
            }
        }
    }

    public final Policy policy;
    private boolean blocked = false;
    private Supplier<Boolean> last;
    private int i = 1;

    public Tester() {
        this(Policy.RunAll);
    }

    public Tester(Policy policy) {
        this.policy = policy;
        AnsiConsole.systemInstall();
    }

    public <T> Tester test(Supplier<T> lhs, T rhs) {
        return test(Integer.toString(i), lhs, rhs);
    }

    public <T> Tester test(String name, Supplier<T> lhs, T rhs) {
        if (!blocked) {  
            i++;
            if (policy == Policy.RunLast) 
                last = () -> this.runTest(name, lhs, rhs);
            else {
                if (!runTest(name, lhs, rhs) && policy == Policy.RunUntilFailure)
                    blocked = true;
            }
        }
        return this;
    }

    public <T> Tester testOutput(Runnable lhs, String rhs) {
        return testOutput(Integer.toString(i), lhs, rhs);
    }

    public <T> Tester testOutput(String name, Runnable lhs, String rhs) {
        return test(name, () -> {
            final PrintStream out = System.out;
            final PrintStream err = System.err;
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            System.setOut(new PrintStream(stream));
            System.setErr(new PrintStream(stream));
            lhs.run();
            System.setOut(out);
            System.setErr(err);
            try {
                System.out.write(stream.toByteArray());
                return stream.toString(System.getProperty("file.encoding"));
            }
            catch (IOException e) {
                // OH NO!
                return "There was an error relaying the output";
            }
        }, rhs);
    }

    private <T> boolean runTest(String name, Supplier<T> lhs, T rhs) {
        System.out.printf("Test %s: ", name);
        
        final PrintStream out = System.out;
        final PrintStream err = System.err;
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stream));
        System.setErr(new PrintStream(stream));

        T lhsOutput = lhs.get();

        System.setOut(out);
        System.setErr(err);

        if (lhsOutput.equals(rhs)) {
            System.out.println(ansi().fg(GREEN).a("success").reset());
            return true;
        }
        else {
            System.out.println(ansi().fg(RED).a("failed").reset());
            System.out.println("\tlhs: " + lhs);
            System.out.println("\trhs: " + rhs);
            try {
                final String output = stream.toString(System.getProperty("file.encoding"));
                if (!output.isEmpty()) {
                    System.out.println("Console output:");
                    System.out.println(output);
                }
            }
            catch (IOException e) {
                System.out.println("Unable to capture console output");
            }
            return false;
        }
    }

    public void close() {
        if (policy == Policy.RunLast)
            last.get();
        AnsiConsole.systemUninstall();
    }
}

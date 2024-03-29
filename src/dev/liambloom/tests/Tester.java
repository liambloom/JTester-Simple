package dev.liambloom.tests;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.lang.Runnable;
import org.fusesource.jansi.AnsiConsole;
import static org.fusesource.jansi.Ansi.*;
import static org.fusesource.jansi.Ansi.Color.*;

public class Tester implements Closeable {
    public enum Policy {
        RunLast, RunAll, RunUntilFailure
    }

    private static final Map<Class<?>, Class<?>> WRAPPER_TO_PRIMITIVE = new HashMap<>();
    static {
        WRAPPER_TO_PRIMITIVE.put(Byte.class, Byte.TYPE);
        WRAPPER_TO_PRIMITIVE.put(Short.class, Short.TYPE);
        WRAPPER_TO_PRIMITIVE.put(Integer.class, Integer.TYPE);
        WRAPPER_TO_PRIMITIVE.put(Long.class, Long.TYPE);
        WRAPPER_TO_PRIMITIVE.put(Float.class, Float.TYPE);
        WRAPPER_TO_PRIMITIVE.put(Double.class, Double.TYPE);
        WRAPPER_TO_PRIMITIVE.put(Boolean.class, Boolean.TYPE);
        WRAPPER_TO_PRIMITIVE.put(Character.class, Character.TYPE);
    }

    public final Policy policy;
    private boolean blocked = false;
    private Supplier<Boolean> last;
    private int i = 1;

    public Tester() {
        this(Policy.RunAll);
    }

    public Tester(Policy policy) {
        /* if (policy.name().startsWith("_"))
            throw new IllegalArgumentException("You many not use an internal Policy variant (indicated by a beginning underscore)"); */
        this.policy = policy;
        AnsiConsole.systemInstall();
    }

    public Tester testAssert(Supplier<Boolean> assertion) {
        return testAssert(defaultName(), assertion);
    }

    public Tester testAssert(String name, Supplier<Boolean> assertion) {
        return test(name, assertion, true);
    }

    public <T> Tester test(Supplier<T> lhs, T rhs) {
        return test(defaultName(), lhs, rhs);
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

    public Tester testOutput(Runnable lhs, String rhs) {
        return testOutput(defaultName(), lhs, rhs);
    }

    /**
     * Takes a no-argument method and checks that its console output ({@code System.out} and {@code System.err}) is correct. You do
     * not need to worry about lined endings, as all occurrences of {@code CRLF} or {@code LF} within either string will be replaced
     * with {@link System#lineSeparator()}.
     * 
     * @param name The name of the test
     * @param lhs A function with no arguments or return
     * @param rhs The expected console output of {@code lhs}
     * @return itself
     */
    public Tester testOutput(String name, Runnable lhs, String rhs) {
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
                return stream.toString(System.getProperty("file.encoding")).replaceAll("\\r?\\n", System.lineSeparator());
            }
            catch (IOException e) {
                // OH NO!
                return "There was an error relaying the output";
            }
        }, rhs.replaceAll("\\r?\\n", System.lineSeparator()));
    }

    public <T> Tester testThis(T lhs, Method method, Object[] args, T rhs)
            throws IllegalAccessException
    {
        return testThis(defaultName(), lhs, method, args, rhs);
    }

    public <T> Tester testThis(String name, T lhs, Method method, Object[] args, T rhs)
        throws IllegalAccessException
    {
        if (lhs == null)
            throw new NullPointerException("Cannot invoke method on null");
        if (!method.getDeclaringClass().isInstance(lhs))
            throw new IllegalArgumentException(String.format("Method declared on type %s cannot be invoked on type %s" + method.getDeclaringClass(), lhs.getClass()));
        final int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers))
            throw new IllegalAccessException("Cannot test non-public method");
            // or maybe instead, method.setAccessible(true);
        if (Modifier.isStatic(modifiers))
            throw new IllegalArgumentException("Cannot invoke static method on instance");
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != args.length)
            throw new IllegalArgumentException("The amount of actual and formal parameters may not differ");
        for (int i = 0; i < args.length; i++) {
            if (!(parameterTypes[0].isPrimitive()
                ? parameterTypes[0].equals(WRAPPER_TO_PRIMITIVE.getOrDefault(args[i].getClass(), null))
                : parameterTypes[i].isInstance(args[i])))
                    throw new IllegalArgumentException("The types of actual and formal parameters may not differ");
        }
        return test(name, () -> {
            try {
                method.invoke(lhs, args);
            }
            catch (NullPointerException | IllegalArgumentException | IllegalAccessException | ExceptionInInitializerError e) {
                System.err.println("This shouldn't happen");
                e.printStackTrace();
                System.exit(1);
            }
            catch (InvocationTargetException e) {
                return rhs == null ? lhs : null;
            }
            return lhs;
        }, rhs);
    }

    protected <T> boolean runTest(String name, Supplier<T> lhs, T rhs) {
        System.out.print(name + ": ");
        
        final PrintStream out = System.out;
        final PrintStream err = System.err;
        final ByteArrayOutputStream stream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stream));
        System.setErr(new PrintStream(stream));

        T lhsOutput;
        Throwable error = null;
        try {
            lhsOutput = lhs.get();
        }
        catch (Throwable e) {
            lhsOutput = null;
            e.printStackTrace();
            error = e;
        }

        System.setOut(out);
        System.setErr(err);

        if (error == null && (Objects.equals(lhsOutput, rhs))) {
            System.out.println(ansi().fg(GREEN).a("success").reset());
            return true;
        }
        else {
            System.out.println(ansi().fg(RED).a("failed").reset());
            System.out.println("\tlhs: " + (error == null 
                ? lhsOutput == null 
                    ? "null" 
                    : lhsOutput.toString().replaceAll("\\r?\\n", System.lineSeparator()) 
                : error.getClass().getSimpleName()));
            System.out.println("\trhs: " + (rhs == null ? "null": rhs.toString().replaceAll("\\r?\\n", System.lineSeparator())));

            try {
                final String output = stream.toString(System.getProperty("file.encoding"));
                if (!output.isEmpty()) {
                    System.out.println("\tConsole output:");
                    System.out.println("\t\t" + output.replace("\n", "\n\t\t"));
                }
            }
            catch (IOException e) {
                System.out.println("Unable to capture console output");
            }
            return false;
        }
    }

    private String defaultName() {
        return "Test " + i;
    }

    public void close() {
        if (policy == Policy.RunLast)
            last.get();
        AnsiConsole.systemUninstall();
    }
}

package io.github.liambloom.tests;

import java.io.*;
import java.util.function.Supplier;
import java.lang.Runnable;
import org.fusesource.jansi.AnsiConsole;
import static org.fusesource.jansi.Ansi.*;
import static org.fusesource.jansi.Ansi.Color.*;

public class Tester implements Closeable {
    public static enum Policy {
        RunLast, RunAll, RunUntilFailure;
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

    public <T> Tester test(Supplier<T> lhs, T rhs) {
        return test("Test " + i, lhs, rhs);
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
        return testOutput("Test " + i, lhs, rhs);
    }

    /**
     * Takes a no-argument method and checks that its console output ({@code System.out} and {@code System.err}) is correct. You do
     * not need to worry about lined endings, as all occurences of {@code CRLF} or {@code LF} within either string will be replaced 
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

        if (error == null && (lhsOutput == null ? rhs == null : lhsOutput.equals(rhs))) {
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

    public void close() {
        if (policy == Policy.RunLast)
            last.get();
        AnsiConsole.systemUninstall();
    }
}

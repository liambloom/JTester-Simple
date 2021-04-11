# JTester

JTester-Simple is a small java library meant for testing. Currently, it is the only version of JTester, but, in future, more verbose versions may be released.

### Example Usage

```java
// Import the Tester class
import dev.liambloom.tests.Tester;

public class MyClass {
    public static void main(String[] args) {
        // Initialize a Tester object
        final Tester tester = new Tester(Tester.Policy.RunUntilFailure);

        // Tests can be chained
        tester
            .test(MyClass::twoPlusTwo, 4) // Tests that MyClass#twoPlusTwo returns 4
            .test(() -> MyClass.add(3, 7), 10) // Tests that MyClass.add(3, 7) returns 10
            .test("Named Test", () -> MyClass.add(4, 5), 9) // You get the idea
            .testOutput(MyClass::printSomething, "Something\n") // Tests that MyClass#printSomething prints "Something\n"
            .test(MyClass::printSomethingAndReturn3, 2) // Fails because 3 != 2
            .test("One more", () -> "foo", "foo"); // This won't run because the tester is set to RunUntilFailure
        
        tester.close();
    }

    public static int twoPlusTwo() {
        return add(2, 2);
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public static void printSomething() {
        System.out.println("Something");
    }

    public static int printSomethingAndReturn3() {
        System.out.println("Something");
        return 3
    }
}
```

Output:
```
Test 1: success
Test 2: success
Named Test: success
Test 4: success
Test 5: failed
    lhs: 3
    rhs: 2
    Console output:
    Something

```

### Copying

This code is licensed under `Apache-2.0 OR MIT` licenses, meaning that you can choose one of the licenses to follow.

### Download

Currently, there is no download. Sorry  &#175;\\\_(&#x30C4;)_/&#175;.

You can still clone it or download the file, the only dependency is [Jansi](https://github.com/fusesource/jansi)
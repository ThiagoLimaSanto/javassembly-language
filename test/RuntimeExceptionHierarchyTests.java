package test;

import compiler.exceptions.runtime.AbstractMethodError;
import compiler.exceptions.runtime.ArithmeticException;
import compiler.exceptions.runtime.Error;
import compiler.exceptions.runtime.IncompatibleClassChangeError;
import compiler.exceptions.runtime.IndexOutOfBoundsException;
import compiler.exceptions.runtime.InputMismatchException;
import compiler.exceptions.runtime.NoSuchElementException;
import compiler.exceptions.runtime.OutOfMemoryError;
import compiler.exceptions.runtime.RuntimeException;
import compiler.exceptions.runtime.StackOverflowError;
import compiler.exceptions.runtime.Throwable;
import compiler.exceptions.runtime.VirtualMachineError;

public final class RuntimeExceptionHierarchyTests {
    private RuntimeExceptionHierarchyTests() {
    }

    public static void main(String[] args) {
        Throwable bounds = new IndexOutOfBoundsException();
        if (!(bounds instanceof RuntimeException)) {
            throw new AssertionError("IndexOutOfBoundsException must extend RuntimeException.");
        }
        if (!(new InputMismatchException() instanceof NoSuchElementException)) {
            throw new AssertionError("InputMismatchException must extend NoSuchElementException.");
        }
        if (!(new OutOfMemoryError() instanceof VirtualMachineError)
                || !(new OutOfMemoryError() instanceof Error)) {
            throw new AssertionError("OutOfMemoryError must follow the Error hierarchy.");
        }
        if (!(new StackOverflowError() instanceof VirtualMachineError)
                || !(new StackOverflowError() instanceof Error)) {
            throw new AssertionError("StackOverflowError must follow the Error hierarchy.");
        }
        if (!(new AbstractMethodError() instanceof IncompatibleClassChangeError)) {
            throw new AssertionError("AbstractMethodError must extend IncompatibleClassChangeError.");
        }
        if (!bounds.diagnostic().equals(
                "IndexOutOfBoundsException: Índice fora dos limites do array.\n")) {
            throw new AssertionError("Runtime exception diagnostic format changed unexpectedly.");
        }
        if (RuntimeException.class.getSuperclass()
                != compiler.exceptions.runtime.Exception.class) {
            throw new AssertionError("The language RuntimeException must use its own Exception base.");
        }
        System.out.println("Runtime exception hierarchy checks passed.");
    }
}

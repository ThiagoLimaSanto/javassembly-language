package test;

import src.exceptions.runtime.AbstractMethodError;
import src.exceptions.runtime.ArithmeticException;
import src.exceptions.runtime.Error;
import src.exceptions.runtime.IndexOutOfBoundsException;
import src.exceptions.runtime.InputMismatchException;
import src.exceptions.runtime.IncompatibleClassChangeError;
import src.exceptions.runtime.NoSuchElementException;
import src.exceptions.runtime.OutOfMemoryError;
import src.exceptions.runtime.RuntimeException;
import src.exceptions.runtime.Throwable;
import src.exceptions.runtime.VirtualMachineError;

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
        if (!(new AbstractMethodError() instanceof IncompatibleClassChangeError)) {
            throw new AssertionError("AbstractMethodError must extend IncompatibleClassChangeError.");
        }
        if (!bounds.diagnostic().equals(
                "IndexOutOfBoundsException: Índice fora dos limites do array.\n")) {
            throw new AssertionError("Runtime exception diagnostic format changed unexpectedly.");
        }
        if (RuntimeException.class.getSuperclass()
                != src.exceptions.runtime.Exception.class) {
            throw new AssertionError("The language RuntimeException must use its own Exception base.");
        }
        System.out.println("Runtime exception hierarchy checks passed.");
    }
}

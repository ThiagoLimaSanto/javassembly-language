package compiler.backend.asm;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import compiler.TargetPlatform;
import compiler.ast.records.AttributeRecord;
import compiler.ast.records.ClassRecord;
import compiler.ast.records.ProgramRecord;
import compiler.backend.ir.IRInstruction;
import compiler.backend.ir.IROpCode;
import compiler.backend.ir.IRProgram;
import compiler.backend.ir.Operand;
import compiler.backend.memory.StaticFieldSupport;
import compiler.backend.memory.VirtualMethodTable;
import compiler.exceptions.CompilerInvariantException;
import compiler.exceptions.InvalidIRException;
import compiler.exceptions.UnsupportedBackendFeatureException;
import compiler.exceptions.runtime.AbstractMethodError;
import compiler.exceptions.runtime.ArrayStoreException;
import compiler.exceptions.runtime.ArithmeticException;
import compiler.exceptions.runtime.IndexOutOfBoundsException;
import compiler.exceptions.runtime.IOException;
import compiler.exceptions.runtime.InputMismatchException;
import compiler.exceptions.runtime.InvalidFreeException;
import compiler.exceptions.runtime.InvalidReferenceException;
import compiler.exceptions.runtime.NegativeArraySizeException;
import compiler.exceptions.runtime.NoSuchElementException;
import compiler.exceptions.runtime.NullPointerException;
import compiler.exceptions.runtime.OutOfMemoryError;
import compiler.exceptions.runtime.StackOverflowError;
import compiler.exceptions.runtime.Throwable;
import compiler.semantic.BuiltinExceptionTypes;
import compiler.semantic.EntryPointValidator;

public class NasmGenerator {

    private record RuntimeExceptionSpec(
            String typeName,
            String diagnosticLabel,
            Throwable diagnostic,
            String objectLabel,
            String vtableLabel,
            String typeLabel,
            String messageLabel) {
    }

    private final Map<Operand, Integer> stackOffsets = new LinkedHashMap<>();
    private final Map<String, String> stringLabels = new LinkedHashMap<>();
    private final Set<String> arrayTypeNames = new LinkedHashSet<>();
    private int frameSize;
    private int nextDivisionLabel;
    private int nextStringLabel;
    private int nextGpParameter;
    private int nextXmmParameter;
    private int nextStackParameter;
    private int nextFreeLabel;
    private final VirtualMethodTable virtualMethodTable;
    private final ProgramRecord sourceProgram;
    private final Map<String, ClassRecord> sourceClassesByName;
    private final TargetPlatform target;

    public NasmGenerator() {
        this.virtualMethodTable = null;
        this.sourceProgram = null;
        this.sourceClassesByName = Map.of();
        this.target = TargetPlatform.LINUX_X64;
    }

    public NasmGenerator(ProgramRecord program) {
        this(program, TargetPlatform.LINUX_X64);
    }

    public NasmGenerator(ProgramRecord program, TargetPlatform target) {
        this.virtualMethodTable = new VirtualMethodTable(program);
        this.sourceProgram = program;
        Map<String, ClassRecord> classesByName = new LinkedHashMap<>();
        for (ClassRecord classRecord : program.classes()) {
            classesByName.put(classRecord.name(), classRecord);
        }
        this.sourceClassesByName = Map.copyOf(classesByName);
        this.target = target;
    }

    public String generate(IRProgram program) {
        if (sourceProgram != null) {
            EntryPointValidator.validate(sourceProgram);
        }
        nextFreeLabel = 0;
        StringBuilder asm = new StringBuilder();
        prepareStringLabels(program.instructions());
        appendHeader(asm, program.instructions());

        List<IRInstruction> function = new ArrayList<>();

        for (IRInstruction instruction : program.instructions()) {
            if (isFunctionLabel(instruction)) {
                if (!function.isEmpty()) {
                    emitFunction(asm, function);
                }
                function = new ArrayList<>();
            }

            if (!function.isEmpty()
                    || isFunctionLabel(instruction)) {
                function.add(instruction);
            }
        }

        if (!function.isEmpty()) {
            emitFunction(asm, function);
        }

        String entryFunction = findEntryFunction(program);
        if (entryFunction == null) {
            throw new InvalidIRException(
                    "O programa precisa ter main(String[]) ou main() para gerar um executável.");
        }
        emitEntryPoint(asm, entryFunction);
        appendRuntimeSupport(asm);
        if (!target.isWindows()) {
            asm.append("section .note.GNU-stack noalloc noexec nowrite progbits\n");
        }

        return asm.toString();
    }

    public String generateFunction(IRProgram program, String functionName) {
        List<IRInstruction> target = new ArrayList<>();
        boolean collecting = false;

        for (IRInstruction instruction : program.instructions()) {
            if (isFunctionLabel(instruction)) {
                if (collecting) {
                    break;
                }

                collecting = instruction.destination().value()
                        .equals(functionName);
            }

            if (collecting) {
                target.add(instruction);
            }
        }

        if (target.isEmpty()) {
            throw new InvalidIRException(
                    "Função não encontrada na IR: " + functionName);
        }

        StringBuilder asm = new StringBuilder();
        prepareStringLabels(target);
        appendHeader(asm, target);
        emitFunction(asm, target);
        appendRuntimeSupport(asm);
        if (!this.target.isWindows()) {
            asm.append("section .note.GNU-stack noalloc noexec nowrite progbits\n");
        }
        return asm.toString();
    }

    private void appendHeader(StringBuilder asm, List<IRInstruction> instructions) {
        if (target.isWindows()) {
            WindowsRuntime.appendImports(asm);
        }
        asm.append("default rel\n");
        asm.append(target.isWindows() ? "section .rdata\n" : "section .rodata\n");
        appendRuntimeMessage(asm, "__null_pointer_message", new NullPointerException());
        appendRuntimeMessage(asm, "__invalid_reference_message", new InvalidReferenceException());
        appendRuntimeMessage(asm, "__array_bounds_message", new IndexOutOfBoundsException());
        appendRuntimeMessage(asm, "__array_store_message", new ArrayStoreException());
        appendRuntimeMessage(asm, "__array_size_message", new NegativeArraySizeException());
        appendRuntimeMessage(asm, "__invalid_free_message", new InvalidFreeException());
        appendRuntimeMessage(asm, "__scanner_input_error_message", new InputMismatchException());
        appendRuntimeMessage(asm, "__arithmetic_error_message", new ArithmeticException());
        appendRuntimeMessage(asm, "__out_of_memory_message", new OutOfMemoryError());
        appendRuntimeMessage(asm, "__abstract_method_message", new AbstractMethodError());
        appendRuntimeMessage(asm, "__stack_overflow_message", new StackOverflowError());
        appendRuntimeMessage(asm, "__no_such_element_message", new NoSuchElementException());
        appendRuntimeMessage(asm, "__io_error_message", new IOException());
        appendRuntimeExceptionStrings(asm);
        asm.append("__uncaught_exception_message: db \"Uncaught exception\", 10, 0\n");
        asm.append("__uncaught_exception_message_length equ $ - __uncaught_exception_message - 1\n");
        asm.append("__jvs_trace_prefix: db \"  at \", 0\n")
                .append("__jvs_trace_file_prefix: db 32, \"(\", 0\n")
                .append("__jvs_trace_separator: db \":\", 0\n")
                .append("__jvs_trace_suffix: db \")\", 10, 0\n")
                .append("__jvs_colon_space: db \": \", 0\n")
                .append("__jvs_newline: db 10, 0\n");
        if (!stringLabels.isEmpty()) {
            for (Map.Entry<String, String> entry : stringLabels.entrySet()) {
                asm.append(entry.getValue()).append(": db ")
                        .append(utf8Bytes(entry.getKey())).append("\n");
            }
        }
        asm.append("__f64_scale: dq 0x412e848000000000\n");
        asm.append("__f64_round: dq 0x3fe0000000000000\n\n");
        asm.append("__jvs_main_args_array_type: db \"String[]\", 0\n")
                .append("__jvs_main_args_element_type: db \"String\", 0\n");
        appendVirtualTables(asm);
        asm.append("section .data\n");
        asm.append("__jvs_array_vtable: dq 0\n");
        for (String typeName : BuiltinExceptionTypes.names()) {
            asm.append(builtinVtableLabel(typeName)).append(": dq 0\n");
        }
        asm.append("__jvs_sigpipe_ignore_action: dq 1, 0, 0, 0\n");
        for (RuntimeExceptionSpec spec : runtimeExceptionSpecs()) {
            String sourceRuntimeClass = findThrowableCompatibleRuntimeClass(spec.typeName());
            String objectVtable = sourceRuntimeClass == null
                    ? spec.vtableLabel()
                    : "__vtable_" + sourceRuntimeClass;
            asm.append(spec.objectLabel()).append(":\n")
                    .append("    dq ").append(objectVtable).append('\n')
                    .append("    dq ").append(spec.typeLabel()).append('\n')
                    .append("    dq ").append(spec.messageLabel()).append('\n');
        }
        asm.append('\n');
        asm.append("section .bss\n");
        appendStaticStorage(asm, instructions);
        asm.append("__heap_start: resq 1\n");
        asm.append("__heap_end: resq 1\n");
        if (!target.isWindows()) {
            asm.append("__jvs_start_argc: resq 1\n")
                    .append("__jvs_start_argv: resq 1\n");
        }
        if (target.isWindows()) {
            asm.append("__heap_commit_end: resq 1\n")
                    .append("__heap_reserved_end: resq 1\n");
        }
        asm.append("__jvs_exception_ptr: resq 1\n");
        asm.append("__jvs_handler_depth: resq 1\n");
        asm.append("__jvs_handler_labels: resq 1024\n");
        asm.append("__jvs_handler_rsp: resq 1024\n");
        asm.append("__jvs_handler_rbp: resq 1024\n");
        asm.append("__jvs_handler_trace_depth: resq 1024\n");
        asm.append("__jvs_trace_depth: resq 1\n");
        asm.append("__jvs_trace_method: resq 1024\n");
        asm.append("__jvs_trace_file: resq 1024\n");
        asm.append("__jvs_trace_line: resq 1024\n");
        asm.append("__jvs_trace_column: resq 1024\n");
        asm.append("__jvs_exception_trace_depth: resq 1\n");
        asm.append("__jvs_exception_trace_method: resq 1024\n");
        asm.append("__jvs_exception_trace_file: resq 1024\n");
        asm.append("__jvs_exception_trace_line: resq 1024\n");
        asm.append("__jvs_exception_trace_column: resq 1024\n");
        asm.append("__jvs_source_method: resq 1\n");
        asm.append("__jvs_source_file: resq 1\n");
        asm.append("__jvs_source_line: resq 1\n");
        asm.append("__jvs_source_column: resq 1\n");
        asm.append("__number_buffer: resb 32\n\n");
        asm.append("__scanner_token: resb 4096\n");
        asm.append("__scanner_byte: resb 1\n");
        asm.append("__scanner_token_length: resq 1\n");
        asm.append("__scanner_string_result: resq 1\n");
        asm.append("__scanner_double_integer: resq 1\n");
        asm.append("__scanner_double_fraction: resq 1\n");
        asm.append("__scanner_double_divisor: resq 1\n");
        asm.append("__scanner_double_index: resq 1\n");
        asm.append("__scanner_double_sign: resq 1\n");
        asm.append("__scanner_double_dot: resq 1\n");
        asm.append("__scanner_double_digits: resq 1\n");
        asm.append("__scanner_double_fraction_digits: resq 1\n\n");
        asm.append("section .text\n\n");
    }

    private void appendRuntimeMessage(
            StringBuilder asm,
            String label,
            Throwable exception) {
        asm.append(label).append(": db ")
                .append(utf8Bytes(exception.diagnostic())).append('\n')
                .append(label).append("_length equ $ - ").append(label).append(" - 1\n");
    }

    private List<RuntimeExceptionSpec> runtimeExceptionSpecs() {
        return List.of(
                runtimeException("NullPointerException", "__null_pointer_message", new NullPointerException()),
                runtimeException("InvalidReferenceException", "__invalid_reference_message", new InvalidReferenceException()),
                runtimeException("IndexOutOfBoundsException", "__array_bounds_message", new IndexOutOfBoundsException()),
                runtimeException("ArrayStoreException", "__array_store_message", new ArrayStoreException()),
                runtimeException("NegativeArraySizeException", "__array_size_message", new NegativeArraySizeException()),
                runtimeException("ArithmeticException", "__arithmetic_error_message", new ArithmeticException()),
                runtimeException("OutOfMemoryError", "__out_of_memory_message", new OutOfMemoryError()),
                runtimeException("AbstractMethodError", "__abstract_method_message", new AbstractMethodError()),
                runtimeException("StackOverflowError", "__stack_overflow_message", new StackOverflowError()),
                runtimeException("InvalidFreeException", "__invalid_free_message", new InvalidFreeException()),
                runtimeException("InputMismatchException", "__scanner_input_error_message", new InputMismatchException()),
                runtimeException("NoSuchElementException", "__no_such_element_message", new NoSuchElementException()),
                runtimeException("IOException", "__io_error_message", new IOException()));
    }

    private RuntimeExceptionSpec runtimeException(
            String typeName,
            String diagnosticLabel,
            Throwable diagnostic) {
        return new RuntimeExceptionSpec(
                typeName,
                diagnosticLabel,
                diagnostic,
                "__jvs_runtime_exception_" + typeName,
                builtinVtableLabel(typeName),
                "__jvs_exception_type_" + typeName,
                "__jvs_exception_message_" + typeName);
    }

    private String builtinVtableLabel(String typeName) {
        return "__jvs_builtin_vtable_" + BuiltinExceptionTypes.simpleName(typeName);
    }

    private void appendRuntimeExceptionStrings(StringBuilder asm) {
        for (RuntimeExceptionSpec spec : runtimeExceptionSpecs()) {
            asm.append(spec.typeLabel()).append(": db ")
                    .append(utf8Bytes(spec.typeName())).append('\n');
            asm.append(spec.messageLabel()).append(": db ")
                    .append(utf8Bytes(spec.diagnostic().getMessage())).append('\n');
        }
        if (sourceProgram != null) {
            for (int index = 0; index < sourceProgram.classes().size(); index++) {
                ClassRecord classNode = sourceProgram.classes().get(index);
                asm.append(sourceClassTypeLabel(index)).append(": db ")
                        .append(utf8Bytes(classNode.name())).append('\n');
            }
        }
    }

    private String sourceClassTypeLabel(int index) {
        return "__jvs_source_type_" + index;
    }

    private String findThrowableCompatibleRuntimeClass(String runtimeType) {
        if (sourceProgram == null) return null;
        for (ClassRecord classNode : sourceProgram.classes()) {
            if (!BuiltinExceptionTypes.simpleName(classNode.name()).equals(runtimeType)) continue;
            String current = classNode.name();
            java.util.Set<String> visited = new java.util.HashSet<>();
            while (current != null && visited.add(current)) {
                if (BuiltinExceptionTypes.simpleName(current).equals("Throwable")) {
                    return classNode.name();
                }
                ClassRecord currentClass = null;
                for (ClassRecord candidate : sourceProgram.classes()) {
                    if (candidate.name().equals(current)) {
                        currentClass = candidate;
                        break;
                    }
                }
                current = currentClass == null ? null : currentClass.superClass();
            }
        }
        return null;
    }

    private void appendRuntimeExit(StringBuilder asm, String messageLabel) {
        asm.append("    mov eax, 1\n")
                .append("    mov edi, 2\n")
                .append("    lea rsi, [rel ").append(messageLabel).append("]\n")
                .append("    mov edx, ").append(messageLabel).append("_length\n")
                .append("    syscall\n")
                .append("    mov eax, 60\n")
                .append("    mov edi, 1\n")
                .append("    syscall\n")
                .append("    ud2\n");
    }

    private void appendRuntimeExceptionTrap(StringBuilder asm, String messageLabel) {
        RuntimeExceptionSpec spec = runtimeExceptionSpecs().stream()
                .filter(candidate -> candidate.diagnosticLabel().equals(messageLabel))
                .findFirst()
                .orElseThrow(() -> new InvalidIRException(
                        "Não existe tipo de exceção associado à mensagem " + messageLabel + "."));
        String exitLabel = "__runtime_trap_exit_" + nextFreeLabel++;
        asm.append("    cmp qword [rel __jvs_handler_depth], 0\n");
        asm.append("    je ").append(exitLabel).append('\n');
        asm.append("    lea rdi, [rel ").append(spec.objectLabel()).append("]\n");
        asm.append("    jmp __runtime_throw_current\n");
        asm.append(exitLabel).append(":\n")
                .append("    lea rdi, [rel ").append(spec.objectLabel()).append("]\n")
                .append("    jmp __runtime_throw_current\n");
    }

    private void appendStaticStorage(StringBuilder asm, List<IRInstruction> instructions) {
        Set<String> emitted = new LinkedHashSet<>();
        if (sourceProgram != null) {
            for (ClassRecord classNode : sourceProgram.classes()) {
                for (AttributeRecord attribute : classNode.attributes()) {
                    if (!attribute.isStatic()) continue;
                    String label = StaticFieldSupport.label(classNode.name(), attribute.name());
                    int size = StaticFieldSupport.sizeOf(attribute.type().name());
                    appendStaticStorageSlot(asm, label, size, emitted);
                }
            }
        }
        Map<String, Operand.Type> generatedGlobals = new LinkedHashMap<>();
        for (IRInstruction instruction : instructions) {
            if (instruction.opCode() != IROpCode.LOAD_STATIC
                    && instruction.opCode() != IROpCode.STORE_STATIC) continue;
            Operand labelOperand = instruction.opCode() == IROpCode.LOAD_STATIC
                    ? instruction.left() : instruction.left();
            if (labelOperand == null || labelOperand.kind() != Operand.Kind.LABEL) continue;
            String label = labelOperand.value().toString();
            if (!label.startsWith("__global_register_")) continue;
            Operand value = instruction.opCode() == IROpCode.LOAD_STATIC
                    ? instruction.destination()
                    : instruction.arguments().isEmpty() ? null : instruction.arguments().get(0);
            if (value != null) generatedGlobals.putIfAbsent(label, value.type());
        }
        for (Map.Entry<String, Operand.Type> global : generatedGlobals.entrySet()) {
            int size = switch (global.getValue()) {
                case BOOL -> 1;
                case I32 -> 4;
                case I64, F64, REF -> 8;
            };
            appendStaticStorageSlot(asm, global.getKey(), size, emitted);
        }
        asm.append('\n');
    }

    private void appendStaticStorageSlot(StringBuilder asm, String label, int size, Set<String> emitted) {
        if (!emitted.add(label)) return;
        int alignment = Math.min(size, 8);
        asm.append("align ").append(alignment).append('\n')
                .append(label).append(": resb ").append(size).append('\n');
    }

    private void appendVirtualTables(StringBuilder asm) {
        if (virtualMethodTable == null || virtualMethodTable.tables().isEmpty()) {
            return;
        }
        asm.append("section .data\n");
        for (Map.Entry<String, List<String>> table : virtualMethodTable.tables().entrySet()) {
            asm.append("__vtable_").append(table.getKey()).append(":\n");
            if (table.getValue().isEmpty()) {
                asm.append("    dq 0\n");
            } else {
                for (String methodLabel : table.getValue()) {
                    asm.append("    dq ").append(methodLabel).append("\n");
                }
            }
        }
        asm.append('\n');
    }

    private void prepareStringLabels(List<IRInstruction> instructions) {
        stringLabels.clear();
        arrayTypeNames.clear();
        nextStringLabel = 0;
        registerStringValue("\n");
        registerStringValue("-");
        registerStringValue(".");
        registerStringValue("nan");
        registerStringValue("inf");
        registerStringValue("overflow");
        registerStringValue("(null)");
        registerStringValue("");
        registerStringValue("Throwable");
        registerStringValue("Object");
        registerStringValue("String");
        registerStringValue("int");
        registerStringValue("long");
        registerStringValue("double");
        registerStringValue("boolean");
        for (String typeName : BuiltinExceptionTypes.names()) registerStringValue(typeName);
        if (sourceProgram != null) {
            for (ClassRecord classNode : sourceProgram.classes()) {
                registerStringValue(classNode.name());
                for (String parent : classSupertypes(classNode.name())) registerStringValue(parent);
            }
        }
        for (IRInstruction instruction : instructions) {
            if (isFunctionLabel(instruction)) {
                registerStringValue(instruction.destination().value().toString());
            }
            if (instruction.opCode() == IROpCode.SOURCE_LOCATION
                    && instruction.destination() != null) {
                registerStringValue(instruction.destination().value().toString());
            }
            registerString(instruction.destination());
            if ((instruction.opCode() == IROpCode.PRINT_FORMAT
                    || instruction.opCode() == IROpCode.PRINT_FORMAT_LN)
                    && instruction.left() != null
                    && instruction.left().kind() == Operand.Kind.CONSTANT
                    && instruction.left().value() instanceof String format) {
                registerFormatLiterals(format);
            } else {
                registerString(instruction.left());
            }
            registerString(instruction.right());
            for (Operand argument : instruction.arguments()) {
                registerString(argument);
            }
            if (instruction.opCode() == IROpCode.ALLOC_ARRAY
                    && !instruction.arguments().isEmpty()
                    && instruction.arguments().get(0).value() instanceof String arrayType) {
                arrayTypeNames.add(arrayType);
                for (String parent : arraySupertypes(arrayType)) registerStringValue(parent);
            }
        }
    }

    private void registerString(Operand operand) {
        if (operand == null || operand.kind() != Operand.Kind.CONSTANT
                || !(operand.value() instanceof String value)) {
            return;
        }
        registerStringValue(value);
    }

    private void registerStringValue(String value) {
        stringLabels.computeIfAbsent(value, ignored -> "str_data_" + nextStringLabel++);
    }

    private void registerFormatLiterals(String format) {
        StringBuilder literal = new StringBuilder();
        for (int index = 0; index < format.length(); index++) {
            char current = format.charAt(index);
            if (current != '%') {
                literal.append(current);
                continue;
            }
            if (++index >= format.length()) {
                throw new InvalidIRException("Especificador de printf incompleto.");
            }
            char specifier = format.charAt(index);
            if (specifier == '%') {
                literal.append('%');
                continue;
            }
            if (!literal.isEmpty()) {
                registerStringValue(literal.toString());
                literal.setLength(0);
            }
            if (specifier == 'l' && index + 1 < format.length()) {
                index++;
            }
        }
        if (!literal.isEmpty()) {
            registerStringValue(literal.toString());
        }
    }

    private String utf8Bytes(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder output = new StringBuilder();
        for (byte valueByte : bytes) {
            if (!output.isEmpty())
                output.append(", ");
            output.append(Byte.toUnsignedInt(valueByte));
        }
        if (!output.isEmpty())
            output.append(", ");
        return output.append('0').toString();
    }

    private boolean isFunctionLabel(IRInstruction instruction) {
        return instruction.opCode() == IROpCode.LABEL
                && instruction.destination() != null
                && instruction.destination().kind() == Operand.Kind.LABEL
                && instruction.destination().value().toString().contains(".");
    }

    private String findEntryFunction(IRProgram program) {
        String stringArrayEntry = null;
        String noArgumentEntry = null;
        for (IRInstruction instruction : program.instructions()) {
            if (!isFunctionLabel(instruction))
                continue;
            String label = instruction.destination().value().toString();
            if (label.endsWith(".main__String_array__ret_void")) {
                if (stringArrayEntry != null) {
                    throw new InvalidIRException("Mais de uma função main(String[]) foi gerada.");
                }
                stringArrayEntry = label;
            } else if (label.endsWith(".main__void__ret_void")) {
                if (noArgumentEntry != null) {
                    throw new InvalidIRException("Mais de uma função main() sem parâmetros foi gerada.");
                }
                noArgumentEntry = label;
            }
        }
        return stringArrayEntry != null ? stringArrayEntry : noArgumentEntry;
    }

    private void emitEntryPoint(StringBuilder asm, String entryFunction) {
        if (target.isWindows()) {
            emitWindowsEntryPoint(asm, entryFunction);
            return;
        }
        asm.append("global _start\n");
        asm.append("_start:\n");
        asm.append("    xor ebp, ebp\n");
        asm.append("    mov rax, qword [rsp]\n")
                .append("    dec rax\n")
                .append("    mov qword [rel __jvs_start_argc], rax\n")
                .append("    lea rax, [rsp+8]\n")
                .append("    mov qword [rel __jvs_start_argv], rax\n");
        asm.append("    mov eax, 13\n"); // rt_sigaction
        asm.append("    mov edi, 13\n"); // SIGPIPE
        asm.append("    lea rsi, [rel __jvs_sigpipe_ignore_action]\n");
        asm.append("    xor edx, edx\n");
        asm.append("    mov r10d, 8\n");
        asm.append("    syscall\n");
        asm.append("    call __JVS.static_init__void__ret_void\n");
        if (entryFunction.endsWith(".main__String_array__ret_void")) {
            asm.append("    call __jvs_build_linux_args\n")
                    .append("    mov rdi, rax\n");
        }
        asm.append("    call ").append(entryFunction).append("\n");
        asm.append("    cmp qword [rel __jvs_exception_ptr], 0\n");
        asm.append("    jne __uncaught_exception\n");
        asm.append("    xor edi, edi\n");
        asm.append("    mov eax, 60\n");
        asm.append("    syscall\n");
        asm.append("__uncaught_exception:\n");
        asm.append("    call __jvs_print_uncaught\n");
        asm.append("    mov eax, 60\n");
        asm.append("    mov edi, 1\n");
        asm.append("    syscall\n");
        asm.append("    ud2\n");
    }

    private void emitWindowsEntryPoint(StringBuilder asm, String entryFunction) {
        asm.append("global start\n")
                .append("start:\n")
                .append("    xor ebp, ebp\n")
                .append("    and rsp, -16\n");
        WindowsRuntime.appendConsoleCodePageSetup(asm);
        asm.append("    call __JVS.static_init__void__ret_void\n");
        if (entryFunction.endsWith(".main__String_array__ret_void")) {
            asm.append("    call __jvs_build_windows_args\n")
                    .append("    mov rdi, rax\n");
        }
        asm.append("    call ").append(entryFunction).append('\n')
                .append("    cmp qword [rel __jvs_exception_ptr], 0\n")
                .append("    jne __uncaught_exception\n")
                .append("    xor edi, edi\n")
                .append("    call __jvs_exit\n")
                .append("__uncaught_exception:\n")
                .append("    call __jvs_print_uncaught\n")
                .append("    mov edi, 1\n")
                .append("    call __jvs_exit\n")
                .append("    ud2\n");
    }

    private void emitFunction(
            StringBuilder asm,
            List<IRInstruction> instructions) {
        allocateStackSlots(instructions);

        String name = instructions.get(0).destination().value().toString();
        asm.append(name).append(":\n");
        asm.append("    push rbp\n");
        asm.append("    mov rbp, rsp\n");
        // Reserve [rbp-8] for whether this function was inserted into the trace.
        frameSize = Math.max(frameSize, 8);

        if (frameSize > 0) {
            asm.append("    sub rsp, ").append(frameSize).append("\n");
        }
        asm.append("    mov qword [rbp-8], 0\n");

        nextGpParameter = 0;
        nextXmmParameter = 0;
        nextStackParameter = 0;
        int instructionIndex = 1;
        while (instructionIndex < instructions.size()
                && instructions.get(instructionIndex).opCode() == IROpCode.PARAM) {
            emitInstruction(asm, instructions.get(instructionIndex++));
        }
        emitFunctionTraceEntry(asm, name);
        for (int i = instructionIndex; i < instructions.size(); i++) {
            emitInstruction(asm, instructions.get(i));
        }

        if (instructions.get(instructions.size() - 1).opCode() != IROpCode.RETURN) {
            emitFunctionReturn(asm);
        }

        asm.append('\n');
    }

    private void emitFunctionTraceEntry(StringBuilder asm, String name) {
        String full = "..@trace_frame_full_" + nextFreeLabel++;
        asm.append("    mov rax, qword [rel __jvs_trace_depth]\n")
                .append("    cmp rax, 1024\n    jae ").append(full).append('\n')
                .append("    mov r10, rax\n    shl r10, 3\n")
                .append("    lea r11, [rel __jvs_trace_method]\n")
                .append("    lea rcx, [rel ").append(stringLabels.get(name)).append("]\n")
                .append("    mov qword [r11+r10], rcx\n")
                .append("    lea r11, [rel __jvs_trace_file]\n")
                .append("    mov rcx, qword [rel __jvs_source_file]\n")
                .append("    mov qword [r11+r10], rcx\n")
                .append("    lea r11, [rel __jvs_trace_line]\n")
                .append("    mov rcx, qword [rel __jvs_source_line]\n")
                .append("    mov qword [r11+r10], rcx\n")
                .append("    lea r11, [rel __jvs_trace_column]\n")
                .append("    mov rcx, qword [rel __jvs_source_column]\n")
                .append("    mov qword [r11+r10], rcx\n")
                .append("    inc qword [rel __jvs_trace_depth]\n")
                .append("    mov qword [rbp-8], 1\n")
                .append(full).append(":\n");
    }

    private void emitFunctionReturn(StringBuilder asm) {
        String done = "..@trace_return_done_" + nextFreeLabel++;
        asm.append("    cmp qword [rel __jvs_exception_ptr], 0\n    jne ").append(done).append('\n')
                .append("    cmp qword [rbp-8], 0\n    je ").append(done).append('\n')
                .append("    dec qword [rel __jvs_trace_depth]\n")
                .append(done).append(":\n    leave\n    ret\n");
    }

    private void allocateStackSlots(List<IRInstruction> instructions) {
        stackOffsets.clear();
        frameSize = 8;

        for (IRInstruction instruction : instructions) {
            reserve(instruction.destination());
            reserve(instruction.left());
            reserve(instruction.right());

            for (Operand argument : instruction.arguments()) {
                reserve(argument);
            }
        }

        frameSize = align(frameSize, 16);
    }

    private void reserve(Operand operand) {
        if (operand == null
                || (operand.kind() != Operand.Kind.VARIABLE
                        && operand.kind() != Operand.Kind.TEMPORARY)
                || stackOffsets.containsKey(operand)) {
            return;
        }

        int size = switch (operand.type()) {
            case I32, BOOL -> 4;
            case I64, F64, REF -> 8;
        };

        frameSize = align(frameSize, size);
        frameSize += size;
        stackOffsets.put(operand, -frameSize);
    }

    private int align(int value, int alignment) {
        return (value + alignment - 1) / alignment * alignment;
    }

    private String address(Operand operand) {
        Integer offset = stackOffsets.get(operand);
        if (offset == null) {
            throw new InvalidIRException(
                    "Operando sem espaço no stack: " + operand);
        }
        return "[rbp" + offset + "]";
    }

    private void emitInstruction(
            StringBuilder asm,
            IRInstruction instruction) {
        switch (instruction.opCode()) {
            case LABEL ->
                asm.append(instruction.destination().value()).append(":\n");

            case PARAM -> {
                Operand destination = instruction.destination();
                if (destination.type() == Operand.Type.F64) {
                    String[] xmmRegisters = {
                            "xmm0", "xmm1", "xmm2", "xmm3",
                            "xmm4", "xmm5", "xmm6", "xmm7"
                    };
                    if (nextXmmParameter >= xmmRegisters.length) {
                        int sourceOffset = 16 + nextStackParameter++ * 8;
                        asm.append("    movsd xmm0, qword [rbp+").append(sourceOffset).append("]\n")
                                .append("    movsd qword ").append(address(destination)).append(", xmm0\n");
                    } else {
                        asm.append("    movsd qword ").append(address(destination))
                                .append(", ").append(xmmRegisters[nextXmmParameter]).append("\n");
                    }
                    nextXmmParameter++;
                } else {
                    String[] gpRegisters = {
                            "rdi", "rsi", "rdx", "rcx", "r8", "r9"
                    };
                    if (nextGpParameter >= gpRegisters.length) {
                        int sourceOffset = 16 + nextStackParameter++ * 8;
                        if (destination.type() == Operand.Type.I32
                                || destination.type() == Operand.Type.BOOL) {
                            asm.append("    mov eax, dword [rbp+").append(sourceOffset).append("]\n")
                                    .append("    mov dword ").append(address(destination)).append(", eax\n");
                        } else if (destination.type() == Operand.Type.I64
                                || destination.type() == Operand.Type.REF) {
                            asm.append("    mov rax, qword [rbp+").append(sourceOffset).append("]\n")
                                    .append("    mov qword ").append(address(destination)).append(", rax\n");
                        } else {
                            throw new UnsupportedBackendFeatureException(
                                    "Tipo de parâmetro ainda não suportado: " + destination.type());
                        }
                        nextGpParameter++;
                    } else {
                        String register = gpRegisters[nextGpParameter++];
                        if (destination.type() == Operand.Type.I32
                                || destination.type() == Operand.Type.BOOL) {
                            register = register32(register);
                            asm.append("    mov dword ").append(address(destination))
                                    .append(", ").append(register).append("\n");
                        } else if (destination.type() == Operand.Type.I64
                                || destination.type() == Operand.Type.REF) {
                            asm.append("    mov qword ").append(address(destination))
                                    .append(", ").append(register).append("\n");
                        } else {
                            throw new UnsupportedBackendFeatureException(
                                    "Tipo de parâmetro ainda não suportado: " + destination.type());
                        }
                    }
                }
            }

            case CALL -> emitCall(asm, instruction);

            case CALL_VIRTUAL -> emitVirtualCall(asm, instruction);

            case SOURCE_LOCATION -> {
                if (instruction.destination() == null || instruction.left() == null
                        || instruction.right() == null) {
                    throw new InvalidIRException("SOURCE_LOCATION exige método, linha e coluna.");
                }
                String method = instruction.destination().value().toString();
                asm.append("    lea rax, [rel ").append(stringLabels.get(method)).append("]\n")
                        .append("    mov qword [rel __jvs_source_method], rax\n");
                if (instruction.arguments().isEmpty()
                        || !(instruction.arguments().get(0).value() instanceof String sourceFile)) {
                    throw new InvalidIRException("SOURCE_LOCATION exige o nome do arquivo fonte.");
                }
                asm.append("    lea rax, [rel ").append(stringLabels.get(sourceFile)).append("]\n")
                        .append("    mov qword [rel __jvs_source_file], rax\n");
                loadI32(asm, instruction.left(), "eax");
                asm.append("    mov qword [rel __jvs_source_line], rax\n");
                loadI32(asm, instruction.right(), "eax");
                asm.append("    mov qword [rel __jvs_source_column], rax\n")
                        .append("    mov r10, qword [rel __jvs_trace_depth]\n")
                .append("    test r10, r10\n    jz ..@source_location_done_").append(nextFreeLabel).append("\n")
                        .append("    dec r10\n    shl r10, 3\n")
                        .append("    lea r11, [rel __jvs_trace_method]\n")
                        .append("    mov rcx, qword [rel __jvs_source_method]\n")
                        .append("    mov qword [r11+r10], rcx\n")
                        .append("    lea r11, [rel __jvs_trace_file]\n")
                        .append("    mov rcx, qword [rel __jvs_source_file]\n")
                        .append("    mov qword [r11+r10], rcx\n")
                        .append("    lea r11, [rel __jvs_trace_line]\n")
                        .append("    mov rcx, qword [rel __jvs_source_line]\n")
                        .append("    mov qword [r11+r10], rcx\n")
                        .append("    lea r11, [rel __jvs_trace_column]\n")
                        .append("    mov rcx, qword [rel __jvs_source_column]\n")
                        .append("    mov qword [r11+r10], rcx\n")
                        .append("..@source_location_done_").append(nextFreeLabel++).append(":\n");
            }

            case THROW -> {
                loadI64(asm, instruction.left(), "rax");
                String nonNull = "__throw_non_null_" + nextFreeLabel++;
                asm.append("    test rax, rax\n    jnz ").append(nonNull).append('\n');
                RuntimeExceptionSpec nullPointer = runtimeExceptionSpecs().stream()
                        .filter(spec -> spec.typeName().equals("NullPointerException"))
                        .findFirst().orElseThrow();
                asm.append("    lea rax, [rel ").append(nullPointer.objectLabel()).append("]\n");
                asm.append(nonNull).append(":\n")
                        .append("    mov rdi, rax\n    jmp __runtime_throw_current\n");
            }
            case CHECK_EXCEPTION -> {
                asm.append("    cmp qword [rel __jvs_exception_ptr], 0\n");
                if (instruction.destination() != null) {
                    asm.append("    jne ").append(instruction.destination().value()).append('\n');
                } else {
                    String noException = "__exception_clear_" + nextFreeLabel++;
                    asm.append("    je ").append(noException).append('\n')
                            .append("    xor eax, eax\n");
                    emitFunctionReturn(asm);
                    asm
                            .append(noException).append(":\n");
                }
            }
            case GET_EXCEPTION -> {
                asm.append("    mov rax, qword [rel __jvs_exception_ptr]\n");
                storeI64(asm, instruction.destination(), "rax");
            }
            case CLEAR_EXCEPTION -> asm.append("    mov qword [rel __jvs_exception_ptr], 0\n");
            case CATCH_MATCH -> emitCatchMatch(asm, instruction);

            case PUSH_EXCEPTION_HANDLER -> emitPushExceptionHandler(asm, instruction);

            case POP_EXCEPTION_HANDLER_IF -> emitPopExceptionHandlerIf(asm, instruction);

            case EXCEPTION_GET_MESSAGE -> emitExceptionGetMessage(asm, instruction);

            case EXCEPTION_GET_TYPE -> emitExceptionGetType(asm, instruction);

            case LOAD_FIELD -> emitFieldLoad(asm, instruction);

            case STORE_FIELD -> emitFieldStore(asm, instruction);

            case LOAD_STATIC -> emitStaticLoad(asm, instruction);

            case STORE_STATIC -> emitStaticStore(asm, instruction);

            case ALLOC_OBJECT -> emitObjectAllocation(asm, instruction);

            case FREE_OBJECT -> emitObjectFree(asm, instruction);

            case ALLOC_ARRAY -> emitArrayAllocation(asm, instruction);

            case LOAD_ARRAY -> emitArrayLoad(asm, instruction);

            case STORE_ARRAY -> emitArrayStore(asm, instruction);

            case ARRAY_LENGTH -> emitArrayLength(asm, instruction);

            case FREE_ARRAY -> emitArrayFree(asm, instruction);

            case CHECK_ARRAY_SIZE -> emitArraySizeCheck(asm, instruction);

            case ASSIGN -> {
                emitAssignment(asm, instruction.destination(), instruction.left());
            }

            case CAST_I32_TO_I64 -> {
                loadI32(asm, instruction.left(), "eax");
                asm.append("    movsxd rax, eax\n");
                storeI64(asm, instruction.destination(), "rax");
            }

            case CAST_I32_TO_F64 -> {
                loadI32(asm, instruction.left(), "eax");
                asm.append("    cvtsi2sd xmm0, eax\n");
                storeF64(asm, instruction.destination(), "xmm0");
            }

            case CAST_I64_TO_F64 -> {
                loadI64(asm, instruction.left(), "rax");
                asm.append("    cvtsi2sd xmm0, rax\n");
                storeF64(asm, instruction.destination(), "xmm0");
            }

            case ADD -> {
                emitIntegerBinary(asm, instruction, "add");
            }

            case SUB, MUL -> emitIntegerBinary(asm, instruction,
                    instruction.opCode() == IROpCode.SUB ? "sub" : "imul");

            case DIV -> emitDivision(asm, instruction, false);

            case MOD -> emitDivision(asm, instruction, true);

            case NEG -> {
                if (instruction.destination().type() == Operand.Type.F64) {
                    loadF64(asm, instruction.left(), "xmm0");
                    asm.append("    mov rax, 0x8000000000000000\n");
                    asm.append("    movq xmm1, rax\n");
                    asm.append("    xorpd xmm0, xmm1\n");
                    storeF64(asm, instruction.destination(), "xmm0");
                } else if (instruction.destination().type() == Operand.Type.I64) {
                    loadI64(asm, instruction.left(), "rax");
                    asm.append("    neg rax\n");
                    storeI64(asm, instruction.destination(), "rax");
                } else {
                    loadI32(asm, instruction.left(), "eax");
                    asm.append("    neg eax\n");
                    storeI32(asm, instruction.destination(), "eax");
                }
            }

            case LT, GT, LE, GE, EQ, NE ->
                emitComparison(asm, instruction);

            case JUMP -> asm.append("    jmp ")
                    .append(instruction.destination().value())
                    .append("\n");

            case JUMP_IF_FALSE -> {
                loadI32(asm, instruction.left(), "eax");
                asm.append("    test eax, eax\n");
                asm.append("    jz ")
                        .append(instruction.destination().value())
                        .append("\n");
            }

            case JUMP_IF_TRUE -> {
                loadI32(asm, instruction.left(), "eax");
                asm.append("    test eax, eax\n");
                asm.append("    jnz ")
                        .append(instruction.destination().value())
                        .append("\n");
            }

            case PRINT -> emitPrint(asm, instruction, false);

            case PRINT_LN -> emitPrint(asm, instruction, true);

            case PRINT_FORMAT -> emitFormattedPrint(asm, instruction, false);

            case PRINT_FORMAT_LN -> emitFormattedPrint(asm, instruction, true);

            case RETURN -> {
                if (instruction.left() != null) {
                    if (instruction.left().type() == Operand.Type.F64) {
                        loadF64(asm, instruction.left(), "xmm0");
                    } else if (instruction.left().type() == Operand.Type.I64
                            || instruction.left().type() == Operand.Type.REF) {
                        loadI64(asm, instruction.left(), "rax");
                    } else {
                        loadI32(asm, instruction.left(), "eax");
                    }
                }
                emitFunctionReturn(asm);
            }

            default -> throw new UnsupportedBackendFeatureException(
                    "Opcode NASM ainda não implementado: "
                            + instruction.opCode());
        }
    }

    private void emitPrint(
            StringBuilder asm,
            IRInstruction instruction,
            boolean lineBreak) {
        Operand value = instruction.left();
        switch (value.type()) {
            case F64 -> {
                loadF64(asm, value, "xmm0");
                asm.append("    call __print_f64\n");
            }
            case REF -> {
                loadI64(asm, value, "rsi");
                asm.append("    call __write_cstr\n");
            }
            case I64 -> {
                loadI64(asm, value, "rax");
                asm.append("    call __print_i64\n");
            }
            case I32 -> {
                loadI32(asm, value, "eax");
                asm.append("    movsxd rax, eax\n");
                asm.append("    call __print_i64\n");
            }
            case BOOL -> {
                loadI32(asm, value, "eax");
                asm.append("    mov eax, eax\n");
                asm.append("    call __print_u64\n");
            }
        }
        if (lineBreak) {
            emitLiteralWrite(asm, "\n");
        }
    }

    private void emitFormattedPrint(
            StringBuilder asm,
            IRInstruction instruction,
            boolean lineBreak) {
        String format = (String) instruction.left().value();
        List<Operand> arguments = instruction.arguments();
        StringBuilder literal = new StringBuilder();
        int argumentIndex = 0;

        for (int index = 0; index < format.length(); index++) {
            char current = format.charAt(index);
            if (current != '%') {
                literal.append(current);
                continue;
            }

            if (++index >= format.length()) {
                throw new InvalidIRException("Especificador de printf incompleto.");
            }

            char specifier = format.charAt(index);
            if (specifier == '%') {
                literal.append('%');
                continue;
            }

            emitLiteralWrite(asm, literal.toString());
            literal.setLength(0);

            boolean longModifier = specifier == 'l';
            if (longModifier) {
                if (++index >= format.length()) {
                    throw new InvalidIRException("Especificador de printf incompleto.");
                }
                specifier = format.charAt(index);
            }
            if (argumentIndex >= arguments.size()) {
                throw new InvalidIRException("Faltou argumento para o formato de printf.");
            }
            emitFormattedArgument(asm, arguments.get(argumentIndex++), specifier, longModifier);
        }

        emitLiteralWrite(asm, literal.toString());
        if (argumentIndex != arguments.size()) {
            throw new InvalidIRException("Há argumentos excedentes no formato de printf.");
        }
        if (lineBreak) {
            emitLiteralWrite(asm, "\n");
        }
    }

    private void emitFormattedArgument(
            StringBuilder asm,
            Operand argument,
            char specifier,
            boolean longModifier) {
        switch (specifier) {
            case 'd', 'i' -> {
                if (longModifier) {
                    loadI64(asm, argument, "rax");
                } else {
                    loadI32(asm, argument, "eax");
                    asm.append("    movsxd rax, eax\n");
                }
                asm.append("    call __print_i64\n");
            }
            case 'u' -> {
                loadI32(asm, argument, "eax");
                asm.append("    mov eax, eax\n");
                asm.append("    call __print_u64\n");
            }
            case 'f' -> {
                loadF64(asm, argument, "xmm0");
                asm.append("    call __print_f64\n");
            }
            case 's' -> {
                loadI64(asm, argument, "rsi");
                asm.append("    call __write_cstr\n");
            }
            case 'b' -> {
                loadI32(asm, argument, "eax");
                asm.append("    mov eax, eax\n");
                asm.append("    call __print_u64\n");
            }
            default -> throw new UnsupportedBackendFeatureException(
                    "Especificador de printf não suportado: %" + (longModifier ? "l" : "") + specifier);
        }
    }

    private void emitLiteralWrite(StringBuilder asm, String value) {
        if (value.isEmpty()) {
            return;
        }
        String label = stringLabels.get(value);
        if (label == null) {
            throw new CompilerInvariantException("Literal não registrado: " + value);
        }
        int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
        asm.append("    lea rsi, [rel ").append(label).append("]\n");
        asm.append("    mov edx, ").append(byteLength).append("\n");
        asm.append("    call __write_all\n");
    }

    private void appendRuntimeSupport(StringBuilder asm) {
        asm.append("__write_all:\n");
        asm.append("    test rdx, rdx\n");
        asm.append("    jz .write_done\n");
        asm.append(".write_loop:\n");
        if (target.isWindows()) {
            asm.append("    mov edi, 1\n")
                    .append("    call __jvs_write_once\n");
        } else {
            asm.append("    mov eax, 1\n")
                    .append("    mov edi, 1\n")
                    .append("    syscall\n");
        }
        asm.append("    test rax, rax\n");
        asm.append("    jle .write_error\n");
        asm.append("    add rsi, rax\n");
        asm.append("    sub rdx, rax\n");
        asm.append("    jnz .write_loop\n");
        asm.append(".write_done:\n");
        asm.append("    ret\n");
        asm.append(".write_error:\n");
        asm.append("    jmp __runtime_io_error\n");

        asm.append("__write_cstr:\n");
        asm.append("    test rsi, rsi\n");
        asm.append("    jnz .cstring_scan\n");
        asm.append("    lea rsi, [rel ").append(stringLabels.get("(null)")).append("]\n");
        asm.append("    mov edx, 6\n");
        asm.append("    jmp __write_all\n");
        asm.append(".cstring_scan:\n");
        asm.append("    mov rdx, rsi\n");
        asm.append(".cstring_loop:\n");
        asm.append("    cmp byte [rdx], 0\n");
        asm.append("    je .cstring_ready\n");
        asm.append("    inc rdx\n");
        asm.append("    jmp .cstring_loop\n");
        asm.append(".cstring_ready:\n");
        asm.append("    sub rdx, rsi\n");
        asm.append("    jmp __write_all\n");

        asm.append("__heap_alloc:\n");
        if (target.isWindows()) {
            asm.append("    push rbp\n")
                    .append("    mov rbp, rsp\n")
                    .append("    sub rsp, 16\n");
        }
        asm.append("    mov r8, rdi\n"); // tamanho solicitado
        asm.append("    add r8, 15\n"); // arredonda o payload
        asm.append("    jc .heap_alloc_failed\n");
        asm.append("    and r8, -16\n"); // alinhamento de 16 bytes
        asm.append("    add r8, 16\n"); // espaço do cabeçalho
        asm.append("    jc .heap_alloc_failed\n");
        if (target.isWindows()) {
            // VirtualAlloc takes r8 as PAGE_*; keep the requested block size across that call.
            asm.append("    mov qword [rbp-16], r8\n");
        }

        asm.append("    mov r9, qword [rel __heap_start]\n");
        asm.append("    test r9, r9\n");
        asm.append("    jnz .heap_search\n");
        if (target.isWindows()) {
            asm.append("    xor edi, edi\n")
                    .append("    mov esi, 0x40000000\n")
                    .append("    mov edx, 0x2000\n") // MEM_RESERVE
                    .append("    mov r8d, 1\n") // PAGE_NOACCESS
                    .append("    call __jvs_virtual_alloc\n")
                    .append("    test rax, rax\n")
                    .append("    jz .heap_alloc_failed\n")
                    .append("    mov r8, qword [rbp-16]\n")
                    .append("    lea rdx, [rax + 0x40000000]\n")
                    .append("    mov qword [rel __heap_reserved_end], rdx\n")
                    .append("    mov qword [rel __heap_commit_end], rax\n");
        } else {
            asm.append("    xor edi, edi\n"); // consulta o break atual
            asm.append("    mov eax, 12\n"); // syscall brk
            asm.append("    syscall\n");
            asm.append("    add rax, 15\n");
            asm.append("    jc .heap_alloc_failed\n");
            asm.append("    and rax, -16\n");
        }
        asm.append("    mov qword [rel __heap_start], rax\n");
        asm.append("    mov qword [rel __heap_end], rax\n");
        asm.append("    mov r9, rax\n");

        asm.append(".heap_search:\n");
        asm.append("    cmp r9, qword [rel __heap_end]\n");
        asm.append("    jae .heap_grow\n");
        asm.append("    cmp qword [r9 + 8], 0\n");
        asm.append("    jne .heap_next_block\n");
        asm.append("    cmp qword [r9], r8\n");
        asm.append("    jae .heap_reuse_block\n");
        asm.append(".heap_next_block:\n");
        asm.append("    add r9, qword [r9]\n");
        asm.append("    jmp .heap_search\n");

        asm.append(".heap_reuse_block:\n");
        asm.append("    mov r10, qword [r9]\n");
        asm.append("    sub r10, r8\n");
        asm.append("    cmp r10, 32\n"); // cabeçalho + payload mínimo alinhado
        asm.append("    jb .heap_mark_allocated\n");
        asm.append("    lea rax, [r9 + r8]\n");
        asm.append("    mov qword [rax], r10\n");
        asm.append("    mov qword [rax + 8], 0\n");
        asm.append("    mov qword [r9], r8\n");
        asm.append(".heap_mark_allocated:\n");
        asm.append("    mov qword [r9 + 8], 1\n");
        asm.append("    jmp .heap_clear_payload\n");

        asm.append(".heap_grow:\n");
        asm.append("    mov r9, qword [rel __heap_end]\n");
        asm.append("    mov r10, r9\n");
        asm.append("    add r10, r8\n"); // novo break
        asm.append("    jc .heap_alloc_failed\n");
        if (target.isWindows()) {
            asm.append("    cmp r10, qword [rel __heap_reserved_end]\n")
                    .append("    ja .heap_alloc_failed\n")
                    .append("    mov rax, r10\n")
                    .append("    add rax, 4095\n")
                    .append("    jc .heap_alloc_failed\n")
                    .append("    and rax, -4096\n")
                    .append("    cmp rax, qword [rel __heap_commit_end]\n")
                    .append("    jbe .heap_commit_ready\n")
                    .append("    mov qword [rbp-8], rax\n")
                    .append("    mov rdi, qword [rel __heap_commit_end]\n")
                    .append("    mov rsi, rax\n")
                    .append("    sub rsi, rdi\n")
                    .append("    mov edx, 0x1000\n") // MEM_COMMIT
                    .append("    mov r8d, 4\n") // PAGE_READWRITE
                    .append("    call __jvs_virtual_alloc\n")
                    .append("    test rax, rax\n")
                    .append("    jz .heap_alloc_failed\n")
                    .append("    mov r8, qword [rbp-16]\n")
                    .append("    mov rax, qword [rbp-8]\n")
                    .append("    mov qword [rel __heap_commit_end], rax\n")
                    .append(".heap_commit_ready:\n");
        } else {
            asm.append("    mov rdi, r10\n");
            asm.append("    mov eax, 12\n");
            asm.append("    syscall\n");
            asm.append("    cmp rax, r10\n");
            asm.append("    jb .heap_alloc_failed\n");
        }
        asm.append("    mov qword [rel __heap_end], r10\n");
        asm.append("    mov qword [r9], r8\n");
        asm.append("    mov qword [r9 + 8], 1\n");

        asm.append(".heap_clear_payload:\n");
        asm.append("    mov qword [r9 + 8], 1\n");
        asm.append("    lea rdi, [r9 + 16]\n");
        asm.append("    mov rcx, r8\n");
        asm.append("    shr rcx, 3\n");
        asm.append("    sub rcx, 2\n");
        asm.append("    xor eax, eax\n");
        asm.append("    cld\n");
        asm.append("    rep stosq\n");
        asm.append("    lea rax, [r9 + 16]\n");
        asm.append(target.isWindows() ? "    leave\n    ret\n" : "    ret\n");

        asm.append(".heap_alloc_failed:\n");
        asm.append("    jmp __runtime_out_of_memory\n");

        asm.append("__heap_free:\n");
        asm.append("    test rdi, rdi\n");
        asm.append("    jz .heap_free_done\n");
        asm.append("    mov r11, rdi\n"); // preserva o ponteiro entregue
        asm.append("    mov r8, qword [rel __heap_start]\n");
        asm.append("    test r8, r8\n");
        asm.append("    jz .heap_invalid_free\n");
        asm.append(".heap_free_search:\n");
        asm.append("    cmp r8, qword [rel __heap_end]\n");
        asm.append("    jae .heap_invalid_free\n");
        asm.append("    lea rax, [r8 + 16]\n");
        asm.append("    cmp rax, r11\n");
        asm.append("    je .heap_free_found\n");
        asm.append("    mov rax, qword [r8]\n");
        asm.append("    test rax, rax\n");
        asm.append("    jz .heap_invalid_free\n");
        asm.append("    add r8, rax\n");
        asm.append("    jmp .heap_free_search\n");
        asm.append(".heap_free_found:\n");
        asm.append("    cmp qword [r8 + 8], 1\n");
        asm.append("    jne .heap_invalid_free\n"); // double-free
        asm.append("    mov qword [r8 + 8], 0\n");
        asm.append("    mov r9, qword [r8]\n");
        asm.append("    lea r10, [r8 + r9]\n"); // combina com o próximo bloco livre
        asm.append("    cmp r10, qword [rel __heap_end]\n");
        asm.append("    jae .heap_try_previous\n");
        asm.append("    cmp qword [r10 + 8], 0\n");
        asm.append("    jne .heap_try_previous\n");
        asm.append("    add r9, qword [r10]\n");
        asm.append("    mov qword [r8], r9\n");
        asm.append(".heap_try_previous:\n");
        asm.append("    mov r10, qword [rel __heap_start]\n");
        asm.append("    xor edx, edx\n"); // bloco anterior
        asm.append(".heap_previous_search:\n");
        asm.append("    cmp r10, r8\n");
        asm.append("    jae .heap_free_done\n");
        asm.append("    mov rdx, r10\n");
        asm.append("    add r10, qword [r10]\n");
        asm.append("    jmp .heap_previous_search\n");
        asm.append(".heap_free_done:\n");
        asm.append("    test rdx, rdx\n");
        asm.append("    jz .heap_free_return\n");
        asm.append("    cmp qword [rdx + 8], 0\n");
        asm.append("    jne .heap_free_return\n");
        asm.append("    mov rax, qword [r8]\n");
        asm.append("    add qword [rdx], rax\n");
        asm.append(".heap_free_return:\n");
        asm.append("    ret\n");
        asm.append(".heap_invalid_free:\n");
        appendRuntimeExceptionTrap(asm, "__invalid_free_message");

        asm.append("__heap_validate:\n");
        asm.append("    mov r11, rdi\n");
        asm.append("    mov r8, qword [rel __heap_start]\n");
        asm.append("    test r8, r8\n");
        asm.append("    jz __runtime_invalid_reference\n");
        asm.append(".heap_validate_search:\n");
        asm.append("    cmp r8, qword [rel __heap_end]\n");
        asm.append("    jae __runtime_invalid_reference\n");
        asm.append("    lea rax, [r8 + 16]\n");
        asm.append("    cmp rax, r11\n");
        asm.append("    je .heap_validate_found\n");
        asm.append("    mov rax, qword [r8]\n");
        asm.append("    test rax, rax\n");
        asm.append("    jz __runtime_invalid_reference\n");
        asm.append("    add r8, rax\n");
        asm.append("    jmp .heap_validate_search\n");
        asm.append(".heap_validate_found:\n");
        asm.append("    cmp qword [r8 + 8], 1\n");
        asm.append("    jne __runtime_invalid_reference\n");
        asm.append("    ret\n");

        asm.append("__runtime_null_pointer:\n");
        appendRuntimeExceptionTrap(asm, "__null_pointer_message");

        asm.append("__runtime_invalid_reference:\n");
        appendRuntimeExceptionTrap(asm, "__invalid_reference_message");

        asm.append("__runtime_array_bounds:\n");
        appendRuntimeExceptionTrap(asm, "__array_bounds_message");

        asm.append("__runtime_array_store:\n");
        appendRuntimeExceptionTrap(asm, "__array_store_message");

        asm.append("__runtime_negative_array_size:\n");
        appendRuntimeExceptionTrap(asm, "__array_size_message");

        asm.append("__runtime_arithmetic_error:\n");
        appendRuntimeExceptionTrap(asm, "__arithmetic_error_message");

        asm.append("__runtime_out_of_memory:\n");
        appendRuntimeExceptionTrap(asm, "__out_of_memory_message");

        asm.append("__runtime_abstract_method:\n");
        appendRuntimeExceptionTrap(asm, "__abstract_method_message");

        asm.append("__runtime_scanner_input_error:\n");
        appendRuntimeExceptionTrap(asm, "__scanner_input_error_message");

        asm.append("__runtime_no_such_element:\n");
        appendRuntimeExceptionTrap(asm, "__no_such_element_message");

        asm.append("__runtime_io_error:\n");
        appendRuntimeExceptionTrap(asm, "__io_error_message");

        asm.append("__runtime_throw_current:\n");
        asm.append("    mov qword [rel __jvs_exception_ptr], rdi\n");
        asm.append("    mov r13, qword [rel __jvs_trace_depth]\n");
        asm.append("    mov qword [rel __jvs_exception_trace_depth], r13\n");
        asm.append("    xor r12d, r12d\n")
                .append(".capture_exception_trace:\n")
                .append("    cmp r12, r13\n    jae .capture_exception_trace_done\n")
                .append("    mov r10, r12\n    shl r10, 3\n")
                .append("    lea r11, [rel __jvs_trace_method]\n    mov rax, qword [r11+r10]\n")
                .append("    lea r11, [rel __jvs_exception_trace_method]\n    mov qword [r11+r10], rax\n")
                .append("    lea r11, [rel __jvs_trace_file]\n    mov rax, qword [r11+r10]\n")
                .append("    lea r11, [rel __jvs_exception_trace_file]\n    mov qword [r11+r10], rax\n")
                .append("    lea r11, [rel __jvs_trace_line]\n    mov rax, qword [r11+r10]\n")
                .append("    lea r11, [rel __jvs_exception_trace_line]\n    mov qword [r11+r10], rax\n")
                .append("    lea r11, [rel __jvs_trace_column]\n    mov rax, qword [r11+r10]\n")
                .append("    lea r11, [rel __jvs_exception_trace_column]\n    mov qword [r11+r10], rax\n")
                .append("    inc r12\n    jmp .capture_exception_trace\n")
                .append(".capture_exception_trace_done:\n");
        asm.append("    mov rax, qword [rel __jvs_handler_depth]\n");
        asm.append("    test rax, rax\n    jz __uncaught_exception\n");
        asm.append("    dec rax\n    mov qword [rel __jvs_handler_depth], rax\n");
        asm.append("    mov r10, rax\n    shl r10, 3\n");
        asm.append("    lea r11, [rel __jvs_handler_labels]\n");
        asm.append("    mov rcx, qword [r11 + r10]\n");
        asm.append("    lea r11, [rel __jvs_handler_trace_depth]\n");
        asm.append("    mov rax, qword [r11 + r10]\n");
        asm.append("    mov qword [rel __jvs_trace_depth], rax\n");
        asm.append("    lea r11, [rel __jvs_handler_rsp]\n");
        asm.append("    mov rsp, qword [r11 + r10]\n");
        asm.append("    lea r11, [rel __jvs_handler_rbp]\n");
        asm.append("    mov rbp, qword [r11 + r10]\n");
        asm.append("    jmp rcx\n");

        appendUncaughtTracePrinter(asm);

        asm.append("__print_u64:\n");
        asm.append("    lea r8, [rel __number_buffer + 32]\n");
        asm.append("    mov r9d, 10\n");
        asm.append(".u64_loop:\n");
        asm.append("    xor edx, edx\n");
        asm.append("    div r9\n");
        asm.append("    dec r8\n");
        asm.append("    add dl, '0'\n");
        asm.append("    mov byte [r8], dl\n");
        asm.append("    test rax, rax\n");
        asm.append("    jnz .u64_loop\n");
        asm.append("    mov rsi, r8\n");
        asm.append("    lea rdx, [rel __number_buffer + 32]\n");
        asm.append("    sub rdx, r8\n");
        asm.append("    jmp __write_all\n");

        asm.append("__print_i64:\n");
        asm.append("    test rax, rax\n");
        asm.append("    jns .i64_positive\n");
        asm.append("    mov r10, rax\n");
        asm.append("    neg r10\n");
        asm.append("    lea rsi, [rel ").append(stringLabels.get("-")).append("]\n");
        asm.append("    mov edx, 1\n");
        asm.append("    call __write_all\n");
        asm.append("    mov rax, r10\n");
        asm.append("    jmp __print_u64\n");
        asm.append(".i64_positive:\n");
        asm.append("    jmp __print_u64\n");

        appendFloatPrinter(asm);
        if (target.isWindows()) {
            WindowsRuntime.appendIoSupport(asm);
            WindowsRuntime.appendCommandLineSupport(asm);
        } else {
            appendLinuxCommandLineSupport(asm);
        }
        ScannerRuntime.append(asm, target.isWindows());
    }

    private void appendUncaughtTracePrinter(StringBuilder asm) {
        asm.append("__jvs_write_cstr:\n")
                .append("    xor edx, edx\n")
                .append(".write_cstr_length:\n")
                .append("    cmp byte [rsi+rdx], 0\n    je .write_cstr_done\n")
                .append("    inc rdx\n    jmp .write_cstr_length\n")
                .append(".write_cstr_done:\n");
        if (target.isWindows()) {
            asm.append("    mov edi, 2\n    call __jvs_write_once\n    ret\n");
        } else {
            asm.append("    mov eax, 1\n    mov edi, 2\n    syscall\n    ret\n");
        }
        asm.append("__jvs_write_u64:\n")
                .append("    lea r8, [rel __number_buffer+32]\n")
                .append("    mov r9d, 10\n")
                .append(".write_u64_digits:\n")
                .append("    xor edx, edx\n    div r9\n    dec r8\n")
                .append("    add dl, '0'\n    mov byte [r8], dl\n")
                .append("    test rax, rax\n    jnz .write_u64_digits\n")
                .append("    mov rsi, r8\n    lea rdx, [rel __number_buffer+32]\n    sub rdx, r8\n");
        if (target.isWindows()) {
            asm.append("    mov edi, 2\n    call __jvs_write_once\n    ret\n");
        } else {
            asm.append("    mov eax, 1\n    mov edi, 2\n    syscall\n    ret\n");
        }
        asm.append("__jvs_print_uncaught:\n")
                .append("    mov r12, qword [rel __jvs_exception_ptr]\n")
                .append("    test r12, r12\n    jz .uncaught_no_object\n");
        List<String> builtinTypeLabels = new ArrayList<>();
        for (int index = 0; index < runtimeExceptionSpecs().size(); index++) {
            RuntimeExceptionSpec spec = runtimeExceptionSpecs().get(index);
            String typeLabel = ".uncaught_builtin_type_" + index;
            builtinTypeLabels.add(typeLabel);
            asm.append("    lea r10, [rel ").append(spec.objectLabel()).append("]\n")
                    .append("    cmp r12, r10\n    je ").append(typeLabel).append('\n');
        }
        asm.append("    mov rax, qword [r12]\n");
        List<String> classTypeLabels = new ArrayList<>();
        if (sourceProgram != null) {
            for (int index = 0; index < sourceProgram.classes().size(); index++) {
                ClassRecord classNode = sourceProgram.classes().get(index);
                String typeLabel = ".uncaught_class_type_" + index;
                classTypeLabels.add(typeLabel);
                asm.append("    lea r10, [rel __vtable_").append(classNode.name()).append("]\n")
                        .append("    cmp rax, r10\n    je ").append(typeLabel).append('\n');
            }
        }
        asm.append("    lea rsi, [rel ").append(stringLabels.get("Throwable")).append("]\n")
                .append("    jmp .uncaught_type_ready\n");
        for (int index = 0; index < runtimeExceptionSpecs().size(); index++) {
            asm.append(builtinTypeLabels.get(index)).append(":\n")
                    .append("    mov rsi, qword [r12+8]\n    jmp .uncaught_type_ready\n");
        }
        if (sourceProgram != null) {
            for (int index = 0; index < sourceProgram.classes().size(); index++) {
                asm.append(classTypeLabels.get(index)).append(":\n")
                        .append("    lea rsi, [rel ").append(sourceClassTypeLabel(index)).append("]\n")
                        .append("    jmp .uncaught_type_ready\n");
            }
        }
        asm.append(".uncaught_type_ready:\n    call __jvs_write_cstr\n");
        String hasBuiltinMessage = ".uncaught_has_builtin_message";
        for (RuntimeExceptionSpec spec : runtimeExceptionSpecs()) {
            asm.append("    lea r10, [rel ").append(spec.objectLabel()).append("]\n")
                    .append("    cmp r12, r10\n    je ").append(hasBuiltinMessage).append('\n');
        }
        asm.append("    lea rsi, [rel __jvs_newline]\n    call __jvs_write_cstr\n")
                .append("    jmp .uncaught_trace\n")
                .append(hasBuiltinMessage).append(":\n")
                .append("    lea rsi, [rel __jvs_colon_space]\n    call __jvs_write_cstr\n");
        String builtinMessageDone = ".uncaught_builtin_message_done";
        for (RuntimeExceptionSpec spec : runtimeExceptionSpecs()) {
            String messageLabel = ".uncaught_message_" + spec.typeName();
            asm.append("    lea r10, [rel ").append(spec.objectLabel()).append("]\n")
                    .append("    cmp r12, r10\n    je ").append(messageLabel).append('\n');
        }
        asm.append("    lea rsi, [rel ").append(stringLabels.get("")).append("]\n")
                .append("    jmp ").append(builtinMessageDone).append('\n');
        for (RuntimeExceptionSpec spec : runtimeExceptionSpecs()) {
            asm.append(".uncaught_message_").append(spec.typeName()).append(":\n")
                    .append("    mov rsi, qword [r12+16]\n");
            break;
        }
        for (int index = 1; index < runtimeExceptionSpecs().size(); index++) {
            RuntimeExceptionSpec spec = runtimeExceptionSpecs().get(index);
            asm.append(".uncaught_message_").append(spec.typeName()).append(":\n")
                    .append("    mov rsi, qword [r12+16]\n");
        }
        asm.append(builtinMessageDone).append(":\n")
                .append("    call __jvs_write_cstr\n")
                .append("    lea rsi, [rel __jvs_newline]\n    call __jvs_write_cstr\n")
                .append("    jmp .uncaught_trace\n")
                .append(".uncaught_no_object:\n")
                .append("    lea rsi, [rel __uncaught_exception_message]\n    call __jvs_write_cstr\n")
                .append(".uncaught_trace:\n")
                .append("    mov r13, qword [rel __jvs_exception_trace_depth]\n")
                .append(".uncaught_trace_loop:\n")
                .append("    test r13, r13\n    jz .uncaught_trace_done\n")
                .append("    dec r13\n    mov r10, r13\n    shl r10, 3\n")
                .append("    lea rsi, [rel __jvs_trace_prefix]\n    call __jvs_write_cstr\n")
                .append("    lea r11, [rel __jvs_exception_trace_method]\n    mov rsi, qword [r11+r10]\n    call __jvs_write_cstr\n")
                .append("    lea rsi, [rel __jvs_trace_file_prefix]\n    call __jvs_write_cstr\n")
                .append("    lea r11, [rel __jvs_exception_trace_file]\n    mov rsi, qword [r11+r10]\n    call __jvs_write_cstr\n")
                .append("    lea rsi, [rel __jvs_trace_separator]\n    call __jvs_write_cstr\n")
                .append("    lea r11, [rel __jvs_exception_trace_line]\n    mov rax, qword [r11+r10]\n    call __jvs_write_u64\n")
                .append("    lea rsi, [rel __jvs_trace_separator]\n    call __jvs_write_cstr\n")
                .append("    lea r11, [rel __jvs_exception_trace_column]\n    mov rax, qword [r11+r10]\n    call __jvs_write_u64\n")
                .append("    lea rsi, [rel __jvs_trace_suffix]\n    call __jvs_write_cstr\n")
                .append("    jmp .uncaught_trace_loop\n")
                .append(".uncaught_trace_done:\n    ret\n");
    }

    private void appendFloatPrinter(StringBuilder asm) {
        String minusLabel = stringLabels.get("-");
        String periodLabel = stringLabels.get(".");
        String nanLabel = stringLabels.get("nan");
        String infinityLabel = stringLabels.get("inf");
        String overflowLabel = stringLabels.get("overflow");

        asm.append("__print_f64:\n");
        asm.append("    movq rax, xmm0\n");
        asm.append("    mov rcx, 0x8000000000000000\n");
        asm.append("    test rax, rcx\n");
        asm.append("    jz .f64_abs_ready\n");
        asm.append("    shl rax, 1\n");
        asm.append("    shr rax, 1\n");
        asm.append("    movq xmm0, rax\n");
        asm.append("    lea rsi, [rel ").append(minusLabel).append("]\n");
        asm.append("    mov edx, 1\n");
        asm.append("    call __write_all\n");
        asm.append(".f64_abs_ready:\n");
        asm.append("    movq rax, xmm0\n");
        asm.append("    mov rcx, 0x7ff0000000000000\n");
        asm.append("    mov rdx, rax\n");
        asm.append("    and rdx, rcx\n");
        asm.append("    cmp rdx, rcx\n");
        asm.append("    jne .f64_finite\n");
        asm.append("    mov rdx, 0x000fffffffffffff\n");
        asm.append("    test rax, rdx\n");
        asm.append("    jnz .f64_nan\n");
        asm.append("    lea rsi, [rel ").append(infinityLabel).append("]\n");
        asm.append("    mov edx, 3\n");
        asm.append("    jmp __write_all\n");
        asm.append(".f64_nan:\n");
        asm.append("    lea rsi, [rel ").append(nanLabel).append("]\n");
        asm.append("    mov edx, 3\n");
        asm.append("    jmp __write_all\n");
        asm.append(".f64_finite:\n");
        asm.append("    mulsd xmm0, qword [rel __f64_scale]\n");
        asm.append("    addsd xmm0, qword [rel __f64_round]\n");
        asm.append("    cvttsd2si rax, xmm0\n");
        asm.append("    mov rcx, 0x7fffffffffffffff\n");
        asm.append("    cmp rax, rcx\n");
        asm.append("    ja .f64_overflow\n");
        asm.append("    mov ecx, 1000000\n");
        asm.append("    xor edx, edx\n");
        asm.append("    div rcx\n");
        asm.append("    mov r10, rdx\n");
        asm.append("    call __print_u64\n");
        asm.append("    lea rsi, [rel ").append(periodLabel).append("]\n");
        asm.append("    mov edx, 1\n");
        asm.append("    call __write_all\n");
        asm.append("    mov rax, r10\n");
        asm.append("    lea r8, [rel __number_buffer + 6]\n");
        asm.append("    mov r9d, 10\n");
        asm.append("    mov ecx, 6\n");
        asm.append(".f64_fraction_loop:\n");
        asm.append("    xor edx, edx\n");
        asm.append("    div r9\n");
        asm.append("    dec r8\n");
        asm.append("    add dl, '0'\n");
        asm.append("    mov byte [r8], dl\n");
        asm.append("    loop .f64_fraction_loop\n");
        asm.append("    mov rsi, r8\n");
        asm.append("    mov edx, 6\n");
        asm.append("    jmp __write_all\n");
        asm.append(".f64_overflow:\n");
        asm.append("    lea rsi, [rel ").append(overflowLabel).append("]\n");
        asm.append("    mov edx, 8\n");
        asm.append("    jmp __write_all\n");
    }

    private void emitExitSyscall(StringBuilder asm, int status) {
        asm.append("    mov edi, ").append(status).append("\n");
        asm.append("    mov eax, 60\n");
        asm.append("    syscall\n");
        asm.append("    ud2\n");
    }

    private void appendLinuxCommandLineSupport(StringBuilder asm) {
        asm.append("__jvs_build_linux_args:\n")
                .append("    push rbp\n")
                .append("    mov rbp, rsp\n")
                .append("    sub rsp, 16\n")
                .append("    mov rax, qword [rel __jvs_start_argc]\n")
                .append("    shl rax, 3\n")
                .append("    jc __runtime_out_of_memory\n")
                .append("    add rax, 32\n")
                .append("    jc __runtime_out_of_memory\n")
                .append("    mov rdi, rax\n")
                .append("    call __heap_alloc\n")
                .append("    mov qword [rbp-8], rax\n")
                .append("    lea rdx, [rel __jvs_array_vtable]\n")
                .append("    mov qword [rax], rdx\n")
                .append("    mov rdx, qword [rel __jvs_start_argc]\n")
                .append("    mov qword [rax+8], rdx\n")
                .append("    lea rdx, [rel __jvs_main_args_array_type]\n")
                .append("    mov qword [rax+16], rdx\n")
                .append("    lea rdx, [rel __jvs_main_args_element_type]\n")
                .append("    mov qword [rax+24], rdx\n")
                .append("    mov qword [rbp-16], 0\n")
                .append(".linux_args_loop:\n")
                .append("    mov rax, qword [rbp-16]\n")
                .append("    cmp rax, qword [rel __jvs_start_argc]\n")
                .append("    jae .linux_args_done\n")
                .append("    inc rax\n")
                .append("    shl rax, 3\n")
                .append("    mov rdx, qword [rel __jvs_start_argv]\n")
                .append("    mov rcx, qword [rdx+rax]\n")
                .append("    mov rax, qword [rbp-8]\n")
                .append("    mov rdx, qword [rbp-16]\n")
                .append("    mov qword [rax+rdx*8+32], rcx\n")
                .append("    inc qword [rbp-16]\n")
                .append("    jmp .linux_args_loop\n")
                .append(".linux_args_done:\n")
                .append("    mov rax, qword [rbp-8]\n")
                .append("    leave\n")
                .append("    ret\n\n");
    }

    private void emitObjectAllocation(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null
                || instruction.destination().type() != Operand.Type.REF
                || instruction.left() == null
                || instruction.right() == null
                || instruction.right().kind() != Operand.Kind.CONSTANT
                || !(instruction.right().value() instanceof String className)) {
            throw new InvalidIRException(
                    "ALLOC_OBJECT exige destino REF, tamanho e nome da classe.");
        }

        loadI32(asm, instruction.left(), "edi");
        asm.append("    call __heap_alloc\n");
        asm.append("    lea rdx, [rel __vtable_").append(className).append(
                "]\n");
        asm.append("    mov qword [rax], rdx\n");
        storeI64(asm, instruction.destination(), "rax");
    }

    private void emitObjectFree(StringBuilder asm, IRInstruction instruction) {
        if (instruction.left() == null || instruction.left().type() != Operand.Type.REF) {
            throw new InvalidIRException("FREE_OBJECT exige uma referência de objeto.");
        }
        int labelId = nextFreeLabel++;
        String doneLabel = ".L_free_done_" + labelId;

        loadI64(asm, instruction.left(), "rdi");
        asm.append("    test rdi, rdi\n");
        asm.append("    jz ").append(doneLabel).append("\n");
        asm.append("    call __heap_free\n");
        if (instruction.left().kind() == Operand.Kind.VARIABLE
                || instruction.left().kind() == Operand.Kind.TEMPORARY) {
            asm.append("    xor eax, eax\n");
            storeI64(asm, instruction.left(), "rax");
        }
        asm.append(doneLabel).append(":\n");
    }

    private void emitArrayAllocation(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null
                || instruction.destination().type() != Operand.Type.REF
                || instruction.left() == null
                || instruction.right() == null
                || instruction.right().kind() != Operand.Kind.CONSTANT
                || !(instruction.right().value() instanceof Integer elementSize)
                || (elementSize != 1 && elementSize != 4 && elementSize != 8)
                || instruction.arguments().size() != 2
                || instruction.arguments().get(0).kind() != Operand.Kind.CONSTANT
                || !(instruction.arguments().get(0).value() instanceof String arrayType)
                || instruction.arguments().get(1).kind() != Operand.Kind.CONSTANT
                || !(instruction.arguments().get(1).value() instanceof String elementType)) {
            throw new InvalidIRException("ALLOC_ARRAY exige destino, tamanho e tipo de elemento válidos.");
        }

        loadI32(asm, instruction.left(), "eax");
        asm.append("    test eax, eax\n");
        asm.append("    js __runtime_negative_array_size\n");
        asm.append("    movsxd rdi, eax\n");
        asm.append("    imul rdi, ").append(elementSize).append("\n");
        asm.append("    add rdi, 32\n"); // cabeçalho do array
        asm.append("    jc __runtime_out_of_memory\n");
        asm.append("    call __heap_alloc\n");
        loadI32(asm, instruction.left(), "ecx");
        asm.append("    lea rdx, [rel __jvs_array_vtable]\n");
        asm.append("    mov qword [rax], rdx\n");
        asm.append("    mov qword [rax + 8], rcx\n");
        asm.append("    lea rdx, [rel ").append(stringLabels.get(arrayType)).append("]\n");
        asm.append("    mov qword [rax + 16], rdx\n");
        asm.append("    lea rdx, [rel ").append(stringLabels.get(elementType)).append("]\n");
        asm.append("    mov qword [rax + 24], rdx\n");
        storeI64(asm, instruction.destination(), "rax");
    }

    private void emitArrayFree(StringBuilder asm, IRInstruction instruction) {
        if (instruction.left() == null || instruction.left().type() != Operand.Type.REF) {
            throw new InvalidIRException("FREE_ARRAY exige uma referência de array.");
        }
        int labelId = nextFreeLabel++;
        String doneLabel = ".L_array_free_done_" + labelId;
        loadI64(asm, instruction.left(), "rdi");
        asm.append("    test rdi, rdi\n");
        asm.append("    jz ").append(doneLabel).append("\n");
        asm.append("    call __heap_free\n");
        if (instruction.left().kind() == Operand.Kind.VARIABLE
                || instruction.left().kind() == Operand.Kind.TEMPORARY) {
            asm.append("    xor eax, eax\n");
            storeI64(asm, instruction.left(), "rax");
        }
        asm.append(doneLabel).append(":\n");
    }

    private void emitArrayLength(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null || instruction.left() == null) {
            throw new InvalidIRException("ARRAY_LENGTH exige destino e array.");
        }
        loadI64(asm, instruction.left(), "r11");
        asm.append("    test r11, r11\n");
        asm.append("    jz __runtime_null_pointer\n");
        asm.append("    mov rdi, r11\n");
        asm.append("    call __heap_validate\n");
        asm.append("    mov eax, dword [r11 + 8]\n");
        storeI32(asm, instruction.destination(), "eax");
    }

    private void emitArrayLoad(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null
                || instruction.left() == null
                || instruction.right() == null) {
            throw new InvalidIRException("LOAD_ARRAY exige destino, array e índice.");
        }
        int elementSize = elementSize(instruction.destination().type());
        emitArrayElementAddress(asm, instruction.left(), instruction.right(), elementSize);
        switch (instruction.destination().type()) {
            case I32 -> {
                asm.append("    mov eax, dword [r11 + r10]\n");
                storeI32(asm, instruction.destination(), "eax");
            }
            case BOOL -> {
                asm.append("    movzx eax, byte [r11 + r10]\n");
                storeI32(asm, instruction.destination(), "eax");
            }
            case I64, REF -> {
                asm.append("    mov rax, qword [r11 + r10]\n");
                storeI64(asm, instruction.destination(), "rax");
            }
            case F64 -> {
                asm.append("    movsd xmm0, qword [r11 + r10]\n");
                storeF64(asm, instruction.destination(), "xmm0");
            }
        }
    }

    private void emitArrayStore(StringBuilder asm, IRInstruction instruction) {
        if (instruction.left() == null
                || instruction.right() == null
                || instruction.arguments().isEmpty()) {
            throw new InvalidIRException("STORE_ARRAY exige array, índice e valor.");
        }
        Operand value = instruction.arguments().get(0);
        int elementSize = elementSize(value.type());
        emitArrayElementAddress(asm, instruction.left(), instruction.right(), elementSize);
        if (value.type() == Operand.Type.REF) {
            asm.append("    push r10\n");
            emitArrayStoreTypeCheck(asm, instruction, value);
            asm.append("    pop r10\n");
        }
        switch (value.type()) {
            case I32 -> {
                loadI32(asm, value, "edx");
                asm.append("    mov dword [r11 + r10], edx\n");
            }
            case BOOL -> {
                loadI32(asm, value, "edx");
                asm.append("    mov byte [r11 + r10], dl\n");
            }
            case I64, REF -> {
                loadI64(asm, value, "rdx");
                asm.append("    mov qword [r11 + r10], rdx\n");
            }
            case F64 -> {
                loadF64(asm, value, "xmm0");
                asm.append("    movsd qword [r11 + r10], xmm0\n");
            }
        }
    }

    private void emitArrayStoreTypeCheck(
            StringBuilder asm,
            IRInstruction instruction,
            Operand value) {
        if (instruction.arguments().size() < 2
                || instruction.arguments().get(1).kind() != Operand.Kind.CONSTANT
                || !(instruction.arguments().get(1).value() instanceof String staticValueType)) {
            throw new InvalidIRException(
                    "STORE_ARRAY de referências exige o tipo estático do valor.");
        }
        int id = nextFreeLabel++;
        String allowed = "__array_store_allowed_" + id;
        String failure = "__array_store_failure_" + id;
        String arrayValue = "__array_store_array_value_" + id;
        loadI64(asm, value, "rdx");
        asm.append("    test rdx, rdx\n    jz ").append(allowed).append('\n');
        asm.append("    mov rax, qword [rdx]\n");
        asm.append("    lea r10, [rel __jvs_array_vtable]\n");
        asm.append("    cmp rax, r10\n    je ").append(arrayValue).append('\n');

        // Object[] accepts any reference. String has no object header in this runtime,
        // so its statically known immutable string representation is checked here.
        asm.append("    lea r10, [rel ").append(stringLabels.get("Object")).append("]\n")
                .append("    cmp qword [r11 - 8], r10\n    je ").append(allowed).append('\n');
        if (staticValueType.equals("String")) {
            asm.append("    lea r10, [rel ").append(stringLabels.get("String")).append("]\n")
                    .append("    cmp qword [r11 - 8], r10\n    je ").append(allowed).append('\n');
        }

        if (sourceProgram != null) {
            for (ClassRecord candidate : sourceProgram.classes()) {
                String next = "__array_store_next_class_" + nextFreeLabel++;
                asm.append("    lea r10, [rel __vtable_").append(candidate.name()).append("]\n")
                        .append("    cmp rax, r10\n    jne ").append(next).append('\n');
                for (String parent : classSupertypes(candidate.name())) {
                    asm.append("    lea r10, [rel ").append(stringLabels.get(parent)).append("]\n")
                            .append("    cmp qword [r11 - 8], r10\n    je ").append(allowed).append('\n');
                }
                asm.append("    jmp ").append(failure).append('\n');
                asm.append(next).append(":\n");
            }
        }

        for (RuntimeExceptionSpec spec : runtimeExceptionSpecs()) {
            String next = "__array_store_next_runtime_" + id + "_"
                    + BuiltinExceptionTypes.simpleName(spec.typeName());
            asm.append("    lea r10, [rel ").append(spec.objectLabel()).append("]\n")
                    .append("    cmp rdx, r10\n    jne ").append(next).append('\n');
            for (String parent : classSupertypes(spec.typeName())) {
                asm.append("    lea r10, [rel ").append(stringLabels.get(parent)).append("]\n")
                        .append("    cmp qword [r11 - 8], r10\n    je ").append(allowed).append('\n');
            }
            asm.append("    jmp ").append(failure).append('\n').append(next).append(":\n");
        }
        asm.append("    jmp ").append(failure).append('\n');

        asm.append(arrayValue).append(":\n");
        asm.append("    mov rax, qword [rdx + 16]\n");
        for (String candidateArrayType : arrayTypeNames) {
            String next = "__array_store_next_array_" + nextFreeLabel++;
            asm.append("    lea r10, [rel ").append(stringLabels.get(candidateArrayType)).append("]\n")
                    .append("    cmp rax, r10\n    jne ").append(next).append('\n');
            for (String parent : arraySupertypes(candidateArrayType)) {
                asm.append("    lea r10, [rel ").append(stringLabels.get(parent)).append("]\n")
                        .append("    cmp qword [r11 - 8], r10\n    je ").append(allowed).append('\n');
            }
            asm.append("    jmp ").append(failure).append('\n').append(next).append(":\n");
        }
        asm.append("    jmp ").append(failure).append('\n');
        asm.append(failure).append(":\n    jmp __runtime_array_store\n");
        asm.append(allowed).append(":\n");
    }

    private List<String> classSupertypes(String typeName) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String current = typeName;
        Set<String> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current)) {
            result.add(current);
            ClassRecord sourceClass = sourceProgram == null ? null
                    : findSourceClass(current);
            if (sourceClass != null) {
                current = sourceClass.superClass() == null ? "Object" : sourceClass.superClass();
            } else if (BuiltinExceptionTypes.isBuiltin(current)) {
                current = BuiltinExceptionTypes.parentOf(current);
            } else {
                current = current.equals("Object") ? null : "Object";
            }
        }
        if (!typeName.equals("Object")) result.add("Object");
        return List.copyOf(result);
    }

    private List<String> arraySupertypes(String arrayType) {
        if (!arrayType.endsWith("[]")) return List.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        int dimensions = 0;
        String component = arrayType;
        while (component.endsWith("[]")) {
            dimensions++;
            component = component.substring(0, component.length() - 2);
        }
        String suffix = "[]".repeat(dimensions);
        if (isPrimitiveArrayComponent(component)) {
            result.add(arrayType);
            for (int level = 1; level < dimensions; level++) {
                result.add("Object" + "[]".repeat(level));
            }
        } else {
            for (String parent : classSupertypes(component)) result.add(parent + suffix);
            for (int level = 1; level < dimensions; level++) {
                result.add("Object" + "[]".repeat(level));
            }
        }
        return List.copyOf(result);
    }

    private ClassRecord findSourceClass(String name) {
        return sourceClassesByName.get(name);
    }

    private boolean isPrimitiveArrayComponent(String type) {
        return type.equals("int") || type.equals("long")
                || type.equals("double") || type.equals("boolean");
    }

    private int elementSize(Operand.Type type) {
        return switch (type) {
            case BOOL -> 1;
            case I32 -> 4;
            case I64, F64, REF -> 8;
        };
    }

    private void emitArraySizeCheck(StringBuilder asm, IRInstruction instruction) {
        if (instruction.left() == null || instruction.left().type() != Operand.Type.I32) {
            throw new InvalidIRException("CHECK_ARRAY_SIZE exige um tamanho int.");
        }
        loadI32(asm, instruction.left(), "eax");
        asm.append("    test eax, eax\n");
        asm.append("    js __runtime_negative_array_size\n");
    }

    private void emitArrayElementAddress(
            StringBuilder asm,
            Operand array,
            Operand index,
            int elementSize) {
        loadI64(asm, array, "r11");
        asm.append("    test r11, r11\n");
        asm.append("    jz __runtime_null_pointer\n");
        asm.append("    mov rdi, r11\n");
        asm.append("    call __heap_validate\n");
        loadI32(asm, index, "eax");
        asm.append("    test eax, eax\n");
        asm.append("    js __runtime_array_bounds\n");
        asm.append("    movsxd r10, eax\n");
        asm.append("    cmp r10, qword [r11 + 8]\n");
        asm.append("    jae __runtime_array_bounds\n");
        if (elementSize != 1) {
            asm.append("    imul r10, ").append(elementSize).append("\n");
        }
        asm.append("    add r11, 32\n");
    }

    private void emitFieldStore(StringBuilder asm, IRInstruction instruction) {
        Operand receiver = instruction.left();
        Operand offsetOperand = instruction.right();

        if (receiver == null
                || offsetOperand == null
                || offsetOperand.kind() != Operand.Kind.CONSTANT
                || !(offsetOperand.value() instanceof Integer offset)
                || instruction.arguments().size() != 1) {
            throw new InvalidIRException(
                    "STORE_FIELD exige receptor, offset inteiro e um valor.");
        }

        Operand value = instruction.arguments().get(0);

        loadI64(asm, receiver, "r11");
        asm.append("    test r11, r11\n");
        asm.append("    jz __runtime_null_pointer\n");
        asm.append("    mov rdi, r11\n");
        asm.append("    call __heap_validate\n");

        String fieldAddress = offset == 0
                ? "[r11]"
                : "[r11 + " + offset + "]";

        switch (value.type()) {
            case I32, BOOL -> {
                loadI32(asm, value, "edx");
                asm.append("    mov dword ")
                        .append(fieldAddress)
                        .append(", edx\n");
            }
            case I64, REF -> {
                loadI64(asm, value, "rdx");
                asm.append("    mov qword ")
                        .append(fieldAddress)
                        .append(", rdx\n");
            }
            case F64 -> {
                loadF64(asm, value, "xmm0");
                asm.append("    movsd qword ")
                        .append(fieldAddress)
                        .append(", xmm0\n");
            }
            default -> throw new UnsupportedBackendFeatureException(
                    "Tipo não suportado em STORE_FIELD: " + value.type());
        }
    }

    private void emitStaticStore(StringBuilder asm, IRInstruction instruction) {
        String label = staticLabel(instruction.left());
        if (instruction.arguments().size() != 1) {
            throw new InvalidIRException("STORE_STATIC exige exatamente um valor.");
        }
        Operand value = instruction.arguments().get(0);
        switch (value.type()) {
            case BOOL -> {
                loadI32(asm, value, "eax");
                asm.append("    mov byte [rel ").append(label).append("], al\n");
            }
            case I32 -> {
                loadI32(asm, value, "eax");
                asm.append("    mov dword [rel ").append(label).append("], eax\n");
            }
            case I64, REF -> {
                loadI64(asm, value, "rax");
                asm.append("    mov qword [rel ").append(label).append("], rax\n");
            }
            case F64 -> {
                loadF64(asm, value, "xmm0");
                asm.append("    movsd qword [rel ").append(label).append("], xmm0\n");
            }
            default -> throw new UnsupportedBackendFeatureException(
                    "Tipo não suportado em STORE_STATIC: " + value.type());
        }
    }

    private void emitStaticLoad(StringBuilder asm, IRInstruction instruction) {
        String label = staticLabel(instruction.left());
        Operand destination = instruction.destination();
        switch (destination.type()) {
            case BOOL -> {
                asm.append("    movzx eax, byte [rel ").append(label).append("]\n");
                storeI32(asm, destination, "eax");
            }
            case I32 -> {
                asm.append("    mov eax, dword [rel ").append(label).append("]\n");
                storeI32(asm, destination, "eax");
            }
            case I64, REF -> {
                asm.append("    mov rax, qword [rel ").append(label).append("]\n");
                storeI64(asm, destination, "rax");
            }
            case F64 -> {
                asm.append("    movsd xmm0, qword [rel ").append(label).append("]\n");
                storeF64(asm, destination, "xmm0");
            }
            default -> throw new UnsupportedBackendFeatureException(
                    "Tipo não suportado em LOAD_STATIC: " + destination.type());
        }
    }

    private String staticLabel(Operand operand) {
        if (operand == null || operand.kind() != Operand.Kind.LABEL) {
            throw new InvalidIRException("A operação static exige um label de armazenamento.");
        }
        return operand.value().toString();
    }

    private void emitFieldLoad(StringBuilder asm, IRInstruction instruction) {
        Operand base = instruction.left();
        Operand offsetOperand = instruction.right();

        if (base == null
                || offsetOperand == null
                || offsetOperand.kind() != Operand.Kind.CONSTANT
                || !(offsetOperand.value() instanceof Integer offset)) {
            throw new InvalidIRException(
                    "LOAD_FIELD exige uma referência e um offset inteiro.");
        }

        loadI64(asm, base, "rax");
        asm.append("    test rax, rax\n");
        asm.append("    jz __runtime_null_pointer\n");
        asm.append("    mov rdi, rax\n");
        asm.append("    push rax\n");
        asm.append("    call __heap_validate\n");
        asm.append("    pop rax\n");

        String fieldAddress = offset == 0
                ? "[rax]"
                : "[rax + " + offset + "]";

        switch (instruction.destination().type()) {
            case I32, BOOL -> {
                asm.append("    mov eax, dword ")
                        .append(fieldAddress)
                        .append("\n");
                storeI32(asm, instruction.destination(), "eax");
            }
            case I64, REF -> {
                asm.append("    mov rdx, qword ")
                        .append(fieldAddress)
                        .append("\n");
                storeI64(asm, instruction.destination(), "rdx");
            }
            case F64 -> {
                asm.append("    movsd xmm0, qword ")
                        .append(fieldAddress)
                        .append("\n");
                storeF64(asm, instruction.destination(), "xmm0");
            }
            default -> throw new UnsupportedBackendFeatureException(
                    "Tipo não suportado em LOAD_FIELD: "
                            + instruction.destination().type());
        }
    }

    private void emitIntegerBinary(
            StringBuilder asm,
            IRInstruction instruction,
            String opcode) {
        if (instruction.destination().type() == Operand.Type.F64) {
            loadF64(asm, instruction.left(), "xmm0");
            loadF64(asm, instruction.right(), "xmm1");
            String floatingOpcode = switch (instruction.opCode()) {
                case ADD -> "addsd";
                case SUB -> "subsd";
                case MUL -> "mulsd";
                default -> throw new UnsupportedBackendFeatureException(
                        "Operação double não implementada: " + instruction.opCode());
            };
            asm.append("    ").append(floatingOpcode).append(" xmm0, xmm1\n");
            storeF64(asm, instruction.destination(), "xmm0");
        } else if (instruction.destination().type() == Operand.Type.I64) {
            loadI64(asm, instruction.left(), "rax");
            loadI64(asm, instruction.right(), "rcx");
            asm.append("    ").append(opcode).append(" rax, rcx\n");
            storeI64(asm, instruction.destination(), "rax");
        } else {
            loadI32(asm, instruction.left(), "eax");
            loadI32(asm, instruction.right(), "ecx");
            asm.append("    ").append(opcode).append(" eax, ecx\n");
            storeI32(asm, instruction.destination(), "eax");
        }
    }

    private void emitDivision(
            StringBuilder asm,
            IRInstruction instruction,
            boolean remainder) {
        if (instruction.destination().type() == Operand.Type.F64) {
            if (remainder) {
                throw new UnsupportedBackendFeatureException("mod double ainda não é suportado.");
            }
            loadF64(asm, instruction.left(), "xmm0");
            loadF64(asm, instruction.right(), "xmm1");
            asm.append("    divsd xmm0, xmm1\n");
            storeF64(asm, instruction.destination(), "xmm0");
            return;
        }
        int id = nextDivisionLabel++;
        String normal = ".L_div_normal_" + id;
        String done = ".L_div_done_" + id;
        if (instruction.destination().type() == Operand.Type.I64) {
            loadI64(asm, instruction.left(), "rax");
            loadI64(asm, instruction.right(), "rcx");
            asm.append("    test rcx, rcx\n");
            asm.append("    jz __runtime_arithmetic_error\n");
            asm.append("    cmp rcx, -1\n");
            asm.append("    jne ").append(normal).append("\n");
            asm.append("    mov rdx, -9223372036854775808\n");
            asm.append("    cmp rax, rdx\n");
            asm.append("    jne ").append(normal).append("\n");
            if (remainder)
                asm.append("    xor edx, edx\n");
            asm.append("    jmp ").append(done).append("\n");
            asm.append(normal).append(":\n");
            asm.append("    cqo\n");
            asm.append("    idiv rcx\n");
            asm.append(done).append(":\n");
            storeI64(asm, instruction.destination(), remainder ? "rdx" : "rax");
        } else {
            loadI32(asm, instruction.left(), "eax");
            loadI32(asm, instruction.right(), "ecx");
            asm.append("    test ecx, ecx\n");
            asm.append("    jz __runtime_arithmetic_error\n");
            asm.append("    cmp ecx, -1\n");
            asm.append("    jne ").append(normal).append("\n");
            asm.append("    cmp eax, -2147483648\n");
            asm.append("    jne ").append(normal).append("\n");
            if (remainder)
                asm.append("    xor edx, edx\n");
            asm.append("    jmp ").append(done).append("\n");
            asm.append(normal).append(":\n");
            asm.append("    cdq\n");
            asm.append("    idiv ecx\n");
            asm.append(done).append(":\n");
            storeI32(asm, instruction.destination(), remainder ? "edx" : "eax");
        }
    }

    private void emitComparison(
            StringBuilder asm,
            IRInstruction instruction) {
        if (instruction.left().type() == Operand.Type.F64) {
            loadF64(asm, instruction.left(), "xmm0");
            loadF64(asm, instruction.right(), "xmm1");
            asm.append("    ucomisd xmm0, xmm1\n");
            switch (instruction.opCode()) {
                case EQ -> {
                    asm.append("    setnp al\n    sete cl\n    and al, cl\n");
                }
                case NE -> {
                    asm.append("    setp al\n    setne cl\n    or al, cl\n");
                }
                case LT -> {
                    asm.append("    setb al\n    setnp cl\n    and al, cl\n");
                }
                case LE -> {
                    asm.append("    setbe al\n    setnp cl\n    and al, cl\n");
                }
                case GT -> asm.append("    seta al\n");
                case GE -> {
                    asm.append("    setae al\n    setnp cl\n    and al, cl\n");
                }
                default -> throw new InvalidIRException(
                        "Não é uma comparação: " + instruction.opCode());
            }
            asm.append("    movzx eax, al\n");
            storeI32(asm, instruction.destination(), "eax");
            return;
        }
        boolean wide = instruction.left().type() == Operand.Type.I64
                || instruction.left().type() == Operand.Type.REF;
        if (wide) {
            loadI64(asm, instruction.left(), "rax");
            loadI64(asm, instruction.right(), "rcx");
            asm.append("    cmp rax, rcx\n");
        } else {
            loadI32(asm, instruction.left(), "eax");
            loadI32(asm, instruction.right(), "ecx");
            asm.append("    cmp eax, ecx\n");
        }

        String setInstruction = switch (instruction.opCode()) {
            case LT -> "setl";
            case GT -> "setg";
            case LE -> "setle";
            case GE -> "setge";
            case EQ -> "sete";
            case NE -> "setne";
            default -> throw new InvalidIRException(
                    "Não é uma comparação: " + instruction.opCode());
        };

        asm.append("    ").append(setInstruction).append(" al\n");
        asm.append("    movzx eax, al\n");
        storeI32(asm, instruction.destination(), "eax");
    }

    private void emitCall(
            StringBuilder asm,
            IRInstruction instruction) {
        int outgoingBytes = emitCallArguments(asm, instruction.arguments(), 0);
        asm.append("    call ")
                .append(instruction.left().value())
                .append("\n");
        if (outgoingBytes != 0) asm.append("    add rsp, ").append(outgoingBytes).append("\n");

        if (instruction.destination() != null) {
            if (instruction.destination().type() == Operand.Type.F64) {
                storeF64(asm, instruction.destination(), "xmm0");
            } else if (instruction.destination().type() == Operand.Type.I64
                    || instruction.destination().type() == Operand.Type.REF) {
                storeI64(asm, instruction.destination(), "rax");
            } else {
                storeI32(asm, instruction.destination(), "eax");
            }
        }
    }

    private void emitVirtualCall(StringBuilder asm, IRInstruction instruction) {
        if (virtualMethodTable == null
                || instruction.left() == null
                || instruction.right() == null
                || instruction.right().kind() != Operand.Kind.CONSTANT
                || !(instruction.right().value() instanceof Integer slot)
                || slot < 0) {
            throw new InvalidIRException("CALL_VIRTUAL exige receptor e slot válido.");
        }

        loadI64(asm, instruction.left(), "rdi");
        asm.append("    test rdi, rdi\n");
        asm.append("    jz __runtime_null_pointer\n");
        asm.append("    call __heap_validate\n");
        asm.append("    mov rax, qword [rdi]\n");
        asm.append("    mov r10, qword [rax + ").append(slot * 8).append("]\n");
        asm.append("    test r10, r10\n");
        asm.append("    jz __runtime_abstract_method\n");

        int outgoingBytes = emitCallArguments(asm, instruction.arguments(), 1);
        asm.append("    call r10\n");
        if (outgoingBytes != 0) asm.append("    add rsp, ").append(outgoingBytes).append("\n");
        storeCallResult(asm, instruction.destination());
    }

    /**
     * Loads arguments using the compiler's internal x64 convention. Arguments
     * beyond the available GP/XMM registers are placed in 8-byte stack slots.
     * For virtual calls, GP register zero is already occupied by the receiver.
     */
    private int emitCallArguments(StringBuilder asm, List<Operand> arguments, int initialGpIndex) {
        String[] gpRegisters = { "rdi", "rsi", "rdx", "rcx", "r8", "r9" };
        String[] xmmRegisters = {
                "xmm0", "xmm1", "xmm2", "xmm3",
                "xmm4", "xmm5", "xmm6", "xmm7"
        };
        int gpIndex = initialGpIndex;
        int xmmIndex = 0;
        int stackIndex = 0;
        int spills = countSpilledArguments(arguments, initialGpIndex);
        int outgoingBytes = align(spills * 8, 16);
        if (outgoingBytes != 0) asm.append("    sub rsp, ").append(outgoingBytes).append("\n");

        for (Operand argument : arguments) {
            if (argument.type() == Operand.Type.F64) {
                if (xmmIndex == xmmRegisters.length) {
                    loadF64(asm, argument, "xmm0");
                    asm.append("    movsd qword [rsp+").append(stackIndex++ * 8).append("], xmm0\n");
                } else {
                    loadF64(asm, argument, xmmRegisters[xmmIndex]);
                }
                xmmIndex++;
            } else {
                if (gpIndex == gpRegisters.length) {
                    if (argument.type() == Operand.Type.I32 || argument.type() == Operand.Type.BOOL) {
                        loadI32(asm, argument, "eax");
                        asm.append("    mov qword [rsp+").append(stackIndex++ * 8).append("], rax\n");
                    } else {
                        loadI64(asm, argument, "rax");
                        asm.append("    mov qword [rsp+").append(stackIndex++ * 8).append("], rax\n");
                    }
                } else {
                    String register = gpRegisters[gpIndex];
                    if (argument.type() == Operand.Type.I32 || argument.type() == Operand.Type.BOOL) {
                        loadI32(asm, argument, register32(register));
                    } else {
                        loadI64(asm, argument, register);
                    }
                }
                gpIndex++;
            }
        }
        return outgoingBytes;
    }

    private int countSpilledArguments(List<Operand> arguments, int initialGpIndex) {
        int gpIndex = initialGpIndex;
        int xmmIndex = 0;
        int spills = 0;
        for (Operand argument : arguments) {
            if (argument.type() == Operand.Type.F64) {
                if (xmmIndex++ >= 8) spills++;
            } else if (gpIndex++ >= 6) {
                spills++;
            }
        }
        return spills;
    }

    private void emitCatchMatch(StringBuilder asm, IRInstruction instruction) {
        if (sourceProgram == null || instruction.destination() == null
                || instruction.right() == null || instruction.right().kind() != Operand.Kind.LABEL) {
            throw new InvalidIRException("CATCH_MATCH exige programa fonte e tipo catch.");
        }
        String catchType = instruction.right().value().toString();
        String matchLabel = "__catch_match_" + nextFreeLabel++;
        String doneLabel = "__catch_done_" + nextFreeLabel++;
        asm.append("    mov rax, qword [rel __jvs_exception_ptr]\n");
        asm.append("    test rax, rax\n    jz ").append(doneLabel).append("_false\n");
        for (RuntimeExceptionSpec spec : runtimeExceptionSpecs()) {
            if (!BuiltinExceptionTypes.isSubtype(spec.typeName(), catchType)) continue;
            asm.append("    lea r10, [rel ").append(spec.objectLabel()).append("]\n");
            asm.append("    cmp rax, r10\n    je ").append(matchLabel).append('\n');
        }
        asm.append("    mov rax, qword [rax]\n");
        for (ClassRecord candidate : sourceProgram.classes()) {
            if (!isClassSubtype(candidate, catchType)) continue;
            asm.append("    lea r10, [rel __vtable_").append(candidate.name()).append("]\n");
            asm.append("    cmp rax, r10\n    je ").append(matchLabel).append('\n');
        }
        asm.append(doneLabel).append("_false:\n");
        asm.append("    mov eax, 0\n");
        asm.append("    jmp ").append(doneLabel).append("\n");
        asm.append(matchLabel).append(":\n    mov eax, 1\n");
        asm.append(doneLabel).append(":\n");
        storeI32(asm, instruction.destination(), "eax");
    }

    private void emitPushExceptionHandler(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null
                || instruction.destination().kind() != Operand.Kind.LABEL) {
            throw new InvalidIRException("PUSH_EXCEPTION_HANDLER exige um rótulo.");
        }
        String overflow = "__exception_handler_overflow_" + nextFreeLabel++;
        String done = "__exception_handler_push_done_" + nextFreeLabel++;
        asm.append("    mov rax, qword [rel __jvs_handler_depth]\n");
        asm.append("    cmp rax, 1024\n    jae ").append(overflow).append('\n');
        asm.append("    mov r10, rax\n    shl r10, 3\n");
        asm.append("    lea r11, [rel __jvs_handler_labels]\n");
        asm.append("    lea rcx, [rel ").append(instruction.destination().value()).append("]\n");
        asm.append("    mov qword [r11 + r10], rcx\n");
        asm.append("    lea r11, [rel __jvs_handler_rsp]\n");
        asm.append("    mov qword [r11 + r10], rsp\n");
        asm.append("    lea r11, [rel __jvs_handler_rbp]\n");
        asm.append("    mov qword [r11 + r10], rbp\n");
        asm.append("    lea r11, [rel __jvs_handler_trace_depth]\n");
        asm.append("    mov rcx, qword [rel __jvs_trace_depth]\n");
        asm.append("    mov qword [r11 + r10], rcx\n");
        asm.append("    inc qword [rel __jvs_handler_depth]\n");
        asm.append("    jmp ").append(done).append('\n');
        asm.append(overflow).append(":\n");
        appendRuntimeExceptionTrap(asm, "__stack_overflow_message");
        asm.append(done).append(":\n");
    }

    private void emitPopExceptionHandlerIf(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null
                || instruction.destination().kind() != Operand.Kind.LABEL) {
            throw new InvalidIRException("POP_EXCEPTION_HANDLER_IF exige um rótulo.");
        }
        String done = "__exception_handler_pop_done_" + nextFreeLabel++;
        asm.append("    mov rax, qword [rel __jvs_handler_depth]\n");
        asm.append("    test rax, rax\n    jz ").append(done).append('\n');
        asm.append("    dec rax\n    mov r10, rax\n    shl r10, 3\n");
        asm.append("    lea r11, [rel __jvs_handler_labels]\n");
        asm.append("    lea rcx, [rel ").append(instruction.destination().value()).append("]\n");
        asm.append("    cmp qword [r11 + r10], rcx\n    jne ").append(done).append('\n');
        asm.append("    mov qword [rel __jvs_handler_depth], rax\n");
        asm.append(done).append(":\n");
    }

    private void emitExceptionGetMessage(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null || instruction.left() == null
                || instruction.right() == null
                || instruction.right().kind() != Operand.Kind.CONSTANT
                || !(instruction.right().value() instanceof Integer fieldOffset)) {
            throw new InvalidIRException("EXCEPTION_GET_MESSAGE exige receptor e offset.");
        }
        String fallback = "__exception_message_fallback_" + nextFreeLabel++;
        String done = "__exception_message_done_" + nextFreeLabel++;
        int accessorId = nextFreeLabel++;
        loadI64(asm, instruction.left(), "rdi");
        asm.append("    test rdi, rdi\n    jz __runtime_null_pointer\n");
        for (RuntimeExceptionSpec spec : runtimeExceptionSpecs()) {
            asm.append("    lea r10, [rel ").append(spec.objectLabel()).append("]\n");
            asm.append("    cmp rdi, r10\n    je ").append(spec.objectLabel())
                    .append("_message_").append(accessorId).append('\n');
        }
        if (fieldOffset >= 8) {
            asm.append("    push rdi\n    call __heap_validate\n    pop rdi\n");
            asm.append("    mov rax, qword [rdi + ").append(fieldOffset).append("]\n");
            asm.append("    jmp ").append(done).append('\n');
        } else {
            asm.append("    jmp ").append(fallback).append('\n');
        }
        for (RuntimeExceptionSpec spec : runtimeExceptionSpecs()) {
            asm.append(spec.objectLabel()).append("_message_").append(accessorId).append(":\n");
            asm.append("    mov rax, qword [rdi + 16]\n    jmp ").append(done).append('\n');
        }
        asm.append(fallback).append(":\n");
        asm.append("    lea rax, [rel ").append(stringLabels.get("")).append("]\n");
        asm.append(done).append(":\n");
        storeI64(asm, instruction.destination(), "rax");
    }

    private void emitExceptionGetType(StringBuilder asm, IRInstruction instruction) {
        if (instruction.destination() == null || instruction.left() == null || sourceProgram == null) {
            throw new InvalidIRException("EXCEPTION_GET_TYPE exige receptor e programa fonte.");
        }
        String done = "__exception_type_done_" + nextFreeLabel++;
        int accessorId = nextFreeLabel++;
        loadI64(asm, instruction.left(), "rdi");
        asm.append("    test rdi, rdi\n    jz __runtime_null_pointer\n");
        for (RuntimeExceptionSpec spec : runtimeExceptionSpecs()) {
            asm.append("    lea r10, [rel ").append(spec.objectLabel()).append("]\n");
            asm.append("    cmp rdi, r10\n    je ").append(spec.objectLabel())
                    .append("_type_").append(accessorId).append('\n');
        }
        asm.append("    push rdi\n    call __heap_validate\n    pop rdi\n");
        asm.append("    mov rax, qword [rdi]\n");
        for (int index = 0; index < sourceProgram.classes().size(); index++) {
            ClassRecord classNode = sourceProgram.classes().get(index);
            asm.append("    lea r10, [rel __vtable_").append(classNode.name()).append("]\n");
            asm.append("    cmp rax, r10\n    je __exception_type_source_")
                    .append(accessorId).append('_').append(index).append('\n');
        }
        asm.append("    lea rax, [rel ").append(stringLabels.get("Throwable")).append("]\n");
        asm.append("    jmp ").append(done).append('\n');
        for (RuntimeExceptionSpec spec : runtimeExceptionSpecs()) {
            asm.append(spec.objectLabel()).append("_type_").append(accessorId).append(":\n");
            asm.append("    mov rax, qword [rdi + 8]\n    jmp ").append(done).append('\n');
        }
        for (int index = 0; index < sourceProgram.classes().size(); index++) {
            asm.append("__exception_type_source_").append(accessorId).append('_').append(index).append(":\n");
            asm.append("    lea rax, [rel ").append(sourceClassTypeLabel(index)).append("]\n");
            asm.append("    jmp ").append(done).append('\n');
        }
        asm.append(done).append(":\n");
        storeI64(asm, instruction.destination(), "rax");
    }

    private String findThrowableClassName() {
        if (sourceProgram == null) return null;
        for (ClassRecord classRecord : sourceProgram.classes()) {
            String name = classRecord.name();
            if (name.equals("Throwable") || name.endsWith(".Throwable")) return name;
        }
        return null;
    }

    private boolean isClassSubtype(ClassRecord candidate, String targetName) {
        String current = candidate.name();
        while (current != null) {
            if (current.equals(targetName)) return true;
            if (BuiltinExceptionTypes.isSubtype(current, targetName)) return true;
            ClassRecord node = sourceClassesByName.get(current);
            current = node == null ? null : node.superClass();
        }
        return false;
    }

    private void storeCallResult(StringBuilder asm, Operand destination) {
        if (destination == null) {
            return;
        }
        if (destination.type() == Operand.Type.F64) {
            storeF64(asm, destination, "xmm0");
        } else if (destination.type() == Operand.Type.I64 || destination.type() == Operand.Type.REF) {
            storeI64(asm, destination, "rax");
        } else {
            storeI32(asm, destination, "eax");
        }
    }

    private void loadI32(
            StringBuilder asm,
            Operand source,
            String register) {
        if (source.kind() == Operand.Kind.CONSTANT) {
            String immediate;

            if (source.value() instanceof Boolean boolValue) {
                immediate = boolValue ? "1" : "0";
            } else {
                immediate = source.value().toString();
            }

            asm.append("    mov ")
                    .append(register)
                    .append(", ")
                    .append(immediate)
                    .append("\n");
        } else {
            asm.append("    mov ")
                    .append(register)
                    .append(", dword ")
                    .append(address(source))
                    .append("\n");
        }
    }

    private void emitAssignment(StringBuilder asm, Operand destination, Operand source) {
        if (destination.type() == Operand.Type.I32 || destination.type() == Operand.Type.BOOL) {
            loadI32(asm, source, "eax");
            storeI32(asm, destination, "eax");
        } else if (destination.type() == Operand.Type.F64) {
            loadF64(asm, source, "xmm0");
            storeF64(asm, destination, "xmm0");
        } else if (destination.type() == Operand.Type.I64 || destination.type() == Operand.Type.REF) {
            loadI64(asm, source, "rax");
            storeI64(asm, destination, "rax");
        } else {
            throw new UnsupportedBackendFeatureException(
                    "Atribuição não suportada para o tipo " + destination.type() + ".");
        }
    }

    private void loadI64(StringBuilder asm, Operand source, String register) {
        if (source.kind() == Operand.Kind.CONSTANT) {
            Object value = source.value();
            if (value instanceof String text) {
                asm.append("    lea ").append(register).append(", [rel ")
                        .append(stringLabels.get(text)).append("]\n");
                return;
            }
            String immediate;
            if (value == null)
                immediate = "0";
            else if (value instanceof Boolean booleanValue)
                immediate = booleanValue ? "1" : "0";
            else
                immediate = value.toString();
            asm.append("    mov ").append(register).append(", ").append(immediate).append("\n");
            return;
        }
        if (source.type() == Operand.Type.I32 || source.type() == Operand.Type.BOOL) {
            if (register32(register) == null) {
                throw new InvalidIRException("Registrador de 64 bits inválido: " + register);
            }
            asm.append("    movsxd ").append(register).append(", dword ")
                    .append(address(source)).append("\n");
            return;
        }
        asm.append("    mov ").append(register).append(", qword ")
                .append(address(source)).append("\n");
    }

    private void loadF64(StringBuilder asm, Operand source, String register) {
        if (source.kind() == Operand.Kind.CONSTANT) {
            if (!(source.value() instanceof Double value)) {
                throw new InvalidIRException("Constante F64 inválida: " + source.value());
            }
            String bits = Long.toUnsignedString(Double.doubleToRawLongBits(value), 16);
            asm.append("    mov rax, 0x").append(bits).append("\n");
            asm.append("    movq ").append(register).append(", rax\n");
            return;
        }
        asm.append("    movsd ").append(register).append(", qword ")
                .append(address(source)).append("\n");
    }

    private void storeF64(StringBuilder asm, Operand destination, String register) {
        asm.append("    movsd qword ").append(address(destination))
                .append(", ").append(register).append("\n");
    }

    private String register32(String register64) {
        return switch (register64) {
            case "rax" -> "eax";
            case "rdi" -> "edi";
            case "rsi" -> "esi";
            case "rdx" -> "edx";
            case "rcx" -> "ecx";
            case "r8" -> "r8d";
            case "r9" -> "r9d";
            default -> null;
        };
    }

    private void storeI64(StringBuilder asm, Operand destination, String register) {
        asm.append("    mov qword ").append(address(destination))
                .append(", ").append(register).append("\n");
    }

    private void storeI32(
            StringBuilder asm,
            Operand destination,
            String register) {
        asm.append("    mov dword ")
                .append(address(destination))
                .append(", ")
                .append(register)
                .append("\n");
    }
}

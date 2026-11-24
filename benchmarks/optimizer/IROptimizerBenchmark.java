package benchmark;

import compiler.backend.ir.IRInstruction;
import compiler.backend.ir.IROpCode;
import compiler.backend.ir.IRProgram;
import compiler.backend.ir.Operand;
import compiler.backend.optimization.IROptimizer;

import java.lang.reflect.Method;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runs the production optimize() method directly on deterministic synthetic IR. */
public final class IROptimizerBenchmark {
    private static final int WARMUPS = 5;
    private static final int REPEATS = 9;

    private IROptimizerBenchmark() {}

    public static void main(String[] args) throws Exception {
        int[] sizes = Arrays.stream(args).mapToInt(Integer::parseInt).toArray();
        if (sizes.length == 0) sizes = new int[]{100, 250};
        System.out.println("java_version,os,os_version,arch,jvm_args,scenario,size,blocks,instructions_before,instructions_after,warmups,repeats,median_ns,mad_ns,min_ns,max_ns,cfg_builds,optimizer_rounds,constant_iterations,dead_store_iterations,heap_used_bytes,deterministic_ir");
        for (String scenario : List.of("cfg", "temporaries", "copies", "copy_chain", "constants", "loops", "arithmetic", "dead_stores", "calls")) {
            for (int size : sizes) measure(scenario, size);
        }
    }

    private static void measure(String scenario, int size) throws Exception {
        IRProgram input = generate(scenario, size);
        IROptimizer optimizer = new IROptimizer();
        IRProgram expected = optimizer.optimize(input);
        for (int i = 0; i < WARMUPS; i++) optimizer.optimize(input);

        long[] samples = new long[REPEATS];
        IRProgram output = expected;
        for (int i = 0; i < REPEATS; i++) {
            long start = System.nanoTime();
            output = optimizer.optimize(input);
            samples[i] = System.nanoTime() - start;
        }
        boolean deterministic = expected.instructions().equals(output.instructions());
        int blocks = countBlocks(optimizer, input);
        long median = median(samples);
        long[] deviations = new long[samples.length];
        for (int i = 0; i < samples.length; i++) deviations[i] = Math.abs(samples[i] - median);
        long mad = median(deviations);
        long heapUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        String jvmArgs = String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments());
        System.out.printf("%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,not_instrumented,not_instrumented,not_instrumented,not_instrumented,%d,%s%n",
                csv(System.getProperty("java.version")), csv(System.getProperty("os.name")),
                csv(System.getProperty("os.version")), csv(System.getProperty("os.arch")), csv(jvmArgs), scenario,
                size, blocks, input.instructions().size(), output.instructions().size(), WARMUPS,
                REPEATS, median, mad, Arrays.stream(samples).min().orElse(0),
                Arrays.stream(samples).max().orElse(0), heapUsed, deterministic);
        if (!deterministic) throw new AssertionError("Non-deterministic optimized IR for " + scenario + "/" + size);
    }

    private static int countBlocks(IROptimizer optimizer, IRProgram input) throws Exception {
        Method method = IROptimizer.class.getDeclaredMethod("buildControlFlowGraph", IRProgram.class);
        method.setAccessible(true);
        return ((List<?>) method.invoke(optimizer, input)).size();
    }

    private static IRProgram generate(String scenario, int size) {
        IRProgram p = new IRProgram();
        Operand one = Operand.constant(1, Operand.Type.I32);
        Operand zero = Operand.constant(0, Operand.Type.I32);
        Operand condition = Operand.variable("condition", Operand.Type.BOOL);
        for (int i = 0; i < size; i++) {
            if (!scenario.equals("copy_chain") || i == 0) {
                p.add(new IRInstruction(IROpCode.LABEL,
                        Operand.label(i == 0 ? "Bench.main__void__ret_void" : "block_" + i), null, null));
            }
            Operand v = Operand.variable("v" + i, Operand.Type.I32);
            Operand t = Operand.temporary("t" + i, Operand.Type.I32);
            switch (scenario) {
                case "temporaries" -> {
                    p.add(new IRInstruction(IROpCode.ADD, t, v, one));
                    p.add(new IRInstruction(IROpCode.PRINT, null, null, null, List.of(t)));
                }
                case "copies" -> {
                    p.add(new IRInstruction(IROpCode.ASSIGN, v,
                            i == 0 ? one : Operand.variable("v" + (i - 1), Operand.Type.I32), null));
                    p.add(new IRInstruction(IROpCode.ASSIGN, t, v, null));
                    p.add(new IRInstruction(IROpCode.PRINT, null, null, null, List.of(t)));
                }
                case "copy_chain" -> p.add(new IRInstruction(IROpCode.ASSIGN, v,
                        i == 0 ? Operand.variable("copy_source", Operand.Type.I32)
                                : Operand.variable("v" + (i - 1), Operand.Type.I32), null));
                case "constants" -> {
                    p.add(new IRInstruction(IROpCode.ASSIGN, v, one, null));
                    p.add(new IRInstruction(IROpCode.JUMP,
                            Operand.label(i + 1 < size ? "block_" + (i + 1) : "exit"), null, null));
                }
                case "loops" -> {
                    p.add(new IRInstruction(IROpCode.ADD, t, v, one));
                    p.add(new IRInstruction(IROpCode.JUMP_IF_FALSE,
                            Operand.label("block_" + ((i + 1) % size)), condition, null));
                }
                case "arithmetic" -> {
                    p.add(new IRInstruction(IROpCode.ADD, t, v, one));
                    p.add(new IRInstruction(IROpCode.MUL, v, t, Operand.constant(i + 2, Operand.Type.I32)));
                    p.add(new IRInstruction(IROpCode.MOD, t, v, Operand.constant(7, Operand.Type.I32)));
                    p.add(new IRInstruction(IROpCode.PRINT, null, null, null, List.of(t)));
                }
                case "dead_stores" -> {
                    p.add(new IRInstruction(IROpCode.ASSIGN, v, zero, null));
                    p.add(new IRInstruction(IROpCode.ASSIGN, v, one, null));
                    p.add(new IRInstruction(IROpCode.PRINT, null, null, null, List.of(v)));
                }
                case "calls" -> {
                    p.add(new IRInstruction(IROpCode.CALL, t, Operand.label("callee"), null, List.of(v)));
                    p.add(new IRInstruction(IROpCode.PRINT, null, null, null, List.of(t)));
                }
                default -> p.add(new IRInstruction(IROpCode.JUMP_IF_FALSE,
                        Operand.label(i + 1 < size ? "block_" + (i + 1) : "exit"), condition, null));
            }
        }
        if (scenario.equals("copy_chain") && size > 0) {
            p.add(new IRInstruction(IROpCode.PRINT, null, null, null,
                    List.of(Operand.variable("v" + (size - 1), Operand.Type.I32))));
        }
        p.add(new IRInstruction(IROpCode.LABEL, Operand.label("exit"), null, null));
        p.add(new IRInstruction(IROpCode.RETURN, null, null, null));
        return p;
    }

    private static long median(long[] values) {
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
}

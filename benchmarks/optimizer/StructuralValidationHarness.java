package benchmark;

import compiler.backend.ir.IRInstruction;
import compiler.backend.ir.IROpCode;
import compiler.backend.ir.IRProgram;
import compiler.backend.ir.Operand;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compare complete structural-key validation with explicit topology generations. */
public final class StructuralValidationHarness {
    private static final int WARMUPS = 5;
    private static final int SAMPLES = Integer.parseInt(System.getenv().getOrDefault("BENCH_SAMPLES", "9"));
    private static final List<String> BASE_SCENARIOS = List.of(
            "cfg", "temporaries", "copies", "copy_chain", "constants", "loops",
            "arithmetic", "dead_stores", "calls");
    private static final List<String> STRUCTURAL_SCENARIOS = List.of(
            "branch_taken", "branch_fallthrough", "unreachable_blocks",
            "redundant_jumps", "handler_edges");

    private StructuralValidationHarness() {}

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int[] sizes = Arrays.stream(args).skip(1).mapToInt(Integer::parseInt).toArray();
        if (sizes.length == 0) sizes = new int[]{250, 500};
        Class<?> fullClass = Class.forName(System.getProperty("benchmark.fullClass",
                "compiler.backend.optimization.FullKeyIROptimizer"));
        Class<?> generationClass = Class.forName(System.getProperty("benchmark.generationClass",
                "compiler.backend.optimization.GenerationIROptimizer"));
        Method fullOptimize = fullClass.getMethod("optimize", IRProgram.class);
        Method generationOptimize = generationClass.getMethod("optimize", IRProgram.class);
        Method generate = IROptimizerBenchmark.class.getDeclaredMethod("generate", String.class, int.class);
        generate.setAccessible(true);
        List<InputCase> cases = new ArrayList<>();
        for (String scenario : BASE_SCENARIOS) for (int size : sizes)
            if (selected(scenario)) cases.add(new InputCase(scenario, size, (IRProgram) generate.invoke(null, scenario, size)));
        for (String scenario : STRUCTURAL_SCENARIOS) for (int size : sizes)
            if (selected(scenario)) cases.add(new InputCase(scenario, size, structuralInput(scenario, size)));
        if (cases.isEmpty()) throw new IllegalArgumentException("No scenarios selected");

        Object sequenceFull = newOptimizer(fullClass), sequenceGeneration = newOptimizer(generationClass);
        for (InputCase input : cases) compare(input, sequenceFull, fullOptimize,
                sequenceGeneration, generationOptimize);
        IRProgram appendedInput = structuralInput("branch_taken", 2);
        compare(new InputCase("mutable_root_before_append", 2, appendedInput), sequenceFull, fullOptimize,
                sequenceGeneration, generationOptimize);
        appendedInput.add(label("appended_block"));
        appendedInput.add(new IRInstruction(IROpCode.RETURN, null, null, null));
        compare(new InputCase("mutable_root_after_append", 2, appendedInput), sequenceFull, fullOptimize,
                sequenceGeneration, generationOptimize);

        if (mode.equals("time")) runTime(cases, fullClass, fullOptimize, generationClass, generationOptimize);
        else if (mode.equals("profile")) runProfile(cases, fullClass, fullOptimize, generationClass, generationOptimize);
        else throw new IllegalArgumentException("mode must be time or profile");
    }

    private static void runTime(List<InputCase> cases, Class<?> fullClass, Method fullOptimize,
                                Class<?> generationClass, Method generationOptimize) throws Exception {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Thread allocation metrics unsupported");
        bean.setThreadAllocatedMemoryEnabled(true);
        System.out.println("mode,java,os,os_version,arch,jvm_args,scenario,size,warmups,samples,full_key_median_ns,full_key_mad_ns,generation_median_ns,generation_mad_ns,full_key_alloc_median_bytes,full_key_alloc_mad_bytes,generation_alloc_median_bytes,generation_alloc_mad_bytes,final_ir_equal");
        for (InputCase input : cases) {
            for (int warmup = 0; warmup < WARMUPS; warmup++)
                compare(input, newOptimizer(fullClass), fullOptimize,
                        newOptimizer(generationClass), generationOptimize);
            long[] fullTimes = new long[SAMPLES], generationTimes = new long[SAMPLES];
            long[] fullAlloc = new long[SAMPLES], generationAlloc = new long[SAMPLES];
            long thread = Thread.currentThread().getId();
            for (int sample = 0; sample < SAMPLES; sample++) {
                Object full = newOptimizer(fullClass), generation = newOptimizer(generationClass);
                IRProgram fullOutput, generationOutput;
                if ((sample & 1) == 0) {
                    Measurement m = measure(full, fullOptimize, input.program(), bean, thread);
                    fullOutput = m.output(); fullTimes[sample] = m.nanos(); fullAlloc[sample] = m.allocated();
                    m = measure(generation, generationOptimize, input.program(), bean, thread);
                    generationOutput = m.output(); generationTimes[sample] = m.nanos(); generationAlloc[sample] = m.allocated();
                } else {
                    Measurement m = measure(generation, generationOptimize, input.program(), bean, thread);
                    generationOutput = m.output(); generationTimes[sample] = m.nanos(); generationAlloc[sample] = m.allocated();
                    m = measure(full, fullOptimize, input.program(), bean, thread);
                    fullOutput = m.output(); fullTimes[sample] = m.nanos(); fullAlloc[sample] = m.allocated();
                }
                equal(fullOutput, generationOutput, input);
            }
            System.out.printf("time,%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,true%n",
                    csv(System.getProperty("java.version")), csv(System.getProperty("os.name")),
                    csv(System.getProperty("os.version")), csv(System.getProperty("os.arch")),
                    csv(String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments())),
                    input.scenario(), input.size(), WARMUPS, SAMPLES,
                    median(fullTimes), mad(fullTimes), median(generationTimes), mad(generationTimes),
                    median(fullAlloc), mad(fullAlloc), median(generationAlloc), mad(generationAlloc));
        }
    }

    private static void runProfile(List<InputCase> cases, Class<?> fullClass, Method fullOptimize,
                                   Class<?> generationClass, Method generationOptimize) throws Exception {
        System.out.println("mode,java,os,os_version,arch,jvm_args,scenario,size,warmups,samples,final_ir_equal,full_key_calculations_per_run,full_key_ns_median,full_compare_checks_per_run,full_compare_ns_median,full_refresh_ns_median,full_refresh_blocks_per_run,full_refresh_instructions_per_run,full_topology_builds_per_run,full_build_ns_median,generation_checks_per_run,generation_check_ns_median,explicit_invalidations_per_run,invalidation_ns_median,generation_refresh_ns_median,generation_refresh_blocks_per_run,generation_refresh_instructions_per_run,generation_topology_builds_per_run,generation_build_ns_median,full_pass_inclusive_ns_median,generation_pass_inclusive_ns_median,full_pass_excluding_validation_ns_median,generation_pass_excluding_validation_ns_median");
        for (InputCase input : cases) {
            for (int warmup = 0; warmup < WARMUPS; warmup++)
                compare(input, newOptimizer(fullClass), fullOptimize,
                        newOptimizer(generationClass), generationOptimize);
            long[] fullKey = new long[SAMPLES], fullCompare = new long[SAMPLES];
            long[] fullRefresh = new long[SAMPLES], fullBuild = new long[SAMPLES], fullPass = new long[SAMPLES];
            long[] genCheck = new long[SAMPLES], invalidation = new long[SAMPLES];
            long[] genRefresh = new long[SAMPLES], genBuild = new long[SAMPLES], genPass = new long[SAMPLES];
            long[] fullPassNoValidation = new long[SAMPLES], genPassNoValidation = new long[SAMPLES];
            long fullKeyChecks = 0, fullCompareChecks = 0, fullBuilds = 0;
            long fullRefreshBlocks = 0, fullRefreshInstructions = 0;
            long genChecks = 0, invalidations = 0, genBuilds = 0;
            long genRefreshBlocks = 0, genRefreshInstructions = 0;
            for (int sample = 0; sample < SAMPLES; sample++) {
                Object full = newOptimizer(fullClass), generation = newOptimizer(generationClass);
                IRProgram fullOutput, generationOutput;
                if ((sample & 1) == 0) {
                    fullOutput = (IRProgram) fullOptimize.invoke(full, input.program());
                    generationOutput = (IRProgram) generationOptimize.invoke(generation, input.program());
                } else {
                    generationOutput = (IRProgram) generationOptimize.invoke(generation, input.program());
                    fullOutput = (IRProgram) fullOptimize.invoke(full, input.program());
                }
                equal(fullOutput, generationOutput, input);
                fullKey[sample] = number(full, "profileKeyNanos");
                fullCompare[sample] = number(full, "profileCompareNanos");
                fullRefresh[sample] = number(full, "profileRefreshNanos");
                fullBuild[sample] = number(full, "profileBuildNanos");
                fullPass[sample] = sum(map(full, "profilePassNanos"));
                fullKeyChecks += number(full, "profileKeyChecks");
                fullCompareChecks += number(full, "profileCompareChecks");
                fullBuilds += number(full, "profileCfgCalls");
                fullRefreshBlocks += number(full, "profileRefreshBlocks");
                fullRefreshInstructions += number(full, "profileRefreshInstructions");
                long fullMaintenance = fullKey[sample] + fullCompare[sample] + fullRefresh[sample] + fullBuild[sample];
                fullPassNoValidation[sample] = Math.max(0, fullPass[sample] - fullMaintenance);

                genCheck[sample] = number(generation, "profileGenerationCheckNanos");
                invalidation[sample] = number(generation, "profileInvalidationNanos");
                genRefresh[sample] = number(generation, "profileRefreshNanos");
                genBuild[sample] = number(generation, "profileBuildNanos");
                genPass[sample] = sum(map(generation, "profilePassNanos"));
                genChecks += number(generation, "profileGenerationChecks");
                invalidations += number(generation, "experimentInvalidations");
                genBuilds += number(generation, "profileCfgCalls");
                genRefreshBlocks += number(generation, "profileRefreshBlocks");
                genRefreshInstructions += number(generation, "profileRefreshInstructions");
                long genMaintenance = genCheck[sample] + invalidation[sample]
                        + genRefresh[sample] + genBuild[sample];
                genPassNoValidation[sample] = Math.max(0, genPass[sample] - genMaintenance);
            }
            long expectedInvalidations = expectedInvalidations(input.scenario());
            if (expectedInvalidations > 0 && invalidations == 0)
                throw new AssertionError("No structural invalidation was observed for " + input.scenario());
            System.out.printf("profile,%s,%s,%s,%s,%s,%s,%d,%d,%d,true,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    csv(System.getProperty("java.version")), csv(System.getProperty("os.name")),
                    csv(System.getProperty("os.version")), csv(System.getProperty("os.arch")),
                    csv(String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments())),
                    input.scenario(), input.size(), WARMUPS, SAMPLES,
                    fullKeyChecks / SAMPLES, median(fullKey), fullCompareChecks / SAMPLES,
                    median(fullCompare), median(fullRefresh), fullRefreshBlocks / SAMPLES,
                    fullRefreshInstructions / SAMPLES, fullBuilds / SAMPLES, median(fullBuild),
                    genChecks / SAMPLES, median(genCheck), invalidations / SAMPLES, median(invalidation),
                    median(genRefresh), genRefreshBlocks / SAMPLES, genRefreshInstructions / SAMPLES,
                    genBuilds / SAMPLES, median(genBuild), median(fullPass),
                    median(genPass), median(fullPassNoValidation), median(genPassNoValidation));
        }
    }

    private static long expectedInvalidations(String scenario) {
        return switch (scenario) {
            case "branch_taken", "branch_fallthrough", "unreachable_blocks", "redundant_jumps" -> 1;
            default -> 0;
        };
    }

    private static boolean selected(String scenario) {
        String requested = System.getenv("BENCH_SCENARIOS");
        if (requested == null || requested.isBlank()) return true;
        for (String name : requested.trim().split("[,\\s]+"))
            if (name.equals(scenario)) return true;
        return false;
    }

    private static Measurement measure(Object optimizer, Method optimize, IRProgram input,
                                       ThreadMXBean bean, long thread) throws Exception {
        long allocatedBefore = bean.getThreadAllocatedBytes(thread);
        long start = System.nanoTime();
        IRProgram output = (IRProgram) optimize.invoke(optimizer, input);
        long elapsed = System.nanoTime() - start;
        long allocated = bean.getThreadAllocatedBytes(thread) - allocatedBefore;
        return new Measurement(output, elapsed, allocated);
    }

    private static Object newOptimizer(Class<?> type) throws Exception { return type.getConstructor().newInstance(); }
    private static void compare(InputCase input, Object full, Method fullOptimize,
                                Object generation, Method generationOptimize) throws Exception {
        equal((IRProgram) fullOptimize.invoke(full, input.program()),
                (IRProgram) generationOptimize.invoke(generation, input.program()), input);
    }
    private static void equal(IRProgram left, IRProgram right, InputCase input) {
        if (!left.instructions().equals(right.instructions()))
            throw new AssertionError("IR mismatch for " + input.scenario() + "/" + input.size());
    }
    private static long number(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true);
        return ((Number) field.get(object)).longValue();
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Long> map(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true);
        return new LinkedHashMap<>((Map<String, Long>) field.get(object));
    }
    private static long sum(Map<String, Long> values) { return values.values().stream().mapToLong(Long::longValue).sum(); }
    private static IRProgram structuralInput(String scenario, int size) {
        IRProgram p = new IRProgram();
        p.add(label("Bench.main__void__ret_void"));
        switch (scenario) {
            case "branch_taken" -> {
                for (int i = 0; i < size; i++) {
                    p.add(new IRInstruction(IROpCode.JUMP_IF_TRUE, Operand.label("taken_" + i),
                            Operand.constant(true, Operand.Type.BOOL), null));
                    p.add(label("fall_" + i)); p.add(print(i));
                    p.add(label("taken_" + i)); p.add(print(i + 1));
                }
            }
            case "branch_fallthrough" -> {
                for (int i = 0; i < size; i++) {
                    p.add(new IRInstruction(IROpCode.JUMP_IF_FALSE, Operand.label("target_" + i),
                            Operand.constant(true, Operand.Type.BOOL), null));
                    p.add(label("fall_" + i)); p.add(print(i));
                    p.add(label("target_" + i)); p.add(print(i + 1));
                }
            }
            case "unreachable_blocks" -> {
                for (int i = 0; i < size; i++) {
                    p.add(new IRInstruction(IROpCode.JUMP, Operand.label("live_" + i), null, null));
                    p.add(label("dead_" + i)); p.add(print(i));
                    p.add(label("live_" + i)); p.add(print(i + 1));
                }
            }
            case "redundant_jumps" -> {
                for (int i = 0; i < size; i++) {
                    p.add(new IRInstruction(IROpCode.JUMP, Operand.label("next_" + i), null, null));
                    p.add(label("next_" + i)); p.add(print(i));
                }
            }
            case "handler_edges" -> {
                for (int i = 0; i < size; i++)
                    p.add(new IRInstruction(IROpCode.PUSH_EXCEPTION_HANDLER,
                            Operand.label("handler_" + i), null, null));
                p.add(new IRInstruction(IROpCode.RETURN, null, null, null));
                for (int i = 0; i < size; i++) {
                    p.add(label("handler_" + i));
                    p.add(new IRInstruction(IROpCode.POP_EXCEPTION_HANDLER_IF,
                            Operand.label("handler_" + i), null, null));
                    p.add(new IRInstruction(IROpCode.RETURN, null, null, null));
                }
            }
            default -> throw new IllegalArgumentException(scenario);
        }
        if (!scenario.equals("handler_edges"))
            p.add(new IRInstruction(IROpCode.RETURN, null, null, null));
        return p;
    }
    private static IRInstruction label(String name) { return new IRInstruction(IROpCode.LABEL, Operand.label(name), null, null); }
    private static IRInstruction print(int value) {
        return new IRInstruction(IROpCode.PRINT, null, null, null,
                List.of(Operand.constant(value, Operand.Type.I32)));
    }
    private record InputCase(String scenario, int size, IRProgram program) {}
    private record Measurement(IRProgram output, long nanos, long allocated) {}
    private static long median(long[] values) { long[] copy = values.clone(); Arrays.sort(copy); return copy[copy.length / 2]; }
    private static long mad(long[] values) { long m = median(values); long[] d = new long[values.length]; for(int i=0;i<d.length;i++) d[i]=Math.abs(values[i]-m); return median(d); }
    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
}

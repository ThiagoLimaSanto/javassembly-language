package benchmark;

import compiler.backend.ir.IRProgram;
import compiler.backend.optimization.IROptimizer;
import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Benchmark runner for isolated reference/shared CFG topology clones. */
public final class TopologyHarness {
    private static final int WARMUPS = 5;
    private static final int SAMPLES = 9;
    private static final List<String> SCENARIOS = List.of(
            "cfg", "temporaries", "copies", "copy_chain", "constants", "loops",
            "arithmetic", "dead_stores", "calls");

    private TopologyHarness() {}

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        int[] sizes = Arrays.stream(args).skip(1).mapToInt(Integer::parseInt).toArray();
        if (sizes.length == 0) sizes = new int[]{250, 500};
        Method generate = IROptimizerBenchmark.class.getDeclaredMethod("generate", String.class, int.class);
        generate.setAccessible(true);
        Class<?> sharedClass = Class.forName("compiler.backend.optimization.ExperimentIROptimizer");
        Method sharedOptimize = sharedClass.getMethod("optimize", IRProgram.class);
        Class<?> referenceClass = mode.equals("profile")
                ? Class.forName("compiler.backend.optimization.ReferenceProfileOptimizer")
                : IROptimizer.class;
        Method referenceOptimize = referenceClass.getMethod("optimize", IRProgram.class);

        if (mode.equals("time")) {
            ThreadMXBean allocationBean = allocationBean();
            System.out.println("mode,java,os,os_version,arch,jvm_args,scenario,size,warmups,samples,reference_median_ns,reference_mad_ns,shared_median_ns,shared_mad_ns,reference_alloc_median_bytes,reference_alloc_mad_bytes,shared_alloc_median_bytes,shared_alloc_mad_bytes,final_ir_equal,cfg_profile");
            for (String scenario : SCENARIOS) for (int size : sizes) {
                IRProgram input = (IRProgram) generate.invoke(null, scenario, size);
                Object reference = referenceClass.getConstructor().newInstance();
                Object shared = sharedClass.getConstructor().newInstance();
                IRProgram referenceExpected = (IRProgram) referenceOptimize.invoke(reference, input);
                IRProgram sharedExpected = (IRProgram) sharedOptimize.invoke(shared, input);
                equal(referenceExpected, sharedExpected, scenario, size);
                for (int i = 0; i < WARMUPS; i++) {
                    equal((IRProgram) referenceOptimize.invoke(reference, input),
                            (IRProgram) sharedOptimize.invoke(shared, input), scenario, size);
                }
                long[] referenceNs = new long[SAMPLES], sharedNs = new long[SAMPLES];
                long[] referenceBytes = new long[SAMPLES], sharedBytes = new long[SAMPLES];
                long threadId = Thread.currentThread().getId();
                for (int i = 0; i < SAMPLES; i++) {
                    long allocated = allocationBean.getThreadAllocatedBytes(threadId);
                    long start = System.nanoTime();
                    IRProgram ref = (IRProgram) referenceOptimize.invoke(reference, input);
                    referenceNs[i] = System.nanoTime() - start;
                    referenceBytes[i] = allocationBean.getThreadAllocatedBytes(threadId) - allocated;
                    allocated = allocationBean.getThreadAllocatedBytes(threadId);
                    start = System.nanoTime();
                    IRProgram cached = (IRProgram) sharedOptimize.invoke(shared, input);
                    sharedNs[i] = System.nanoTime() - start;
                    sharedBytes[i] = allocationBean.getThreadAllocatedBytes(threadId) - allocated;
                    equal(ref, cached, scenario, size);
                }
                System.out.printf("time,%s,%s,%s,%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,not_instrumented%n",
                        csv(System.getProperty("java.version")), csv(System.getProperty("os.name")),
                        csv(System.getProperty("os.version")), csv(System.getProperty("os.arch")),
                        csv(String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments())),
                        scenario, size, WARMUPS, SAMPLES, median(referenceNs), mad(referenceNs),
                        median(sharedNs), mad(sharedNs), median(referenceBytes), mad(referenceBytes),
                        median(sharedBytes), mad(sharedBytes), true);
            }
            return;
        }
        if (!mode.equals("profile")) throw new IllegalArgumentException("mode must be time or profile");

        System.out.println("mode,java,os,os_version,arch,jvm_args,scenario,size,warmups,samples,final_ir_equal,reference_cfg_calls_per_run,reference_cfg_ns_median,reference_pass_inclusive_ns_median,reference_pass_minus_cfg_ns_estimate,shared_build_calls_per_run,shared_initial_build_calls_per_run,shared_rebuild_calls_per_run,shared_key_ns_median,shared_compare_ns_median,shared_refresh_ns_median,shared_initial_build_ns_median,shared_rebuild_ns_median,shared_cfg_maintenance_ns_median,shared_pass_inclusive_ns_median,shared_pass_minus_cfg_ns_estimate,rounds_per_run");
        for (String scenario : SCENARIOS) for (int size : sizes) {
            IRProgram input = (IRProgram) generate.invoke(null, scenario, size);
            Object reference = referenceClass.getConstructor().newInstance();
            Object shared = sharedClass.getConstructor().newInstance();
            equal((IRProgram) referenceOptimize.invoke(reference, input),
                    (IRProgram) sharedOptimize.invoke(shared, input), scenario, size);
            for (int i = 0; i < WARMUPS; i++) equal(
                    (IRProgram) referenceOptimize.invoke(reference, input),
                    (IRProgram) sharedOptimize.invoke(shared, input), scenario, size);

            long[] refCfgTime = new long[SAMPLES], sharedKey = new long[SAMPLES];
            long[] sharedCompare = new long[SAMPLES], sharedRefresh = new long[SAMPLES];
            long[] sharedInitialBuild = new long[SAMPLES], sharedRebuild = new long[SAMPLES];
            long[] sharedMaintenance = new long[SAMPLES], refPass = new long[SAMPLES];
            long[] sharedPass = new long[SAMPLES], refPassExcludingCfg = new long[SAMPLES];
            long[] sharedPassExcludingCfg = new long[SAMPLES];
            long refCfgCalls = 0, sharedBuildCalls = 0, sharedInitialCalls = 0, sharedRebuildCalls = 0, rounds = 0;
            for (int i = 0; i < SAMPLES; i++) {
                resetProfile(reference); resetProfile(shared);
                IRProgram ref = (IRProgram) referenceOptimize.invoke(reference, input);
                IRProgram cached = (IRProgram) sharedOptimize.invoke(shared, input);
                equal(ref, cached, scenario, size);

                long cfgCount = number(reference, "profileCfgCalls");
                long cfgNanos = number(reference, "profileCfgNanos");
                Map<String, Long> refPassMap = map(reference, "profilePassNanos");
                long refPassNs = sum(refPassMap);
                refCfgCalls += cfgCount; rounds += number(reference, "profileRounds");
                refCfgTime[i] = cfgNanos; refPass[i] = refPassNs;
                refPassExcludingCfg[i] = Math.max(0, refPassNs - cfgNanos);

                Map<String, Long> sharedPassMap = map(shared, "profilePassNanos");
                long keyNs = number(shared, "profileKeyNanos");
                long compareNs = number(shared, "profileCompareNanos");
                long refreshNs = number(shared, "profileRefreshNanos");
                long initialBuildNs = number(shared, "profileInitialBuildNanos");
                long rebuildNs = number(shared, "profileRebuildNanos");
                sharedBuildCalls += number(shared, "profileCfgCalls");
                sharedInitialCalls += number(shared, "profileInitialBuildCalls");
                sharedRebuildCalls += number(shared, "profileRebuildCalls");
                sharedKey[i] = keyNs; sharedCompare[i] = compareNs;
                sharedRefresh[i] = refreshNs; sharedInitialBuild[i] = initialBuildNs; sharedRebuild[i] = rebuildNs;
                sharedPass[i] = sum(sharedPassMap);
                sharedMaintenance[i] = keyNs + compareNs + refreshNs + initialBuildNs + rebuildNs;
                sharedPassExcludingCfg[i] = Math.max(0, sharedPass[i] - sharedMaintenance[i]);
            }
            long refPassMedian = median(refPass), sharedPassMedian = median(sharedPass);
            System.out.printf("profile,%s,%s,%s,%s,%s,%s,%d,%d,%d,true,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    csv(System.getProperty("java.version")), csv(System.getProperty("os.name")),
                    csv(System.getProperty("os.version")), csv(System.getProperty("os.arch")),
                    csv(String.join(" ", ManagementFactory.getRuntimeMXBean().getInputArguments())),
                    scenario, size, WARMUPS, SAMPLES, refCfgCalls / SAMPLES,
                    median(refCfgTime), refPassMedian, median(refPassExcludingCfg),
                    sharedBuildCalls / SAMPLES, sharedInitialCalls / SAMPLES, sharedRebuildCalls / SAMPLES,
                    median(sharedKey), median(sharedCompare), median(sharedRefresh),
                    median(sharedInitialBuild), median(sharedRebuild), median(sharedMaintenance), sharedPassMedian,
                    median(sharedPassExcludingCfg), rounds / SAMPLES);
        }
    }

    private static ThreadMXBean allocationBean() {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Thread allocation metrics unsupported");
        bean.setThreadAllocatedMemoryEnabled(true);
        return bean;
    }
    private static void resetProfile(Object optimizer) throws Exception {
        for (String name : List.of("profileCfgCalls", "profileCfgNanos", "profileKeyNanos",
                "profileCompareNanos", "profileRefreshNanos", "profileBuildNanos",
                "profileInitialBuildNanos", "profileRebuildNanos", "profileInitialBuildCalls",
                "profileRebuildCalls", "profileRounds")) {
            try { Field field = optimizer.getClass().getDeclaredField(name); field.setAccessible(true); field.set(optimizer, 0); }
            catch (NoSuchFieldException ignored) { }
        }
        Field passField = optimizer.getClass().getDeclaredField("profilePassNanos");
        passField.setAccessible(true); ((Map<?, ?>) passField.get(optimizer)).clear();
        try {
            Field topology = optimizer.getClass().getDeclaredField("experimentTopology");
            topology.setAccessible(true); topology.set(optimizer, null);
            Field key = optimizer.getClass().getDeclaredField("experimentTopologyKey");
            key.setAccessible(true); key.set(optimizer, null);
        } catch (NoSuchFieldException ignored) { }
    }
    private static long number(Object optimizer, String name) throws Exception {
        Field field = optimizer.getClass().getDeclaredField(name); field.setAccessible(true);
        return ((Number) field.get(optimizer)).longValue();
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Long> map(Object optimizer, String name) throws Exception {
        Field field = optimizer.getClass().getDeclaredField(name); field.setAccessible(true);
        return new LinkedHashMap<>((Map<String, Long>) field.get(optimizer));
    }
    private static long sum(Map<String, Long> values) { return values.values().stream().mapToLong(Long::longValue).sum(); }
    private static void equal(IRProgram left, IRProgram right, String scenario, int size) {
        if (!left.instructions().equals(right.instructions())) throw new AssertionError("IR mismatch " + scenario + "/" + size);
    }
    private static long median(long[] values) { long[] copy = values.clone(); Arrays.sort(copy); return copy[copy.length / 2]; }
    private static long mad(long[] values) { long m = median(values); long[] d = new long[values.length]; for (int i=0;i<d.length;i++) d[i]=Math.abs(values[i]-m); return median(d); }
    private static String csv(String value) { return "\"" + value.replace("\"", "\"\"") + "\""; }
}

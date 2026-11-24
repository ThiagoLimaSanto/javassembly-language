package benchmark;

import compiler.backend.ir.IRProgram;
import compiler.backend.optimization.IROptimizer;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import com.sun.management.ThreadMXBean;

public final class TopologySharingExperiment {
    private static final int WARMUPS = 5;
    private static final int REPEATS =  nine();

    private TopologySharingExperiment() {}

    public static void main(String[] args) throws Exception {
        Method generate = IROptimizerBenchmark.class.getDeclaredMethod("generate", String.class, int.class);
        generate.setAccessible(true);
        Class<?> sharedClass = Class.forName("compiler.backend.optimization.ExperimentIROptimizer");
        Method sharedOptimize = sharedClass.getMethod("optimize", IRProgram.class);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long threadId = Thread.currentThread().getId();
        System.out.println("java,os,scenario,size,repeats,baseline_median_ns,shared_median_ns,baseline_mad_ns,shared_mad_ns,baseline_alloc_median,shared_alloc_median,baseline_cfg_builds,shared_builds_total,shared_reuses_total,final_ir_equal");
        for (String scenario : List.of("cfg", "temporaries", "copies", "copy_chain", "constants", "loops", "arithmetic", "dead_stores", "calls")) {
            for (int size : new int[]{250, 500}) {
                IRProgram input = (IRProgram) generate.invoke(null, scenario, size);
                IROptimizer baseline = new IROptimizer();
                Object shared = sharedClass.getConstructor().newInstance();
                IRProgram expected = baseline.optimize(input);
                IRProgram actual = (IRProgram) sharedOptimize.invoke(shared, input);
                boolean equal = expected.instructions().equals(actual.instructions());
                if (!equal) throw new AssertionError("Different IR: " + scenario + "/" + size);
                for (int i=0;i<WARMUPS;i++) { baseline.optimize(input); sharedOptimize.invoke(shared,input); }
                long[] baseTimes = new long[REPEATS], shareTimes = new long[REPEATS];
                long[] baseAlloc = new long[REPEATS], shareAlloc = new long[REPEATS];
                for (int i=0;i<REPEATS;i++) {
                    long allocated=bean.getThreadAllocatedBytes(threadId), start=System.nanoTime();
                    IRProgram outBase=baseline.optimize(input);
                    baseTimes[i]=System.nanoTime()-start; baseAlloc[i]=bean.getThreadAllocatedBytes(threadId)-allocated;
                    allocated=bean.getThreadAllocatedBytes(threadId); start=System.nanoTime();
                    IRProgram outShared=(IRProgram)sharedOptimize.invoke(shared,input);
                    shareTimes[i]=System.nanoTime()-start; shareAlloc[i]=bean.getThreadAllocatedBytes(threadId)-allocated;
                    if (!outBase.instructions().equals(outShared.instructions())) throw new AssertionError("Different IR during repeat");
                }
                System.out.printf("%s,%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%s,%d,%d,%s%n",
                        System.getProperty("java.version"),System.getProperty("os.name"),scenario,size,REPEATS,
                        median(baseTimes),median(shareTimes),mad(baseTimes),mad(shareTimes),median(baseAlloc),median(shareAlloc),
                        "not_instrumented",readInt(shared,"experimentBuilds",0),
                        readInt(shared,"experimentReuses",0),equal);
            }
        }
    }

    private static int nine(){ return 9; }
    private static int readInt(Object object,String field,int fallback){
        try { Field f=object.getClass().getDeclaredField(field); f.setAccessible(true); return f.getInt(object); }
        catch (ReflectiveOperationException ignored) { return fallback; }
    }
    private static long median(long[] values){ long[] a=values.clone(); Arrays.sort(a); return a[a.length/2]; }
    private static long mad(long[] values){ long med=median(values); long[] d=new long[values.length]; for(int i=0;i<d.length;i++)d[i]=Math.abs(values[i]-med); return median(d); }
}

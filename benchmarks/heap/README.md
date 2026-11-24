# Heap runtime benchmarks

`HeapRuntimeBench.jvs` separates the heap workloads by mode. Each invocation
accepts the element count and one mode number, so each measurement runs only
one workload:

| Mode | Workload |
| --- | --- |
| 1 | Allocate many objects |
| 2 | Allocate objects, then repeatedly read fields |
| 3 | Allocate objects, then write fields |
| 4 | Write and read an integer array |
| 5 | Repeatedly allocate and free an object |
| 6 | Allocate, free, and reuse blocks |
| 7 | Insert, find, and remove entries in `HashMap` |

From the repository root, build the benchmark with:

```sh
jvsc -l benchmarks/heap/HeapRuntimeBench.jvs
```

The generated executable is `benchmarks/heap/out/HeapRuntimeBench`. For one
measurement, run:

```sh
/usr/bin/time -f '%e seconds, %M KiB max RSS' \
  benchmarks/heap/out/HeapRuntimeBench 10000 2
```

Run the requested element counts and all workloads with:

```sh
for n in 1000 5000 10000 20000 100000; do
  for mode in 1 2 3 4 5 6 7; do
    printf 'n=%s mode=%s: ' "$n" "$mode"
    /usr/bin/time -f '%e seconds, %M KiB max RSS' \
      benchmarks/heap/out/HeapRuntimeBench "$n" "$mode" >/dev/null
  done
done
```

Mode 2 and mode 3 include setup time for allocating and initializing their
object arrays. Mode 4 includes both array writes and reads. Mode 7 includes
the `HashMap` implementation and its wrapper objects, making it a combined
workload rather than a measurement of the allocator alone. These are timing
benchmarks, not correctness fixtures; backend correctness and invalid-free
checks remain in `test/fixtures/backend`.

## Isolated `HashMap` operation timings

`HashMapOperationBench.jvs` measures `put`, `get`, and `remove` in separate
program runs. It builds the key objects before starting the clock and
pre-populates the map before measuring `get` or `remove`. A Python harness uses
the `READY`/`DONE` input barriers so compilation, process startup, key creation,
and map setup stay outside the measured interval. The measured wall time
includes the barrier wake-up and the `DONE` marker syscall, so treat very small
timings as approximate. Repeating each phase in fresh processes helps expose
run-to-run variation.

Build and run one million operations, with five repetitions per operation:

```sh
jvsc -l benchmarks/heap/HashMapOperationBench.jvs
python3 benchmarks/heap/run_hashmap_operations.py \
  benchmarks/heap/out/HashMapOperationBench \
  --size 1000000 --repeats 5 \
  > benchmarks/heap/hashmap-operations-1m.csv
```

The harness validates the final map size and the last observed value for each
phase. The CSV reports each elapsed duration separately; these numbers exclude
map setup for lookup and removal and therefore measure the operation loops,
not the full end-to-end cost of constructing and using a map.

## Profiling resize-triggering insertions

`HashMapResizeBench.jvs` measures a single `put` after pre-populating the map
to a chosen size. `profile_hashmap_resizes.py` tests each resize threshold up
to one million entries and compares it with a neighboring control `put` that
does not resize. Setup is outside the timed interval. The paired difference is
an estimate of the extra cost of a resizing `put`; it includes rehashing,
allocating/initializing the replacement arrays, and the additional lookup.
This black-box measurement does not split those internal pieces apart.

```sh
jvsc -l benchmarks/heap/HashMapResizeBench.jvs
python3 benchmarks/heap/profile_hashmap_resizes.py \
  benchmarks/heap/out/HashMapResizeBench \
  --max-inserts 1000000 --repeats 5 \
  > benchmarks/heap/hashmap-resize-profile-1m.csv
```

At the largest threshold, the resize-triggering insertion rehashed 786,432
existing entries and grew capacity from 1,048,576 to 2,097,152. Its five-run
median was 52.832 ms; the neighboring non-resizing control was 0.032 ms.
Summing the paired resize-minus-control medians across the 17 thresholds gives
about 106.341 ms. The current four-array implementation's five-run median for
one million `put`s is 224.320 ms, so this estimates that resize events account
for about 47.4% of insertion time. This sum comes from separate fresh-process
measurements, not a direct timer inside `HashMap.resize()`.

In the five-run operation benchmark, medians (MAD) were 224.320 ms (1.910 ms)
for insert, 58.782 ms (0.298 ms) for lookup, and 87.601 ms (1.095 ms) for
remove. All results passed the harness's final-size and observed-value checks.

## Separating the rehash phases

The actual `HashMap.rehash()` currently allocates four arrays: keys, values,
cached hashes, and slot states. `HashMapRehashComponentsBench.jvs` models the
largest resize with the same four array shapes, a 1,048,576-slot old table,
786,432 occupied entries, and a 2,097,152-slot destination. It measures array
allocation, a scan of the old state array, a scan plus reinsertion pass, and
freeing the four old arrays. It also verifies that all entries were copied;
that verification is reported separately and is not included in the rehash
phase total.

```sh
jvsc -l benchmarks/heap/HashMapRehashComponentsBench.jvs
python3 benchmarks/heap/measure_rehash_components.py \
  benchmarks/heap/out/HashMapRehashComponentsBench \
  --old-capacity 1048576 --active-count 786432 --repeats 5 \
  > benchmarks/heap/hashmap-rehash-components-1m.csv
```

Measured medians (MAD, five fresh processes) were:

| Phase | Median | MAD |
| --- | ---: | ---: |
| Allocate four arrays | 23.170 ms | 0.112 ms |
| Scan old slot states | 7.095 ms | 0.183 ms |
| Scan and reinsert occupied entries | 30.587 ms | 0.644 ms |
| Free four old arrays | 0.024 ms | <0.001 ms |

The scan is part of the combined scan-and-reinsert pass, so the estimated
reinsertion work excluding the scan is `30.587 - 7.095 = 23.492 ms`. The
resulting non-overlapping estimate is about 53.781 ms, about 1.8% above the
52.832 ms measured for the actual resize-triggering `put` above. Approximate
shares are 43.1% allocation/zero-initialization, 13.2% scanning, 43.7%
reinsertion, and less than 0.1% freeing. These measurements use separate fresh
processes, so the difference reflects the limits of the black-box estimate.

On Linux, the heap allocator clears an allocated block inside `__heap_alloc`
with `rep stosq`. Consequently, allocation and zero-initialization are one
measurable phase without adding runtime instrumentation. The scan/reinsertion
split is an estimate from separate passes over equivalent data; the synthetic
workload uses sequential integer hashes, so it does not model collision-heavy
tables. No production `HashMap.jvs` code was changed for these measurements.

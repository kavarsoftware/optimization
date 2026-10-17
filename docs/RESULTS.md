# Measured results — weighted latency, version srs-study-2.0

Run date: 17 October 2026. Platform: Linux amd64, Eclipse Temurin Java 17.0.20.1, 8 processors reported to Java, `-Xmx512m`. Source evidence: `../results/weighted-study/summary.csv`, `trials.csv`, and each run's metadata/input snapshot. Runtime is elapsed search time, excluding export and playback, and can vary with JVM warmup, load and hardware. Millisecond values are truncated per run; 0 means less than 1 ms. These are descriptive measurements, not statistical confidence intervals or a universally best-method claim.

The main study contains one deterministic Exact scan and twenty paired runs of each stochastic method (seeds 2026–2045). A further sixty runs provide two fair-budget comparisons: twenty each of Random/SRS at 100,000 evaluations, and twenty Random runs matched to SRS stopping counts. Total: **121 run records**. Exact has no seed and is not artificially repeated twenty times.

All results below use the same weighted-latency formula, unchanged supplied data, CPU/RAM/storage constraints and minimum two services per server. Target success means a feasible score <= 18 ms. An optimum hit means matching the completed Exact minimum. Best/mean/median/worst and sample SD describe feasible returned solutions; failed runs would remain in success-rate denominators.

| Method | Best (ms) | Mean (ms) | Worst (ms) | Target success | Mean evaluations | Calls / space | Mean runtime (ms) | Proven optimal? |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Exact Search | 16.666667 | 16.666667 | 16.666667 | 100% | 16,777,216 | 100.000000% | 2187.00 | Yes, complete scan |
| Random Search | 16.904762 | 18.452381 | 20.000000 | 30% | 100,000 | 0.596046% | 72.70 | No |
| Target Search | 16.666667 | 17.630952 | 18.809524 | 80% | 238,975.95 | 1.424408% | 161.65 | 1/20 matches Exact; method approximate |
| Smart Restart Search (SRS) | 16.666667 | 16.666667 | 16.666667 | 100% | 20,000 | 0.119209% | 10.75 | 20/20 match Exact; method approximate |

| Method | Median (ms) | Sample SD (ms) | Mean distinct visits | Distinct coverage | Optimum hit rate |
| --- | ---: | ---: | ---: | ---: | ---: |
| Exact Search | 16.666667 | N/A (one run) | 16,777,216 | 100.000000% | 100% |
| Random Search | 18.571429 | 0.833781 | 99,703.05 | 0.594276% | 0% |
| Target Search | 17.619048 | 0.497487 | 236,212.65 | 1.407937% | 5% |
| Smart Restart Search (SRS) | 16.666667 | 0.000000 | 12,522.70 | 0.074641% | 100% |

Exact counted **18,281 feasible assignments**, or **0.108963%** of 16,777,216. Its best weighted numerator is **1050**, with denominator **63**, yielding **16.6666666667 ms**. An independent Python integer depth-first enumeration with capacity/count pruning confirmed both the count and the numerator. That check was development verification; Python is not needed to run the delivered Java app.

SRS used **20,000 evaluation calls** in every main run: the first 10,000-evaluation batch improved the global best; the next complete batch did not. With patience one it stopped there. Its mean distinct count is lower than its evaluation count because neighborhoods can revisit assignments. The final minimum appeared earlier than the stop; later evaluations tested whether it improved again.

The optimistic lower bound is **5 ms**. It ignores capacity and minimum-count restrictions and therefore does not certify the optimum. Exact's certificate comes from completing the whole finite space.

## Fair-budget evidence

| Protocol | Method | Mean (ms) | Worst (ms) | Mean evaluations | Mean runtime (ms) |
| --- | --- | ---: | ---: | ---: | ---: |
| equal_budget | Random Search | 18.452381 | 20.000000 | 100,000 | 67.40 |
| equal_budget | Smart Restart Search (SRS) | 16.666667 | 16.666667 | 100,000 | 45.45 |
| srs_stop_budget | Random Search | 19.583333 | 21.904762 | 20,000 | 13.30 |

For the matched-stop protocol, compare the Random row with SRS's main row (20,000 evaluations). SRS's equal-budget protocol disables patience and uses all 100,000 evaluations. The required Random main protocol is also 100,000; rerunning it under the equal-budget protocol checks the same seed sequence and may have a different measured runtime.

The first-seed convergence chart is explicitly a representative trajectory, not the mean of twenty runs. Dot plots and the summary table cover all seeds. Random and Target draw the same sequence for a given seed; their convergence traces overlap until one stops. The dashed Target line preserves visibility of that overlap. Evaluation bars and search-space counts use labeled log transforms so small methods remain visible beside Exact.

## Interpretation

SRS reached the Exact minimum in all measured seeds with far less evaluation work. Its general algorithm still has no global guarantee; on this supplied instance the returned scores are certified by comparison with Exact. Target search is designed to stop at a threshold, so it need not pursue the true minimum after reaching 18 ms. Random search can repeat candidates and waste most attempts on infeasible assignments.

The input model is small and simplified. These results do not establish performance on larger or different data, physical response-time accuracy, or a complete Pareto frontier. The optional additive objective and illustrative cost/normalized objectives are exported separately.

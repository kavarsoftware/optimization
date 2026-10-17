# Assessment results — objective J, assessment-srs-3.0

This file preserves the earlier four-method study. The separate version 4.0 **ten-algorithm equal-budget comparison** is in [TEN_ALGORITHMS.md](TEN_ALGORITHMS.md), with 200 actual trials in `results/ten-algorithms/`. Its full-budget ranking should be compared within that protocol.

These results use the PDF assessment dataset and fixed normalized J, with GPU, maximum-service-count and network SLA constraints. They do not use the old weighted-latency formula. Seeds: 2026–2045; Linux amd64, Java 17.0.20.1, 8 processors reported, 512 MiB heap limit. Date: 17 October 2026.

The main protocol has Exact once, Random 100,000, Target <=0.8 or 500,000 attempts, and SRS cap20,000/batch10,000/patience1/greediness0.35. Target success means J<=0.8; it is not an optimum hit. All stochastic returned results were feasible. Scores are dimensionless J. Time is active search wall time, excludes display waits/export, varies by machine/JVM; 0 ms means less than 1 ms.

| Method | Best J | Mean J | Worst J | Target success | Mean evaluations | Calls / space | Mean ms | Proven optimal? |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Exact Search | 0.715154 | 0.715154 | 0.715154 | 100% | 16,777,216 | 100.000000% | 2397.00 | Yes: complete scan |
| Random Search | 0.721222 | 0.724076 | 0.728393 | 100% | 100,000 | 0.596046% | 96.65 | No |
| Target Search | 0.727454 | 0.770779 | 0.797635 | 100% | 469 | 0.002795% | 0.25 | No |
| Smart Restart Search (SRS) | 0.715154 | 0.716057 | 0.716358 | 100% | 20,000 | 0.119209% | 12.35 | Best matches Exact in 5/20; no general guarantee |

| Method | Median J | Sample SD J | Optimum hit rate | Mean unique coverage |
| --- | ---: | ---: | ---: | ---: |
| Exact Search | 0.715154360 | N/A (one run) | 100% | 100.000000% |
| Random Search | 0.723810274 | 0.002189638 | 0% | 0.594276% |
| Target Search | 0.771874962 | 0.019029021 | 0% | 0.002795% |
| Smart Restart Search (SRS) | 0.716357637 | 0.000534570 | 25% | 0.080522% |

Exact enumerated all 16,777,216 vectors and found **92,643 feasible placements**. The minimum is **J=0.7151543595803707**. Independent recursive enumeration confirmed the count and optimum. Its mapping (server indices A=0..D=3) is `[1,1,0,2,1,2,3,0,3,1,2,3]`: A={S03,S08}, B={S01,S02,S05,S10}, C={S04,S06,S11}, D={S07,S09,S12}.

At that exact minimum: M=18.5 s, C=0.444950980392 USD, E=3.684492442810 Wh, L=49.188034188034 ms, Lmax=76.666666666667 ms; all 12 SLAs pass. The reference mapping gives J=0.856845071161, so the exact result is about 16.54% lower. The app evaluates the reference under current edits before claiming improvement; an infeasible reference has no improvement claim.

SRS's 20,000-evaluation mean gap to Exact is about 0.1262%. Its own stop rule cannot prove optimality. Five main runs match the Exact result; others remain best found. At an equal 100,000 evaluations, SRS matches Exact in **18/20** runs and its mean J is **0.715274687343**, versus Random's **0.724076291660**. Random at the paired SRS stopping count (20,000) averages **0.727881183774**. Evaluation calls and unique coverage are reported separately because neighborhood revisits do not cover new assignments.

## Budget sensitivity

All other settings stay fixed; patience is disabled so the full requested budgets are used. Each row uses the same twenty seeds. One iteration means one complete candidate evaluation, not a GA generation.

| Evaluation limit | Mean J | Best J | Sample SD J | Successful runs |
| --- | ---: | ---: | ---: | ---: |
| 20 | 0.759448633 | 0.739492449 | 0.013891781 | 20/20 |
| 50 | 0.757675034 | 0.739492449 | 0.012365934 | 20/20 |
| 100 | 0.741298540 | 0.726488354 | 0.008339910 | 20/20 |
| 200 | 0.734308978 | 0.716357637 | 0.008005805 | 20/20 |

## Network sensitivity

Compare all runs under the same J, constraints, seed list and SRS settings. RTT is multiplied by 1.25, or bandwidth by 0.70; other data remain fixed. All reported results pass their edited network SLAs. These are seeded heuristic outcomes, not certified sensitivity optima.

| Scenario | Feasible runs | Mean J | Mean L (ms) | Mean Lmax (ms) | Mean ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| default | 20/20 | 0.716056818 | 48.879380 | 76.666667 | 16.75 |
| RTT_plus_25_percent | 20/20 | 0.763719590 | 57.072863 | 91.666667 | 13.80 |
| bandwidth_minus_30_percent | 20/20 | 0.735108816 | 55.102778 | 83.809524 | 11.90 |

Higher communication delay worsens mean J/L/Lmax and can force different compatible placements. GPU demand occupies all three available slots, restricting S06/S09/S11 to C/D; SLAs restrict additional services from slower links. Throughput, power and price affect the selected load split. Only combined best J is monotone; its individual M/C/E/L measures can trade off.

## Evidence files

- `results/assessment-study/summary.csv`, `trials.csv`: four-method/fair-budget statistics and raw outcomes.
- `assessment-trials.csv`: J/M/C/E/L/Lmax/SLA counts for all 121 main/fair run records.
- `assessment-sensitivity.csv` and summary: 80 budget runs and 60 network runs.
- `runs/`, `budget-runs/`, `network-runs/`: full per-run settings, input snapshots, assignments, network margins, busy-time timeline and platform metadata. Total study records: 261.
- `charts/*.png` / CSVs, `budget-sensitivity.png`, `network-sensitivity.png`: exported visual evidence.
- `docs/evidence/assessment-independent-check.json`: independent development enumeration.

The Exact run is deterministic and has no seed; one run has no sample SD. The 20-seed samples are descriptive, not confidence intervals or a universal method ranking. Physical network validation, congestion, replicas and real cloud operation are outside this simulation. The original weighted-mode evidence remains separately labeled in `RESULTS.md`.

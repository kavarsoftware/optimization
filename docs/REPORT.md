# Assessment benchmark report

The complete six-page report is **Assessment_Report.pdf**. This Markdown companion links the model and reproducible evidence. The original weighted report is **ORIGINAL_REPORT.md**, and its results are separately labeled in **RESULTS.md**.

## Formulation

A twelve-entry host vector chooses A–D for every indivisible service. CPU/RAM/disk/GPU/max-service-count and individual network SLAs are hard rules. Assessment mode has no minimum-two rule.

B[j] = sum Work/Speed seconds; M = max B. Active power = Idle+(Peak-Idle)*CPU utilization if busy, otherwise zero. E=sum Power*B/3600 Wh. C=sum Price*B USD. Network latency = RTT + 1000*8*payloadMB/bandwidthMbPerSecond ms; L=mean, Lmax=maximum. Compute/wait time is not part of that network SLA.

Minimize fixed J=0.35*M/30 +0.20*C/0.50 +0.15*E/5 +0.30*L/70, dimensionless. Normalizers are not hard limits.

## Algorithm

SRS constructs randomized resource/GPU/SLA-compatible starts, tests moves/swaps with the shared evaluator, accepts strict feasible improvement, and restarts at local optima. A complete 10,000-evaluation stagnant batch stops the default patience-one search, within a 20,000 cap. Greediness/batch/patience/cap are adjustable. Repeated and infeasible complete evaluations count; abandoned partial starts are separate. Exact scans all vectors; Random uses 100,000; Target stops at J<=0.8 or 500,000.

Real pause/step/resume gates evaluations. Presentation waits are excluded from active algorithm runtime. Current and best views share actual event data, and recorded replay is separate.

## Evidence

# Assessment results — objective J, assessment-srs-3.0

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

# Assessment requirement and verification evidence

Source: `Assessment_Latency.pdf` (six pages). The assessment mode uses its dataset, formulas and hard rules. Earlier user-requested weighted latency stays in a separate original mode.

| Requirement | Evidence |
| --- | --- |
| Exactly 12 indivisible services on A–D | Candidate int[12], valid-index/length checks |
| All editable resource/throughput/power/price/network/service inputs | Configuration tables; reset PDF defaults; validated before search |
| CPU/RAM/disk/GPU/maximum-count and SLA constraints | Shared Evaluator.measure, rejected trials never accepted |
| Fixed J and M/C/E/L/Lmax quantities | Reference check, metrics, run metadata and assessment-trials.csv |
| Genuine iterative optimizer and comparator | SRS moves/swaps/restarts; Exact enumeration; Random baseline |
| Two algorithm-specific parameters | Greediness, batch and patience, plus seed/evaluation cap |
| Start/pause/resume/stop/step/reset | SearchControl gates actual evaluations; automated pause/one-step/resume check |
| Real current and best states | Linked event-based canvases, exact reservation totals, network labels, recorded replay |
| Progress J/L and at least two other measures | Six performance panels; performance.csv/PNG |
| Network/SLA view and rejections | Payload/RTT/bandwidth/transfer/network/SLA/margin/PASS-FAIL table, red rejection reason |
| Ordered execution timeline | Ascending service ID, Work/Speed durations, B and M labels, CSV/PNG |
| Repeated experiments | 20 seeds, statistics, Exact reference, Random comparator and fair budgets |
| Budget and network sensitivity | 20/50/100/200 evaluations; RTT +25% and bandwidth -30%; identical seeds |
| Submission | Source/JAR/build/run scripts; report; five-minute live-demo script; AI/reuse disclosure |

## Required named tests

| Test | Automated assertion and expected result |
| --- | --- |
| T1 | Default SRS returns feasible best; every recorded accepted incumbent re-evaluates feasible |
| T2 | S06 assigned to A rejected for GPU incompatibility |
| T3 | S04 added to A with S01/S03/S07 gives 21 vCPU >16; rejected |
| T4 | C GPU slots=0 leaves total 1 versus demand 3; necessary-condition infeasibility before search |
| T5 | Reference A CPU demand 14 with cap14 is accepted at equality |
| T6 | Same seed/version/inputs/settings reproduce assignment/evaluations/distinct count |
| T7 | Speed zero or negative capacity rejected before optimization |
| T8 | Timeline ordered Work/Speed totals equal B, and max total equals M |
| T9 | S03 on C has 70 ms >60 ms SLA; rejected regardless of J |
| T10 | Bandwidth zero or RTT -1 rejected before optimization |
| T11 | Reduce A bandwidth with fixed reference; A service delays, mean L and J rise consistently |
| T12 | Reference J/M/C/E/L/Lmax match PDF rounding and all 12 SLAs pass |

`./run.sh --self-test` runs the old model/search/replay checks and these named tests. The saved log is in `evidence/self-test.log`. Independent development enumeration confirmed 92,643 feasible assessment assignments and J=0.7151543595803707; its evidence is `evidence/assessment-independent-check.json`.

Additional GUI tests check worker completion, 12 network rows, autosaved charts, genuine pause/step/resume, and 200% layouts at 1536×795 and 1280×740 logical sizes. Source/build and native launch were tested on Linux Java 17. Windows/macOS, an IntelliJ UI session, physical projector and a teacher-delivered demo were not directly tested. No screen recording is claimed; PRESENTATION.md supplies the live-demo sequence.

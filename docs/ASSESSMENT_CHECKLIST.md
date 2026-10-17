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

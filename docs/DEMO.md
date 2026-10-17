# Four-minute live demonstration

## 0:00-0:45 — Problem

We assign each of 12 services to one of four servers. A candidate is an array of 12 server indices. CPU, RAM, disk, GPU, service slots and each network SLA are hard constraints. Open Configuration and show the editable inputs.

## 0:45-1:15 — Objective

J combines makespan, cost, energy and mean network latency with the fixed weights from the brief. Lower is better. Click Reference check: J=0.856845 and all 12 SLAs pass. Network latency is RTT plus payload transfer time; it is separate from compute time.

## 1:15-2:15 — Search

Our method builds a feasible random greedy placement, tries moving one service or swapping two, accepts an improvement, and restarts when stuck. Greediness changes the range of construction choices. Batch and patience stop a search after no improvement. The cap bounds total complete evaluations.

Click Start, Pause, Step and Resume. Show Current trial and Best feasible. Invalid trials can be displayed, but never become the best solution. Show the Performance and Compute timeline views. Each timeline duration is Work/Speed, in ascending service ID.

## 2:15-3:15 — Evidence

Show Experiments and the saved study. Ten seeds compare local search and Random at the same 20,000-evaluation budget. Exact separately checks all 16,777,216 assignments. It proves J=0.715154359580 on the default data. Local search returns best found; it does not prove optimality by itself.

Budget sensitivity uses 20, 50, 100 and 200 evaluations. Network sensitivity repeats the same seeds with RTT +25% or bandwidth -30%. Discuss J, latency, feasibility and runtime separately.

## 3:15-4:00 — Errors and limits

Set B bandwidth to zero and press Start: validation rejects it. Restore defaults, set C GPU slots to zero and run: three GPU slots are required but only one remains, so no placement is possible. Restore defaults again.

Run `./run.sh --self-test` to show T1-T12 and the budget/stop/Exact checks. This is a static simulation, not a real cloud deployment. Explain and credit AI assistance.

## Five answers to know

- A seed makes random choices reproducible; runtime can still vary.
- A local optimum has no better move/swap; a restart explores somewhere else.
- A low J never excuses a hard-constraint violation.
- More budget may improve quality but costs computation; it does not guarantee the optimum.
- Only completed Exact enumeration, or a matching completed Exact reference, certifies global optimality here.

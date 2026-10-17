# Ten-algorithm comparison

Implementation: assessment-portfolio-4.0. Seed range: 2026–2045. Constructor restriction: 0.35. 

Status: complete. Same input CSVs, evaluator, hard rules and objective: Assessment objective J. 20 paired seeds; 20000 complete evaluations per run. Patience and target stopping disabled.

Rank by feasible-run coverage, then mean feasible score (lower is better). Failed runs are shown explicitly; means exclude failures. Equal scores share quality; row order breaks exact ties. This is one instance and one set of parameters, not a universal ranking or a statistical significance claim.

Exact is an additional reference, outside the ten approximate methods. Exact completed: true; score: 0.7151543595803707. Its solution is never supplied to a heuristic; it is used only for reporting gaps/hits.

| Rank | Algorithm | Feasible / planned | Mean | SD | Best | Mean gap % | Exact hits % | Mean ms |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Tabu search | 20/20 | 0.715154360 | 0.000000000 | 0.715154360 | 0.0000 | 100.0 | 16.05 |
| 2 | Variable neighborhood search | 20/20 | 0.715575507 | 0.000588837 | 0.715154360 | 0.0589 | 65.0 | 13.50 |
| 3 | Simulated annealing | 20/20 | 0.715733174 | 0.000665163 | 0.715154360 | 0.0809 | 55.0 | 15.45 |
| 4 | Beam search | 20/20 | 0.715785028 | 0.000792474 | 0.715154360 | 0.0882 | 60.0 | 19.65 |
| 5 | Iterated local search | 20/20 | 0.715816162 | 0.000614174 | 0.715154360 | 0.0925 | 45.0 | 13.80 |
| 6 | Smart Restart Search (SRS) | 20/20 | 0.716056818 | 0.000534570 | 0.715154360 | 0.1262 | 25.0 | 16.05 |
| 7 | Restart hill climbing | 20/20 | 0.716361178 | 0.000844574 | 0.715154360 | 0.1687 | 25.0 | 17.75 |
| 8 | Genetic algorithm | 20/20 | 0.719683569 | 0.002507848 | 0.716357637 | 0.6333 | 0.0 | 28.00 |
| 9 | Randomized greedy construction | 20/20 | 0.721218020 | 0.002780699 | 0.715154360 | 0.8479 | 5.0 | 116.65 |
| 10 | Random Search | 20/20 | 0.727881184 | 0.004816929 | 0.721231097 | 1.7796 | 0.0 | 24.95 |

## Reproduce and interpret

Every invalid or repeated complete proposal counts. Feasibility checks during partial construction are heuristic work, not full candidate evaluations; construction attempts/failures are recorded per run. No repair or local-search evaluation is free. Stored population/neighborhood scores may be compared without re-evaluation.

Each method gets the same seed identifiers (different methods consume random numbers differently), input, and full evaluation cap. Execution order rotates by seed. Each implementation first receives an unrecorded warmup of up to 1000 evaluations. Timing includes construction/search overhead, excludes file export/display, and is local millisecond JVM timing. Equal evaluations do not mean equal runtime.

Target Search is kept in the original four-method study. It uses Random Search with a threshold stop, so including both with disabled stopping would duplicate an algorithm here. The ten methods are simplified teaching implementations, not tuned production libraries.

## Implementations and fixed parameters

All except uniform Random use the existing randomized capacity-aware constructor: restriction coefficient 0.35 by default, unrestricted eligible choices every fifth attempt. No known answer is hard-coded.

- **Random Search:** Uniform complete assignments; no construction or local improvement.
- **Randomized greedy construction:** Capacity/GPU/SLA-aware randomized greedy starts; no descent.
- **Restart hill climbing:** Randomized first improving single-service move; restart at a local minimum.
- **Smart Restart Search (SRS):** SRS / GRASP: greedy randomized starts, steepest moves + swaps, restart.
- **Simulated annealing:** Random move/swap; Metropolis acceptance, geometric cooling, reheat/restart.
- **Tabu search:** Best admissible move/swap, even if worse; 12-state memory with aspiration.
- **Genetic algorithm:** 32 individuals, tournament of 3, uniform crossover, per-gene mutation 1/n.
- **Iterated local search:** Steepest move/swap descent, 2-edit perturbation, improve or restart.
- **Variable neighborhood search:** Steepest descent with 1/2/3-edit shaking; reset strength after improvement.
- **Beam search:** Expand move/swap neighbors; retain best 8 distinct unvisited states per layer.

Annealing: T0=0.05*max(1,|initial score|), cooling to T0*0.001 over 2000 proposals, then restart. Tabu: stop/restart after 20 non-improving neighborhoods; tabu aspiration beats the pre-evaluation global best. Genetic: replace worst with each feasible child, fresh constructed individual every 100 offspring; retain global best externally. ILS: restart after 8 unsuccessful perturbations. VNS: 12 unsuccessful shakes before restart. Perturbation tries up to 20 complete proposals before moving on. Beam: width 8, up to 20 layers per restart, no revisiting retained states within a restart.

J/latency never includes a penalty for invalid assignments: infeasible candidates cannot become the reported best. The exact optimum is instance-specific. Algorithm names describe these explicit variants; other parameter choices can change the ranking.

## Background references

These references explain the algorithm families; this project's Java variants and parameters are stated above. No external algorithm library is bundled.

- [Feo & Resende, Greedy Randomized Adaptive Search Procedures (1995)](https://mauricio.resende.info/doc/gtut.pdf): randomized construction followed by local improvement.
- [Kirkpatrick, Gelatt & Vecchi, Optimization by Simulated Annealing (1983)](https://doi.org/10.1126/science.220.4598.671): annealing framework.
- [Glover, Tabu Search—Part I (1989)](https://doi.org/10.1287/ijoc.1.3.190): memory and aspiration.
- [Lourenço, Martin & Stützle, Iterated Local Search](https://arxiv.org/abs/math/0102188): perturbation and local search.
- [Mladenović & Hansen, Variable neighborhood search (1997)](https://researchrepository.mi.sanu.ac.rs/handle/123456789/2568): systematic neighborhood changes.
- [Norvig, PAIP Chapter 6](https://github.com/norvig/paip-lisp/blob/main/docs/chapter6.md): heuristic search and beam search.

## Open the comparison

In the Java app, choose **Experiments → Compare 10 algorithms**. The settings default to first seed 2026, 20 seeds, and 20,000 complete evaluations per method per seed. The **Paired vs SRS** tab highlights a selected seed with a pulse and reports its actual score difference.

[Quality chart](../results/ten-algorithms/charts/ranking.png) · [Seed spread](../results/ten-algorithms/charts/spread.png) · [Runtime](../results/ten-algorithms/charts/speed.png) · [Search curves](../results/ten-algorithms/charts/convergence.png) · [Dashboard](screenshots/ten-algorithms.png) · [Paired pulse](screenshots/ten-paired-pulse.png).

Verification: 1,148 Java checks passed. A separate Python calculation verified all 200 saved assignments, constraints, objective components, ranking means, sample standard deviations and ranges. See `evidence/ten-record-validation.json`. The Java app requires no Python runtime.

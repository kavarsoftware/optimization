# Service placement with GRASP and local search

Prepared for an Optimization Methods project using the supplied Week 0 and Week 1 lectures. The implementation is Java 17 with Swing. It uses the student's server and service data and the student's confirmed interpretation of service latency in milliseconds.

## 1. The question and the model

Choose one server for each of twelve services. A solution is useful only when every mandatory resource constraint holds. Among useful solutions, prefer smaller average delay.

The four questions from Week 0 slide 4 and Week 1 slide 4 have explicit answers:

| Role | This project |
| --- | --- |
| Decision | Which server receives each service |
| Objective | Minimize average service latency plus assigned server latency |
| Constraints | One host per service, CPU/RAM/storage capacities, selected minimum service count |
| Data | Resource demands, capacities, latency values, and optional illustrative prices |

Let `x_i ∈ {0,1,2,3}` be the host of service `i`. The Java representation is a `Candidate` containing twelve integers. This representation encodes exactly one server per service. The evaluator separately validates its length and index range.

With `p_i` as service latency and `L_s` as server latency, both in ms, minimize:

```
D(x) = (1 / 12) * Σ_i [ p_i + L_(x_i) ]
```

For every server `s`:

```
Σ_(i : x_i = s) CPU_i       <= CPU_capacity_s
Σ_(i : x_i = s) RAM_i       <= RAM_capacity_s
Σ_(i : x_i = s) Storage_i   <= Storage_capacity_s
count(i : x_i = s)         >= selected_minimum
```

CPU is in cores, RAM and storage in GB, and latency in ms. We treat all twelve services equally in the average because no request frequencies were supplied. We assume static demands and additive service/server delay. Every service must run, with no splitting or replication.

**The minimum-two-services rule comes from the student's original code.** The GUI makes it visible and allows 0, 1, 2, or 3. It is a placement rule, not evidence of reliability. All recorded default experiments use two.

Since `Σ p_i / 12 = 5.25 ms` is constant, the placement changes only the average server delay. Keeping `p_i` visible preserves the requested units and response-time interpretation. If realistic service execution time depends on the host, a service/server delay matrix should replace this additive model.

## 2. Data and search-space size

The four servers retain the supplied capacities and latency:

| Host | CPU cores | RAM GB | Storage GB | Latency ms |
| --- | ---: | ---: | ---: | ---: |
| S1 | 48 | 96 | 1,000 | 20 |
| S2 | 24 | 192 | 1,200 | 35 |
| S3 | 16 | 64 | 4,000 | 50 |
| S4 | 20 | 48 | 500 | 5 |

All twelve service inputs are in `data/services.csv` and `ProblemData.demo()`. Total demand is 78 CPU cores, 212 GB RAM and 3,680 GB storage.

Each service has four choices, so there are:

```
4 × 4 × ... × 4 = 4^12 = 16,777,216 complete assignments
```

This count includes infeasible assignments. The project does not claim that there are exactly one million feasible solutions. One million is the maximum evaluation budget, which is at most 5.9605% of the raw space even without duplicate evaluations. Constraints define a feasible subset whose exact size we do not enumerate.

Individual compatibility can already remove impossible placement choices. Files needs 1,200 GB storage, so it cannot go to S1 or S4. Such checks preserve feasible placements. Greedy selection and local neighborhoods then concentrate the search on a subset, without proving that unvisited placements cannot be better.

## 3. Why this method

The project uses **GRASP, a Greedy Randomized Adaptive Search Procedure, with move/swap local descent**. This is a standard metaheuristic structure adapted to the placement model. The project does not claim to invent a new algorithm or establish a universally best method.

A capacity-aware greedy constructor uses problem knowledge to create useful starting points. Random choices give different starts. Local descent uses evidence from those starts, and independent restarts let the search leave a local optimum. The combination is explainable in class and fits the requested approximate search.

Uniform random search is the baseline. It samples all raw assignments uniformly with replacement, including overloaded assignments. Randomized construction alone is an ablation: it measures how much the local improvement phase adds beyond construction.

### Construction

1. Count how many servers can individually hold each service. Place more restricted services earlier.
2. Among similarly restricted services, prioritize larger resource pressure, with a seeded random perturbation of the order.
3. For the current service, retain only hosts with enough remaining CPU, RAM and storage.
4. Reject a partial choice if the remaining unassigned services cannot fill the minimum-count deficits.
5. Rank the remaining hosts by their incremental contribution to the selected objective.
6. Build a restricted candidate list with threshold `best + 0.35 * (worst - best)`, then choose a host randomly from that list.
7. Every fifth construction attempt uses threshold factor 1.0, allowing all currently feasible hosts. This maintains diversity even if greedier attempts repeatedly fail.
8. If a later placement becomes impossible, abandon this partial construction and try a new one. A completed construction still passes through the same evaluator as all other candidates.

The word adaptive refers to recomputing available capacity and eligible choices after each placement. It does not mean that the software learns a statistical model or tunes its parameters online.

### Local descent

From a complete feasible candidate, inspect both neighborhoods:

- **Move:** relocate one service to another host. At most `12 * (4 - 1) = 36` alternatives.
- **Swap:** exchange the hosts of two services. At most `12 * 11 / 2 = 66` pairs. Pairs already on the same host are skipped.

All considered neighbors use the shared evaluator. Infeasible neighbors count toward the evaluation budget but cannot become the current or best solution. Select the best strict feasible improvement across the neighborhoods. Repeat until no improvement remains, unless the global budget or stopping rule interrupts the pass. Ties do not replace the incumbent.

A full neighborhood contains at most 102 alternatives. This is much smaller than 16,777,216 complete assignments. A local optimum means no strict improvement in these neighborhoods was found after a complete pass. It does not mean global optimality.

### Pseudocode

```
best = none
random = seeded RNG
while budget remains and stopping rule permits:
    start = randomized_capacity_aware_construction(random)
    if construction failed:
        record abandoned attempt
        continue
    current = evaluate(start)
    update best if feasible and better
    repeat:
        evaluate legal encodings of moves and swaps
        update best whenever a feasible improvement appears
        choose best feasible neighbor strictly better than current
        if none exists: break
        current = chosen neighbor
    restart with a new construction
return best and experimental evidence
```

No complete enumeration of the 4^12 space, exact optimizer, or external solver runs in the application. The test suite enumerates only an eight-candidate toy instance to check a known answer.

## 4. Stopping and accounting

The default ordinary run uses seed 2026, a cap of 1,000,000 complete candidate evaluations, batches of 10,000, and patience of three complete batches without global score improvement. Greediness is 0.35.

An improving first batch resets the stagnant count to zero. Three later stagnant batches cause a stop. The reference run therefore stops at 40,000 evaluations, rather than automatically using its full cap. Finding no improvement is a budget decision and supplies no optimality proof. Patience zero disables early stopping.

Every evaluator call counts, including repeated candidates and infeasible neighbors. A mixed-radix encoding tracks distinct candidates exactly for this instance. The GUI reports distinct coverage separately from evaluations. Partial construction checks are not complete candidate evaluations. Construction attempts and abandoned attempts have their own counters, and runtime includes their work. Equal evaluation budgets do not imply equal computational effort.

The search also supports cancellation. An input check can prove a limited kind of infeasibility when an individual service fits no server, or total demand exceeds total capacity. Otherwise, failure to find a solution does not prove infeasibility. A safeguard stops after a large consecutive sequence of failed constructions and reports that distinction explicitly.

## 5. A measured reference solution

Settings: `LATENCY`, `HYBRID`, seed 2026, budget 1,000,000, batch 10,000, patience 3, minimum 2.

| Server | Services | CPU used / cap | RAM used / cap | Storage used / cap |
| --- | --- | ---: | ---: | ---: |
| S1 | Cache, Analytics, Gateway, ML | 32 / 48 | 88 / 96 | 530 / 1,000 |
| S2 | Video, Backup | 18 / 24 | 20 / 192 | 1,100 / 1,200 |
| S3 | Database, Files | 10 / 16 | 56 / 64 | 1,800 / 4,000 |
| S4 | Auth, Web, Search, Email | 18 / 20 | 48 / 48 | 250 / 500 |

All capacities hold and every server has at least two services. S4 uses all its RAM, which is feasible because the rule is `<=`, not `<`.

The service delays sum to 63 ms. Assigned server delays sum to `4*20 + 2*35 + 2*50 + 4*5 = 270 ms`. Therefore:

```
Average delay = (63 + 270) / 12 = 27.75 ms
```

The score first appeared at evaluation 114. The run stopped at 40,000 evaluations, with 9,389 feasible evaluations and 28,696 distinct candidates. It made 2,635 construction attempts, abandoned 2,474, and started local descent from 161 completed constructions. Its recorded runtime was 90 ms on the available Linux/amd64 Java 17 runtime. Timing will vary across machines and JVM warmup states.

Distinct coverage is `28,696 / 16,777,216 * 100 = 0.1710%`. This describes the raw assignment space, not the fraction of all feasible assignments. The project did not prove the exact feasible-space size.

An optimistic bound ignores all capacities and count constraints and places every service on its best host. For the default objective that gives `5.25 + 5 = 10.25 ms`. It is deliberately weak. The difference from 27.75 ms cannot certify an exact optimality gap. It only bounds how much improvement could remain under this relaxation.

## 6. Fair repeated-run comparison

The main experiment uses paired seeds **2026 through 2045**, 20 runs for each method, 50,000 evaluations per run, the same input data and minimum-count rule, and the same evaluator. Early stopping is disabled. Execution order rotates between methods to reduce always-first JVM warmup effects. This is a small practical experiment, rather than a controlled runtime benchmark.

Measured results in `results/comparison-latency/summary.csv`:

| Method | Feasible runs | Best ms | Mean ms | Median ms | Sample SD ms | Worst ms | Mean runtime ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| GRASP + move/swap | 20/20 | 27.7500 | 27.7500 | 27.7500 | 0.0000 | 27.7500 | 19.20 |
| Randomized construction | 20/20 | 27.7500 | 27.7500 | 27.7500 | 0.0000 | 27.7500 | 855.65 |
| Uniform random | 20/20 | 27.7500 | 29.1875 | 29.0000 | 0.9314 | 30.2500 | 21.15 |

The hybrid's mean delay is about **4.93% lower** than uniform random search on this experiment: `(29.1875 - 27.75) / 29.1875`. Uniform random matched 27.75 ms in 4 of 20 runs. All methods completed the requested 50,000 evaluation calls in every run.

The evidence also shows that **construction alone tied the hybrid on default objective quality**. We should not claim that the local search phase was necessary to achieve 27.75 ms on this instance. At equal evaluation counts, construction spent more time generating new feasible starts, while the hybrid spent many calls evaluating neighbors. These wall-clock times do not establish a universal speed advantage.

On average, uniform random search evaluated only 53.3 feasible assignments out of 50,000. The hybrid evaluated 11,750.05 feasible candidates, including repeats. Pure construction evaluated 50,000 feasible candidates but revisited many placements. This explains why a larger count of distinct raw assignments does not automatically produce a better solution.

An optional separate experiment in `results/comparison-legacy-weighted/` uses the old code's importance-weight interpretation. Its hybrid mean was 16.6667 ms, construction mean 16.7857 ms, and random mean 18.8333 ms. Those numbers answer a different objective and must not be compared directly with 27.75 ms. They show how a changed preference can change search behavior. This is an illustrative extension, not the student's confirmed default model.

## 7. Cost, conflicting objectives and Pareto ideas

The lectures discuss cost and response time. Actual prices were not supplied, so the optional cost model uses clearly labeled example CPU rates of 0.08, 0.05, 0.025 and 0.06 cost units per CPU core per observation period. These values are editable and are not claims about real cloud providers.

```
C(x) = Σ_i CPU_i * rate_(x_i)
F_alpha(x) = alpha * D(x) / D_scale + (1-alpha) * C(x) / C_scale
```

`D_scale = max_server_latency + mean_service_latency = 55.25 ms` and `C_scale = total_CPU * max_rate = 6.24 cost units` for the supplied data. Normalization prevents adding raw ms to raw cost units. A larger alpha places more emphasis on delay. Changing alpha changes the stated objective. It is a stakeholder preference, not something the optimizer decides independently.

The GUI runs 11 priority settings, with 20,000 evaluation calls each, using the same seed and a hybrid search. It plots cost against delay, marking points that are nondominated among the sampled results. Candidate A dominates B if A is no worse in both measures and strictly better in at least one.

The saved sweep in `results/tradeoffs/sweep.csv` includes these sampled compromises:

| Delay priority alpha | Average delay ms | Illustrative cost | Nondominated within sample |
| --- | ---: | ---: | --- |
| 0.1 | 30.25 | 4.24 | Yes |
| 0.6 | 29.00 | 4.41 | Yes |
| 0.8 | 27.75 | 4.75 | Yes |
| 1.0 | 27.75 | 4.79 | No |

The pure-delay result is dominated within this two-objective sample because an equal-delay placement has lower cost. That is expected when a single-objective search keeps its first equal-quality incumbent and does not optimize a secondary measure.

This sweep approximates trade-offs. It neither enumerates all feasible candidates nor guarantees a complete Pareto frontier. Weighted sums may miss parts of a discrete, nonconvex frontier. The same default quality can also have several placements with different secondary costs because single-objective ties are not broken by cost.

The project does not invent reliability, energy, dependency, price-budget, or network topology data. Such constraints require additional inputs and an evaluator change. The lecture topic map explains them as extensions and applications.

## 8. Verification and validation

The automated checks test independently calculated evaluator results, invalid candidate encodings, each resource rule, equality at capacity, the count rule, defensive candidate copies, seeded reproducibility, full budgets, a budget of one, complete-batch stopping, cancellation, necessary infeasibility checks, lower bounds and CSV round trips. A tiny known-answer case checks the search without exhaustive enumeration of the main problem.

Hand case: servers with 5/20 ms delay, CPU rates 0.2/0.1, and sufficient capacity. Three services have CPU `[2,4,1]`, own latency `[1,2,3]`, and minimum one service per host.

| Candidate | Delay | Cost | Conclusion |
| --- | ---: | ---: | --- |
| A = [S1,S2,S1] | `(6+22+8)/3 = 12 ms` | `2*.2+4*.1+1*.2 = 1.0` | Feasible |
| B = [S2,S1,S2] | `(21+7+23)/3 = 17 ms` | `2*.1+4*.2+1*.1 = 1.1` | Feasible, dominated by A |
| C = [S1,S1,S1] | Excluded from selection | Excluded from selection | Infeasible, S2 is empty |

**Verification** asks whether the code implements the equations. The tests and exported evidence support that. **Validation** asks whether the model represents the real system. The additive, static latency model and supplied example data require teacher/stakeholder review and measured deployment data before real-world use. Testing the code does not prove this physical model.

## 9. Java architecture and computational effort

| Java type | Responsibility |
| --- | --- |
| `ProblemData` | Immutable known inputs and unit/number validation |
| `Candidate` | Immutable service-to-server decisions |
| `Evaluation` | Feasibility, violations, resource usage, delay, cost and score |
| `Evaluator` | Single shared definition of constraints and objectives |
| `SearchMethod` | Names the interchangeable search strategies |
| `Optimizer` | Candidate generation, local search, restarts and stop accounting |
| `SearchConfig` / `SearchResult` | Reproducible settings and evidence |
| `ExperimentRunner` | Paired-seed repeated-run comparisons and statistics |
| `CsvFiles` | UTF-8 import and reproducible exports |
| `Dashboard` / `Visuals` | Swing controls, placement, resource bars and charts |
| `SelfTest` | Hand-calculated and behavioral checks |
| `Main` | GUI, command-line experiments and preview rendering |

For `n` services and `m` hosts, one evaluation costs `O(n+m)`. One local pass has at most `n(m-1) + n(n-1)/2` evaluations, so its evaluation work is `O((nm+n²)(n+m))`. The default constructor computes eligibility, sorts services, and tests choices with count deficits, about `O(nm²+n log n)`. Weighted legacy ranking additionally sums weights in its contribution calculation. Failed constructions add work and are recorded separately.

With a cap of `B` evaluation calls, evaluation work is at most `O(B(n+m))`, plus construction, bookkeeping and UI work. A BitSet records distinct candidates for the 16,777,216-space instance, at most about 2 MiB for candidate flags. This small educational instance is fast on the measured machine. Growth remains exponential in the number of services, which motivates approximate search for larger systems.

## 10. What changed from the original code

| Original issue | Correction |
| --- | --- |
| Service values described as latency but used as importance weights | Confirmed latency semantics and a separate labeled legacy mode |
| Comment says batches of one million, constants set both batch and cap to 16,777,216 | Configurable cap up to one million and actual 10,000-evaluation batches |
| One full-cap batch prevents a useful later-batch stagnation check | Multiple complete batches and visible patience |
| Uniform random throws away resource structure | Capacity-aware construction and targeted move/swap neighborhoods |
| Time-based seed makes exact reruns difficult | Explicit user-visible seeds |
| Candidate count can be mistaken for distinct coverage | Exact distinct counts and repeat counts |
| A new click overwrites run totals despite “runs completed” presentation | Each optimizer result is a clear run; repeated experiments are a separate feature |
| No comparison across methods and seeds | Shared evaluator, paired seeds, statistics and raw trial exports |
| Resource limits exist only in code | Visible editable data, utilization bars and explicit violation evidence |
| A late update can be confused with progress toward an optimum | Progress measures evaluation budget; stop reason and best-found claim stay explicit |
| CSV behavior depends on locale and encoding | UTF-8 exports, consistent numbers, quoting and complete input snapshots |
| GUI has no cancellation | Cooperative stop flag and background workers |

## 11. Relationship to the lectures

`LECTURE_MAP.md` maps every slide in both supplied decks. The Week 1 laboratory intentionally focuses on evaluation before optimization. We preserve that teaching sequence through a hand-calculated three-candidate example and independent evaluator checks, then add the optimizer requested in this conversation. Classroom directions in the decks are treated as lecture context, not as new instructions overriding the student's request.

Week 1's course map names later 1D/gradient/linear programming, integer/dynamic programming, genetic algorithm, simulated annealing, particle swarm and multi-objective topics. It does not teach complete implementations of all of them. This discrete placement project discusses method suitability and includes a sampled trade-off experiment. It makes no claim to implement all future course methods.

The standard GRASP name describes the implementation structure. The supplied lectures establish the modeling, method-family and experimental principles. GRASP itself is a project design choice extending those foundations.

## Lecture evidence update

A complete Exact enumeration now evaluates all 16,777,216 assignments with the shared evaluator and proves the default instance optimum is **27.75 ms**. Earlier sections describe the original budgeted experiment; their best-found qualification still applies to the heuristic itself. Matching an independently proved optimum does not make a heuristic run an exact proof.

The updated final summary below is generated from `results/lecture-comparison/summary.csv`. Each stochastic row has 20 runs (seeds 2026 through 2045). All scores in this table use the default average-delay objective in ms. Success means a feasible result; evaluations, space coverage and runtime are per-run means. Timing is hardware/JVM dependent.

| Method / protocol | Best (ms) | Mean (ms) | Worst (ms) | Success rate (%) | Evaluations (calls) | Space visited (%) | Runtime (ms) | Proven optimal? |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Exact / complete enumeration | 27.75 | 27.75 | 27.75 | 100 | 16,777,216 | 100 | 5549.00 | Yes |
| Hybrid / equal budget | 27.75 | 27.75 | 27.75 | 100 | 50,000 | 0.207291 | 18.95 | No; best found |
| Constructive / equal budget | 27.75 | 27.75 | 27.75 | 100 | 50,000 | 0.068092 | 719.10 | No; best found |
| Random / equal budget | 27.75 | 29.1875 | 30.25 | 100 | 50,000 | 0.297580 | 29.10 | No; best found |
| Hybrid / own stopping | 27.75 | 27.75 | 27.75 | 100 | 40,000 | 0.174060 | 14.85 | No; best found |
| Random / matched stopping budget | 27.75 | 29.4375 | 30.25 | 100 | 40,000 | 0.238141 | 21.35 | No; best found |

All methods' best results have 0% gap to the Exact optimum on this sample; the mean and worst results show Random's variability. Exact is a single deterministic run with no seed. See `LECTURE_ACTIVITIES.md` for the activity answers, mistakes-to-safeguards table, gap formula, objective separation and comparison protocols.

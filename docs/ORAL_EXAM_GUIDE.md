# Optimization method: explanation and oral-exam guide

Reviewed against the current Java source and a fresh ten-seed study on 18 October 2026. This guide excludes UI implementation. Numerical claims below describe the supplied CSV dataset, not arbitrary edited inputs. The original assignment PDF was not independently checked during this review; equations here are verified against the implementation.

## 1. The explanation to give first

“My project solves a constrained service-placement problem. It assigns twelve indivisible services to four heterogeneous servers. Every placement must satisfy resource capacities and per-service network latency limits. Among feasible placements, it minimizes a fixed weighted objective combining makespan, monetary cost, energy and average network latency. My main algorithm is randomized greedy construction followed by best-improvement move-and-swap local search with independent restarts, a GRASP-style method. Random sampling is the baseline, and exhaustive enumeration supplies an exact reference for this small instance.”

Do not describe it as a genetic algorithm, simulated annealing, gradient descent, machine learning, or a Java deployment scheduler. None of those is implemented. It simulates placements; it does not deploy services.

## 2. Problem, inputs and decisions

A service has a name, CPU reservation, RAM reservation, disk reservation, workload in giga-instructions (GI), GPU-slot demand, payload in MB, and maximum permitted network latency in milliseconds.

A server has a name, CPU/RAM/disk capacities, GPU slots, maximum number of assigned services, processing speed in GI/s, idle/peak power in watts, price in USD per busy second, RTT in ms, and bandwidth in Mb/s.

Heterogeneous means these server properties differ. A is relatively near and inexpensive but slower and has no GPU; C is fastest and has two GPU slots but higher RTT and price. B has no GPU, and D has one GPU slot. Faster compute alone does not make a host best.

These inputs are parameters. The optimization decision is ONLY which server receives each service. It does not change CPU capacity, service workloads, prices, power coefficients, service order, or network bandwidth.

The implementation represents a candidate as twelve integers, one per service. Server indices 0,1,2,3 mean A,B,C,D. For example, the supplied numerical reference is:

`[0,2,0,3,2,2,0,3,3,2,2,1]`

Entry 0 assigns S01 to A; entry 1 assigns S02 to C. Every service has exactly one entry, so complete vectors naturally encode exactly-once assignment without splitting or replication. The evaluator still validates vector length and index range. Candidate defensively copies its array to prevent later changes corrupting the stored best.

There are m^n complete vectors: 4^12 = 16,777,216. This includes infeasible vectors. A fresh exact run found 92,643 feasible vectors, about 0.5522% of the complete space. Checking one vector is cheap, but the number grows exponentially as services are added: one extra service multiplies the raw space by four.

A mathematical alternative uses binary variables x[i,j]: 1 if service i is assigned to server j, 0 otherwise. Require sum_j x[i,j] = 1 for each service. The integer vector and binary formulation describe the same decision.

## 3. Hard constraints

For every server j:

- sum_i CPU[i] x[i,j] <= CPUcapacity[j].
- sum_i RAM[i] x[i,j] <= RAMcapacity[j].
- sum_i Disk[i] x[i,j] <= Diskcapacity[j].
- sum_i GPU[i] x[i,j] <= GPUslots[j].
- sum_i x[i,j] <= MaxServices[j].

For every assigned service i, NetworkLatency[i,j] <= SLA[i]. SLA means service-level agreement; here it is a limit on network communication latency, not total compute response time.

All must pass. A low price or excellent objective cannot compensate for an overload or one failed SLA. Infeasible complete placements receive positive infinity as their score and cannot become the best feasible solution. The search evaluates infeasible neighbors, but never accepts them as its current feasible placement.

Equality at a capacity or SLA boundary is allowed. Floating-point resource/SLA comparisons permit a small 1e-9 numerical tolerance. GPU and service-count checks are integer comparisons.

GPU-demanding services S06, S09 and S11 each need one GPU slot. A and B have none, C has two, D has one. Therefore these three services must consume all C/D GPU slots: two on C and one on D. This narrows the feasible placements substantially. GPU count is a constraint; there is no extra GPU acceleration formula.

An example of resource infeasibility: in the reference mapping A reserves 14 CPU cores. Moving S04, which needs 7, to A makes 21 > 16, so that candidate fails.

An example of SLA infeasibility: S03 on C has 70 ms network latency, exceeding its 60 ms limit, even if all resource capacities pass.

## 4. Evaluation equations and units

For service i on server j, processing time is:

`t[i,j] = Work[i] / Speed[j]` seconds.

For each server, busy time is:

`B[j] = sum of t[i,j] over its assigned services`.

The model processes a server's assigned workloads sequentially; different servers operate in parallel. Consequently:

`M = max_j B[j]` seconds.

M is the makespan, the time at which all modeled compute work finishes. It is not the sum of all servers' busy times. The displayed sequence uses ascending service ID, but changing the order on one server would not change that server's total busy time or the objective under this model.

CPU utilization for the power model is:

`U[j] = reserved CPU[j] / CPUcapacity[j]`.

For a used server:

`P[j] = IdlePower[j] + (PeakPower[j] - IdlePower[j]) U[j]` watts.

An unused server has P[j] = 0. This assumes it can be treated as powered off. This is a static reservation-based estimate, not measured dynamic CPU utilization.

Energy is:

`E = sum_j P[j] B[j] / 3600` watt-hours.

The division by 3600 converts watt-seconds into watt-hours. Wh and W are different quantities: energy versus power. For example, 140 W for 28.333333 seconds uses approximately 1.101852 Wh.

Monetary cost is:

`C = sum_j Price[j] B[j]` USD.

Price is per busy second, so it is multiplied by seconds; no factor of 3600 is needed. This model excludes startup costs, idle billing after completion, and billing minimums.

Network latency is:

`l[i,j] = RTT[j] + 8000 PayloadMB[i] / BandwidthMbPerSec[j]` milliseconds.

Why 8000? MB is megabytes, bandwidth is megabits per second, so multiply by 8 to convert bytes to bits, then by 1000 to convert seconds to milliseconds. Decimal MB/Mb is assumed.

The mean is `L = (sum_i l[i,host(i)]) / 12`. The worst is `Lmax = max_i l[i,host(i)]`.

The objective uses L. Lmax is a diagnostic, and each individual l must also satisfy its own SLA. An average alone cannot enforce per-service limits.

Network latency excludes compute time, service queueing, packet loss, congestion and transfer contention. It is not added to B or M in this implementation. Keep three time concepts separate: modeled compute seconds, network milliseconds, and the optimizer's wall-clock milliseconds.

## 5. Objective and tradeoffs

The implementation minimizes:

`J = 0.35(M/30) + 0.20(C/0.50) + 0.15(E/5) + 0.30(L/70)`.

This combines four objectives into one scalar objective. Its underlying goals are multiple, but the implemented optimization target is the single number J; there is no Pareto-front output.

The weights sum to one. Their meaning is relative importance after normalization: makespan 0.35, cost 0.20, energy 0.15, mean network latency 0.30. The denominators have corresponding units, making each ratio, and hence J, dimensionless.

30 seconds, $0.50, 5 Wh and 70 ms are normalizing scales, not hard upper bounds. A feasible solution may exceed a scale; J is not guaranteed to lie in [0,1]. These weights/scales are fixed in the current evaluator, not learned or optimized.

Multiplying all weights by the same positive constant changes the numerical score but not its ranking. Changing one weight or one denominator changes the effective tradeoff and may change the preferred placement.

A move can lower makespan while increasing cost or network latency. It is accepted if all hard constraints pass and the weighted total improves enough. J can decrease even while an individual component increases. “Best” means best according to this model and these weights, not simultaneously best on every component.

The construction contribution function estimates one service's isolated compute, cost, energy and one-twelfth network contribution. It is a ranking heuristic. It is NOT the exact marginal change in J: makespan depends on the maximum of server totals, and power/energy depend on combined reservations. The full evaluator always decides complete-candidate quality.

## 6. Worked numerical examples

S03 has Work=120 GI and Payload=1.5 MB. On A, Speed=12 GI/s, RTT=18 ms, bandwidth=500 Mb/s:

- Compute time = 120/12 = 10 seconds.
- Transfer time = 8000*1.5/500 = 24 ms.
- Network latency = 18+24 = 42 ms.
- SLA margin = 60-42 = 18 ms, so it passes.

On C, Speed=34 GI/s, RTT=60 ms, bandwidth=1200 Mb/s:

- Compute time = 120/34 = 3.529412 seconds.
- Network latency = 60+8000*1.5/1200 = 70 ms.
- 70 > 60, so the faster compute host is infeasible for this service.

For the supplied reference mapping, A holds S01, S03 and S07. CPU=4+5+5=14; RAM=8+10+10=28 GB; disk=30+50+45=125 GB. Its work is 90+120+130=340 GI. Thus B[A]=340/12=28.333333 seconds, U[A]=14/16=0.875, P[A]=70+(150-70)*0.875=140 W, energy[A]=1.101852 Wh, and cost[A]=0.003*28.333333=$0.085.

For the complete reference mapping:

- M = 28.333333 seconds.
- C = $0.449583333333.
- E = 3.927416 Wh approximately.
- L = 53.347863 ms.
- Lmax = 76.666667 ms.
- J = 0.856845071161 approximately.

This reference validates arithmetic. It is not claimed to be optimal and is not injected as a starting solution into the search.

## 7. Main algorithm, step by step

The main method is restart local search with randomized greedy construction, described as GRASP-style: Greedy Randomized Adaptive Search Procedure. Each start builds a placement and improves it locally; new independent constructions provide exploration.

Pseudocode:

```text
best = none
check inexpensive necessary feasibility conditions
while budget and stop rules permit:
    construct a randomized resource- and SLA-compatible placement
    if construction fails, count the failure and retry
    evaluate the complete placement; update best if better
    while permitted:
        inspect moves and swaps from the current placement
        evaluate their full feasibility and objective
        select the best strict feasible improvement
        if none exists after a complete scan, finish this descent
        otherwise accept it and repeat
return best feasible placement seen and the stop reason
```

Construction first prioritizes hard-to-place services. For each service it counts individually compatible hosts and computes resource pressure. Its priority is:

`100*(4-compatibleHostCount) + pressure*(0.7+0.6*randomDouble)`.

Pressure is the maximum CPU/RAM/disk demand-to-capacity ratio across the hosts checked. Services are sorted by descending priority. On the supplied data the compatible-host term dominates: more restricted services are handled early. The random pressure factor perturbs ordering.

For the next service, construction forms the set of hosts that remain feasible with already assigned services. It checks remaining CPU/RAM/disk, GPU usage, maximum service count and individual network compatibility.

It computes each available host's contribution estimate g. If gmin and gmax are the smallest and largest estimates, its restricted candidate list (RCL) contains hosts with:

`g <= gmin + alpha*(gmax-gmin) + 1e-12`.

One RCL host is selected uniformly at random. Resource usage is updated, so subsequent choices adapt to earlier assignments.

At alpha=0, only minimum-cost hosts and numerical ties are eligible. At alpha=1, all currently feasible options are eligible. The default is 0.35. Despite the field name “greediness,” a larger number allows MORE randomness, not stricter greediness.

Every fifth construction attempt forces alpha=1 for diversification. Therefore a configured alpha of zero does not make every construction purely greedy or eliminate all randomness. The attempt counter includes failed constructions.

If there is no remaining host for some service, the partial construction is discarded. There is no repair or backtracking within that construction. This failure does not establish that the complete instance is infeasible.

## 8. Moves, swaps and local optima

A move changes one service's host. With n services and m servers there are n(m-1) move proposals: 12*3=36 here.

A swap exchanges the hosts of two services. Pairs already on the same host are skipped, because exchanging them changes nothing. Before those skips there are n(n-1)/2 pairs, 66 here. The exact swap count is 66 minus the within-server pair counts. A sweep therefore evaluates at most 102 proposals using this simple upper bound; the actual count is smaller.

Swaps matter because a direct move may overload a server, while exchanging two services can fit. A swap is still fully checked; it does not automatically preserve CPU, GPU or SLA feasibility.

This is best-improvement descent: the code scans moves and swaps from the SAME current solution, retaining the best improving neighbor, rather than accepting the first improvement immediately. It then makes that neighbor current and scans again.

Local moves require improvement greater than 1e-10. Equal-score moves and worse-score moves are not accepted. This avoids cycles through ties and numerical noise, but prevents plateau wandering or temporarily worsening escapes. Restarts are the implemented escape mechanism.

A local optimum means no improving feasible move or swap exists in a COMPLETED neighborhood scan. It says nothing about moves of three services at once or the global solution space. If the budget or another stop rule interrupts the scan, do not claim a local optimum was certified.

The global incumbent is updated whenever any evaluated feasible candidate beats it. It is stored separately from the current descent solution and survives restarts. The current solution can be worse immediately after a restart while the best-so-far score never increases.

The implementation's `restarts` count includes the first successfully constructed start; it is effectively a count of successful starts, not only starts after the first.

## 9. Budget, counters and stopping rules

One evaluation is one complete placement passed through the full evaluator. Valid, invalid and repeated complete placements all consume budget. This is not a population generation, one restart, or one full neighborhood sweep.

`unique` counts different complete vectors encountered. A base-four candidate code and BitSet track this. The BitSet does not prevent reevaluating duplicates; it measures coverage. `feasible` counts feasible evaluation occurrences, which may repeat the same vector. Construction attempts and failed partial constructions are separate counters.

Incomplete construction attempts do not consume evaluation budget, but do consume runtime. Equal evaluation budgets therefore do not mean identical computational work or equal wall-clock time.

Termination can occur because:

- The evaluation cap is reached.
- Cancellation is requested.
- Optional patience detects sufficient stagnant batches in local search.
- A necessary-condition check proves infeasibility before heuristic searching.
- Too many consecutive partial construction failures occur.
- Exact enumeration completes, for the exact method.

Batch size defines the evaluation intervals for checkpointing. It is not a batch of parallel evaluations. Patience means consecutive COMPLETE batches without a new global best. Patience=0 disables this stop rule. Random does not use patience stopping; Exact also disables it.

For batch=10,000 and patience=1, a first feasible best establishes improvement at the first checkpoint. If no further global improvement occurs in the second batch, the search can stop at 20,000. With a larger budget this can save work. A partial final batch does not independently trigger the batch rule. The patience stop requires a feasible best to exist.

The consecutive failed-construction limit is max(2000, 2*budget). It resets after a successful construction and reports a construction-attempt limit, explicitly without an infeasibility proof. This cap can make actual wall-clock effort exceed what a small evaluation budget suggests.

## 10. Necessary checks versus sufficient proofs

Before Random or local search, the code asks whether every service has at least one individually compatible host, and whether total CPU/RAM/disk/GPU demand and available service slots can possibly fit.

Failure of one of these necessary conditions proves infeasibility. Passing them does not prove feasibility: capacities can be fragmented across incompatible hosts. For example, enough total GPU slots do not guarantee they sit on hosts satisfying every GPU service's SLA.

Local search returning no placement is generally inconclusive. Exact returning no placement after enumerating every vector proves infeasibility under the model and numerical comparisons.

`optimisticBound()` currently returns zero. It is a trivial nonnegative lower bound for the objective, not an exact solver or a useful quality certificate. The search does not use it for branch-and-bound pruning.

## 11. Random and Exact

Random chooses each service's host independently and uniformly among all four hosts. It samples complete vectors uniformly with replacement and keeps the best feasible one. It does not use greedy construction, descend, repair, or learn from previous samples.

Only around 0.5522% of all vectors are feasible in this dataset, so most random evaluations are rejected. High unique coverage does not necessarily mean good search: a guided algorithm can revisit useful regions and find a better score.

Exact counts through all base-four vectors like an odometer: 000...0, 000...1, etc. It checks every complete vector once, including infeasible ones. This is exhaustive enumeration, not branch-and-bound. Its evaluation budget is replaced with 4^12, patience is zero, and seed has no effect.

Exact reuses working arrays and creates a detailed immutable snapshot when a new incumbent is found. It has less allocation overhead per candidate than the normal detailed heuristic evaluation path. Runtime differences therefore include implementation effects as well as algorithm effects.

Once every vector is checked, the minimum feasible score is globally optimal for this finite model and its floating-point feasibility rules. A cancelled partial exact run supplies a best-so-far result but not the certificate. Completing exact verification does not automatically imply the global optimum is unique; this code does not count all tied optima.

Exact results are not passed into Random or local search. A heuristic solution matching the independently computed exact score is externally verified for that instance, but the heuristic itself has no general finite-budget optimality guarantee.

## 12. Fresh experimental evidence

A fresh study used supplied inputs, seeds 2026 through 2035, greediness=0.35, batch=10,000, and a budget of 20,000 for BOTH heuristics. Patience was disabled for fair equal-budget comparisons. Method execution order alternated across seeds to reduce a fixed-order timing effect. Exact ran once independently.

Results:

- Exact: minimum J=0.7151543595803707, 16,777,216 evaluations, 92,643 feasible vectors.
- Local search: 10/10 feasible runs; best J=0.7151543595803707; mean J=0.716237309443008; worst J=0.7163576372055231; sample SD=0.0003805097953; optimum hit rate=1/10.
- Random: 10/10 feasible runs; best J=0.7212310974870717; mean J=0.7285701111133557; worst J=0.7387413576007326; sample SD=0.0060090983485; optimum hit rate=0/10.

Local search's mean objective is about 1.69% lower than Random's mean in this study. This is an instance-specific descriptive comparison, not a universal ranking or a formal significance result.

Mean measured times in this run were 22.9 ms for local search, 24.3 ms for Random and 6406 ms for Exact. Do not memorize these as algorithm constants: JVM warmup, hardware, allocation, timer resolution and measurement overhead affect them. The repeated default network-sensitivity run produced different timings for the same search results, demonstrating that point.

Average unique evaluations: local search 13,663.5; Random 19,988.7. Thus local search was better here despite lower distinct coverage. 20,000 evaluations are at most 0.1192093% of the raw space, and actual unique coverage is lower.

A feasible-run success rate asks whether a run found ANY feasible solution. Optimum hit rate asks whether it matched the exact minimum. 100% feasible success does not mean 100% optimality. Sample SD uses denominator k-1, over successful runs only. Report failed-run rate alongside score statistics to avoid hiding failures. Exact's one deterministic run has undefined sample SD, not a zero estimate of stochastic variation.

A relative optimality gap is `100*(J_found-J_exact)/abs(J_exact)` percent, only when an exact optimum is available. Compare scores under identical inputs and objectives. The baseline optimum cannot certify results for a changed network instance.

## 13. The exact optimum and a check you can explain

One optimal mapping found by the current enumeration is:

- A: S03, S08.
- B: S01, S02, S05, S10.
- C: S04, S06, S11.
- D: S07, S09, S12.

The server busy times are approximately A=18.333333 s, B=18.5 s, C=16.911765 s, D=18.333333 s. Therefore M=18.5 s. GPU usage is 0,0,2,1, and every individual network SLA passes.

Its metrics are C=$0.4449509803921569, E=3.684492442810458 Wh, L=49.18803418803418 ms and Lmax=76.66666666666667 ms. Substituting in J gives 0.7151543595803707.

The reference mapping's larger J=0.856845... is a separate arithmetic check. Keep these two mappings and their roles distinct in an oral exam.

## 14. Sensitivity experiments

With seeds fixed and patience off, mean local-search J at budgets 20,50,100,200 was respectively 0.759725669, 0.757385980, 0.740063501 and 0.732769681. At 20,000 it was 0.716237309. These figures support improved quality with more search on this instance; not every added evaluation must improve a score, and improvements need not be smooth.

For an uninterrupted run the incumbent is monotone non-increasing. Across separately configured runs, different stopping rules or other changed parameters require care before asserting a nesting relationship.

Changing ONLY RTT to 1.25 times its baseline produced mean J=0.763719590 at the main budget. Changing ONLY bandwidth to 0.70 times baseline produced mean J=0.735165882. These are separately reoptimized scenarios, not just re-scoring the original assignment. The study does not run a new exact enumeration for every sensitivity variant, so their heuristic scores are not certified variant optima.

For a fixed host/service pair, a 30% bandwidth reduction makes the transfer component 1/0.70 = 1.428571 times as large, not merely 30% larger. RTT itself is unchanged in that experiment. Increased latency can both increase the objective and remove host options through hard SLA failures.

These experiments vary budget and network conditions. They do not establish an optimal alpha, statistical significance, performance on larger datasets, or superiority over other metaheuristics.

## 15. Complexity and scalability

For generalized n services and m servers, a full evaluator pass is O(n+m), summing service demands and checking servers. The implementation is currently fixed to n=12,m=4, so this general expression explains how a generalized version would scale.

One move/swap neighborhood has O(nm+n^2) proposals. Full evaluation of every proposal gives approximately O((nm+n^2)(n+m)) work per sweep, plus copying and bookkeeping. More than one improving sweep can occur per start. Construction adds sorting, compatibility calculations, RCL selection and partial resource checks; it is not free.

Exact has O(m^n(n+m)) evaluation work. Exponential growth is why it is useful as a reference here but unsuitable as the only strategy for much larger instances.

The heuristic's full-evaluation portion is budget-bounded, but total time includes construction attempts and scans/checks. Its BitSet for distinct vectors can occupy roughly m^n bits when high codes are visited: about 2 MiB for 4^12, before object overhead. This is not constant-memory tracking and does not scale well to a much larger raw space. Trace/event storage adds memory.

The exact loop avoids storing all vectors, keeping only its working state, best and trace. The generalized code would also need to revisit the current exact int-sized search-space conversion and the BitSet int index casts. Merely adding CSV rows does not generalize the current benchmark: data validation and evaluator loops explicitly require twelve services and four servers.

## 16. Code responsibilities to know

- `src/optimizer/brain/ProblemData.java`: records, input validation, benchmark defaults and numerical reference mapping.
- `Candidate.java`: integer-vector representation, defensive copying and base-m candidate identifier.
- `Evaluator.java`: compatibility, full feasibility, metrics, objective and construction ranking estimate.
- `Optimizer.java`: Random, Exact, construction, descent, best tracking, counters and stopping logic.
- `SearchConfig.java`: seed, evaluation budget, batch, patience and alpha-like greediness parameter.
- `SearchResult.java`: best result, metrics, trace, stop reason and completed-enumeration certificate checks.
- `CsvFiles.java`: CSV input and result/input-snapshot export.
- `src/optimizer/experiments/ExperimentRunner.java`: exact reference and paired multi-seed comparison, summary statistics and gaps.
- `AssessmentExperiments.java`: budget and network sensitivity runs.
- `tests/`: regression checks for formulas, feasibility, exact results, reproducibility and execution control.

`Main.java` selects and connects these pieces. The algorithm does not need knowledge of screen widgets to make optimization decisions.

## 17. Review findings and limitations

No mismatch was found between the evaluator equations, full exact enumeration and the fresh reproduced benchmark scores. This is evidence of internal consistency, not proof that every possible input or requirement is handled correctly. The original PDF was not independently rechecked in this review.

Points to acknowledge honestly:

1. The name “greediness” is potentially misleading: increasing it widens random choice. The every-fifth construction override is important to disclose.
2. Construction's isolated contribution is an approximation to global impact. It does not compute the actual marginal makespan/energy change.
3. Best-improvement scans have a fixed service/host order. When a tiny budget cuts a scan short, earlier move proposals are favored and swaps may not be examined at all.
4. Strict descent cannot cross an infeasible region, plateau or worsening intermediate state. Independent reconstruction is its only escape mechanism.
5. Equal evaluation counts are a quality-comparison control, not a claim of equal time or identical per-evaluation work. Exact uses a faster allocation path.
6. Only one small fixed instance and ten seeds were tested. There is no formal significance test or broad cross-instance comparison. The “confidence” field is a textual description, not a statistical confidence interval.
7. `ExperimentRunner.study` accepts an `equalBudget` argument but constructs actual runs using `settings.budget()`. Current callers pass the same value, so present results are consistent; future callers should not treat that argument as an independent budget override.
8. The zero optimistic bound is trivial. It does not supply a useful heuristic optimality certificate.
9. Current report images/raw paths referring to `results/assessment-study` are absent after the requested cleanup. Reproduce results rather than claiming deleted raw evidence is still present.
10. The simulation assumes static demand, fixed speed/bandwidth/RTT, sequential per-server compute, reservation-based power and no idle energy after a server finishes. It excludes contention, failures, migration, replicas, task dependencies, queueing and real deployment. Real systems would require additional modeling and validation.

Possible future improvements include marginal-cost construction, backtracking/repair, incremental neighbor evaluation, adaptive alpha, randomized neighborhood order, plateau handling or other escape strategies, larger/multiple instances, formal statistical comparisons, and a MILP/constraint solver reference. These are proposals, not implemented features.

## 18. Likely teacher questions and direct answers

**What exactly is optimized?** The service-to-server assignment, to minimize fixed scalar J while satisfying every resource and individual network constraint.

**Why not choose the fastest server for everything?** It can exceed capacities and GPU/service limits, violate RTT-based SLAs, and increase monetary or energy cost.

**Why is this a discrete problem?** Assignments select indivisible hosts, not continuously variable fractions. The implementation uses integer vectors and finite neighborhoods.

**Is it NP-hard?** The generalized family contains difficult assignment/scheduling problems; for example, with nonbinding extra constraints and makespan as the objective, it contains multiprocessor scheduling. Do not call the fixed 12-service instance itself an asymptotic complexity class. Its finite space is fully enumerable.

**Why GRASP-style?** Each iteration constructs a solution using a greedy randomized RCL adapted to remaining capacity, then applies local improvement. The implementation adds independent restarts and periodic full-RCL diversification.

**Is it purely greedy?** No. Host selection is randomized within an RCL, service priority is perturbed, and every fifth construction broadens selection fully.

**Does alpha=1 mean uniform random over complete feasible placements?** No. It means uniform selection among currently available hosts at each construction step. Ordering and sequential capacity restrictions bias the distribution of final placements. This differs from the Random baseline's uniform sampling over all complete vectors.

**Why can a construction fail if a solution exists?** Earlier irrevocable assignments can block later services. The algorithm retries another construction rather than proving infeasibility.

**Why swaps?** They allow resource exchanges where moving either service alone may violate a capacity.

**First improvement or best improvement?** Best strict feasible improvement among the neighbors inspected. With no interruption, all eligible moves/swaps are scanned before acceptance.

**Can it accept a worse solution?** A local descent step cannot. A new restart can begin worse than the previous local solution, while the global best remains preserved.

**Does it find the global optimum?** Sometimes. In the repeated benchmark it matched the exact minimum in 1 of 10 runs. It offers no finite-budget global guarantee; completed Exact supplies the certificate.

**What proves a local optimum?** A completed move-and-swap scan with no strict feasible improvement, under the configured numerical tolerance.

**What proves infeasibility?** A failed necessary feasibility condition, or complete exact enumeration with no feasible vector. Heuristic failure alone does not.

**Are infeasible candidates repaired or penalized?** Neighbor infeasibility is rejected with infinite objective. Failed partial constructions are discarded. No repair routine or tuned finite penalty is implemented.

**Does checking a duplicate waste budget?** It still consumes one evaluation. Duplicate tracking measures unique coverage rather than filtering duplicates.

**Why multiple seeds?** The method is stochastic. Several seeds reveal variation and reliability that a selected best run would hide. The same seed and inputs/settings reproduce the search trajectory, apart from externally timed cancellation; runtime need not match.

**Why use Random?** It is a simple independent baseline showing whether guided construction/descent adds value over uninformed sampling. It is not claimed to be the strongest competitor.

**Why run Exact only once?** It is deterministic; repeating the same instance does not add independent solution-quality evidence. Timing benchmarking would require a separate repeated protocol.

**What does a lower J mean?** A better weighted compromise under fixed weights/scales. It does not mean every raw component decreased or that J is a probability.

**Does a passing average latency imply every SLA passes?** No. Each service is checked individually, in addition to minimizing mean latency.

**Does a GPU make work faster in your equations?** GPU availability restricts assignment. Processing speed is the server's fixed Speed field; there is no separate GPU acceleration multiplier.

**Is RAM affecting compute time?** It is a feasibility reservation, not a throughput factor in this model. CPU reservation affects power and feasibility; service compute time directly uses work/speed.

**Does the exact result validate a changed dataset?** No. Changing inputs, constraints, weights or scales changes the optimization problem and invalidates reuse of an old optimality certificate.

**What is the main empirical conclusion?** For this dataset and tested settings, local search produced better and less variable objective scores than uniform Random at equal evaluation caps. Exact establishes the benchmark minimum. Generalization needs broader experiments.

## 19. How to reproduce without UI

From `optimization/`:

```sh
./build.sh
./run.sh --self-test
./run.sh --cli --method LOCAL_SEARCH --seed 2026 --budget 20000 --patience 0 --out /tmp/placement-local
./run.sh --cli --method RANDOM --seed 2026 --budget 20000 --out /tmp/placement-random
./run.sh --cli --method EXACT --out /tmp/placement-exact
./run.sh --study 10 --seed 2026 --budget 20000 --out /tmp/placement-study
```

Use patience=0 when explaining an equal-budget local-search comparison. Check exported input snapshots, settings, stop reason, feasibility and complete-enumeration status before interpreting scores. The fresh review study was written to `/tmp/placement-method-review` to avoid adding generated runs back into the cleaned repository; temporary files may later disappear.

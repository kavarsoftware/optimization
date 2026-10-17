# Speaking guide for Dr. Godar

Use `Presentation.html` in a browser for the classroom slides. This file is the speaking script. Aim for about 8–10 minutes, including the Java demo.

## 1. The problem (45 seconds)

“Our project assigns twelve services to four servers. Each server has CPU, RAM, storage and latency. Every service has resource demands and its own latency. We want the smallest average service-plus-server delay while respecting all resource limits.”

Show the Model and data tab. Explain that capacities and measured delays are inputs. The optimizer chooses host IDs. The default two-services-per-server rule comes from the supplied code and is configurable.

## 2. Formulation (one minute)

“Following Weeks 0 and 1, we defined the model before choosing a method. A candidate is a twelve-entry array. Each entry selects one of four servers. The objective is average delay. Constraints check CPU, RAM, storage and minimum counts. A fast placement is rejected if it breaks any mandatory rule.”

Write `D = sum(service latency + assigned server latency) / 12`. Mention that service latency is in milliseconds, not an importance weight. Each service contributes equally to the average because no request frequencies were supplied.

## 3. Search space (45 seconds)

“Each of the twelve services has four choices. That gives 4^12, or 16,777,216 complete assignments before constraints. One million is our maximum evaluation budget. It is not the total number of solutions. We seek a good feasible answer by examining a small part of the space.”

## 4. The method (two minutes)

“We use GRASP, combining randomized greedy construction with local improvement. First we place services that have fewer compatible hosts. At each step we consider remaining resources and choose randomly from a list of good hosts. Every fifth attempt allows more diverse feasible choices.”

“Then we inspect two neighborhoods. A move changes one service's host. A swap exchanges two services' hosts. With twelve services, these give at most 36 moves and 66 swaps. We accept the best feasible strict improvement. When no improvement remains, we create a new starting placement.”

“A restart helps leave a local optimum. It does not prove that we have reached the global optimum.”

## 5. Live Java demo (two minutes)

1. Select the default hybrid and service latency objective, seed 2026, minimum two services per host.
2. Press Run optimizer. The supplied instance is small, so this can finish quickly.
3. Point to S4's RAM: 48 of 48 GB. Equality satisfies the capacity rule.
4. Explain the placement and 27.75 ms average delay.
5. Point to the chart and best-found label. The score first appeared at evaluation 114. The run continued to check for later improvements.
6. Explain that three stagnant batches caused a stop at 40,000 evaluations.
7. Point to 0.171% distinct coverage. Evaluations and unique candidates are different quantities.

## 6. Evidence (one minute)

“We compared three methods using seeds 2026 through 2045. Every run used 50,000 evaluations, and all methods shared the same evaluator. The hybrid's mean delay was 27.75 ms. Uniform random search averaged 29.1875 ms. Randomized construction alone also reached 27.75 ms in all twenty runs.”

“This means the data support our approach over uniform random sampling on this instance. They do not show that the local search phase always improves the final quality, and they do not prove that the method is best for every problem.”

Open the Experiments tab or show the saved summary. Keep runtime claims tied to the measured machine and explain construction overhead.

## 7. Trade-offs and conclusion (one minute)

“The lectures also explain conflicting objectives. Our optional cost/delay experiment changes a normalized priority weight and displays sampled nondominated results. Its prices are illustrative because actual costs were not supplied. The default project stays with the requested latency objective.”

“Our evidence is a feasible placement, a clear objective, reproducible seeds, repeated comparisons, and checked evaluation logic. We report the best answer found within the budget without a global certificate.”

## Questions you should be ready to answer

**Why is the number of possibilities larger than one million?**

There are four independent host choices for each of twelve services: 4^12. One million is the cap on evaluations. Feasibility rules reduce the acceptable subset.

**Is 27.75 ms the exact optimum?**

The search does not certify that. It is the best found in our recorded runs. The optimistic lower bound of 10.25 ms ignores constraints and is weak.

**What does heuristic mean? What makes GRASP a metaheuristic?**

A heuristic uses useful problem rules, such as placing restricted services early. A metaheuristic coordinates broader exploration. GRASP repeatedly builds randomized greedy starts and improves them through local search.

**Why not stop after one batch with no change?**

A batch may miss useful starts. Three stagnant batches are a configurable compromise between time and search effort. Any finite patience can stop before a better candidate is found.

**How do you avoid a local optimum?**

We restart from new randomized constructions. Swaps also reach some improvements that individual moves cannot. There is still no guarantee of escaping every difficult region.

**Why can service latency stay in the objective if it is constant?**

It expresses total modeled delay and preserves units. It adds 5.25 ms to every placement, so placement rankings depend on assigned server delays. Host-dependent execution times would need richer input data.

**Why not put everything on S4?**

CPU, RAM and storage would overload, and the other hosts would violate the count rule. Such a candidate is infeasible even if its raw delay looks attractive.

**Do two services on every host guarantee reliability?**

No. This is a distribution rule from the old code. Real reliability constraints need failure probabilities, replicas and failure-domain requirements.

**What is deterministic and what is stochastic here?**

The evaluator is deterministic. The search makes seeded random choices. Same inputs, version, parameters and seed reproduce the search result and counts. Runtime varies.

**What is the difference between verification and validation?**

Verification checks that Java matches our equations. Validation checks whether those equations and inputs represent the real deployment. Automated checks help with the first; measured data and stakeholder review help with the second.

**Does a 50,000-evaluation comparison guarantee equal running time?**

No. Construction may perform many partial checks before producing a complete candidate. We report those counters and wall time separately. Equal evaluator budgets make one aspect comparable.

**Why keep construction-only if it ties the hybrid?**

It is an ablation. The tie is useful evidence that greedy construction is already strong for this simple latency objective. The project should explain this instead of hiding it.

**Did you implement genetic algorithms, simulated annealing and particle swarm too?**

No. Week 1 names them in the semester map as later topics. Our project explains their relationship, uses one clear approximate method, and compares it with meaningful baselines.

**Can you explain the evaluator by hand?**

Use the three-candidate two-server example in REPORT §8 or the Lecture guide tab. Show why A is 12 ms, B is 17 ms, and C is infeasible.

**How does the Java structure follow the lectures?**

Show `ProblemData`, `Candidate`, `Evaluation`, `Evaluator`, `SearchMethod` and `ExperimentRunner`. The problem model stays fixed when the search method changes.

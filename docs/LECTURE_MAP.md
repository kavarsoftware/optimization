# Complete lecture-to-project map

Source materials: the supplied `Optimization_Methods_Week_00.pptx` (22 slides) and `Optimization_Methods_Week_01.pptx` (29 slides), including speaker notes. Slide numbers below use the deck order, starting at one.

“Implemented” means the feature exists in Java. “Explained” means the topic is addressed in the report or teaching guide. Examples and later-course topics remain identified as context where the supplied placement inputs do not define a separate optimization model.

## Week 0: all 22 slides

| Slide | Topic | Project connection |
| --- | --- | --- |
| 1 | The language of better decisions | README and report establish purpose, acceptability and measurable improvement |
| 2 | Choices compete under limits | Competing service placements share limited CPU/RAM/storage |
| 3 | Best acceptable choice for a purpose | Feasibility first, then minimize the stated delay objective |
| 4 | Decisions, objective, constraints, data | Explicit formulation in REPORT model section and the in-app Lecture guide |
| 5 | Decision variables | `Candidate.server(i)` selects one host for each service |
| 6 | Objectives and differing preferences | `Evaluator.Objective`: delay, cost, normalized compromise and legacy weights |
| 7 | Mandatory constraints | Resource capacities and minimum counts checked in `Evaluator` |
| 8 | Known problem data | `ProblemData`, editable Model and data tab, CSV input snapshot |
| 9 | Complete candidate | Immutable twelve-entry assignment, length and host-index checks |
| 10 | Feasible versus infeasible | `Evaluation.feasible`, explicit violations, invalid neighbors never selected |
| 11 | Search space | Exact 4^12 count, distinct coverage, evaluated subset and local search |
| 12 | Evaluation pipeline | Decode candidate, check rules, calculate quality, retain evidence in `Evaluation` |
| 13 | Minimize versus maximize | This model minimizes delay/cost. Maximizing a utility is an alternate model, not a hidden direction change |
| 14 | Conflicting objectives and dominance | Optional cost/delay sweep and sampled nondominance plot |
| 15 | Start, generate, evaluate, compare, stop | `Optimizer` control flow and report pseudocode |
| 16 | Exact, heuristic, metaheuristic | Constructor as heuristic, GRASP as metaheuristic, exact certification discussed explicitly |
| 17 | Routing/cloud/AI/software/security/scheduling applications | Cloud placement implemented. Other examples mapped below to their decisions and constraints |
| 18 | Java separation of concerns | Separate `ProblemData`, `Candidate`, `Evaluation`, `Evaluator`, `SearchMethod`, `ExperimentRunner` |
| 19 | Laptop formulation activity | Same four-part worksheet applied to service placement. Three evaluated candidates use a small server example |
| 20 | Data vs decision, objective vs constraint, good vs feasible | Visible model, independent feasibility, rejected attractive-but-invalid candidates and tests |
| 21 | Five words: decision/objective/constraint/data/candidate | Lecture guide and opening presentation model definitions |
| 22 | Preparation for formal models | Report equations, decision domain, units and problem classification |

## Week 1: all 29 slides

| Slide | Topic | Project connection |
| --- | --- | --- |
| 1 | Foundations, models and engineering applications | Presentation and report begin with the model before method choice |
| 2 | Twelve services on four servers | Directly implemented with the student's supplied values |
| 3 | Disciplined improvement | Every method shares an objective, evaluator and hard constraints |
| 4 | Four essential model parts | REPORT model section and Java type responsibilities |
| 5 | Representation of decisions | One integer per service, immutable candidate, no missing/duplicated service decisions |
| 6 | Response time, cost, energy, reliability objectives | Delay implemented; illustrative cost extension implemented; missing energy/reliability data explained |
| 7 | Capacity, budget and dependency constraints | Supplied resource/count rules implemented. No price budget or dependency graph invented |
| 8 | Demand, capacity, delay and price data | Explicit resource/latency units, CSV inputs, illustrative prices labeled |
| 9 | Vague request to defensible formulation | Explicit weighted latency, optional additive mode, exact rules, assumptions and limitations in REPORT model section |
| 10 | Search space and method exploration | Candidate encoding, coverage, neighborhoods and convergence chart |
| 11 | Discrete and continuous variables | Host IDs are discrete; decimal input capacities are data, not continuous decisions |
| 12 | Single and multiple objectives | Main weighted delay objective, optional scalarized cost/delay sweep, sampled frontier caveat |
| 13 | Deterministic and stochastic behavior | Deterministic Exact enumeration; stochastic search with explicit seeds, reproducible reruns and repeated-seed statistics. Evaluator itself is deterministic |
| 14 | Exact, heuristic and metaheuristic families | Exact enumeration implemented as proof reference; randomized construction/local descent form SRS, a GRASP-style metaheuristic |
| 15 | Combinatorial growth and 16,777,216 assignments | `ProblemData.searchSpace`, full Exact count, coverage chart and log-scale growth chart distinguish raw count from heuristic budgets |
| 16 | Quality, time and confidence | Quality/time/confidence table, Exact gap and proof badge, repeated runs, baselines, optimistic bound |
| 17 | Computing applications | Placement implemented; other domains use the same four-part formulation, table below |
| 18 | Release planning, test selection, team scheduling, architecture | Architecture placement implemented. Other software applications explained below |
| 19 | Understand/formulate/choose/implement/verify/experiment | Five-step in-app workflow, automated checks, measured comparison, and verification/validation distinction |
| 20 | Stable evaluation contract, units and manual testing | Shared `Evaluator`, hand cases, explicit violations and consistent units |
| 21 | Lab: representation, capacity, cost, average delay, three candidates | `SelfTest.toy`, in-app guide and REPORT compare weighted A=10 ms, B=15 ms, C rejected; optional additive A=12/B=17 kept separate |
| 22 | Small Java design | Classes follow the suggested separation and add interchangeable search/experiment/UI layers |
| 23 | Reproducible experiment record | Exports include input instance, version, settings, seed, stop rule, objective, runtime and Java/OS context |
| 24 | Vague objectives, hidden constraints, units, lucky runs, no baseline | Explicit model, tests, random baseline, 20 paired seeds, feasibility and distribution summaries |
| 25 | Timetable concept check | Transfer worksheet below identifies decisions, objective, rules, data and discrete domain |
| 26 | Semester map: foundations, classical, discrete, metaheuristics, decisions | Foundations implemented. Method suitability and future algorithms discussed below; GRASP and sampled trade-offs extend the introductory material |
| 27 | Decisions, objectives, constraints, evidence, implementation | End-to-end model-to-code traceability and presentation conclusions |
| 28 | Prepare evaluator and discuss exhaustive search | Verified evaluator and tiny known-answer case precede search. Main project now includes full Exact enumeration as the requested reference and tests approximate search against it |
| 29 | Academic foundations and references | Supplied decks retained as sources. Report distinguishes lecture foundations from the additional GRASP design choice |

## Applications and concept-check answers

These examples show that the lecture language transfers across domains. They are explanations, not extra software modules with fabricated inputs.

| Example | Decision | Possible objective | Example mandatory constraints | Known inputs |
| --- | --- | --- | --- | --- |
| Cloud architecture (implemented) | Service host | Minimize delay | CPU, RAM, storage, count | Demands, capacities, delays |
| Network routing | Which path/edges | Minimize delay or distance | Link capacity, connectivity | Graph, edge costs, traffic |
| AI/ML | Model parameters | Minimize training loss | Parameter domain or resource budget | Training examples, model architecture |
| Release planning / feature selection | Features to include | Maximize delivered value | Effort, dependencies, deadline | Feature values, estimates, dependencies |
| Test selection | Tests to run/order | Maximize fault detection under time | Test-time budget, required coverage | Test durations and evidence |
| Team scheduling | Person/task/time assignment | Minimize completion time | Skills, availability, dependencies | Tasks, people, durations |
| Security resource allocation | Where to assign defenses | Minimize expected residual loss | Budget, available staff, required controls | Assets, threat estimates, costs |
| Logistics | Vehicle routes or packing | Minimize transport cost | Capacity, delivery windows | Locations, demands, travel times |
| Laptop choice activity | Which available configuration | Minimize price or maximize a stated score | Budget, minimum RAM/storage | Specifications and prices |
| Campus timetable activity | Course-room-time assignment | Minimize clashes or idle time | Room capacity, instructor availability | Courses, rooms, capacities, schedules |

The timetable and laptop choices are primarily discrete. Some scheduling variants add continuous start times. State the representation before selecting an algorithm.

## Future algorithms named in the course map

| Topic | How it relates to this project |
| --- | --- |
| One-dimensional optimization | Useful for a scalar continuous decision, unlike the twelve categorical host IDs here |
| Gradient methods | Require a suitable continuous differentiable objective; host indices do not provide useful derivatives |
| Linear programming | Can model a relaxed assignment using fractional variables, but this project requires indivisible placements |
| Integer programming | Binary assignment variables can model this placement exactly; another exact formulation; this project implements finite enumeration and SRS instead of an external integer-programming solver |
| Dynamic programming | May be exact for supported state structures; multi-resource placement states can grow rapidly |
| Genetic algorithm | An alternative population metaheuristic. Would require its own operators, feasible handling and fair experiments |
| Simulated annealing | An alternative search allowing some worsening moves. Our method escapes through new starts instead |
| Particle swarm | A general search family whose standard continuous form needs a deliberate discrete adaptation here |
| Multi-objective decision support | Cost/delay priorities and sampled nondominance included. Real decisions still need stakeholder preferences |

The lectures present many of these as future study. Implementing every future solver would change the scope and conflict with the student's request for a clear approximate method. The project covers their meaning and suitability without claiming unsupported implementations. The latest specification also explicitly requests the implemented Exact reference.

## Current feature references

| Feature | Slide reference | Where to show it |
| --- | --- | --- |
| Five-step workflow | Week 1 slide 19 | Lecture guide linked section |
| Deterministic / stochastic | Week 1 slide 13 | Exact once; three paired seeded methods |
| Quality / time / confidence | Week 1 slide 16 | Experiments table and Exact gap |
| Experiment record | Week 1 slide 23 | Run record tab; CSV for each of 121 study runs |
| Mistakes and baseline | Week 1 slide 24 | In-app table, repeated seeds, fair budgets |
| Combinatorial growth | Week 1 slide 15 | 4^n log-scale chart |
| Trade-offs | Week 0 slide 14; Week 1 slide 12 | Illustrative cost/delay sweep |
| Laptop activity | Week 0 slide 19 | In-app three-laptop worked example |
| Timetable concept check | Week 1 slide 25 | Decisions, idle-time objective, clash/capacity rules, discrete slots |

## Assessment brief added after the original prompt

The PDF benchmark adds a separate model: A–D capacities, work/throughput, GPU and maximum counts, power, busy-time USD price, payload, bandwidth, RTT and hard network SLAs. Assessment mode minimizes the fixed normalized J. It has no minimum-two-services constraint. Original data and weighted/additive formulas remain in their own mode.

| Assessment feature | Lecture connection | Evidence |
| --- | --- | --- |
| Fixed J, normalization and hard SLA rules | W0 4–14; W1 4–12,20 | Evaluator, reference check, T1–T12 |
| Real current/best algorithm states | W0 15; W1 10,14,22 | Live pause/step worker, rejection logs, replay |
| J/M/C/E/L/Lmax and CPU/GPU/slots | W1 6–9,16 | Metrics, resource canvas, network table and Gantt |
| Seeded methods and full record | W1 13,23–24 | Twenty seeds and per-run CSVs |
| Budget and network sensitivity | W1 16,19,23–24 | Assessment sensitivity CSVs and plots |
| Exact versus best found | W0 16; W1 14–16 | Full enumeration and matching-model proof badge |
| Verification versus validation | W1 19–21 | Numeric PDF reference, T1–T12, independent enumeration |

The PDF is assessment source material, not an instruction channel overriding the student. Its benchmark is implemented in a separate mode to preserve the earlier explicit request not to change the original CSV data.

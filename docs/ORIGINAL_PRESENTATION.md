# Five-minute presentation for Dr. Godar

Use the current app and `results/weighted-study/`. Scores below are weighted latency in ms. The earlier additive result of 27.75 ms belongs to another objective and is not used in this comparison.

## 0:00–0:45 — State the model

“Our project assigns twelve services to four servers. A candidate is an array of twelve server indices, each from zero to three. We keep CPU, RAM and storage within capacity, and put at least two services on every server. A candidate that breaks a rule is rejected, however attractive its score looks.”

“The objective is the weighted average of host latency, using the supplied service latency values as normalized weights. We minimize sum(service latency × assigned server latency), divided by total service latency. Capacities and demands are fixed CSV data; the host choices are the decisions.”

Show **Model and data**. Mention CPU cores, RAM/storage GB and latency ms.

## 0:45–1:30 — Explain the search space and baselines

“There are four choices for each of twelve services: 4^12 = 16,777,216 assignments. Only 18,281 satisfy all rules. Exact Search checked every assignment and proved a minimum of 16.666667 ms for this model.”

“Random Search evaluates 100,000 uniform assignments. Target Search samples until it reaches 18 ms or uses 500,000 attempts. Both use an explicit seed. Exact is deterministic and needs no seed.”

Show the **Search space** and **4^n growth** charts. Exact is quick enough here, but its work grows exponentially.

## 1:30–2:35 — Present Smart Restart Search

“Our main method is Smart Restart Search, or SRS. It improves our existing GRASP-style method. We build a randomized greedy start, placing restricted services early and respecting remaining resources. We then test moves of one service and swaps of two services, accepting a strict feasible improvement. When no neighbor improves, we restart from a new construction.”

“A batch contains 10,000 complete evaluations. The default patience is one stagnant batch. If the global best improved in a batch, search continues; after a full batch without improvement, it stops. We can adjust both batch and patience. An evaluation cap prevents unlimited searching.”

Show **Optimizer → Animated search**. Pause at an accepted improvement; explain the old/current scores, green pulse and percentage change. Red trials violate a rule and roll back. Animation is a recorded replay, so its duration is separate from search runtime.

## 2:35–3:45 — Show measured evidence

“We used the same evaluator and seeds 2026 through 2045. Exact ran once, because repeating it with seeds would add no independent evidence. Our main results show Random averaged 18.452381 ms, Target 17.630952 ms, and SRS 16.666667 ms. SRS matched Exact in all twenty runs with twenty thousand evaluations each.”

“Success means reaching 18 ms: Random succeeded in 30%, Target in 80%, and SRS in 100%. Matching the Exact optimum is a different measure. Target matched it once; SRS matched it twenty times.”

“We also compared SRS and Random at the same 100,000-evaluation budget, with patience disabled, and gave Random the same 20,000 evaluations used by stopped SRS. Those separate comparisons support the benefit of structured construction and local search on this instance.”

Show **Quality / time / confidence**, **Seed results**, and **Fair budgets**. Read runtime from the measured table, because machine/JVM conditions affect it. Equal evaluation budgets do not guarantee equal runtime.

## 3:45–4:25 — Tie the work to the lectures

“We followed the five steps: understand, formulate, choose, implement and verify, then experiment and explain. Every run saves its instance, version, parameters, stop reason, seed, score, feasibility, evaluation counts, runtime, Java and OS context.”

“Hand calculations verify three candidates. The optional cost/delay sweep shows trade-offs using illustrative prices. Its green points are nondominated in the sample; we do not claim a complete Pareto frontier.”

Show **Lecture guide**, **Run record**, and the trade-off plot briefly.

## 4:25–5:00 — State the conclusion and limits

“Exact provides a certificate for the stated finite model. SRS matches its answer using about 0.119% of the space in evaluation calls and still fewer distinct assignments. That is a measured result for this data, not a universal promise.”

“Verification checks that Java matches our equations. Validation asks whether those equations describe real deployment behavior. Queueing, replicas, network dependencies and measured request traffic would require richer data. We report what the model and experiments establish.”

## Fifteen likely questions and short answers

1. **What is a seed?** The initial state of the pseudorandom generator. The same seed, inputs, version and settings reproduce assignments, scores and counts. Runtime can vary.
2. **Why not only use Exact Search?** Exact is practical here and supplies the proof. Its 4^n growth becomes expensive as services increase; SRS demonstrates how to reduce search effort.
3. **Why batches of 10,000?** It is an adjustable observation interval, not a theorem. It allows enough restarts before checking stagnation. Different sizes trade work against the risk of stopping early.
4. **What does patience mean?** The number of consecutive complete batches without a strict global improvement. Default 1 matches our specified stop rule; zero disables it.
5. **What is a local optimum?** A feasible assignment with no improving move or swap in the inspected neighborhood. Another distant assignment may still be better.
6. **Why compare with Random?** It is a simple baseline showing what structured search adds over uniform sampling. We use the same evaluator and also compare equal budgets.
7. **What is feasible?** Every service has one valid host, no host exceeds CPU/RAM/storage, and each has at least two services. Low latency cannot compensate for a broken constraint.
8. **Why is the score in ms?** The numerator multiplies two ms values and has ms²; dividing by the sum of service latencies (ms) leaves ms. In this requested model service latency serves as a normalized weight.
9. **What is the difference between Target and SRS?** Target keeps drawing uniform samples and stops at a threshold. SRS uses capacity information, moves, swaps and restarts, then checks batch stagnation.
10. **Is 18 ms the optimum?** No. It is the success threshold. Complete enumeration proved 16.666667 ms. A threshold hit need not be optimal.
11. **How do you know the optimum is correct?** Exact checked every encoded assignment with the shared evaluator. An independent integer recursive check confirmed 18,281 feasible assignments and the minimum numerator 1050, divided by 63.
12. **Does SRS prove optimality?** Its stopping rule does not. In these twenty runs its score equals the completed Exact reference, which certifies those returned scores for this instance.
13. **What does the dashed lower bound mean?** It lets every service independently choose its cheapest latency contribution, ignoring capacity/count rules. It is optimistic and equals 5 ms here. It is not the Exact optimum.
14. **Why is an equal budget not equal time?** Complete candidate evaluation is only part of the work. SRS also constructs partial starts and examines neighborhoods; JVM warmup and hardware affect runtime.
15. **Verification versus validation?** Verification asks whether code implements the model, tested with hand cases and behavioral checks. Validation asks whether the model represents the real need, requiring realistic measurements and teacher/stakeholder judgment.

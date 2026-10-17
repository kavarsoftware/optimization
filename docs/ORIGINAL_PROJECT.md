# Server Placement Lab — Java 17

An improved version of the existing Swing project. The supplied `data/servers.csv` and `data/services.csv` are unchanged. No external Java libraries, Maven, Gradle or network connection are required.

## Run and build

```sh
java -jar ServerPlacementLab.jar
# Linux/macOS alternatives
./run.sh
./build.sh
./run.sh --self-test
```

On Windows, use `run.bat` / `build.bat`. In IntelliJ: open this folder, select JDK 17+, mark `src` as Sources Root, and run `optimizer.Main`. The JAR reads the CSVs beside the project/JAR; if run alone without those files, it uses the identical built-in values. Linux high-DPI scaling is automatic; `--ui-scale 1` or `--ui-scale 2` overrides it.

## Model

A candidate is an `int[12]` inside `Candidate`: each value is a server index 0..3. There are **4^12 = 16,777,216 assignments**. Every server must respect CPU (cores), RAM/storage (GB), and the default minimum of two services.

The main objective follows the latest specification:

`weighted latency = sum(serviceLatency[i] * serverLatency[host[i]]) / sum(serviceLatency[i])`.

Its unit is ms, and lower is better. Service latencies act as normalized weights in this formula. The earlier service-plus-server additive score remains optional. Different objective modes have separate records and are never pooled in a study. The cost/delay sweep uses explicitly illustrative CPU prices.

## Four methods

| Method | Main stopping rule |
| --- | --- |
| Exact | Enumerate all assignments once; completion proves the optimum for this model |
| Random | Exactly 100,000 uniform random assignments |
| Target | First feasible score <= 18 ms, or 500,000 random attempts |
| **Smart Restart Search (SRS)** | Capacity-aware randomized greedy starts + move/swap improvement + restarts; stop after one complete stagnant batch by default |

SRS is our name for the existing GRASP-style approach. Default seed: 2026; batch: 10,000 evaluations; patience: 1; cap: 1,000,000. Batch/patience/cap are adjustable. Patience 0 disables early stopping. Construction-only remains an extra ablation. Failed partial constructions are counted separately; only complete candidate evaluations consume the evaluation budget. Every repeat and infeasible trial counts.

## Demonstrate the app

1. **Model and data:** inspect the unchanged inputs and units.
2. **Optimizer:** run SRS; pause, Step or Replay actual recorded moves/swaps. The previous/current score readout pulses on a change. **Compare → Previous run** compares consecutive completed runs within the app session.
3. Run **Exact Search** to obtain a matching proof reference. The **Global optimum found?** badge says YES only when the displayed final score matches a completed Exact result for the same model.
4. **Experiments → Run seeded study:** Exact once, plus 20 paired seeds for the three stochastic methods. Separate equal-budget and SRS-stop-budget comparisons test SRS against Random fairly.
5. Inspect **Quality / time / confidence**, **20-seed statistics**, **Charts**, **Fair budgets**, and **Run records**. The charts include all-method convergence with an optimistic dashed lower bound, evaluations, mean/min/max scores, seed dots, search-space counts, and log-scale 4^n growth.
6. Run the illustrative cost/delay sweep. Its green points are nondominated within the sample.

Every desktop run and study saves CSV evidence automatically under `results/`. Single runs also save placement/convergence PNGs; studies save all six charts plus CSV chart data. **Run record** displays the full metadata. Export buttons create an additional copy. Search time excludes playback and file export. Animation replays a bounded sample after the fast calculation; counters include unshown evaluations.

## Reproduce the saved study

```sh
./run.sh --study 20 --out results/my-study
./run.sh --cli --method EXACT --out results/my-exact
./run.sh --cli --method TARGET --seed 2026 --out results/my-target
./run.sh --cli --method HYBRID --batch 10000 --patience 1 --out results/my-srs
./run.sh --cli --objective LATENCY --method HYBRID --out results/additive
./run.sh --help
```

The measured reference is `results/weighted-study/`. Exact found **18,281 feasible assignments** (0.108963% of the space) and a minimum of **16.666667 ms**. SRS matched that minimum in all 20 seeds using 20,000 evaluations per run. This verifies those results against Exact on this instance; it is not a general guarantee for SRS. See `docs/RESULTS.md` for the complete measured table and limitations.

Read `docs/PRESENTATION.md` for the five-minute script and 15 teacher questions; `docs/REPORT.md` for the model/algorithm; `docs/CODE_WALKTHROUGH.md` for the source reading order; and `docs/LECTURE_MAP.md` for every supplied lecture slide. `docs/Presentation.html` is a browser speaking aid. Earlier outputs, if retained, are explicitly archived and use different settings/objectives.

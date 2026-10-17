package optimizer;

/** A concise teaching view; the complete slide-by-slide map is in docs/LECTURE_MAP.md. */
public final class LectureGuide {
    private LectureGuide() {}
    public static String html(){return """
        <html><body style='font-family:SansSerif;color:#19273e;margin:24px'>
        <h1>Model first. Search second.</h1>
        <p>Based on the supplied Optimization Methods lectures, Weeks 0 and 1.</p>
        <h2>Week 1 slide 19: five-step workflow</h2>
        <ol><li>Understand: identify the placement need, inputs and units.</li><li>Formulate: decisions, objective and mandatory capacity/count rules.</li><li>Choose method: Exact for proof, Random for a baseline, GRASP for budgeted search.</li><li>Implement and verify: one evaluator; hand examples and automated checks.</li><li>Experiment and explain: 20 paired seeds, budgets, stopping conditions, quality, time and confidence.</li></ol>
        <h2>1. The four parts of the model</h2>
        <p><b>Decision:</b> x[i] is the selected server, 0 to 3, for service i. A candidate contains all 12 decisions.</p>
        <p><b>Objective:</b> minimize D(x) = sum(serviceLatency[i] + serverLatency[x[i]]) / 12.</p>
        <p><b>Constraints:</b> every service has exactly one server, CPU / RAM / storage capacities hold, and every server has at least the selected number of services.</p>
        <p><b>Data:</b> capacities, resource demands and latency values. Optional prices are illustrative inputs.</p>
        <h2>2. Search space and feasibility</h2>
        <p>There are 4^12 = 16,777,216 complete assignments before constraints. One million evaluations is a budget.
        It can visit at most 5.96% of that space. Repeated evaluations reduce distinct coverage further.</p>
        <p>An infeasible placement receives no selectable objective score. Low delay never compensates for an overloaded server.</p>
        <h2>3. Our method: GRASP with move and swap descent</h2>
        <ol><li>Place difficult services first, considering only hosts with remaining capacity.</li>
        <li>Randomly choose among hosts with good incremental objective values. Every fifth construction attempt uses unrestricted feasible choices.</li>
        <li>Evaluate the complete candidate with the shared evaluator.</li>
        <li>Inspect moves of one service and swaps of two services. Accept the best strict feasible improvement.</li>
        <li>When the neighborhood stops improving, construct a fresh starting point.</li>
        <li>Stop at the evaluation cap, cancellation, or the chosen number of complete batches without global improvement.</li></ol>
        <p>A 12-service neighborhood contains at most 36 moves and 66 swaps. These are local alternatives, not all complete assignments.
        Early stopping and local optimality do not certify the global optimum.</p>
        <h2>4. Evidence and reproducibility</h2>
        <p>Compare uniform random search, randomized greedy construction, and our hybrid using identical data, paired seeds and equal evaluation budgets.
        Report feasible runs, mean / median / spread, runtime, unique candidates and the stopping reason. All evaluation calls count, including repeats and invalid neighbors.</p>
        <p>For ordinary runs the default cap is 1,000,000 evaluations. A batch contains 10,000 evaluations and patience is three stagnant batches.
        Experiments disable patience to keep budgets equal. Runtime includes abandoned partial constructions, which are counted separately.</p>
        <h2>5. Trade-offs and method families</h2>
        <p>The default model has one objective. The optional cost / delay sweep uses normalized weighted sums with 11 priority settings.
        Green points are nondominated among sampled results only. Prices are examples, not measured cloud tariffs.</p>
        <p>Our placement decisions are discrete. Capacities can contain decimals but are fixed inputs. Derivative-based continuous methods require another model.
        Greedy construction is a heuristic. GRASP with repeated local search is a metaheuristic. Exact methods may certify optimality under their assumptions.</p>
        <h2>6. A hand-calculated evaluator example</h2>
        <p>Two servers have delays 5 and 20 ms, prices 0.2 and 0.1 per CPU unit, and enough capacity.
        Three services have CPU demands [2,4,1] and own delays [1,2,3] ms. Minimum one service per server.</p>
        <p>Candidate A = [S1,S2,S1]: delay = (6+22+8)/3 = 12 ms, cost = 1.0.<br>
        Candidate B = [S2,S1,S2]: delay = (21+7+23)/3 = 17 ms, cost = 1.1.<br>
        Candidate C = [S1,S1,S1]: infeasible, because S2 is empty. A beats B for both stated objectives.</p>
        <h2>7. Boundaries and interpretation</h2>
        <p>The user confirmed that service values are latencies in milliseconds. The original weighted interpretation is available for comparison only.
        The minimum-two rule comes from the supplied code and can be changed.</p>
        <p>Each service's own delay is constant across hosts, so it adds a constant to the default objective. Placement changes server delay.
        This additive model does not predict queueing, service dependencies, replication or reliability.</p>
        <p>The lectures mention budgets, reliability, energy, routing, AI and later algorithms as examples or future study.
        They are explained in the complete topic map, rather than being given invented inputs or claims in this placement model.</p>
        <p><b>Verification:</b> does the code match the equations? <b>Validation:</b> do these equations and inputs represent the real deployment need?
        The project includes automated evaluator checks. Real deployment validation needs measured data and stakeholder review.</p>
        <h2>Files for presentation</h2><p>Read README.md, docs/REPORT.md, docs/LECTURE_MAP.md and docs/PRESENTATION.md.
        Export a run to preserve its full input snapshot, seed, parameters and result.</p>
        </body></html>
        """;}
}

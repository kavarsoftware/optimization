package optimizer;

/**
 * Slide references connect each app feature to the supplied lectures, without inventing lecture
 * requirements.
 */
public final class LectureGuide {
    private LectureGuide() {}

    public static String html() {
        return """
<html><body style='font-family:SansSerif;font-size:13px;color:#19273e;margin:24px'>
<h1>Server Placement Lab: model, method, evidence</h1>
<p>References use the supplied Week 0 (22 slides) and Week 1 (29 slides), numbered from 1.</p>
<p><a href='#workflow'>Workflow</a> | <a href='#model'>Model</a> | <a href='#methods'>Methods</a> |
<a href='#evidence'>Experiments</a> | <a href='#lab'>Hand example</a> | <a href='#checks'>Concept checks</a></p>
<a name='workflow'></a><h2>Five-step workflow — Week 1, slide 19</h2>
<ol><li><b>Understand:</b> assign 12 services to 4 limited servers.</li>
<li><b>Formulate:</b> define decisions, score, hard rules, data and units.</li>
<li><b>Choose:</b> Exact reference, Random baseline, Target threshold, Smart Restart Search.</li>
<li><b>Implement and verify:</b> one evaluator; hand calculations and behavioral self-tests.</li>
<li><b>Experiment and explain:</b> 20 paired seeds, fair budgets, plots and exported run records.</li></ol>
<a name='model'></a><h2>Model and evaluation pipeline</h2>
<table border='1' cellpadding='7' cellspacing='0'>
<tr><th>Feature</th><th>Implementation / meaning</th><th>Lecture reference</th></tr>
<tr><td>Decision and candidate</td><td>int[12]; x[i] is server index 0..3. Each service has exactly one host.</td><td>W0: 4,5,9; W1: 4,5</td></tr>
<tr><td>Known data</td><td>CSV capacities, demands and latencies; CPU cores, RAM/storage GB, latency ms.</td><td>W0: 8; W1: 8</td></tr>
<tr><td>Objective / minimize</td><td>D(x) = sum(serviceLatency[i] * serverLatency[x[i]]) / sum(serviceLatency[i]). Lower is better.</td><td>W0: 6,13; W1: 6,9</td></tr>
<tr><td>Constraints</td><td>CPU/RAM/storage cannot exceed capacity; default minimum 2 services on each server.</td><td>W0: 7,10; W1: 7,20</td></tr>
<tr><td>Pipeline</td><td>Decode assignment; total resources and counts; check rules; reject invalid; compare feasible scores.</td><td>W0: 12; W1: 20,21</td></tr>
<tr><td>Search space</td><td>4^12 = 16,777,216 before constraints. Exact counts the feasible subset. Distinct visits differ from evaluation calls.</td><td>W0: 11; W1: 10,15</td></tr>
</table>
<p>The requested weighted formula uses each service latency as a normalized weight. It does not add that latency to the host delay.
The earlier additive formula remains a separate optional mode. Charts and run records identify the objective and score unit.
A model choice needs validation; a correct program does not establish that this formula predicts real response times.</p>
<a name='methods'></a><h2>Method families — Week 0 slide 16; Week 1 slides 13,14</h2>
<ul><li><b>Exact Search:</b> deterministic base-4 enumeration of every assignment. Completion proves the minimum for this finite model.</li>
<li><b>Random Search:</b> 100,000 uniform assignments. Repeats and infeasible samples count.</li>
<li><b>Target Search:</b> uniform samples until a feasible score &lt;= 18 ms or 500,000 attempts. It can stop above the optimum.</li>
<li><b>Smart Restart Search (SRS):</b> capacity-aware randomized greedy starts, best improving moves/swaps, and restarts.
It is a GRASP-style metaheuristic. A completed stagnant batch stops the default search; batch = 10,000, patience = 1.</li></ul>
<p>A move changes one host; a swap exchanges two hosts. There are at most 36 moves and 66 swaps per neighborhood.
Local optimality depends on that neighborhood and does not prove global optimality. Random restarts explore new regions.</p>
<p>Exact is practical for this small instance. Its 4^n growth motivates reducing search effort as instances grow.
The growth chart highlights n=12 on a log scale (W1 slide 15).</p>
<a name='evidence'></a><h2>Quality, time and confidence — Week 1, slide 16</h2>
<p>The Experiments tab reports the Exact optimum, percentage gap, runtime, evaluations and confidence.
The YES badge requires a matching completed Exact reference for the same inputs, rules and objective.
A heuristic that matches that score is verified for this instance, while its general method remains approximate.</p>
<h3>Fair and reproducible experiments — Week 1, slides 13,23,24</h3>
<p>Use seeds 2026..2045 for Random, Target and SRS. Exact has no seed and runs once; its reference is reused.
Report best/mean/median/worst, sample SD, target success and optimum hits. One lucky run is insufficient.</p>
<p>Main settings differ deliberately. Equal-budget results separately compare SRS and Random with patience disabled.
A third comparison gives Random the same evaluation count as each SRS stopped run.</p>
<p>Each exported run includes instance/input snapshot, version, method, parameters, stopping condition, seed (where used), score, feasibility,
evaluations, distinct coverage, runtime, Java version and OS. The Run record tab shows those fields. Playback/export time is excluded from search time.</p>
<p>Convergence, effort bars, score ranges, seed dots, search-space counts and growth are exported as PNG with CSV evidence.
The dashed optimistic lower bound ignores capacities and counts; it is weaker than an Exact certificate.</p>
<h3>Trade-offs — Week 0 slide 14; Week 1 slide 12</h3>
<p>The optional sweep combines normalized weighted latency with CPU cost at 11 priorities. Prices are illustrative units per observation period.
Green points are nondominated among sampled solutions. This does not prove a complete Pareto frontier.</p>
<a name='lab'></a><h2>Three hand-checked candidates — Week 1, slides 20,21</h2>
<p>Two hosts have 5/20 ms latency. Three services have latency values 1,2,3 ms and CPU 2,4,1 cores.
Capacities are sufficient; each host must have at least one service.</p>
<table border='1' cellpadding='7' cellspacing='0'>
<tr><th>Candidate</th><th>Weighted latency</th><th>Result</th></tr>
<tr><td>A = [S1,S2,S1]</td><td>(1*5 + 2*20 + 3*5) / 6 = 10 ms</td><td>Feasible</td></tr>
<tr><td>B = [S2,S1,S2]</td><td>(1*20 + 2*5 + 3*20) / 6 = 15 ms</td><td>Feasible, worse than A</td></tr>
<tr><td>C = [S1,S1,S1]</td><td>Raw value 5 ms</td><td>Rejected: S2 is empty</td></tr></table>
<p>For all eight assignments, the exact optimum is 7.5 ms: place the smallest weight on S2. Self-tests check this independently.
The optional additive mode gives A=12 ms and B=17 ms. Scores from these two modes are never pooled.</p>
<h3>Verification versus validation — Week 1 slide 19</h3>
<p><b>Verification:</b> does Java implement the formulas and rules? Use hand cases, reproducibility and full enumeration checks.
<b>Validation:</b> do those formulas and supplied numbers represent the real system? That needs measurements and stakeholder review.
The model omits queueing, dependencies, replicas and network routing.</p>
<a name='checks'></a><h2>Concept checks and common mistakes</h2>
<p><b>Week 0 slide 19, laptop activity:</b> choose among three stated laptops. Example data: A=8 GB RAM/256 GB storage/600 price units,
B=16/512/800, C=32/1024/1100. Minimize price, require at least 16 GB RAM and 512 GB storage, with budget 1000.
A and C fail requirements; B is the feasible choice. These are invented classroom numbers, not buying advice.</p>
<p><b>Week 1 slide 25, timetable:</b> decide course-room-time assignments. Minimize student idle time, while preventing room/instructor clashes
and respecting capacity/availability. Courses, students, rooms and available slots are data. With fixed slots the decision space is discrete.</p>
<table border='1' cellpadding='7' cellspacing='0'>
<tr><th>Week 1 slide 24 mistake</th><th>How this project avoids it</th></tr>
<tr><td>Vague objective</td><td>Explicit formula, minimize direction and units.</td></tr>
<tr><td>Hidden constraints</td><td>Visible capacities/count rule; every method uses one evaluator.</td></tr>
<tr><td>Wrong units</td><td>cores, GB and ms labels; cost and normalized scores kept separate.</td></tr>
<tr><td>Model/code mismatch</td><td>Hand-checked A/B/C and shared fast/detailed evaluation logic.</td></tr>
<tr><td>One lucky run</td><td>20 paired seeds and a dot plot, range and statistics.</td></tr>
<tr><td>No baseline</td><td>Random baseline, Exact optimum, equal-budget comparisons.</td></tr>
</table>
<p>Other lecture applications and future algorithms are explained in docs/LECTURE_MAP.md.
The lectures supply the modeling principles; SRS is our project design choice based on the standard GRASP structure.</p>
</body></html>
""";
    }
}

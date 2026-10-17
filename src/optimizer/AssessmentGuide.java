package optimizer;

public final class AssessmentGuide {
    private AssessmentGuide() {}

    public static String html() {
        return """
<html><body style='font-family:SansSerif;font-size:13px;color:#19273e;margin:22px'>
<h1>Assessment benchmark: model first, evidence second</h1>
<p>This mode implements the separately supplied Assessment_Latency brief. The original weighted CSV project remains available through its button or --original. The two datasets/objectives are never pooled.</p>
<h2>Five-step workflow — Week 1 slide 19</h2><ol><li>Understand: 12 indivisible services on A–D.</li><li>Formulate: vector decisions; resource/GPU/count/SLA rules; fixed normalized objective J.</li><li>Choose: SRS metaheuristic, Exact reference and Random baseline.</li><li>Implement and verify: shared evaluator, reference arithmetic and tests T1–T12.</li><li>Experiment and explain: paired seeds, budget and network sensitivity, documented records.</li></ol>
<h2>Decisions, objective, constraints and data — Week 0 slides 4–12; Week 1 slides 4–9</h2>
<p>Each of 12 vector entries selects one of 4 hosts. Capacities, work GI, speed GI/s, power W, busy-time price USD/s, RTT ms, bandwidth Mb/s, payload MB and per-service network SLA ms are known editable data.</p>
<p>Hard rules: CPU/RAM/disk/GPU reservations; maximum service count; one valid host per service; every network SLA. There is no minimum-two rule in this assessment mode.</p>
<p>B[j]=sum Work/Speed (seconds). M=max B. U=CPU used/capacity. Active P=Idle+(Peak-Idle)*U if busy, otherwise zero. E=sum P*B/3600 Wh. C=sum Price*B USD.</p>
<p>Network latency=RTT+1000*8*payloadMB/bandwidthMbPerSecond (ms). L is its mean; Lmax its maximum. CPU processing and waiting time are excluded from the network SLA.</p>
<p><b>Minimize J=0.35*M/30 + 0.20*C/0.50 + 0.15*E/5 + 0.30*L/70.</b> Weights and normalizers are fixed. The normalizers are not hard limits.</p>
<h2>Search and confidence — Week 0 slides 15–16; Week 1 slides 13–16</h2>
<p>Smart Restart Search uses capacity/GPU/SLA-aware starts, move/swap improvement and restarts. Greediness, batch size and patience are configurable. Failed partial starts are rejected; no invalid candidate is accepted. Exact enumerates all 4^12 possibilities and can certify a matching result. A search failure is not an infeasibility proof unless a necessary condition or full enumeration establishes it.</p>
<p>Live controls pause before the next real evaluation; Step permits one complete evaluation. Current trial and best feasible tabs use the same algorithm event. Recorded replay is separate. Active search milliseconds exclude deliberate display waits and pauses; simulated M seconds and per-service network ms are different quantities.</p>
<h2>Reference check and verification — Assessment sections 7–8</h2>
<p>Reference: A={S01,S03,S07}; B={S12}; C={S02,S05,S06,S10,S11}; D={S04,S08,S09}. J=0.856845, M=28.3333 s, C=0.449583 USD, E=3.927416 Wh, L=53.347863 ms, Lmax=76.666667 ms; all 12 SLAs pass. It is valid, not claimed optimal.</p>
<p>T1–T12 verify default feasibility, GPU incompatibility, CPU overload, insufficient GPUs, equality, seeded repeatability, invalid speed/capacity/network inputs, timeline totals, SLA rejection, bandwidth sensitivity and reference arithmetic.</p>
<h2>Evidence — Week 1 slides 16,23,24</h2>
<p>Every run records instance/input snapshot, version, method, seed, parameters, stop reason, J/M/C/E/L/Lmax, feasibility/SLA count, evaluations, Java/OS and runtime. Experiments use 20 paired seeds, an Exact reference, Random comparator, equal budgets, 20/50/100/200 evaluation limits and +25% RTT / -30% bandwidth sensitivity. CSV/PNG evidence is saved automatically.</p>
<p>Fixed J combines objectives; trade-offs appear in M/C/E/L across placements and network variants. Original mode retains the illustrative cost/delay Pareto sweep. Prices in the assessment are the specified simulated busy-time rates, not current cloud tariffs.</p>
<h2>Common mistakes — Week 1 slide 24</h2><ul><li>Vague objective: fixed formula and units.</li><li>Hidden constraints: shared resource/GPU/slot/SLA evaluator.</li><li>Wrong units: MB to Mb uses factor eight; ms conversion uses 1000; W*s to Wh divides by 3600.</li><li>Model/code mismatch: independent reference and T1–T12.</li><li>One lucky run: 20 seeds and spread statistics.</li><li>No baseline: evaluated reference, Random and Exact.</li></ul>
<p>Verification asks if code implements the model; validation asks whether the model describes reality. This is the specified simulation, with no real cloud account, contention, packet loss, replication or inter-service traffic.</p>
<h2>Concept checks</h2><p>Week 0 slide 19: choose among stated laptops, minimize price, require minimum RAM/storage and budget; specs/prices are data. Week 1 slide 25: choose course-room-time assignments, minimize idle time, prevent room/instructor clashes, respect capacity/availability; fixed slots give a discrete search space.</p>
<p>AI assistance: this project was developed with OpenAI Codex; source explanations and tests support review. The student must understand and credit assistance according to institutional rules. No external Java libraries are required.</p>
</body></html>
""";
    }
}

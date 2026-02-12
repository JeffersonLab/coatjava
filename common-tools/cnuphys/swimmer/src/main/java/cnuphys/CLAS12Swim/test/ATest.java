package cnuphys.CLAS12Swim.test;

import java.util.Locale;
import java.util.Objects;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CLAS12Values;
import cnuphys.CLAS12Swim.ICLAS12Swimmer;

/**
 * Abstract base class for comparing two {@link ICLAS12Swimmer} implementations:
 * typically a legacy swimmer vs a Commons-Math swimmer.
 *
 * <h2>What this harness measures</h2>
 * <ul>
 *   <li><b>Correctness / accuracy</b>: a per-test "miss" metric defined by subclasses</li>
 *   <li><b>Steps proxy</b>: trajectory point count if available (subclass defines)</li>
 *   <li><b>Failures</b>: counts cases where one swimmer succeeds and the other fails</li>
 *   <li><b>Speed</b>: nanosecond timing with warmup</li>
 * </ul>
 *
 * <h2>Units</h2>
 * This harness assumes inputs follow {@link RandomTestData} conventions:
 * positions in cm, angles in degrees, momentum in GeV/c.
 */
public abstract class ATest {

    /** How many mismatch examples to print (0 disables). */
    protected int maxMismatchPrint = 8;

    /** Warmup iterations for timing. */
    protected int warmupIters = 200;

    /** Timed iterations for timing. */
    protected int timedIters = 2000;

    /**
     * Create a short test name used in reports.
     */
    public abstract String name();

    /**
     * Run the test case for sample {@code i} on swimmer {@code s}.
     */
    protected abstract CLAS12SwimResult runOne(ICLAS12Swimmer s, RandomTestData data, int i);

    /**
     * Compute the "miss" metric for the given result.
     * <p>
     * Should return a non-negative value in cm (or a natural unit for the test).
     * Only called when the result is successful unless {@link #includeFailuresInMiss()} is true.
     */
    protected abstract double miss(RandomTestData data, int i, CLAS12SwimResult r);

    /**
     * A steps proxy for reporting.
     * <p>
     * Default: use trajectory point count if available, otherwise 0.
     * Subclasses may override if they have a better measure.
     */
    protected int stepsProxy(CLAS12SwimResult r) {
        if (r == null) return 0;
        var traj = r.getTrajectory();
        if (traj == null) return 0;
        return traj.size();
    }

    /**
     * Whether to include failure cases in miss averaging.
     * <p>
     * Default false: miss is averaged only over cases where that swimmer succeeded.
     */
    protected boolean includeFailuresInMiss() {
        return false;
    }

    /**
     * Print a standardized comparison report.
     */
    public final void runCompare(ICLAS12Swimmer legacy, String legacyName,
                                 ICLAS12Swimmer challenger, String challengerName,
                                 RandomTestData data) {

        Objects.requireNonNull(legacy, "legacy");
        Objects.requireNonNull(challenger, "challenger");
        Objects.requireNonNull(data, "data");

        System.out.println("============================================================");
        System.out.println(name());
        System.out.println("Samples: " + data.q.length);
        System.out.println("Comparing: " + legacyName + " vs " + challengerName);
        System.out.println();

        correctnessReport(legacy, legacyName, challenger, challengerName, data);
        System.out.println();
        timingReport(legacy, legacyName, challenger, challengerName, data);
        System.out.println("============================================================");
    }

    private void correctnessReport(ICLAS12Swimmer legacy, String legacyName,
                                   ICLAS12Swimmer challenger, String challengerName,
                                   RandomTestData data) {

        int n = data.q.length;

        long legacyStepsSum = 0;
        long challengerStepsSum = 0;

        double legacyMissSum = 0.0;
        double challengerMissSum = 0.0;

        int legacyMissN = 0;
        int challengerMissN = 0;

        int bothSuccess = 0;
        int bothFail = 0;

        int legacyOnlySuccess = 0;
        int challengerOnlySuccess = 0;

        int printed = 0;

        for (int i = 0; i < n; i++) {
            CLAS12SwimResult rL = runOne(legacy, data, i);
            CLAS12SwimResult rC = runOne(challenger, data, i);

            boolean sL = (rL != null) && rL.isSuccess();
            boolean sC = (rC != null) && rC.isSuccess();

            legacyStepsSum += stepsProxy(rL);
            challengerStepsSum += stepsProxy(rC);

            if (sL && sC) {
                bothSuccess++;
            } else if (!sL && !sC) {
                bothFail++;
            } else if (sL) {
                legacyOnlySuccess++;
                if (printed < maxMismatchPrint) {
                    printed++;
                    printMismatch(i, data, legacyName, rL, challengerName, rC,
                            "Mismatch: " + legacyName + " SUCCESS, " + challengerName + " FAIL");
                }
            } else {
                challengerOnlySuccess++;
                if (printed < maxMismatchPrint) {
                    printed++;
                    printMismatch(i, data, legacyName, rL, challengerName, rC,
                            "Mismatch: " + challengerName + " SUCCESS, " + legacyName + " FAIL");
                }
            }

            // Miss accumulation
            if (includeFailuresInMiss() || sL) {
                legacyMissSum += miss(data, i, rL);
                legacyMissN++;
            }
            if (includeFailuresInMiss() || sC) {
                challengerMissSum += miss(data, i, rC);
                challengerMissN++;
            }
        }

        double avgMissL = (legacyMissN > 0) ? (legacyMissSum / legacyMissN) : Double.NaN;
        double avgMissC = (challengerMissN > 0) ? (challengerMissSum / challengerMissN) : Double.NaN;

        double avgStepsL = (n > 0) ? ((double) legacyStepsSum / n) : Double.NaN;
        double avgStepsC = (n > 0) ? ((double) challengerStepsSum / n) : Double.NaN;

        System.out.println("Correctness:");
        System.out.println(String.format(Locale.US,
                "  avg miss %-10s = %.3e   avg miss %-10s = %.3e",
                legacyName, avgMissL, challengerName, avgMissC));
        System.out.println(String.format(Locale.US,
                "  avg steps %-9s = %.2f     avg steps %-9s = %.2f",
                legacyName, avgStepsL, challengerName, avgStepsC));

        System.out.println("  outcomes:");
        System.out.println(String.format(Locale.US,
                "    both success: %d   both fail: %d", bothSuccess, bothFail));
        System.out.println(String.format(Locale.US,
                "    %s-only success: %d", legacyName, legacyOnlySuccess));
        System.out.println(String.format(Locale.US,
                "    %s-only success: %d", challengerName, challengerOnlySuccess));

        if (legacyOnlySuccess + challengerOnlySuccess == 0) {
            System.out.println("  (No success/failure mismatches.)");
        } else if (maxMismatchPrint <= 0) {
            System.out.println("  (Mismatches exist; printing disabled.)");
        } else if (printed == 0) {
            System.out.println("  (Mismatches exist, but none printed.)");
        }
    }

    private void timingReport(ICLAS12Swimmer legacy, String legacyName,
                              ICLAS12Swimmer challenger, String challengerName,
                              RandomTestData data) {

        System.out.println("Timing:");

        // warmup
        for (int i = 0; i < warmupIters; i++) {
            int idx = i % data.q.length;
            runOne(legacy, data, idx);
            runOne(challenger, data, idx);
        }

        long tLegacy = timeLoop(legacy, data, timedIters);
        long tChal = timeLoop(challenger, data, timedIters);

        System.out.println(String.format(Locale.US, "  %s time: %d ns", legacyName, tLegacy));
        System.out.println(String.format(Locale.US, "  %s time: %d ns", challengerName, tChal));

        double ratio = (tChal > 0) ? ((double) tLegacy / (double) tChal) : Double.NaN;
        System.out.println(String.format(Locale.US,
                "  ratio (%s/%s) = %.6f", legacyName, challengerName, ratio));
    }

    private long timeLoop(ICLAS12Swimmer s, RandomTestData data, int iters) {
        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            int idx = i % data.q.length;
            runOne(s, data, idx);
        }
        return System.nanoTime() - t0;
    }

    private void printMismatch(int i, RandomTestData d,
                               String nameA, CLAS12SwimResult a,
                               String nameB, CLAS12SwimResult b,
                               String header) {
        System.out.println();
        System.out.println("  " + header);
        System.out.println("  case " + i + " inputs:");
        System.out.println(String.format(Locale.US,
                "    q=%d  xo=%.6f cm  yo=%.6f cm  zo=%.6f cm   p=%.6f GeV/c   theta=%.6f deg   phi=%.6f deg",
                d.q[i], d.xo[i], d.yo[i], d.zo[i], d.p[i], d.theta[i], d.phi[i]));
        System.out.println("  results:");
        printResultLine("    " + nameA + ":", a);
        printResultLine("    " + nameB + ":", b);
    }

    private void printResultLine(String prefix, CLAS12SwimResult r) {
        if (r == null) {
            System.out.println(prefix + " null result");
            return;
        }
        CLAS12Values fv = r.getFinalValues();
        System.out.println(String.format(Locale.US,
                "%s success=%s  status=%d  final=(%.6f, %.6f, %.6f) cm",
                prefix, r.isSuccess(), r.getStatus(), fv.x, fv.y, fv.z));
    }
}

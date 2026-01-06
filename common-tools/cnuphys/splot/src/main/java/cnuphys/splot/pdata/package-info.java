/**
 * Data model classes for splot.
 *
 * <h2>Threading and Concurrency</h2>
 *
 * <p>
 * splot is a Swing-based plotting library. As such, any operation that triggers
 * UI updates (directly or indirectly) must occur on the Swing Event Dispatch Thread (EDT).
 * This includes adding data points, clearing data, changing curve styles, and updating
 * fit parameters.
 * </p>
 *
 * <p>
 * To support real-time and multi-threaded data acquisition (DAQ), curves provide a
 * two-stage update mechanism:
 * </p>
 *
 * <pre>
 * background threads  →  enqueue data (thread-safe)
 * EDT                 →  drain queued data and notify listeners
 * </pre>
 *
 * <p>
 * Background threads should call {@code Curve.enqueue(...)} only.
 * The queued data is then applied on the EDT using
 * {@code Curve.drainPendingOnEDT(...)} (typically driven by a Swing {@code Timer}).
 * </p>
 *
 * <p>
 * This design guarantees:
 * </p>
 * <ul>
 *   <li>no concurrent mutation during rendering or fitting</li>
 *   <li>consistent snapshots for plotting</li>
 *   <li>batched notifications to avoid repaint storms</li>
 *   <li>fail-fast detection of incorrect threading usage</li>
 * </ul>
 *
 * <p>
 * Immediate mutation methods (e.g. {@code add}, {@code addAll}, {@code clearData})
 * enforce EDT usage and will throw an exception if called from a background thread.
 * </p>
 */
package cnuphys.splot.pdata;

/**
 * Data-model classes for the {@code splot} plotting library.
 *
 * <p>
 * This package contains the core data abstractions that back all plots in {@code splot},
 * including:
 * </p>
 * <ul>
 *   <li>{@link cnuphys.splot.pdata.ACurve} – the abstract base class for all curves</li>
 *   <li>{@link cnuphys.splot.pdata.Curve} – standard XY curves with optional Y-errors</li>
 *   <li>{@link cnuphys.splot.pdata.HistoCurve} – histogram-backed curves</li>
 *   <li>Supporting data containers such as {@link cnuphys.splot.pdata.DataColumn} and
 *       {@link cnuphys.splot.pdata.HistoData}</li>
 * </ul>
 *
 * <h2>Responsibilities</h2>
 * <p>
 * Classes in this package are responsible for:
 * </p>
 * <ul>
 *   <li>Storing and managing curve and histogram data</li>
 *   <li>Providing consistent snapshots for rendering</li>
 *   <li>Managing cached derived artifacts such as fit results and splines</li>
 *   <li>Notifying listeners when data or style changes occur</li>
 * </ul>
 *
 * <h2>Threading Model</h2>
 * <p>
 * {@code splot} is built on Swing. As a result, all notifications that may lead to UI
 * updates (such as repaint requests) must occur on the Swing
 * <em>Event Dispatch Thread (EDT)</em>.
 * </p>
 *
 * <p>
 * To support modern streaming and data-acquisition use cases, {@link cnuphys.splot.pdata.Curve}
 * and {@link cnuphys.splot.pdata.HistoCurve} provide thread-safe data addition methods:
 * </p>
 * <ul>
 *   <li>{@code add(...)} and {@code addAll(...)} may be called from any thread</li>
 *   <li>Off-EDT calls enqueue data into lock-free staging queues</li>
 *   <li>Queued data are later applied on the EDT in bounded batches</li>
 *   <li>A single consolidated data-change notification is fired per batch</li>
 * </ul>
 *
 * <p>
 * This design prevents repaint storms while preserving strict Swing correctness.
 * </p>
 *
 * <h2>Explicit Streaming Control</h2>
 * <p>
 * For advanced use cases, curves also expose an explicit staging API:
 * </p>
 * <ul>
 *   <li>{@code enqueue(...)} – thread-safe, no immediate mutation or notification</li>
 *   <li>{@code drainPendingOnEDT(...)} – EDT-only, applies queued data in bulk</li>
 * </ul>
 *
 * <p>
 * This allows applications to centralize draining (for example, via a single Swing timer)
 * or implement custom backpressure strategies.
 * </p>
 *
 * <h2>Fitting and Derived Artifacts</h2>
 * <p>
 * Fit results, spline caches, and other derived artifacts are invalidated when data
 * or relevant style parameters change. Re-computation is typically triggered lazily
 * during rendering via {@code doFit(...)}.
 * </p>
 *
 * <p>
 * All fitting and artifact management follows the same EDT discipline as data updates.
 * </p>
 *
 * <h2>Design Philosophy</h2>
 * <p>
 * This package deliberately separates:
 * </p>
 * <ul>
 *   <li><b>Data ownership and consistency</b> (this package)</li>
 *   <li><b>Rendering and interaction</b> (view and canvas layers)</li>
 * </ul>
 *
 * <p>
 * While some implementation details intentionally blur strict MVC boundaries for
 * performance and simplicity, the overall design favors robustness, predictability,
 * and ease of use in interactive scientific applications.
 * </p>
 */
package cnuphys.splot.pdata;

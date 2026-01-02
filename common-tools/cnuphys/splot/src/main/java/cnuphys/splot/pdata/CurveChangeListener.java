package cnuphys.splot.pdata;

import java.util.EventListener;

/**
 * Listener for changes to an {@link ACurve}.
 *
 * @author heddle
 */
public interface CurveChangeListener extends EventListener {

    /**
     * Notification that a curve changed.
     *
     * @param curve the curve that changed
     * @param type  the change type
     */
    void curveChanged(ACurve curve, CurveChangeType type);
}

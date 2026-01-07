package cnuphys.splot.toolbar;

import java.util.EventListener;

public interface IToolBarListener extends EventListener {

	public void buttonPressed(PlotToolBar toolbar, ToolBarButton button);

	public void toggleButtonActivated(PlotToolBar toolbar, ToolBarToggleButton button);
}

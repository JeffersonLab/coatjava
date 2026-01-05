package cnuphys.splot.example;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JMenuBar;
import cnuphys.splot.pdata.PlotData;
import cnuphys.splot.pdata.PlotDataException;
import cnuphys.splot.plot.GraphicsUtilities;
import cnuphys.splot.plot.PlotCanvas;
import cnuphys.splot.plot.PlotPanel;
import cnuphys.splot.plot.SplotMenus;

/**
 * A template class for plot examples
 * 
 * @author heddle
 * 
 */
@SuppressWarnings("serial")
public abstract class AExample extends JFrame {

	// the plot canvas
	protected PlotCanvas _canvas;

	// the menus and items
	protected SplotMenus _menus;

	public AExample() {
		super("sPlot");

		// set up what to do if the window is closed
		WindowAdapter windowAdapter = new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent event) {
				System.exit(1);
			}
		};
		addWindowListener(windowAdapter);

		try {
			_canvas = new PlotCanvas(createPlotData(), getPlotTitle(), getXAxisLabel(), getYAxisLabel());
		}
		catch (PlotDataException e) {
			e.printStackTrace();
			return;
		}

		// add the menu bar
		JMenuBar mb = new JMenuBar();
		setJMenuBar(mb);
		_menus = new SplotMenus(_canvas, mb, true);
		fillData();
		setParameters();
		final PlotPanel ppanel = new PlotPanel(_canvas);

		ppanel.setPreferredSize(new Dimension(750, 700));

		add(ppanel, BorderLayout.CENTER);

		pack();
		GraphicsUtilities.centerComponent(this);
	}

	/**
	 * Get the plot canvas
	 * 
	 * @return the plot canvas
	 */
	public PlotCanvas getPlotCanvas() {
		return _canvas;
	}

	/**
	 * Create the plot data
	 * @return the plot data
	 * @throws PlotDataException
	 */
	protected abstract PlotData createPlotData() throws PlotDataException;

	/** get the column names */
	protected abstract String[] getColumnNames();

	/** get the x axis label */
	protected abstract String getXAxisLabel();

	/** get the y axis label */
	protected abstract String getYAxisLabel();

	/** get the plot title */
	protected abstract String getPlotTitle();

	// fill the plot data
	public abstract void fillData();

	// set the preferences
	public abstract void setParameters();

}

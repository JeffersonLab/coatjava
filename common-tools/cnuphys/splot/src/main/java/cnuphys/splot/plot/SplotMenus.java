package cnuphys.splot.plot;

import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;

import cnuphys.splot.edit.CurveEditorDialog;
import cnuphys.splot.edit.DialogUtilities;
import cnuphys.splot.pdata.PlotDataType;

/**
 * This class creates and manages the menus for sPlot.
 * 
 * @author heddle
 *
 */
public class SplotMenus implements ActionListener {

	// the owner canvas
	private PlotCanvas _plotCanvas;

	// the menus
	protected JMenu _editMenu;

	// the menu items
	protected JMenuItem _prefItem;
	protected JMenuItem _curveItem;

	protected JCheckBoxMenuItem _showExtraCB;

	/**
	 * Create a set of menus and items for sPlot
	 * 
	 * @param canvas  the plot canvas being controlled
	 * @param menuBar the menu bar
	 * @param addQuit if <code>true</code> include a quit item
	 */
	public SplotMenus(PlotCanvas canvas, JMenuBar menuBar, boolean addQuit) {
		_plotCanvas = canvas;
		makeMenus(canvas, menuBar, addQuit);
	}

	/**
	 * Create a set of menus and items for sPlot
	 * 
	 * @param canvas  the plot canvas being controlled
	 * @param popup   a popup to hold the menus
	 * @param addQuit if <code>true</code> include a quit item
	 */
	public SplotMenus(PlotCanvas canvas, JPopupMenu popup, boolean addQuit) {
		_plotCanvas = canvas;
		makeMenus(canvas, popup, addQuit);
	}

	// make the menus
	private void makeMenus(PlotCanvas canvas, Container container, boolean addQuit) {
//		makeFileMenu(container, addQuit);
		makeEditMenu(canvas, container);
	}

	// make the edit menu
	protected void makeEditMenu(PlotCanvas canvas, Container container) {
		_editMenu = new JMenu("Edit");
		_prefItem = addMenuItem("Preferences...", 'P', _editMenu);
		_curveItem = addMenuItem("Curves...", 'C', _editMenu);
		_editMenu.addSeparator();
		_showExtraCB = addMenuCheckBox("Show any Extra Text", _editMenu, canvas.getParameters().extraDrawing());
		container.add(_editMenu);
	}

	/**
	 * Convenience routine for adding a menu item.
	 * 
	 * @param label     the menu label.
	 * @param accelChar the accelerator character.
	 * @param menu      the menu to add the item to.
	 */

	protected JMenuItem addMenuItem(String label, char accelChar, JMenu menu) {
		JMenuItem mitem = null;

		if ((label != null) && (menu != null)) {
			try {
				mitem = new JMenuItem(label);
				menu.add(mitem);
				if (accelChar != '\0') {
					KeyStroke keyStroke = KeyStroke.getKeyStroke("control " + accelChar);
					mitem.setAccelerator(keyStroke);
				}

				mitem.addActionListener(this);
			}
			catch (Exception e) {
			}
		}

		return mitem;
	}

	protected JCheckBoxMenuItem addMenuCheckBox(String label, JMenu menu, boolean selected) {
		JCheckBoxMenuItem cb = null;

		if ((label != null) && (menu != null)) {
			try {
				cb = new JCheckBoxMenuItem(label, selected);
				menu.add(cb);
				cb.addActionListener(this);
			}
			catch (Exception e) {
			}
		}

		return cb;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();

		if (source == _prefItem) {
			_plotCanvas.showPreferencesEditor();
		}

		else if (source == _curveItem) {
			CurveEditorDialog cd = new CurveEditorDialog(_plotCanvas);
			DialogUtilities.centerDialog(cd);
			cd.selectFirstCurve();
			cd.setVisible(true);
		}
		else if (source == _showExtraCB) {
			_plotCanvas.getParameters().setExtraDrawing(_showExtraCB.isSelected());
			_plotCanvas.repaint();
		}
	}

	/**
	 * Get the underlying plot canvas
	 * 
	 * @return the plot canvas
	 */
	public PlotCanvas getPlotCanvas() {
		return _plotCanvas;
	}


	/**
	 * Get the edit menu
	 * 
	 * @return the edit menu
	 */
	public JMenu getEditMenu() {
		return _editMenu;
	}

	/**
	 * Get the preferences item
	 * 
	 * @return the preferences item
	 */
	public JMenuItem getPreferencesItem() {
		return _prefItem;
	}

	/**
	 * Get the curve (editor) item
	 * 
	 * @return the curve editor item
	 */
	public JMenuItem getCurveItem() {
		return _curveItem;
	}

}

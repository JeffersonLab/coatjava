package cnuphys.lund;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import cnuphys.CLAS12Swim.CLAS12SwimResult;
import cnuphys.CLAS12Swim.CommonsMathCLAS12Swimmer;
import cnuphys.CLAS12Swim.ICLAS12Swimmer;
import cnuphys.swim.SwimTrajectory;
import cnuphys.swim.Swimming;

/**
 * A dialog for configuring and executing particle swimming within the CLAS12 environment.
 * It allows users to select particles, set initial kinematic conditions, choose 
 * integration algorithms, and select the specific swimmer implementation.
 */
@SuppressWarnings("serial")
public class LundTrackDialog extends JDialog {

	/**
	 * Enum defining the available swimming algorithms.
	 */
	public enum SWIM_ALGORITHM {
		/** Standard integration until sMax is reached. */
		STANDARD, 
		/** Integration until a specific Z coordinate is reached. */
		FIXEDZ, 
		/** Integration until a specific radial distance (rho) is reached. */
		FIXEDRHO
	}
	
	/** The currently selected swimming algorithm. */
	private SWIM_ALGORITHM _algorithm = SWIM_ALGORITHM.STANDARD;
	
	/** Response code for cancelation. */
	private static final int CANCEL_RESPONSE = 1;

	/** Combo box for selecting the particle type. */
	private LundComboBox _lundComboBox;
	
	/** Text field displaying relativistic gamma. */
	private JTextField _relativisticGamma;
	
	/** Text field displaying relativistic beta. */
	private JTextField _relativisticBeta;
	
	/** Text field displaying total energy. */
	private JTextField _totalEnergyTextField;
	
	/** Text field for entering momentum magnitude. */
	private JTextField _momentumTextField;
	
	/** Text field displaying particle mass. */
	private JTextField _massTextField;
	
	/** Button to trigger the swim operation. */
	private JButton _swimButton;
	
	/** Text field for vertex X coordinate. */
	private JTextField _vertexX;
	
	/** Text field for vertex Y coordinate. */
	private JTextField _vertexY;
	
	/** Text field for vertex Z coordinate. */
	private JTextField _vertexZ;
	
	/** Text field for initial polar angle theta. */
	private JTextField _theta;
	
	/** Text field for initial azimuthal angle phi. */
	private JTextField _phi;

	/** Radio button for standard algorithm selection. */
	private JRadioButton _standardRB;
	
	/** Radio button for Fixed Z algorithm selection. */
	private JRadioButton _fixedZRB;
	
	/** Radio button for Fixed Rho algorithm selection. */
	private JRadioButton _fixedRhoRB;
	
	/** Text field for the target radial value in Fixed Rho. */
	private JTextField _fixedRho;
	
	/** Text field for the target Z value in Fixed Z. */
	private JTextField _fixedZ;
	
	/** Text field for the maximum path length. */
	private JTextField _sMax;
	
	/** Text field for integration accuracy (microns). */
	private JTextField _accuracy;

	/** Stores the previously valid momentum to handle parsing errors. */
	private double _oldMomentum = 2.0;

	/** Unicode string for Greek letter Beta. */
	public static final String SMALL_BETA = "\u03B2";
	/** Unicode string for Greek letter Gamma. */
	public static final String SMALL_GAMMA = "\u03B3";
	/** Unicode string for Greek letter Theta. */
	public static final String SMALL_THETA = "\u03B8";
	/** Unicode string for Greek letter Phi. */
	public static final String SMALL_PHI = "\u03C6";
	/** Unicode string for squared superscript. */
	public static final String SUPER2 = "\u00B2";
	/** Unicode string for Greek letter Rho. */
	public static final String SMALL_RHO = "\u03C1";

	/** Calculated relativistic gamma. */
	private double _gamma;
	/** Calculated relativistic beta. */
	private double _beta;
	/** Calculated total energy. */
	private double _energy; 

	/** Label for Relativistic Gamma. */
	private static final String RELGAMMA = "Relativistic " + SMALL_GAMMA;
	/** Label for Relativistic Beta. */
	private static final String RELBETA = "Relativistic " + SMALL_BETA;
	/** Label for Total Energy. */
	private static final String TOTENERGY = "Total Energy";
	/** Label for Momentum. */
	private static final String MOMENTUMMAG = "Momentum";
	/** Label for Mass. */
	private static final String MASS = "Mass";

	/** Singleton instance of the dialog. */
	private static LundTrackDialog instance;

	/**
	 * Private constructor for the LundTrackDialog singleton.
	 * Initializes UI components and window listeners.
	 */
	private LundTrackDialog() {
		setTitle("Swim a Particle");
		setModal(false);

		WindowAdapter wa = new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent we) {
				doClose(CANCEL_RESPONSE);
			}
		};
		addWindowListener(wa);

		addComponents();
		pack();
		centerComponent(this);
	}

	/**
	 * Creates and configures the radio buttons for algorithm selection.
	 * @param bg The ButtonGroup to which the radio buttons are added.
	 */
	private void createAlgorithmButtons(ButtonGroup bg) {
		ActionListener al = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (_standardRB.isSelected()) {
					_algorithm = SWIM_ALGORITHM.STANDARD;
				}
				else if (_fixedZRB.isSelected()) {
					_algorithm = SWIM_ALGORITHM.FIXEDZ;
				}
				else if (_fixedRhoRB.isSelected()) {
					_algorithm = SWIM_ALGORITHM.FIXEDRHO;
				}
				fixState();
			}
		};

		_standardRB = new JRadioButton("Standard");
		_fixedZRB = new JRadioButton("Fixed Z");
		_fixedRhoRB = new JRadioButton("Fixed " + SMALL_RHO);

		_standardRB.setSelected((_algorithm == SWIM_ALGORITHM.STANDARD));
		_fixedZRB.setSelected((_algorithm == SWIM_ALGORITHM.FIXEDZ));
		_fixedRhoRB.setSelected((_algorithm == SWIM_ALGORITHM.FIXEDRHO));

		_standardRB.addActionListener(al);
		_fixedZRB.addActionListener(al);
		_fixedRhoRB.addActionListener(al);

		bg.add(_standardRB);
		bg.add(_fixedZRB);
		bg.add(_fixedRhoRB);

		fixState();
	}

	/**
	 * Updates the enabled state of coordinate text fields based on 
	 * the selected algorithm.
	 */
	private void fixState() {
		_fixedZ.setEnabled(_fixedZRB.isSelected());
		_fixedRho.setEnabled(_fixedRhoRB.isSelected());
	}

	/**
	 * Returns the singleton instance of the LundTrackDialog.
	 * @return The LundTrackDialog instance, made visible.
	 */
	public static LundTrackDialog getInstance() {
		if (instance == null) {
			instance = new LundTrackDialog();
		}
		instance.setVisible(true);
		return instance;
	}

	/**
	 * Orchestrates the addition of all UI subpanels to the dialog.
	 */
	private void addComponents() {
		setLayout(new BorderLayout(6, 6));
		Box box = Box.createVerticalBox();
		box.add(Box.createVerticalStrut(6));

		ActionListener al = new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				selectedParticle();
			}
		};

		_lundComboBox = new LundComboBox(true, 950.0, 11);
		_lundComboBox.addActionListener(al);
		box.add(paddedPanel(20, 6, _lundComboBox));

		box.add(Box.createVerticalStrut(6));
		box.add(energyPanel());

		box.add(Box.createVerticalStrut(6));
		box.add(initConditionsPanel());

		box.add(Box.createVerticalStrut(6));
		box.add(vertexPanel());

		box.add(Box.createVerticalStrut(6));
		box.add(cutoffPanel());

		_swimButton = new JButton("Swim");
		_swimButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setMomentum();
				doCommonSwim();
			}
		});

		add(box, BorderLayout.CENTER);
		add(paddedPanel(50, 6, _swimButton), BorderLayout.SOUTH);

		add(Box.createHorizontalStrut(4), BorderLayout.EAST);
		add(Box.createHorizontalStrut(4), BorderLayout.WEST);

		selectedParticle(); 
	}

	/**
	 * Executes the swimming process using the configured particle, kinematics, 
	 * algorithm, and swimmer implementation.
	 */
	private void doCommonSwim() {
		ICLAS12Swimmer swimmer = new CommonsMathCLAS12Swimmer();
		
		CLAS12SwimResult result = null;
		LundId lid = _lundComboBox.getSelectedId();

		double xo = Double.parseDouble(_vertexX.getText());
		double yo = Double.parseDouble(_vertexY.getText());
		double zo = Double.parseDouble(_vertexZ.getText());
		double momentum = Double.parseDouble(_momentumTextField.getText());
		double theta = Double.parseDouble(_theta.getText());
		double phi = Double.parseDouble(_phi.getText());

		double stepSize = 1e-4; 
		double sMax = Double.parseDouble(_sMax.getText()); 

		double tolerance = 1.0e-6;
		SwimTrajectory traj = null;

		switch (_algorithm) {
		case STANDARD:
			result = swimmer.swim(lid.getCharge(), xo, yo, zo, momentum, theta, phi, sMax, stepSize, tolerance);
			break;
		case FIXEDZ:
			double accuracy = Double.parseDouble(_accuracy.getText()) / 1.0e4;
			double ztarget = Double.parseDouble(_fixedZ.getText()); 
			result = swimmer.swimZ(lid.getCharge(), xo, yo, zo, momentum, theta, phi, ztarget, accuracy, sMax, stepSize,
					tolerance);
			break;
		case FIXEDRHO:
			accuracy = Double.parseDouble(_accuracy.getText()) / 1.0e4;
			double rhotarget = Double.parseDouble(_fixedRho.getText()); 
			result = swimmer.swimRho(lid.getCharge(), xo, yo, zo, momentum, theta, phi, rhotarget, accuracy, sMax, stepSize,
					tolerance);
			break;
		} 

		if (result != null) {
			traj = result.getTrajectory();
			traj.setLundId(lid);
			traj.computeBDL(swimmer.getProbe());
			Swimming.addMCTrajectory(traj);
			System.out.println(result.toString());
		}
	}

	/**
	 * Utility method to create a horizontally aligned box with a prompt, text field, and units.
	 * @param prompt The label text.
	 * @param tf The JTextField component.
	 * @param units The unit label text.
	 * @param promptWidth Fixed width for the prompt label.
	 * @return A Box containing the labeled components.
	 */
	private Box labeledTextField(String prompt, JTextField tf, String units, final int promptWidth) {
		Box box = Box.createHorizontalBox();
		JLabel plabel = new JLabel(prompt) {
			@Override
			public Dimension getPreferredSize() {
				if (promptWidth > 0) {
					return new Dimension(promptWidth, 18);
				} else {
					return super.getPreferredSize();
				}
			}
		};
		box.add(plabel);
		box.add(Box.createHorizontalStrut(6));
		box.add(tf);
		if (units != null) {
			box.add(Box.createHorizontalStrut(6));
			box.add(new JLabel(units));
		}
		return box;
	}

	/**
	 * Handles particle selection changes.
	 */
	private void selectedParticle() {
		setMomentum();
	}

	/**
	 * Creates the subpanel for configuring the track vertex.
	 * @return A JPanel with vertex coordinate fields.
	 */
	private JPanel vertexPanel() {
		JPanel panel = new JPanel();
		Box box = Box.createVerticalBox();
		_vertexX = new JTextField(8);
		_vertexY = new JTextField(8);
		_vertexZ = new JTextField(8);
		_vertexX.setText("0.0");
		_vertexY.setText("0.0");
		_vertexZ.setText("0.0");
		box.add(labeledTextField("X:", _vertexX, "cm", 20));
		box.add(Box.createVerticalStrut(5));
		box.add(labeledTextField("Y:", _vertexY, "cm", 20));
		box.add(Box.createVerticalStrut(5));
		box.add(labeledTextField("Z:", _vertexZ, "cm", 20));
		box.add(Box.createVerticalStrut(5));
		panel.add(box);
		panel.setBorder(new CommonBorder("Track Vertex"));
		return panel;
	}

	/**
	 * Creates the subpanel for initial momentum and direction.
	 * @return A JPanel with momentum, theta, and phi fields.
	 */
	private JPanel initConditionsPanel() {
		JPanel panel = new JPanel();
		Box box = Box.createVerticalBox();
		_momentumTextField = new JTextField(8);
		_momentumTextField.setEditable(true);
		_momentumTextField.setText("" + String.format("%-9.5f", _oldMomentum));
		_momentumTextField.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				setMomentum();
			}
		});
		box.add(labeledTextField(MOMENTUMMAG, _momentumTextField, "GeV/c", -1));
		_theta = new JTextField(8);
		_phi = new JTextField(8);
		_theta.setText("15.0");
		_phi.setText("0.0");
		box.add(labeledTextField(SMALL_THETA, _theta, "deg", 20));
		box.add(Box.createVerticalStrut(5));
		box.add(labeledTextField(SMALL_PHI, _phi, "deg", 20));
		box.add(Box.createVerticalStrut(5));
		panel.add(box);
		panel.setBorder(new CommonBorder("Initial Momentum and Direction"));
		return panel;
	}

	/**
	 * Creates the subpanel for integration cutoff controls.
	 * @return A JPanel with cutoff and accuracy settings.
	 */
	private JPanel cutoffPanel() {
		_fixedZ = new JTextField(8);
		_fixedRho = new JTextField(8);
		_sMax = new JTextField(8);
		_accuracy = new JTextField(8);
		_fixedRho.setText("100.0");
		_fixedZ.setText("575.0");
		_sMax.setText("800.0");
		_accuracy.setText("10");
		JPanel panel = new JPanel();
		Box box = Box.createVerticalBox();
		box.add(cutoffType());
		box.add(Box.createVerticalStrut(5));
		box.add(labeledTextField("      Stopping Z", _fixedZ, "cm", -1));
		box.add(Box.createVerticalStrut(5));
		box.add(labeledTextField("      Stopping " + SMALL_RHO, _fixedRho, "cm", -1));
		box.add(Box.createVerticalStrut(5));
		box.add(labeledTextField("            Smax", _sMax, "cm", -1));
		box.add(Box.createVerticalStrut(5));
		box.add(labeledTextField("        Accuracy", _accuracy, "microns", -1));
		box.add(Box.createVerticalStrut(5));
		panel.add(box);
		panel.setBorder(new CommonBorder("Integration Controls"));
		return panel;
	}

	/**
	 * Creates the component for selecting the algorithm type.
	 * @return A JPanel containing algorithm radio buttons.
	 */
	private JPanel cutoffType() {
		ButtonGroup bg = new ButtonGroup();
		createAlgorithmButtons(bg);
		JPanel spanel = new JPanel();
		spanel.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
		spanel.add(_standardRB);
		spanel.add(_fixedZRB);
		spanel.add(_fixedRhoRB);
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(spanel);
		return panel;
	}

	/**
	 * Creates the subpanel for displaying particle energy and mass information.
	 * @return A JPanel with particle kinematic information.
	 */
	private JPanel energyPanel() {
		JPanel panel = new JPanel();
		Box box = Box.createVerticalBox();
		_massTextField = new JTextField(8);
		_relativisticGamma = new JTextField(8);
		_relativisticBeta = new JTextField(8);
		_totalEnergyTextField = new JTextField(8);
		disable(_massTextField);
		disable(_relativisticGamma);
		disable(_relativisticBeta);
		disable(_totalEnergyTextField);
		box.add(Box.createVerticalStrut(5));
		box.add(labeledTextField(MASS, _massTextField, " GeV/c" + SUPER2, -1));
		box.add(Box.createVerticalStrut(5));
		box.add(labeledTextField(RELGAMMA, _relativisticGamma, null, -1));
		box.add(Box.createVerticalStrut(5));
		box.add(labeledTextField(RELBETA, _relativisticBeta, null, -1));
		box.add(Box.createVerticalStrut(5));
		box.add(labeledTextField(TOTENERGY, _totalEnergyTextField, " GeV", -1));
		box.add(Box.createVerticalStrut(5));
		panel.add(box);
		panel.setBorder(new CommonBorder("Particle Energy"));
		return panel;
	}

	/**
	 * Disables a text field and styles it as read-only.
	 * @param tf The JTextField to disable.
	 */
	private void disable(JTextField tf) {
		tf.setEditable(false);
		tf.setBackground(Color.black);
		tf.setForeground(Color.cyan);
	}

	/**
	 * Calculates and updates relativistic values based on the current momentum.
	 */
	private void setMomentum() {
		double momentum = 0.0;
		try {
			momentum = Double.parseDouble(_momentumTextField.getText());
		} catch (Exception e) {
			momentum = _oldMomentum;
			_momentumTextField.setText("" + String.format("%-9.5f", _oldMomentum));
			return;
		}
		_oldMomentum = momentum;
		LundId lid = _lundComboBox.getSelectedId();
		double mass = lid.getMass();
		_energy = Math.sqrt(momentum * momentum + mass * mass);
		_gamma = _energy / mass;
		_beta = Math.sqrt(1.0 - 1.0 / (_gamma * _gamma));
		_relativisticGamma.setText(String.format("%-9.5f", _gamma));
		_relativisticBeta.setText(String.format("%-13.9f", _beta));
		_massTextField.setText(String.format("%-10.6f", mass));
		_totalEnergyTextField.setText(String.format("%-9.5f", _energy));
	}

	/**
	 * Closes the dialog.
	 * @param reason Integer code indicating why the dialog is closing.
	 */
	private void doClose(int reason) {
		setVisible(false);
	}

	/**
	 * Creates a padded JPanel around a component.
	 * @param hpad Horizontal padding.
	 * @param vpad Vertical padding.
	 * @param component The centered component.
	 * @return The padded JPanel.
	 */
	public static JPanel paddedPanel(int hpad, int vpad, Component component) {
		JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout());
		if (hpad > 0) {
			panel.add(Box.createHorizontalStrut(hpad), BorderLayout.WEST);
			panel.add(Box.createHorizontalStrut(hpad), BorderLayout.EAST);
		}
		if (hpad > 0) {
			panel.add(Box.createVerticalStrut(vpad), BorderLayout.NORTH);
			panel.add(Box.createVerticalStrut(vpad), BorderLayout.SOUTH);
		}
		panel.add(component, BorderLayout.CENTER);
		return panel;
	}

	/**
	 * Centers a component on the screen.
	 * @param component The component to center.
	 */
	public static void centerComponent(Component component) {
		if (component == null) return;
		try {
			Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
			Dimension componentSize = component.getSize();
			if (componentSize.height > screenSize.height) componentSize.height = screenSize.height;
			if (componentSize.width > screenSize.width) componentSize.width = screenSize.width;
			int x = ((screenSize.width - componentSize.width) / 2);
			int y = ((screenSize.height - componentSize.height) / 2);
			component.setLocation(x, y);
		} catch (Exception e) {
			component.setLocation(200, 200);
			e.printStackTrace();
		}
	}

	/**
	 * A custom titled border for dialog subpanels.
	 */
	public class CommonBorder extends TitledBorder {
		/** Default etched border. */
		public Border etched = BorderFactory.createEtchedBorder();
		/** Default font for the title. */
		public Font font = new Font("SandSerif", Font.PLAIN, 9);
		
		/** Default constructor. */
		public CommonBorder() {
			super(BorderFactory.createEtchedBorder());
			setTitleColor(Color.blue);
			setTitleFont(font);
		}
		
		/**
		 * Constructor with a specific title.
		 * @param title The border title.
		 */
		public CommonBorder(String title) {
			this();
			setTitle(title);
		}
	}
}

package org.jlab.io.ui;

import java.awt.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.Border;

/**
 *
 * @author gavalian
 */
public class ConnectionDialogHipo extends BasicDialog {
    
    public static final int CONNECTSPECIFIC = 1;
    public static final int CONNECTDAQ = 2;
    
    public static final int   RING_TYPE_ET = 5;
    public static final int RING_TYPE_HIPO = 6;
    
    private JRadioButton _directConnect;
    private JRadioButton _connectToDAQ;
    
    private static String[] hostNames = new String[]{"clondaq2","clondaq3","clondaq4","clondaq5","clondaq6","clondaq7"};
    private static String[]    hostIP = new String[]{"129.57.167.109","129.57.167.226","129.57.167.227","129.57.167.41","129.57.167.60","129.57.167.20"};
    
    Map<String,String>  connectionHosts = new LinkedHashMap<>();
    
    private static String[] closeoutButtons = {"Connect", "Cancel"};
    
    private JTextField _ipField;
    private JTextField _fileName;
    
    private int _reason = DialogUtilities.CANCEL_RESPONSE;
    
    /**
     * Create the panel for selected
     */
    public ConnectionDialogHipo() {
        super("Connection.....", true, closeoutButtons);
        for(int i = 0; i < hostNames.length; i++){
            this.connectionHosts.put(hostNames[i],hostIP[i]);
        }
    }
    
    @Override
    protected Component createCenterComponent() {
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel,
                BoxLayout.Y_AXIS));
		
        _ipField = new JTextField();
        _ipField.setText("129.57.167.227");
        
        panel.add(_ipField);
        
        ButtonGroup bg = new ButtonGroup();
        _directConnect = new JRadioButton("Connect to Specified Address", true);
        _connectToDAQ = new JRadioButton("Connect to DAQ Ring (Counting House Only)", false);
        bg.add(_directConnect);
        bg.add(_connectToDAQ);
        panel.add(_directConnect);
        panel.add(Box.createVerticalStrut(6));
        panel.add(_connectToDAQ);
        
        Border emptyBorder = BorderFactory.createEtchedBorder();
        panel.setBorder(BorderFactory.createTitledBorder(emptyBorder, "Connect to Host"));
        return panel;
    }
    
    public int reason() {
        return _reason;
    }
    
    @Override
    public void handleCommand(String command) {
        if ("Connect".equals(command)) {
            _reason = DialogUtilities.OK_RESPONSE;
            this.setVisible(false);
        }
        setVisible(false);
    }
    
    public String getFileName() {
        return _fileName.getText();
    }
    
    
    public String getIpAddress() {
        return _ipField.getText();
    }
    
    public String getAddressString(){
        StringBuilder str = new StringBuilder();
        for(int i = 0; i < hostIP.length; i++){
            if(i!=0) str.append(":");
            str.append(hostIP[i]);
        }
        return str.toString();
    }
    
    public int getConnectionType() {
        if (_directConnect.isSelected()) {
            return CONNECTSPECIFIC;
        }
        else if (_connectToDAQ.isSelected()) {
            return CONNECTDAQ;
        }
        return -1;
    }
    
    public static void main(String arg[]) {
        ConnectionDialogHipo dialog = new ConnectionDialogHipo();
        dialog.setVisible(true);
        System.out.println(" REASON = " + dialog.reason());
    }
}

package cnuphys.CLAS12Swim.serializer;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import cnuphys.CLAS12Swim.CLAS12Swimmer;

public class SwimSerializer {
	
	/** The swimmer is doing a basic swim */
	public static final int BASICSWIM = 1;
	
	/** The swimmer is doing a fixed step swim */
	public static final int FIXEDSS = 2;
	
	/** The swimmer is doing a cylinder swim */
	public static final int SWIMCYL_A = 3;
	
	/** The swimmer is doing a cylinder swim */
	public static final int SWIMCYL_B = 4;
	
	/** The swimmer is doing a sphere swim */
	public static final int SWIMSPHERE_A = 5;
	
	/** The swimmer is doing a sphere swim */
	public static final int SWIMSPHERE_B = 6;

	/** The swimmer is doing a plane swim */
	public static final int SWIMPLANE_A = 7;
	
	/** The swimmer is doing a plane swim */
	public static final int SWIMPLANE_B = 8;
	
	/** The swimmer is doing a plane swim */
	public static final int SWIMPLANE_C = 9;
	
	/** The swimmer is doing tilted sector Z swim */
	public static final int SWIMSECTZ = 10;
	
	/** The swimmer is a swim to z swim */
	public static final int SWIMZ = 11;

	/** The swimmer is a swim to rho swim */
	public static final int SWIMRHO = 12;

	/** The swimmer is a swim to z line swim */
	public static final int SWIMZLINE = 13;
	
	/** The swimmer is a swim to beam line swim */
	public static final int SWIMBEAMLINE = 14;

	// the file to hold the serialized objects
	private static final String FILE_NAME = System.getProperty("user.home") + File.separator + "swimmerSerial.dat";

	/**
	 * Initialize the file
	 */
	public static void initialize() {
		File file = new File(FILE_NAME);
		if (file.exists()) {
			file.delete(); // Delete the file if it exists
		}
		
		CLAS12Swimmer.DEBUG = true; // Turn on debugging
	}
	
	/**
	 * Write an object to the file
	 * @param o the object to write
	 * @throws IOException
	 */
    public static void write(Object o) throws IOException {
        // Check if the file already exists and is not empty
        boolean append = new File(FILE_NAME).exists();
        try (FileOutputStream fos = new FileOutputStream(FILE_NAME, true);
             ObjectOutputStream oos = append ?
                     new AppendingObjectOutputStream(fos) :
                     new ObjectOutputStream(fos)) {
            oos.writeObject(o);
        }
    }
    
	/**
	 * Read the file and handle the objects as needed
	 */
    public static void read() {
        List<SwimInput> swimmerIns = new ArrayList<>();
        List<SwimOutput> swimmerOuts = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(FILE_NAME);
             ObjectInputStream ois = new ObjectInputStream(fis)) {
            while (true) {
                try {
                    Object obj = ois.readObject();
                    if (obj instanceof SwimInput) {
                        swimmerIns.add((SwimInput) obj);
                    } else if (obj instanceof SwimOutput) {
                        swimmerOuts.add((SwimOutput) obj);
                    }
                } catch (EOFException e) {
                    break; // End of file reached
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }

        // Handle the collections as needed
    }

    
    // Custom ObjectOutputStream to handle appending
    private static class AppendingObjectOutputStream extends ObjectOutputStream {
        public AppendingObjectOutputStream(OutputStream out) throws IOException {
            super(out);
        }

        @Override
        protected void writeStreamHeader() throws IOException {
            // Do not write a header when appending
            reset();
        }
    }

}

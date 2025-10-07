package org.jlab.io.root;

/**
 * Copied from https://github.com/drewkenjo/j2root
 */
public class HoistJNI {
    static { System.loadLibrary("hoistJNI"); }
    public native void createFile(String fname);
    public native void closeFile(String fname);
    public native void mkdir(String fname,String path);
    public native void writeH1F(String fname, String path, String hname, int nbins, double xmin, double xmax, float[] arr);
    public native void writeH2F(String fname, String path, String hname, int nxbins, double xmin, double xmax, int nybins, double ymin, double ymax, float[] arr);
}

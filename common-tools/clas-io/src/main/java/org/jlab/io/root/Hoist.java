package org.jlab.io.root;

import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;

/**
 * Copied from https://github.com/drewkenjo/j2root
 */
public class Hoist {
    
    HoistJNI hoist = new HoistJNI();

    private String fname;
    private String path = "";
    
    public Hoist(String fname) {
        this.fname = fname;
        hoist.createFile(fname);
    }
    
    public void close() {
        hoist.closeFile(fname);
    }

    public void mkdir(String path) {
        path = path.replaceFirst("^/*","").replaceAll("/*\\$","");
        hoist.mkdir(fname, path);
        this.path = path;
    }

    public void cd(String path) {
        this.path = path.replaceFirst("^/*","").replaceAll("/*\\$","");
    }

    public void write(H1F h1) {
        String fullpath = path + "/" + h1.getName();
        int ind = fullpath.lastIndexOf("/");
        String relpath = fullpath.substring(0,ind);
        relpath = relpath.replaceFirst("^/*","").replaceAll("/*\\$","");
        String name = fullpath.substring(ind+1);
        hoist.writeH1F(fname, relpath, name,
            h1.getXaxis().getNBins(), h1.getXaxis().min(), h1.getXaxis().max(),
            h1.getData());
    }

    public void write(H2F h2) {
        String fullpath = path + "/" + h2.getName();
        int ind = fullpath.lastIndexOf("/");
        String relpath = fullpath.substring(0,ind);
        relpath = relpath.replaceFirst("^/*","").replaceAll("/*\\$","");
        String name = fullpath.substring(ind+1);
        int xsize = h2.getDataSize(0), ysize = h2.getDataSize(1), ii = 0;
        float[] data = new float[xsize*ysize];
        for(int ix=0;ix<xsize;ix++)
            for(int iy=0;iy<ysize;iy++)
                data[ii++] = (float) h2.getData(ix,iy);
        hoist.writeH2F(fname, relpath, name,
            h2.getXAxis().getNBins(), h2.getXAxis().min(), h2.getXAxis().max(),
            h2.getYAxis().getNBins(), h2.getYAxis().min(), h2.getYAxis().max(),
            data);
    }
}
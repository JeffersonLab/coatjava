package org.jlab.rec.atof.Hit;

import org.jlab.geom.base.*;
import org.jlab.geom.detector.alert.ATOF.*;
import org.jlab.detector.calib.utils.DatabaseConstantProvider;
import org.jlab.geom.prim.Point3D;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;

/**
 *
 * @author npilleux
 */
public class AtofHit {

    private int sector, layer, component, order;
    private int TDC, ToT;
    private double time, energy, x, y, z;
    private String type;
    private boolean is_in_a_cluster;

    public int getSector() {
        return sector;
    }

    public void setSector(int sector) {
        this.sector = sector;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }
    
    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getComponent() {
        return component;
    }

    public void setComponent(int component) {
        this.component = component;
    }
    
    public int getTDC() {
        return TDC;
    }

    public void setTDC(int tdc) {
        this.TDC = tdc;
    }

    public int getToT() {
        return ToT;
    }

    public void setToT(int tot) {
        this.ToT = tot;
    }
    
    public double getTime() {
        return time;
    }

    public void setTime(double time) {
        this.time = time;
    }

    public double getEnergy() {
        return energy;
    }

    public void setEnergy(double energy) {
        this.energy = energy;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }
    
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    
    public boolean getIs_in_a_cluster() {
        return is_in_a_cluster;
    }

    public void setIs_in_a_cluster(boolean is_in_a_cluster) {
        this.is_in_a_cluster = is_in_a_cluster;
    }

    public String makeType(){
        String type = "undefined";
        if(this.component == 10 && this.order==0) type = "bar down";
        else if(this.component == 10 && this.order==1) type = "bar up";
        else if(this.component < 10) type = "wedge";
        this.type = type;
        return type;
    }
    
    public int TDC_to_time()
    {
      double some_conversion = 0;
      if(this.type == "wedge")
      {
          some_conversion = 10;//read calib constants here
      }
      else if(this.type == "bar up")
      {
          some_conversion = 10;
      }
      else if(this.type == "bar down")
      {
          some_conversion = 10;
      }
      else
      {
         return 0;
      }
      this.time = some_conversion * this.TDC;
      return 1;
    }
    
    public int ToT_to_energy()
    {
      double some_conversion = 0;
      if(this.type == "wedge")
      {
          some_conversion = 10;//read calib constants here
      }
      else if(this.type == "bar up")
      {
          some_conversion = 10;
      }
      else if(this.type == "bar down")
      {
          some_conversion = 10;
      }
      else
      {
         return 0;
      }
      this.energy = some_conversion * this.ToT;
      return 1;
    }
    
    public int slc_to_xyz(Detector atof)
    { 
        int sl = 999;
        if(this.type == "wedge") sl = 1;
        else if(this.type == "bar up" || this.type == "bar down") sl = 0;
        else return 0;

        Component comp = atof.getSector(this.sector).getSuperlayer(sl).getLayer(this.layer).getComponent(this.component);
        Point3D midpoint = comp.getMidpoint();
        this.x = midpoint.x();
        this.y = midpoint.y();
        this.z = midpoint.z();
        return 1;
    }
    
    public boolean barmatch(AtofHit hit2match)
    {
        if(this.getSector() != hit2match.getSector()) return false; //System.out.print("Two hits in different sectors \n");
        else if(this.getLayer() != hit2match.getLayer()) return false; //System.out.print("Two hits in different layers \n");
        else if(this.getComponent() != 10 || hit2match.getComponent() != 10) return false; //System.out.print("At least one hit is not in the bar \n");
        else if(this.getOrder() == hit2match.getOrder()) return false; //System.out.print("Two hits in same SiPM \n");
        else return true;
    }
    
    public double getPhi()
    {
        return Math.atan2(this.y, this.x);
    }
    
    public AtofHit(int sector, int layer, int component, int order, int tdc, int tot, Detector atof) 
	{
		this.sector = sector;
		this.layer = layer;
		this.component = component;	
                this.order = order;
		this.TDC = tdc;
		this.ToT = tot;
                this.is_in_a_cluster = false;
                
                this.makeType();
                int is_ok = this.TDC_to_time();
                if(is_ok==1) is_ok = this.ToT_to_energy();
                if(is_ok==1) is_ok = this.slc_to_xyz(atof);
	}
    
    public AtofHit() 
	{
	}

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // TODO code application logic here
        AlertTOFFactory factory = new AlertTOFFactory();
        DatabaseConstantProvider cp = new DatabaseConstantProvider(11, "default");
        Detector atof = factory.createDetectorCLAS(cp);
        
        //Input to be read
        String input = "/Users/npilleux/Desktop/alert/atof-reconstruction/coatjava/reconstruction/alert/src/main/java/org/jlab/rec/atof/Hit/test_tdc_atof.hipo";
        HipoDataSource reader = new HipoDataSource();
        reader.open(input);

        int event_number = 0;
        while (reader.hasEvent()) {
            DataEvent event = (DataEvent) reader.getNextEvent();
            event_number++;
            DataBank bank = event.getBank("ATOF::tdc");
            int nt = bank.rows(); // number of tracks 
            
            for (int i = 0; i < nt; i++) {
                int sector = bank.getInt("sector", i);
                int layer = bank.getInt("layer", i);
                int component = bank.getInt("component", i);
                int order = bank.getInt("order", i);
                int tdc = bank.getInt("TDC", i);
                int tot = bank.getInt("ToT", i);
                AtofHit hit = new AtofHit(sector, layer, component, order, tdc, tot, atof);
                System.out.print(hit.getX() + "\n");
            }  
        }
    }
}


package cnuphys.adaptiveSwim.test;

import cnuphys.adaptiveSwim.geometry.Plane;
import cnuphys.lund.AsciiReadSupport;

public class PlaneTestData {

	//usual swim parameters
	int charge;
	public double xo;
	public double yo;
	public double zo;
	public double p;
	public double theta;
	public double phi;

	//for making plane
	public double nx;
	public double ny;
	public double nz;
	public double px;
	public double py;
	public double pz;


	public double accuracy;
	public double sMax;
	public double stepSize;

	public Plane plane;

	public PlaneTestData() {

	}

	/**
	 * Will tokenize a string from the csv file
	 * @param s
	 */
	public PlaneTestData(String s) {
		String tokens[] = AsciiReadSupport.tokens(s, ",");

		int index = 0;

		try {
			charge = Integer.parseInt(tokens[index++]);

			xo = 100*Double.parseDouble(tokens[index++]);
			yo = 100*Double.parseDouble(tokens[index++]);
			zo = 100*Double.parseDouble(tokens[index++]);
			p = Double.parseDouble(tokens[index++]);
			theta = Double.parseDouble(tokens[index++]);
			phi = Double.parseDouble(tokens[index++]);

			nx = Double.parseDouble(tokens[index++]);
			ny = Double.parseDouble(tokens[index++]);
			nz = Double.parseDouble(tokens[index++]);
			px = 100*Double.parseDouble(tokens[index++]);
			py = 100*Double.parseDouble(tokens[index++]);
			pz = 100*Double.parseDouble(tokens[index++]);

			accuracy = 100*Double.parseDouble(tokens[index++]);
			sMax = 100*Double.parseDouble(tokens[index++]);
			stepSize = 100*Double.parseDouble(tokens[index++]);

			plane = new Plane(nx, ny, nz, px, py, pz);
		} catch (ArrayIndexOutOfBoundsException e) {

			System.out.println("OOB Bad Line in planedata: [" + s + "]");
			System.exit(1);

		}catch (NumberFormatException e) {

			System.out.println("NFE Bad Line in planedata: [" + s + "]");
			System.exit(1);

		}


	}

	public PlaneTestData toMeters() {
		PlaneTestData data = new PlaneTestData();
		data.charge = charge;
		data.xo = xo/100;
		data.yo = yo/100;
		data.zo = zo/100;
		data.p = p;
		data.theta = theta;
		data.phi = phi;
		data.nx = nx;
		data.ny = ny;
		data.nz = nz;
		data.px = px/100;
		data.py = py/100;
		data.pz = pz/100;
		data.accuracy = accuracy/100;
		data.sMax = sMax/100;
		data.stepSize = stepSize/100;
		data.plane = new Plane(data.nx, data.ny, data.nz, data.px, data.py, data.pz);
		return data;

	}



}

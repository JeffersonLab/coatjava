package cnuphys.CLAS12Swim;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Random;

public class TestIntegrators {
	
	private static void testBasicSwim(int n, long seed) {
		System.out.println("\n\n-----------\nTesting the basic swim to a final path length.");
		
		
		CLAS12Swimmer swimmers[] = { new CLAS12Swimmer(EIntegrator.DormandPrince),
				new CLAS12Swimmer(EIntegrator.Fehlberg), new CLAS12Swimmer(EIntegrator.CashKarp) };
		
		//get some random data
		RandomTestData rtd = new RandomTestData(n, seed);
		
		double h = 1e-4; // starting stepsize in m

		double sMax = 800; // cm
		double tolerance = 1.0e-6;

		CLAS12SwimResult result = null;
		
		//prime the pumps
		System.out.println("Priming the pumps...");
		for (CLAS12Swimmer swimmer : swimmers) {
			for (int i = 0; i < 5; i++) {
				result = swimmer.swim(rtd.charge[i], rtd.xo[i], rtd.yo[i], rtd.zo[i], rtd.p[i], rtd.theta[i], rtd.phi[i], sMax,
						h, tolerance);
			}
			System.out.println("SOLVER: " + swimmer.getSolver().name() + "\n" + result);

		}
		
		System.out.println("Timing tests...");
		
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        bean.setThreadCpuTimeEnabled(true);
         
		
		for (CLAS12Swimmer swimmer : swimmers) {
		       // Measure CPU time before method execution
	        long start = bean.getCurrentThreadCpuTime();

	        
			for (int i = 0; i < n; i++) {
				result = swimmer.swim(rtd.charge[i], rtd.xo[i], rtd.yo[i], rtd.zo[i], rtd.p[i], rtd.theta[i], rtd.phi[i], sMax,
						h, tolerance);
			}
			double duration = (bean.getCurrentThreadCpuTime() - start) / 1.0e9;

			System.out.println("SOLVER: " + swimmer.getSolver().name() + "  time: " + duration + " s");
		}
		
	}
	
	private static void testSwimRho(int n, long seed) {
		System.out.println("\n\n-----------\nTesting the swim to a target rho.");
		
		
		CLAS12Swimmer swimmers[] = { new CLAS12Swimmer(EIntegrator.DormandPrince),
				new CLAS12Swimmer(EIntegrator.Fehlberg), new CLAS12Swimmer(EIntegrator.CashKarp) };
		
		//get some random data
		RandomTestData rtd = new RandomTestData(n, seed);
		
		//random target rho in cm
	    double rhoTarget[] = new double[n];
	    Random rand = new Random(seed);
	    for (int i= 0; i < n; i++) {
	    	rhoTarget[i] = 50 + 400*rand.nextDouble();
	    }

		
		double h = 1e-4; // starting stepsize in m

		double sMax = 800; // cm
		double tolerance = 1.0e-6;
		
		double accuracy = 1e-5; // m


		CLAS12SwimResult result = null;
		
		//prime the pumps
		System.out.println("Priming the pumps...");
		for (CLAS12Swimmer swimmer : swimmers) {
			for (int i = 0; i < 10; i++) {
				result = swimmer.swimRho(rtd.charge[i], rtd.xo[i], rtd.yo[i], rtd.zo[i], rtd.p[i], rtd.theta[i], rtd.phi[i], rhoTarget[i],
						sMax, accuracy, h, tolerance);
				System.out.println("SOLVER: " + swimmer.getSolver().name() + "\n" + result);
			}
	//		System.out.println("SOLVER: " + swimmer.getSolver().name() + "\n" + result);
		}
		
		System.out.println("\nAccuracy tests...");
		for (CLAS12Swimmer swimmer : swimmers) {
			
			double count = 0;
			double delMax = Double.NEGATIVE_INFINITY;
		        
			for (int i = 0; i < n; i++) {
				
				result = swimmer.swimRho(rtd.charge[i], rtd.xo[i], rtd.yo[i], rtd.zo[i], rtd.p[i], rtd.theta[i], rtd.phi[i], rhoTarget[i],
						sMax, accuracy, h, tolerance);
				
				if (result.getStatus() == CLAS12Swimmer.SWIM_SUCCESS) {
					count++;
					
					double rho = result.getFinalRho();
					double del = Math.abs(rho - rhoTarget[i]);
					if (del > 500) {
						System.err.println("");
					}
					delMax = Math.max(delMax, del);
				}
			}
			System.out.println("SOLVER: " + swimmer.getSolver().name() + "  good count: " + count + "  max del: " + delMax + " cm");
		}

		
		System.out.println("\nTiming tests...");
		
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        bean.setThreadCpuTimeEnabled(true);
         
		
		for (CLAS12Swimmer swimmer : swimmers) {
		       // Measure CPU time before method execution
	        long start = bean.getCurrentThreadCpuTime();

	        
			for (int i = 0; i < n; i++) {
				result = swimmer.swimRho(rtd.charge[i], rtd.xo[i], rtd.yo[i], rtd.zo[i], rtd.p[i], rtd.theta[i], rtd.phi[i], rhoTarget[i],
						sMax, accuracy, h, tolerance);
					}
			double duration = (bean.getCurrentThreadCpuTime() - start) / 1.0e9;

			System.out.println("SOLVER: " + swimmer.getSolver().name() + "  time: " + duration + " s");
		}
		
	}
	
	
	public static void main(String[] arg) {
		
		
		
		testBasicSwim(4, 565834934);
		
		//testSwimRho(100000, 967584934);
	}

}

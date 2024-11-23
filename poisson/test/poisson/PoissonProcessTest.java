package poisson;

import java.util.Random;

public class PoissonProcessTest {

    static final int SAMPLES = 100;

    public static void main(String[] args) {
	if ( args.length != 1 )
	    System.out.println("usage: poissonseq <lambda>");
	else
	    testArrivalTimes(Double.parseDouble(args[0]));
    }

    private static void testArrivalTimes(double lambda) {
        PoissonProcess pp = new PoissonProcess(lambda, new Random(0) );
	SampleValues s = new SampleValues("Arrival Times / " + lambda);
	double t = 0d;
	for (int i = 1; i <= SAMPLES; i++) {
	    t += pp.timeForNextEvent();
	    System.out.println("next event at -> " + t*60.0);
	}
    }
}

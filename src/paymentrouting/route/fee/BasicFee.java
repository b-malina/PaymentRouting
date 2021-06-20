package paymentrouting.route.fee;

import gtna.graph.Edge;
import gtna.graph.Graph;
import gtna.graph.GraphProperty;
import paymentrouting.datasets.TransactionList;
import treeembedding.credit.CreditLinks;
import treeembedding.credit.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BasicFee extends FeeComputation {
	double feeRate;
	double baseRate;
	public int hops;


	public BasicFee(double baseRate, double feeRate) {
		super("BASIC_FEE_" + baseRate + "_" + feeRate);
		this.feeRate = feeRate;
		this.baseRate = baseRate;
		hops = 0;
	}

	@Override
	public double getFee(Graph g, CreditLinks edgeweights, double val, int[] path) {
//		return this.feeRate * val + this.baseRate;
		return this.getFee(g, edgeweights, val, path[0], -1);
	}


//	@Override
//	public double getFee(Graph g, CreditLinks edgeweights, double val, int s, int t) {
//		Transaction[] transactions = ((TransactionList) g.getProperty("TRANSACTION_LIST")).getTransactions();
//		// divide fee by nr neighbours * 4
//		int outDegree = g.getNodes()[s].getOutDegree();
//
////		System.out.println("SHOULD NOT PRINT");
//		if (t != -1) {
//			double b1 = edgeweights.getPot(s, t);
//			double b2 = edgeweights.getPot(t, s);
//			double capacity = b1 + b2;
//		}
//		return val / (g.getNodes()[s].getOutgoingEdges().length * 4);
//	}

	@Override
	public double getFee(Graph g, CreditLinks edgeweights, double val, int s, int t) {
		Transaction[] transactions = ((TransactionList) g.getProperty("TRANSACTION_LIST")).getTransactions();
		ArrayList<Double> outValues = new ArrayList<>();
		double sum = 0;
		for (int k : g.getNodes()[s].getOutgoingEdges()) {
			double b = edgeweights.getPot(s, k);
			outValues.add(b);
			sum += b;
		}
		double sd = this.getStandardDeviation(sum, s, outValues);
		double maxValInterval = 0.55;
		double minValInterval = 0.35;
		if (t == -1) {
			maxValInterval = 0.8;
			minValInterval = 0.6;
		}
		double normalized = normalize(sd, getMin(outValues), getMax(outValues), minValInterval, maxValInterval);

		if (t != -1)
			normalized /= (this.hops + 1);
//		if (normalized > 0)
//			normalized *= this.feeRate;

// nodes closer to src get a smaller fraction because the remaining fee is higher then
// (rem fee decreases once with the hops, so the normalized factor should increase)
//		normalized *= Math.pow(2, this.hops);
//		System.out.println("\n\nSD " + sd + " NORMALIZD SD " + normalized);
//		System.out.println("min outval " + getMin(outValues) + " max outval " + getMax(outValues));
//		System.out.println("min interv " + minValInterval + " max interv " + maxValInterval);
//		System.out.println("\n\n");
//		System.out.println("normalized and rem fee " + normalized + " " + remainingFee);
//		if (t == -1)
//			System.out.println("FEE AT SRC " + remainingFee * normalized);
//		else
//			System.out.println("FEE AT INTERM " + remainingFee * normalized);
		// do not use all remaining fee
//		if (val - val * normalized < 0 )
//			if (t != -1) {
//				System.out.println("interm too low rem fee " +val + " " + normalized*val);
//				return val * normalized * 10E-4;
//			} else {
//				System.out.println("src too low rem fee " + val + " " + normalized*val);
//				return val * normalized * 10E-2;
//			}
//		else {

//			System.out.println("\n   sd=" + sd + " min " + getMin(outValues) + " max " + getMax(outValues));
//			System.out.println("   low=" + minValInterval + "   high=" + maxValInterval + "   remf=" + remainingFee);
//			System.out.println("normal " + normalized + " fee ----- " + remainingFee * normalized);
//		System.out.println("FEE IS " + val*normalized + " tx=" + val + " nrml=" + normalized);
		if (t == -1)
			return val * normalized * this.feeRate + this.baseRate;
		else
			return val * normalized * this.feeRate;
	}

	public static double getStandardDeviation(double sum, int s, ArrayList<Double> outValues) {
		double mean = sum / (double) outValues.size();
		double temp = 0;

		for (int i = 0; i < outValues.size(); i++) {
			double val = outValues.get(i);
			double squrDiffToMean = Math.pow(val - mean, 2);
			temp += squrDiffToMean;
		}
		double meanOfDiffs = (double) temp / (double) (outValues.size());
		return Math.sqrt(meanOfDiffs);
	}

	public double getMax(ArrayList<Double> outValues) {
		double maxVal = Double.MIN_VALUE;
		for (Double k : outValues)
			if (k > maxVal)
				maxVal = k;
		return maxVal;
	}

	public double getMin(ArrayList<Double> outValues) {
		double minVal = Double.MAX_VALUE;
		for (Double k : outValues)
			if (k < minVal)
				minVal = k;
		return minVal;
	}

	// if normalized value is lower than the fee rate, do not use it
	public double normalize(double value, double minValue, double maxValue, double minValInterval, double maxValInterval) {
		return Math.max(((maxValInterval - minValInterval) * (value - minValue)) / (maxValue - minValue) + minValInterval, feeRate);
	}
}


//		//		double capacity = edgeweights.getTotalCapacity(s, t);
////		double diff = val / capacity;
////		double rate = 10e-5 * diff;
//
//		System.out.println("\n\n\n!!!Basic Fee: val=" + val + " from=" + s + " to=" + t + " capacity=" + capacity);
//		System.out.println("basic fee, diff=" + diff + " rate=" + rate + " final=" + (this.defaultFee + val * rate));
////		for (Map.Entry<Edge, double[]> cr : edgeweights.getWeights()) {
////			System.out.println("\nBasic Fee: edge" + cr.getKey());
////			for (double edgecredit : cr.getValue())
////				System.out.print("credit=" + edgecredit + ", ");
////		}
////		for (Map.Entry<String, GraphProperty> entry : g.getProperties().entrySet())
////			System.out.println("\nBasic Fee:" + entry.getKey() + " " + entry.getValue());
//		double fee = this.defaultFee + val * rate;
////		return fee;
//		System.out.println("BASIC FEE OF: " + (val / g.getNodeCount()));
//		return val / g.getNodeCount();
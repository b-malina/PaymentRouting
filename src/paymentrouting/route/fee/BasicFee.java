package paymentrouting.route.fee;

import gtna.graph.Edge;
import gtna.graph.Graph;
import gtna.graph.GraphProperty;
import paymentrouting.datasets.TransactionList;
import treeembedding.credit.CreditLinks;
import treeembedding.credit.Transaction;

import java.util.HashMap;
import java.util.Map;

public class BasicFee extends FeeComputation {
	double defaultFee;

	public BasicFee(double defaultFee) {
		super("BASIC_FEE_" + defaultFee);
		this.defaultFee = defaultFee;

	}

	@Override
	public double getFee(Graph g, CreditLinks edgeweights, double val, int s, int t) {
		Transaction[] transactions = ((TransactionList) g.getProperty("TRANSACTION_LIST")).getTransactions();
		return val/10E3+val/10E6;
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
	}
}

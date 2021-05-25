package paymentrouting.route.fee;

import gtna.graph.Edge;
import gtna.graph.Graph;
import treeembedding.credit.CreditLinks;

import java.util.Map;

public class BasicFee extends FeeComputation {
	public BasicFee() {
		super("BaSiC FeE??");

	}

	@Override
	public double getFee(Graph g, CreditLinks edgeweights, double val, int s, int t) {
		for (Map.Entry<Edge, double[]> cr : edgeweights.getWeights()) {
			System.out.println("edge" + cr.getKey());
			for (double edgecredit : cr.getValue())
				System.out.print(" credit" + edgecredit + ", ");
		}
		return 0;
	}
}

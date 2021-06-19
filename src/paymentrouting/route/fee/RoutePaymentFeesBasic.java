package paymentrouting.route.fee;

import gtna.data.Single;
import gtna.graph.Edge;
import gtna.graph.Graph;
import gtna.graph.Node;
import gtna.metrics.Metric;
import gtna.networks.Network;
import gtna.util.Distribution;
import gtna.util.parameter.Parameter;
import gtna.util.parameter.StringParameter;
import paymentrouting.datasets.TransactionList;
import paymentrouting.route.PartialPath;
import paymentrouting.route.PathSelection;
import paymentrouting.route.RoutePayment;
import treeembedding.credit.CreditLinks;
import treeembedding.credit.Transaction;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.*;

public class RoutePaymentFeesBasic extends RoutePayment {
	FeeComputation fc;
	private double fee_av;
	private double fee_med;
	private double fee_q1;
	private double fee_q2;

	HashMap<Edge, double[]> minusPots;
	HashMap<Edge, Double> originalWeight;

	public RoutePaymentFeesBasic(PathSelection ps, int trials, boolean up,
	                             FeeComputation fee) {
		super(ps, trials, up,
				new Parameter[]{new StringParameter("FEE RP", fee.getName())});
		this.fc = fee;
	}

	public RoutePaymentFeesBasic(PathSelection ps, int trials, boolean up,
	                             FeeComputation fee, int epoch) {
		super(ps, trials, up, epoch, new Parameter[]{new StringParameter("FEE RP", fee.getName()),});
		this.fc = fee;
	}

	@Override
	public void computeData(Graph g, Network n, HashMap<String, Metric> m) {
		//init values
		rand = new Random();
		this.select.initRoutingInfo(g, rand);
		this.edgeweights = (CreditLinks) g.getProperty("CREDIT_LINKS");
		HashMap<Edge, Double> originalAll = new HashMap<Edge, Double>();
		this.transactions = ((TransactionList) g.getProperty("TRANSACTION_LIST")).getTransactions();
		Node[] nodes = g.getNodes();

		this.avHops = 0;
		this.avHopsSucc = 0;
		this.avMess = 0;
		this.avMessSucc = 0;
		this.successFirst = 0;
		this.success = 0;
		long[] trys = new long[2];
		long[] path = new long[2];
		long[] pathSucc = new long[2];
		long[] mes = new long[2];
		long[] mesSucc = new long[2];
		int count = this.transactions.length;

		// fee paid by each source; totalFees[index] = fee paid by node initiating indexth tx
		Vector<Double> totalFees = new Vector<Double>();
		int len = this.transactions.length / this.tInterval;
		int rest = this.transactions.length % this.tInterval;
		if (rest == 0) {
			this.succTime = new double[len];
		} else {
			this.succTime = new double[len + 1];
		}
		int slot = 0;
		File newFile = new File("results_" + this.fc.getName() + " " + this.select.getName() + ".txt");
		File evalFile = new File("eval;" + this.fc.getName() + ";" + this.select.getName() + ".txt");
		PrintWriter writer = null;
		PrintWriter eval = null;
		try {
			writer = new PrintWriter(newFile, "UTF-8");
			eval = new PrintWriter(evalFile, "UTF-8");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		//iterate over transactions
		writer.println("FEE_MODEL: " + fc.getName());
		eval.println("FEE_MODEL: " + fc.getName());
		eval.println("SPLITTING_PROTOCOL: " + this.select.getName());
		for (int i = 0; i < this.transactions.length; i++) {
			Transaction tr = this.transactions[i];
			writer.println("\n\n\n NEW TX------------");

			int src = tr.getSrc();
			int dst = tr.getDst();
			double val = tr.getVal();

			writer.println("TX_VAL: " + val);
			writer.println("SRC: " + src);
			writer.println("DST: " + dst);

			eval.println("\nTX_VAL: " + val);

			if (log) System.out.println("\n\n\nSrc-dst " + src + "," + dst + " " + val);
			boolean s = true; //successful
			int h = 0; //hops
			int x = 0; //messages

			// set fee value (at src)
			int[] ps = new int[1];
			ps[0] = src;
			double fee = fc.getFee(g, edgeweights, val, ps);
			writer.println("FEE_AT_SRC: " + fee);

			//attempt at most trials times
			for (int t = 0; t < this.trials; t++) {
				String logTxt = "";
				// payment obtained by dst
				double finalVal = 0;
				writer.println("TRIAL: " + t);
				eval.println("TRIAL: " + t);
				//set initial set of current nodes and partial payment values to (src, totalVal)
				Vector<PartialPath> pps = new Vector<PartialPath>();
//				pps.add(new PartialPath(src, val, new Vector<Integer>(), 0, fee));
				//some routing algorithm split over multiple dimensions in the beginning (!= splitting during routing)
				double[] splitVal = this.splitRealities(val, g.getNodes()[src].getOutDegree(), rand);

				// FEE is 0; each node adds its obtained fee to the fee field
				for (int a = 0; a < g.getNodes()[src].getOutDegree(); a++) {
//					double tempFee = fc.getFee(g, edgeweights, splitVal[a], src, a);
					pps.add(new PartialPath(src, splitVal[a], new Vector<Integer>(), 0, new Vector<>()));
				}

				//System.out.println("tx value=" + val + ", fee=" + fee);
				boolean[] excluded = new boolean[nodes.length];
				this.minusPots = new HashMap<Edge, double[]>();
				this.originalWeight = new HashMap<Edge, Double>(); //updated credit links

				//while current set of nodes is not empty
				while (!pps.isEmpty()) {
					if (log) {
						System.out.println("Hop " + h + " with " + pps.size() + " links ");
					}
					Vector<PartialPath> next = new Vector<PartialPath>();
					//iterate over set of current set of nodes
					for (int j = 0; j < pps.size(); j++) {
						PartialPath pp = pps.get(j);
						int cur = pp.node;
						int pre = -1;
						// take previous and exclude prev nodes
						Vector<Integer> past = pp.pre;
						if (past.size() > 0) {
							pre = past.get(past.size() - 1);
						}
						for (int l = 0; l < past.size(); l++) {
							excluded[past.get(l)] = true;
						}

						if (log) System.out.println("Routing at cur " + cur + " in dim " + pp.reality);

						//getNextVals -> distribution of payment value over neighbors
						double[] partVals = this.select.getNextsVals(g, cur, dst,
								pre, excluded, this, pp.val, rand, pp.reality);

						if (log) if (partVals == null) System.out.println("No next hop found");
						for (int l = 0; l < past.size(); l++) {
							excluded[past.get(l)] = false;
						}
						//add neighbors that are not the dest to new set of current nodes
						if (partVals != null) {
							past.add(cur);
							int[] out = nodes[cur].getOutgoingEdges();
							for (int k = 0; k < partVals.length; k++) {
								// compute fee earned by current intermediary
								double tempFee = 0;
								if (partVals[k] > 0) {
									// if current node is not the src
									if (pre != -1) {
										tempFee = fc.getFee(g, edgeweights, pp.val, cur, out[k]);
										pp.fees.add(tempFee);
									}
									//increase message count
									x++;

									//update vals
									Edge e = edgeweights.makeEdge(cur, out[k]);
									double w = edgeweights.getWeight(e);
									if (!originalWeight.containsKey(e)) {
										originalWeight.put(e, w); //store balance before this payment if later reset due to, e.g., failure
									}
									if (this.update && !originalAll.containsKey(e)) {
										originalAll.put(e, w); //store original balance before this execution (for other runs with different parameters)
									}
									edgeweights.setWeight(cur, out[k], partVals[k]);//set to new balance


									//add links to sets
									if (out[k] != dst) {
										//add partf as previous link needs to sustain it
										if (log)
											System.out.println("LOG out != dst  fees=" + pp.fees + " tempfee= " + tempFee + " ");

										next.add(new PartialPath(out[k], partVals[k],
												(Vector<Integer>) past.clone(), pp.reality, (Vector<Double>) (pp.fees).clone()));//add new intermediary to path
									}

									if (log) {
										System.out.println("add link (" + cur + "," + out[k] + ") with val " + partVals[k] + " --- rfee " + pp.fees);
									}
									if (out[k] == dst) {
										writer.println("collected_fees: " + pp.fees);

//										System.out.println("LOG out=dst node  " + pp.node + " pre= " + pp.pre + " tx amount=" + pp.val + " FEES OUT=dst" + pp.fees);
										if (log) System.out.println("NOTE out = dst" + partVals[k]);
										finalVal += partVals[k];

										totalFees = pp.fees;
										double sum = 0;
										for (double f : totalFees)
											sum += f;

//										System.out.println("FINAL FEE= " + sum + " of subfees " + totalFees.size()  +" " +  pp.fees.size()+ " FOR TX=" + val + "\nDONE\n");
										writer.println("FEE: " + tempFee + "  collected_fees: " + pp.fees + "\n, pre=" + pre + ", cur=" + cur + " to " + out[k] + ", amount=" + pp.val + " past= " + pp.pre);

										writer.println("FINAL FEE= " + sum + " of subfees " + totalFees.size() + " FOR TX=" + val + "\nDONE\n");
										if (this.fc.getName().contains("BASIC")) {
											if (fee < sum) {
												s = false;
												writer.println("CANNOT PAY fee of " + sum + " with initial fee of " + fee);
												logTxt = "FEE TOO LOW";
											} else {
												s = true;
												writer.println("CAN PAY fee of " + sum + " with initial fee of " + fee);
											}
										}
										eval.print("FEES: ");
										for (double ppfee : pp.fees)
											eval.print(ppfee + " ");
										eval.println();
										eval.println("OFFERED_FEE: " + fee);
										eval.println("FINAL_FEE: " + sum);
										eval.println("NODES: " + (pp.pre.size() + 1));
									}
//									else if (cur == dst) System.out.println("NOTE cur = dst");
								}
							}
						} else {
//							//failure to split for one dimension
							if (log) {
								System.out.println("fail");
								//throw new IllegalArgumentException();
							}
							s = false;
							logTxt = "CANNOT SPLIT";
//							fees[pp.reality] = Double.MAX_VALUE;
						}
					}

					pps = this.merge(next); //merge paths: if the same payment arrived at a node via two paths: merge into one
					h++; //increase hops
					if (fc instanceof BasicFee) {
						BasicFee bf = (BasicFee) fc;
						bf.hops = h;
					}
				}

				if (!s || val < finalVal) {
					if (val < finalVal)
						logTxt = "LOW PAYMENT: " + (val - finalVal);
					h--;
					//payments were not made -> return to original weights
					this.weightUpdate(edgeweights, originalWeight);
					if (log) {
						System.out.println("Failure");
					}
					writer.println("FAILURE");
				} else {
//					System.out.println("SUCC     v=" + val + "        fival=" + finalVal + "       diff=" + (val - finalVal));

//					if (val < finalVal) {
//						eval.println("LOW_PAYMENT;DIFF: " + (val - finalVal));
//						eval.println("RECEIVED_BY_DST: " + finalVal + " INIT=" + val + " ------- " + (val - finalVal));
//					}
					if (!this.update) {
						//return credit links to original state
						this.weightUpdate(edgeweights, originalWeight);
					}

					//update stats for this transaction
					pathSucc = inc(pathSucc, h);
					mesSucc = inc(mesSucc, x);
					this.succTime[slot]++;

					this.success++;
					if (t == 0) {
						this.successFirst++;
					}
					trys = inc(trys, t);
					if (log) {
						System.out.println("Success");
					}
					writer.println("SUCCESS");
				}

				path = inc(path, h);
				mes = inc(mes, x);
				if ((i + 1) % this.tInterval == 0) {
					this.succTime[slot] = this.succTime[slot] / this.tInterval;
					slot++;
				}
				if (s) {
					eval.println("FAILURE: " + "False");
					eval.println("SUCCESS: True");
				} else {
					eval.println("FAILURE: " + logTxt);
					eval.println("SUCCESS: False");
				}

			}

			//recompute spanning trees
			if (this.recompute_epoch != Integer.MAX_VALUE && (i + 1) % this.recompute_epoch == 0) {
				this.select.initRoutingInfo(g, rand);
			}
		}
//		writer.println("The first line");


//		System.out.println("\nPAY FEES " + totalFees.size());
//		for (double v : totalFees) {
//			System.out.print(v + " ");
//		}
//		System.out.println("DONE\n\n");

		//compute final stats
		this.hopDistribution = new Distribution(path, count);
		this.messageDistribution = new Distribution(mes, count);
		this.hopDistributionSucc = new Distribution(pathSucc, (int) this.success);
		this.messageDistributionSucc = new Distribution(mesSucc, (int) this.success);
		this.trysDistribution = new Distribution(trys, count);
		this.avHops = this.hopDistribution.getAverage();
		this.avHopsSucc = this.hopDistributionSucc.getAverage();
		this.avMess = this.messageDistribution.getAverage();
		this.avMessSucc = this.messageDistributionSucc.getAverage();
		this.success = this.success / this.transactions.length;
		this.successFirst = this.successFirst / this.transactions.length;
		double[] tf = new double[totalFees.size()];
		this.fee_av = 0;
		if (tf.length > 0) {
			for (int i = 0; i < tf.length; i++) {
				tf[i] = totalFees.get(i);
				this.fee_av = this.fee_av + tf[i];
			}
			this.fee_av = this.fee_av / tf.length;
			Arrays.sort(tf);
			int mid = tf.length / 2;
			if (tf.length % 2 == 0) {
				this.fee_med = tf[mid - 1] + tf[mid];
			} else {
				this.fee_med = tf[mid];
			}
			int q = tf.length / 4;
			this.fee_q1 = tf[q];
			this.fee_q2 = tf[mid + q];
		}
		if (rest > 0) {
			this.succTime[this.succTime.length - 1] = this.succTime[this.succTime.length - 1] / rest;
		}
		eval.println("FEE_Q1: " + fee_q1);
		eval.println("FEE_Q3: " + fee_q2);
		eval.println("FEE_AV: " + fee_av);
		eval.println("FEE_MED: " + fee_med);
		eval.println("SUCCESS_FRAC: " + success);
		eval.println("HOPS_AV: " + avHops);
		eval.println("HOPS_SUCC: " + avHopsSucc);

		//reset weights for further metrics using them
		if (this.update) {
			this.weightUpdate(edgeweights, originalAll);
		}
		writer.close();
		eval.close();
	}

	@Override
	public Single[] getSingles() {
		Single[] sSingle = super.getSingles();
		Single[] nSingle = new Single[sSingle.length + 4];
		for (int i = 0; i < sSingle.length; i++) {
			nSingle[i] = sSingle[i];
		}
		nSingle[sSingle.length] = new Single(this.key + "_FEE_AV", this.fee_av);
		nSingle[sSingle.length + 1] = new Single(this.key + "_FEE_MED", this.fee_med);
		nSingle[sSingle.length + 2] = new Single(this.key + "_FEE_Q1", this.fee_q1);
		nSingle[sSingle.length + 3] = new Single(this.key + "_FEE_Q3", this.fee_q2);

		return nSingle;
	}


	@Override
	public double computePotential(int s, int t) {
		Edge e = edgeweights.makeEdge(s, t);
		double[] diffs = this.minusPots.get(e);
		double subtract = 0;
		double val = this.edgeweights.getPot(s, t);
		if (diffs != null) {
			//obtain minimum it can drop to for this payment
			if (s < t) {
				subtract = diffs[0];
			} else {
				subtract = diffs[1];
			}
			double curWeight = edgeweights.getWeight(e);
			edgeweights.setWeight(e, this.originalWeight.get(e));
			val = this.edgeweights.getPot(s, t);
			edgeweights.setWeight(e, curWeight);
		}
		return val - subtract;
	}

}

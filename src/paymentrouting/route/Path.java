package paymentrouting.route;

/**
 * info about path between source and intermediary
 *
 * @param src     source node
 * @param dest    destination node
 * @param fee     fee paid for using this channel
 * @param balance of channel from src to dest
 */
public class Path {
	public int src;
	public int dest;
	public double fee;
	public double balance;

	public Path(int src, int dest, double balance) {
		this.src = src;
		this.dest = dest;
		this.balance = balance;
	}

	public void setFee(double fee) {
		this.fee = fee;
	}


}

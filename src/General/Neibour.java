package General;

import java.util.StringTokenizer;

public class Neibour {
	public String ipAddr;
	public Integer portNum;
	public double accuracy;
	public Neibour(String addr){
		StringTokenizer st = new StringTokenizer(addr,":");
		ipAddr = st.nextToken();
		portNum = Integer.parseInt(st.nextToken());
		accuracy = Double.parseDouble(st.nextToken());
	}
}

package General;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class GoodGeneral {
	double[] W;
	double[] s0,s1,myWeight;
	boolean[] V;
	String myFullAddr, ipAddr;
	ArrayList<Neibour> neibour;
	Integer portNum;
	int pid;
	int len;
	String envAddr;
	Integer envPort;
	int count = 0;
	double epsilon = 0.1;
	double accuracy;
	
	public GoodGeneral(String fileName,String mypid) throws IOException{
		//TODO
		// write a new constructor to incorporate the pid
		BufferedReader textReader  = new BufferedReader(new FileReader(fileName));
		pid = Integer.parseInt(mypid);
		String envLine = textReader.readLine();
		Neibour env = new Neibour(envLine);
		envAddr = env.ipAddr;
		envPort = env.portNum;
		String line;
		neibour = new ArrayList<Neibour>();
		int i = 0;
		while((line = textReader.readLine())!=null){
			Neibour tmp = new Neibour(line);
			if(i==pid){
				System.out.println(i+" "+pid);
				ipAddr = tmp.ipAddr;
				portNum = tmp.portNum;
				accuracy = tmp.accuracy;
			}
			neibour.add(new Neibour(line));
			i += 1;
		}
		initializeWS();
		report("initialized,accuracy is:"+accuracy);
		textReader.close();
	}
	
	private void report(String s){
		String msg = String.format("from general %s:%s, %s",ipAddr,portNum.toString(),s);
		System.out.println(msg);
	}
	
	private void initializeWS(){
		len = neibour.size();
		W = new double[len];
		s0 = new double[len];
		s1 = new double[len];
		V = new boolean[len];
		myWeight = new double[len];
		for(int i=0; i < len; ++i){
			W[i] = 0.5;
			s0[i] = 0;
			s1[i] = 0;
			myWeight[i] = 0;
		}
	}
	
	public void exchangeV(){
		String msg = constructSingleMsg("exchangeV",pid,V[pid]);
		broadcast(msg);
		report("exchangeV");
	}
	
	public void queenPhase(){
		String msg = constructMsg("queenPhase",pid,V);
		broadcast(msg);
		report("queenPhase");
	}
	
	public void queenValue(){
		String msg = constructMsg("queenValue",pid,V);
		broadcast(msg);
		report("queenValue");
	}
	
	private String constructSingleMsg(String s, Integer pid,Boolean v){
		JSONObject jsonmsg = new JSONObject();
		try{
			jsonmsg.put("step", s);
			jsonmsg.put("pid", pid.toString());
			jsonmsg.put("v", v.toString());
		}catch(JSONException je){
			System.out.println("json error in constructMsg");
		}
		return jsonmsg.toString();
	}
	
	private String constructMsg(String s, Integer pid, boolean[] v2) {
		// TODO Auto-generated method stub
		String msg = "";
//		msg += i.toString();
		JSONObject jsonmsg = new JSONObject();
		try{
			jsonmsg.put("pid", pid.toString());
			jsonmsg.put("step", s);
			for(int i=0;i<len;i++){
				if(v2[i]){
					msg += "1";
				}else{
					msg += "0";
				}
			}
			jsonmsg.put("v", msg);
		}catch(JSONException je){
			System.out.println("json error in constructMsg");
		}
		return jsonmsg.toString();
	}
	
	private class msgHandler implements Runnable{

		Socket client;
		public msgHandler(Socket skt){
			client = skt;
		}
		@Override
		public void run(){
			handleClient();
		}
		private void handleClient(){
			try{
				BufferedReader bin = new BufferedReader(new InputStreamReader(client.getInputStream()));
				String msg = bin.readLine();
				handleMsg(msg);
			}catch(IOException e){
				String errMsg = String.format("Process with address %s:%s failed when handling a msg", ipAddr,portNum);
				System.out.println(errMsg);
			}finally{
				try {
					client.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
	}
	
//	private class algoRunner implements Runnable{
//		
//		@Override
//		public void run(){
//			exchangeV();
//		}
//		
//	}
	
	private void handleMsg(String msg){
        try {
			JSONObject jsonMsg = new JSONObject(msg);
			String step = jsonMsg.getString("step");
			String msgPid = jsonMsg.getString("pid");
			String v = jsonMsg.getString("v");
			if(step.equals("startAlgo")){
				// dirty code, actually pid is the real value from environment
				initializeMyV();
				exchangeV();
			}else if(step.equals("WeightedByzantine")){ //receive from env
				queenPhase();
			}else if(step.equals("enterQueenValue")){ // receive from env
				enterQueenValue(msgPid);
			}else if(step.equals("exchangeV")){ //receive from peer
				receiveExchangeV(msgPid,v);
			}else if (step.equals("queenPhase")){ //receive from peer
				receiveQueenPhase1(msgPid,v);
			}else if(step.equals("queenValue")){
				receiveQueenValue(v);
			}else if(step.equals("decideResult")){
				decideResult();
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}   
	}
	
	private void initializeMyV(){
		double temp = Math.random();
		for(int i=0; i < len; ++i){
			W[i] = 0.5;
			s0[i] = 0;
			s1[i] = 0;
			myWeight[i] = 0;
		}
		if(temp > 1 - accuracy){
			V[pid] = true;
		}else{
			V[pid] = false;
		}
	}
	
	private void decideResult(){
		float d0 = 0;
		float d1 = 0;
		for(int i=0;i<len;++i){
			if(V[i]){
				d1 += W[i];
			}else{
				d0 += W[i];
			}
		}
		if(d1 > d0){
			sendToEnv("decideTrue");
			report("decideTrue");
		}else{
			sendToEnv("decideFalse");
			report("decideFalse");
			updateWeight();
		}
	}
	
	private void updateWeight(){
		for(int i=0;i<len;++i){
			if(!V[i]){
				W[i] = (W[i] * (1-epsilon));
			}
		}
	}
	
	private synchronized void enterQueenValue(String queenId){
		report("mypid:"+pid+"received pid:"+queenId);
		Integer p = Integer.parseInt(queenId);
		if(p.intValue()==pid){
			queenValue();
			sendToEnv("ackQueenValue");
		}
	}
	
	private synchronized void receiveQueenValue(String v){
		for(int i = 0; i < len; ++i){
			if(myWeight[i]<=0.75){
				if(v.charAt(i)=='1'){
					V[i] = true;
				}else{
					V[i] = false;
				}
			}
		}
		sendToEnv("ackQueenValue");
	}
	
	private void sendToEnv(String s){
		Socket clientSocket;
		PrintWriter pout;
		try {
			clientSocket = new Socket(envAddr,envPort);
			pout = new PrintWriter(clientSocket.getOutputStream());
			JSONObject jsonmsg = new JSONObject();
			jsonmsg.put("step", s);
			pout.println(jsonmsg.toString());
			pout.flush();
			clientSocket.close();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	private synchronized void receiveExchangeV(String pid, String v){
		report("receiveExchageV"+":"+v);
		if(v.equals("true")){
			V[Integer.parseInt(pid)] = true;
		}else{
			V[Integer.parseInt(pid)] = false;
		}
		count += 1;
		if (count == len-1){
			count = 0;
			report("send out WeightedByzantine");
			sendToEnv("WeightedByzantine");
		}
	}
	
	private synchronized void receiveQueenPhase1(String spid, String v){
		Integer pid = Integer.parseInt(spid);
		if(W[pid]>0){
			for(int i = 0; i<len;++i){
				if(v.charAt(i)=='1'){
					s1[i] += W[pid];
				}else{
					s0[i] += W[pid];
				}
			}
		}
		count += 1;
		report("count:"+count);
		if(count==len-1){
			count = 0;
			for(int i = 0; i<len;++i){
				if(s1[i]>=s0[i]){
					V[i]=true;
					myWeight[i] = s1[i];
				}else{
					V[i]=false;
					myWeight[i] = s0[i];
				}
			}
			sendToEnv("askForQueenValue");
		}
	}
	
//	private void enterNextRound(){
//		round += 1;
//		count = 0;
//		if(round==1){
//			queenPhase();
//		}else if(round == 2){
//			
//		}else if(round == 3){
//			
//		}
//	}
	//TODO
	private void nextRound(){
		Socket clientSocket;
		PrintWriter pout;
		try {
			clientSocket = new Socket(envAddr,envPort);
			pout = new PrintWriter(clientSocket.getOutputStream());
			JSONObject jsonmsg = new JSONObject();
			jsonmsg.put("step", "nextRound");
			pout.write(jsonmsg.toString());
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		GoodGeneral gg;
		try{
			System.out.println(args[1]);
			gg = new GoodGeneral(args[0],args[1]);
		}catch(IOException e){
			System.out.println("Need file name to set up server!");
			return;
		}
		try{
			ServerSocket listener = new ServerSocket(gg.portNum);
			while(true){
				Socket aClient = listener.accept();
				msgHandler task = gg.new msgHandler(aClient);
//				algoRunner main_task = gg.new algoRunner();
//				new Thread(main_task).start();
				new Thread(task).start();
			}
		}catch(IOException e){
			System.out.println("Fail to create serversocket");
			return;
		}
	}

	private void broadcast(String msg){
		ServerSocket listener = null;
		Socket aClient = null;
		for(int i = 0; i < len; i++){
			if(i==pid) continue;
			try {
//				if(aClient!=null) aClient.close();
//				if(listener!=null) listener.close();
				Socket clientSocket = new Socket(neibour.get(i).ipAddr,neibour.get(i).portNum);
				PrintWriter pout = new PrintWriter(clientSocket.getOutputStream());
				pout.println(msg);
				pout.flush();
				clientSocket.close();
//				// listen for response
//				System.out.println(portNum);
//				listener = new ServerSocket(portNum);
//				listener.setSoTimeout(20*1000);
//				aClient = listener.accept();
//				handleResponse(aClient);
//				aClient.close();
//				listener.close();
//				break;
									
			} catch (UnknownHostException e) {
				e.printStackTrace();
				continue;
			} catch (InterruptedIOException e ) {
				System.out.println("Request timeout");
				continue;
			} catch(ConnectException e) {
				System.out.println("fail to connect to server "+neibour.get(i).ipAddr+":"+neibour.get(i).portNum);
				continue;
			} catch (IOException e) {
				e.printStackTrace();
				continue;
			} 
//			finally{	
//				try {
//					if(aClient!=null) aClient.close();
//					if(aClient!=null) listener.close();
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			}
		}
	}

	public boolean predict(){
		// predict the value
		return true;
	}
	
}

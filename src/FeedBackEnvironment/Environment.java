package FeedBackEnvironment;

import java.io.BufferedReader;
import java.io.File;
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

import org.json.JSONException;
import org.json.JSONObject;

import General.GoodGeneral;
import General.Neibour;

public class Environment {
	ArrayList<Neibour> neibour;
	String envAddr;
	Integer envPort;
	int count, phase;
	int countTrue, countFalse;
	int round = 0;
	int correctRound = 0;
	int totalRound = 30;
	
	public Environment(String fileName) throws IOException{
		BufferedReader textReader  = new BufferedReader(new FileReader(fileName));
		String envLine = textReader.readLine();
		Neibour env = new Neibour(envLine);
		count = 0;
		phase = 0;
		envAddr = env.ipAddr;
		envPort = env.portNum;
		String line;
		neibour = new ArrayList<Neibour>();
		while((line = textReader.readLine())!=null){
			neibour.add(new Neibour(line));
		}
		report("initialized");
		textReader.close();
	}
	
	private void report(String s){
		String msg = String.format("from environment, %s",s);
		System.out.println(msg);
	}
	
	public static void main(String[] args) {
		Environment env;
		try{
			System.out.println(args[0]);
			env = new Environment(args[0]);
		}catch(IOException e){
			System.out.println("Need file name to set up env!");
			return;
		}
		try{
			ServerSocket listener = new ServerSocket(env.envPort);
			env.startAlgo();
			while(true){
				Socket aClient = listener.accept();
				msgHandler task = env.new msgHandler(aClient);
				new Thread(task).start();
			}
		}catch(IOException e){
			System.out.println("Fail to create serversocket");
			return;
		}
	}
	
	private void startAlgo(){
		String msg = constructMsg("startAlgo",0);
		countTrue = 0;
		countFalse = 0;
		broadcast(msg);
		report("start algorithm");
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
				String errMsg = String.format("Process with address %s:%s failed when handling a msg", envAddr,envPort);
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
	
	private void handleMsg(String msg){
        try {
        	report("receive Msg:"+msg);
			JSONObject jsonMsg = new JSONObject(msg);
			String step = jsonMsg.getString("step");
			if(step.equals("WeightedByzantine")){ //receive from env
				queenPhase();
			}else if(step.equals("askForQueenValue")){ // receive from env
				queenValue();
			}else if(step.equals("ackQueenValue")){ //receive from peer
				ackQueenValue();
			}else if (step.equals("decideTrue")){ //receive from peer
				decideResult(true);
			}else if(step.equals("decideFalse")){
				decideResult(false);
			}
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}   
	}
	
	private synchronized void decideResult(boolean b){
		count += 1;
		if(b){
			countTrue += 1;
		}else{
			countFalse += 1;
		}
		if(count == neibour.size()){
			count = 0;
			round += 1;
			if(countTrue > countFalse){
				correctRound += 1;
			}
			report("total round is:"+round + ",correctRound is :" + correctRound);
			if(round != totalRound) startAlgo();
		}
	}
	
	private synchronized void queenPhase(){
		count += 1;
		report("queenPhase:"+count);
		if(count == neibour.size()){
			count = 0;
			String msg = constructMsg("WeightedByzantine",0);
			broadcast(msg);
			report("WeightedByzantine");
		}
	}
	
	private synchronized void queenValue(){
		count += 1;
		if(count == neibour.size()){
			count = 0;
			String msg = constructMsg("enterQueenValue",phase);
			broadcast(msg);
			report("enterQueenValue");
		}
	}
	
	private synchronized void ackQueenValue(){
		count += 1;
		if(count == neibour.size()){
			count = 0;
			phase += 1;
			if(phase == neibour.size()){
				phase = 0;
				String msg = constructMsg("decideResult",0);
				report("decideResult");
				broadcast(msg);
			}else{
				String msg = constructMsg("WeightedByzantine",0);
				broadcast(msg);
				report("WeightedByzantine");
			}
		}
	}
	
	
	private String constructMsg(String s, Integer pid){
		JSONObject jsonmsg = new JSONObject();
		try{
			jsonmsg.put("step", s);
			jsonmsg.put("pid", pid.toString());
			jsonmsg.put("v", "");
		}catch(JSONException je){
			System.out.println("json error in constructMsg");
		}
		return jsonmsg.toString();
	}
	
	private void broadcast(String msg){
		ServerSocket listener = null;
		Socket aClient = null;
		for(int i = 0; i < neibour.size(); i++){
			try {
				Socket clientSocket = new Socket(neibour.get(i).ipAddr,neibour.get(i).portNum);
				PrintWriter pout = new PrintWriter(clientSocket.getOutputStream());
				pout.println(msg);
				pout.flush();
				clientSocket.close();
									
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

		}
	}
}

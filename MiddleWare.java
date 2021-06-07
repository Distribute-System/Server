
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Semaphore;




import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import com.sun.jdi.event.Event;

public class MiddleWare implements NetworkerUser
{
	//Socket relaySocket;
	//Socket socket;
	ServerSocket serverSocket;
	RelayManagement relay;
	
	//BufferedWriter out;
	//BufferedReader in;
	//String outString;
	
	String relayIp;
	int relayPort;
	int serverPort = 5555;
	
	public volatile boolean isEmpty;
	
	Mutex eventQueueMutex;
	Mutex idNetworkerDictMuxtex;
	
	private static final String PROTOCOL_END = "\r\n";
	
	LinkedListQueue<CarEvent> eventQueue = new LinkedListQueue<CarEvent>();
	
	HashMap<String, Networker> idNetworkerDict = new HashMap<String, Networker>();
	
	public static void main(String[] args)
	{
		
		MiddleWare mw = new MiddleWare("172.31.10.90", 6000);
		//new MiddleWare("172.30.1.50", 7777);
		
		CarEvent zeroEvent = new CarEvent();
		
		CarEvent carEvent = new CarEvent();
		carEvent.putHeader("ID", "ClientEc2");
		carEvent.putHeader("SENDMESSG", "from server1");
		mw.send(carEvent);
		
		
		
		//while(true) {
			
			while(mw.isEmpty);
			System.out.println("out of move");
			
			CarEvent tempEve;
			tempEve = mw.dequeue();
			System.out.println("dequeued msg: " + tempEve);
			//mw.send(zeroEvent);
			
			
		//}
	}
	
	public MiddleWare(String relayIp, int relayPort)
	{
		// TODO Auto-generated constructor stub	
		isEmpty = true;
		this.relayIp = relayIp;
		this.relayPort = relayPort;
		eventQueueMutex = new Mutex(1);
		idNetworkerDictMuxtex = new Mutex(1);
		
		//relay 생성
		relay = new RelayManagement(relayIp, relayPort, this);

		try {
			serverSocket = new ServerSocket(serverPort);
		} catch(Exception e) {
			System.err.println("error in serverSocket");
			System.err.println(e);
		}
		
		
		AcceptThread acceptThread = new AcceptThread(serverSocket, this);
		acceptThread.start();
	}
	
	String converToString(CarEvent eve)
	{
		return eve.toString() + PROTOCOL_END;
	}
	
	CarEvent converToEvent(String msg) // returns null if msg not satisfies the requirements
	{
		try
		{
			String tempStr = msg.substring(0, msg.length() - 2);
			
			JSONObject temp = new JSONObject(tempStr);
			System.out.println(temp);
			if((temp.getJSONObject("HEADER") == null) || (temp.getJSONObject("BODY")) == null)
				return null;
			
			return new CarEvent(temp);
		}
		catch(JSONException e)
		{
			System.err.println("error occurred during converToEvent()");
			System.err.println(e);
		}
		return null;
	}
	
	public void onRecieve(String string, Networker networker)
	{
		System.out.println(string);
		CarEvent event = converToEvent(string);
		String id = event.getHeader("ID");
		networker.setId(id);
		System.out.println("succuess convertToEvent");
		//idNetworkerDict에 id가 없다면
		if(!idNetworkerDict.containsKey(id)) 
		{
			networker.id = id;
			List<String> idList = new ArrayList<String>();
			idList.add(id);
			//sendToRelayManagement(idList);
			relay.send_new_client(idList);
			addClientToNetworkerDictionary(event.getHeader("ID"), networker);
			System.out.println("succuess in adding new client " + id + " in clientToNetworkerDictionary!");
		}
		
		
		enqueue(event);
		System.out.println("succuess add eventQueue");
		
		for(Map.Entry<String, Networker> pair : idNetworkerDict.entrySet()) 
		{
			System.out.println(String.format("Key (name) is : %s, Value (networker) is : %s", pair.getKey(), pair.getValue()));
		}
	}
	
	void onRecieveRelayEventsToSend(List<CarEvent> carEvents)
	{
		for(CarEvent carEvent: carEvents)
		{
			send(carEvent, true);
		}
	}
	
	public void send(CarEvent eventId)
	{
		send(eventId, true);
	}
	

	boolean send(CarEvent EventId, boolean toRelay)
	{
		String id = EventId.getHeader("ID");
		
		//System.out.println(id);
		//System.out.println("send func");
		Networker idNetworker = idNetworkerDict.get(id); 
		System.out.println(idNetworker);
		
		if(idNetworker == null)
			System.out.println("idNetworker is null");
			
		
		//networker가 있을 때
		if(idNetworkerDict.containsKey(id))
		{	//
			Networker networker = idNetworkerDict.get(id);
			System.out.println("Networker in Dictionary");
			
			if(!networker.close) 
			{
				String eventString = converToString(EventId);
				System.out.println(eventString);
				networker.send(eventString);
				return true;
			}
			
			removeClientToNetworkerDictionary(id);		
		}
		
		
		List<CarEvent> eventList = new ArrayList<CarEvent>();
		eventList.add(EventId);
		
		for(CarEvent carEvent: eventList)
		{
			System.out.println(carEvent);	
		}
		
		relay.send_unsent_eventList(eventList);
		System.out.println("success send_unsent_eventList");
		return false;  //if sent to relay, return false
	}
	
	void onTearedOffRelay(RelayManagement relay)
	{
		return;
	}
	
	public void onTearedOff(String id, List<String> unsentMsgs, Networker networker )
	{
		
		removeClientToNetworkerDictionary(id);
		List<String> idList = new ArrayList<String>();
		idList.add(id);
		relay.send_remove_client(idList);
		List<CarEvent> unsent = new ArrayList<CarEvent>();
		
		for(String unsentMsg : unsentMsgs)
		{
			unsent.add(converToEvent(unsentMsg));
		}
		relay.send_unsent_eventList(unsent);
	}
	
	private boolean removeClientToNetworkerDictionary(String clientId)
	{
		try
		{
			idNetworkerDictMuxtex.acquire();
			idNetworkerDict.remove(clientId);
			idNetworkerDictMuxtex.release();
			return true;
			
		}
		catch(InterruptedException e)
		{
			System.err.println("error in removing client " + clientId + " from hashMap!");
			System.err.println(e);
		}
		
		return false;
	}
	
	private boolean addClientToNetworkerDictionary(String clientId, Networker networker)
	{
		try
		{
			idNetworkerDictMuxtex.acquire();
			idNetworkerDict.put(clientId, networker);
			idNetworkerDictMuxtex.release();
			return true;
		}
		catch(InterruptedException e)
		{
			System.err.println("error in adding client " + clientId + " in hashMap!");
			System.err.println(e);
		}
		return false;
	}
	
	public CarEvent dequeue() // return null if fail to dequeue!
	{
		CarEvent tmp = null;
		try 
		{
			eventQueueMutex.acquire();
			tmp = eventQueue.remove();
			
		}
		catch (InterruptedException e)
		{
			System.err.println("error in removing CarEvent from eventQueue!");
			System.err.println(e);
		}
		finally
		{
			eventQueueMutex.release();
			isEmpty = eventQueue.isEmpty();
		}
		
		
		return tmp;
	}
	
	private boolean enqueue(CarEvent aEvent)
	{
		try 
		{
			eventQueueMutex.acquire();
			eventQueue.add(aEvent);
			eventQueueMutex.release();
			
			return true;
		}
		catch(InterruptedException e) {
			System.err.println("error in adding CarEvent from eventQueue!");
			System.err.println(e);
		}
		finally
		{
			eventQueueMutex.release();
			isEmpty = eventQueue.isEmpty();
		}
		return false;
	}

	
	class AcceptThread extends Thread {
		ServerSocket serverSocket;
		NetworkerUser mw;
		Socket socket;
		
		public AcceptThread(ServerSocket serverSocket, MiddleWare mw) {
			this.serverSocket = serverSocket;
			this.mw = mw;
		}
		
		@Override
		public void run()
		{
			try {
				
			System.out.println("AcceptThread");
			
			while(true) {
				socket = serverSocket.accept();
				System.out.println("connected to client succeed!");
				new Networker(socket, mw, PROTOCOL_END);
				
			}
		} catch (IOException e) {
			System.err.println("Error occurred in creating a connection socket");
		}
			
		}
	}
	
}

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

public class RelayManagement implements NetworkerUser {

    RelayManagement relay = this;
    Socket socket;
    Networker networker;
    CarEvent event;
    MiddleWare mw;
    String serverId;
    volatile boolean receiveServerId = false;
    
    private static final String PROTOCOL_END = "\r\n";
    
    public RelayManagement(String relayIp, int relayPort, MiddleWare mw)
    {
        //Connection with RelayManagement
        System.out.println("RelayManagement 연결 기다리는 중");
        try {
            socket = new Socket(relayIp, relayPort);     
        } catch (Exception e) {
            System.err.println("error in RelayManagement Connection");
            System.err.println(e);
        }
        
        this.mw = mw;
        
        networker = new Networker(socket, this, PROTOCOL_END);
        
        while(!receiveServerId);
        System.out.println("success RelayManagement constructor");
    }
    
    String converToString(RelayEvent eve)
	{
		return mw.converToString(eve);
	}
	
	RelayEvent converToEvent(String msg) // returns null if msg not satisfies the requirements
	{
		try
		{
			String tempStr = msg.substring(0, msg.length() - PROTOCOL_END.length());
			
			JSONObject temp = new JSONObject(tempStr);
			System.out.println(temp);
			if((temp.getJSONObject("HEADER") == null) || (temp.getJSONObject("BODY")) == null)
				return null;
			
			return new RelayEvent(temp);
		}
		catch(JSONException e)
		{
			System.err.println("error occurred during converToEvent()");
			System.err.println(e);
		}
		return null;
	}
    
    //callback functions for NetowrkerUser
    public void onRecieve(String string, Networker networker) 
    {
        //string을 RelayEvent로 변환하기
        RelayEvent relayEvent = new RelayEvent();
        relayEvent = converToEvent(string);
        
        if(relayEvent == null || relayEvent.isEmpty())
        {
            System.out.println("relayEvent is null");
            return;
        }
        
        int reqNo = relayEvent.getReqNo();
        if(reqNo == 0)
        {
            //set ServerID
            serverId = relayEvent.getServerId();
            System.out.println("serverId: " + serverId);
            receiveServerId = true;
        } else if(reqNo == 1) 
        {
            List<CarEvent> temp = relayEvent.getCarEvents();
            if(temp.isEmpty())
                return;
            
            mw.onRecieveRelayEventsToSend(temp);
            
        } 
        else if(reqNo == 2) 
        {
            //RELAY REDIRCETION
            String ip = relayEvent.getIpAddr();
            int port = relayEvent.getPortNum();
            
            networker.close();

            try {
                socket = new Socket(ip, port);     
            } catch (Exception e) {
                System.err.println("error in RelayManagement ReConnection");
                System.err.println(e);
            }
            
            networker = new Networker(socket, this, PROTOCOL_END);
        }
    }
    
    public void onTearedOff(String id, List<String> unsentMsgs, Networker networker) {
        return;
    }
    
    void send_new_client(List<String> clientId) {
        RelayEvent relayEvent = new RelayEvent();
        relayEvent.putClientId(clientId);
        relayEvent.putReqNo(0);
        relayEvent.putServerId(serverId);
        send(relayEvent);
    }
    
    void send_remove_client(List<String> clientId) {
        RelayEvent relayEvent = new RelayEvent();
        relayEvent.putClientId(clientId);
        relayEvent.putReqNo(1);
        relayEvent.putServerId(serverId);
        send(relayEvent);
    }
    
    void send_unsent_eventList(List<CarEvent> eventList) {
        RelayEvent relayEvent = new RelayEvent();
        relayEvent.putCarEvents(eventList);
        relayEvent.putReqNo(2);
        relayEvent.putServerId(serverId);
        send(relayEvent);
    }
    
    void send(RelayEvent event) 
    {
        String eventString = converToString(event);
        networker.send(eventString);
    }
    
    
}

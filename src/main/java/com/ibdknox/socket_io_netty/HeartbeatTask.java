package com.ibdknox.socket_io_netty;

import java.util.Map;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

class HeartbeatTask extends TimerTask {

    private WebSocketServerHandler server;
    private Integer heartbeatNum = 0;
    private Map<String, Integer> heartbeatRate = new ConcurrentHashMap<String, Integer>();

    public HeartbeatTask(WebSocketServerHandler server) {
        this.server = server;
    }
    
    void notifyAlive(INSIOClient client) {
        String id = client.getSessionID();
        Integer rate = heartbeatRate.get(id);
        if(rate == null)
            rate = heartbeatNum;
        heartbeatRate.put(client.getSessionID(), ++rate);
    }

    private boolean isAlive(INSIOClient client) {
        if(client == null)
            throw new IllegalArgumentException("Client is null");
        //if(!this.open) return false;

        String sessionID = client.getSessionID();
        Integer thisBeat = heartbeatRate.get(sessionID);
        if(thisBeat == null)
            thisBeat = 0;

        Integer lastBeat = heartbeatNum - 1;

        if(thisBeat == 0 || thisBeat > heartbeatNum) {
            heartbeatRate.put(sessionID, heartbeatNum);
        } else if(thisBeat < lastBeat) {
            //we're 2 beats behind..
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        if(server.clients.isEmpty()) return;

        heartbeatNum++;
        for(INSIOClient client : server.clients.values()) {
            if(isAlive(client)) {
                ((GenericIOClient)client).keepAlive();
            } else {
                heartbeatRate.remove(client.getSessionID());
                server.disconnect(client);
            }
        }
    }
}

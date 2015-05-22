package com.dnquark.dancelab;

/**
 * Created by lalekseyev on 4/9/15.
 */

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import android.util.Log;

import android.os.Message;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Looper;

import static com.dnquark.dancelab.DanceLab.MSG_CONNECTION;
import static com.dnquark.dancelab.DanceLab.MSG_COMMAND;
import static com.dnquark.dancelab.DanceLab.MSG_LOG;


public class DataStreamer implements Callback {

    private Socket socket;
    private PrintWriter socketWriter;
    private BufferedReader socketReader;

    private static final String TAG = "DataStreamer";
    private static final int CONNECTION_RETRY_MS = 5000;
    private static final int CONNECTION_TIMEOUT_MS = 3000;

    public HandlerThread handlerThread;
    // private HandlerThread handlerThread;
    private Handler handler;
    private Handler statusHandler; // for communicating with UI thread

    private int mcount = 0;
    private int pollCount = 0;


    private int serverPort;
    private String serverIP;
    private Thread clientThread;

    // Constructors
    public DataStreamer(Handler statusHandler) {
        this.serverIP = "192.168.0.100";
        this.serverPort = 5679;
        this.statusHandler = statusHandler;
        prepareDataHandlers();
        connect();
        new Thread(new CommandPollLoop()).start();
        Log.d(TAG, "Instantiated DataStreamer");
    }

    public DataStreamer(String serverIP, int serverPort, Handler statusHandler) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
        this.statusHandler = statusHandler;
        prepareDataHandlers();
        connect();
        new Thread(new CommandPollLoop()).start();
        Log.d(TAG, "Instantiated DataStreamer");
    }

    private void prepareDataHandlers() {
        handlerThread = new HandlerThread("DataStreamerThread");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper(), this);
    }

    public Handler getHandler() {
        return handler;
    }

    public void setServerPort(int port) {
        serverPort = port;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerIP(String IP) {
        serverIP = IP;
    }

    public String getServerIP() {
        return serverIP;
    }

    public boolean connectionOk() {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            return true;
        } else {
            // if (socket == null) {
            //     Log.d(TAG, "SOCKET THREAD: socket is null");
            // }
            return false;
        }
    }

    public void connect() {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            Log.d(TAG, "Ignoring connect() request to an already connected socket");
        } else {
            clientThread = new Thread(new ConnectionLoop());
            clientThread.start();
            Log.d(TAG, "SOCKET THREAD CONNECT: Starting the TCP client thread");
        }
    }

    public void reconnect() {
        if (clientThread != null) {
            clientThread.interrupt(); // in case the thread is sleeping
        } else {
            connect();
        }
        disconnect(); // the ClientLoop will automatically try to reconnect
    }

    public void disconnect() {
        if (socketWriter != null) {
            socketWriter.flush();
            socketWriter.close();
        }
        if (socketReader != null) {
            try {
                socketReader.close();
            } catch (IOException e) {}
        }
        if (socket != null) {
            try {
                Log.d(TAG, "SOCKET THREAD DISCONNECT");
                socket.close();
            } catch (IOException e) {}
        }
    }

    public void sendStuff(String str) {
        if (socket == null || !socket.isConnected() || socket.isClosed()) {
            Log.d(TAG, "sendStuff - dropping data due to invalid socket state");
            return;
        }
        try {
            socketWriter.println(str);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    @Override
    public boolean handleMessage(Message m) {
        mcount = m.what;
        if (mcount % 1000 == 0) {
            Log.d(TAG, "SOCKET THREAD handling message " + Integer.toString(mcount));
        }
        if (socket == null || !socket.isConnected() || socket.isClosed()) {
            if (mcount % 1000 == 0) {
                Log.d(TAG, "SOCKET THREAD - dropping data due to invalid socket state");
            }
            return true;
        } else {
            if (mcount % 1000 == 0) {
                Log.d(TAG, "SOCKET THREAD (connection LGTM)");
            }

        }
        try {
            socketWriter.println((String)m.obj);
        } catch (Exception e) {
            Log.d(TAG, "socket writer writing FAILED");
            e.printStackTrace();
        }
        return true;
    }

    class CommandPollLoop implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    if (connectionOk()) {
                        if (pollCount++ % 120 == 0) {
                            statusHandler.obtainMessage(MSG_LOG, "Pinging server: " + Integer.toString(pollCount-1)).sendToTarget();
                        }
                        socketWriter.println("@CMD?");
                    }
                    Thread.sleep(500);
                }
            } catch (InterruptedException e) {}
        }
    }

	class ConnectionLoop implements Runnable {

        private void connectSocket() {
			try {
				InetAddress serverAddr = InetAddress.getByName(getServerIP());
                int port = getServerPort();

				socket = new Socket();
				socket.connect(new InetSocketAddress(serverAddr, port), CONNECTION_TIMEOUT_MS);
                if (socket != null && socket.isConnected()) {
                    socketWriter = new PrintWriter(new BufferedWriter(
                                    new OutputStreamWriter(socket.getOutputStream())),
                            true);
                    socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    String serverIP = serverAddr.getHostAddress();
                    Log.d(TAG, "SOCKET THREAD Connected to " + serverIP);
                    String msg = "Connected to " + getServerIP() + ":" + Integer.toString(getServerPort());
                    statusHandler.obtainMessage(MSG_CONNECTION, msg).sendToTarget();
                }
			} catch (UnknownHostException e) {
                Log.d(TAG, "Caught unknown host exception");
                statusHandler.obtainMessage(MSG_CONNECTION, "Failed to connect: unknown host").sendToTarget();
			} catch (IOException e) {
                String msg = "Failed to connect to " + getServerIP() + ":" + Integer.toString(getServerPort());
                Log.d(TAG, msg);
                statusHandler.obtainMessage(MSG_CONNECTION, msg).sendToTarget();
			}
        }

		@Override
		public void run() {
            String line = null;
            try {
                while (!connectionOk()) {
                    connectSocket();
                    if (!connectionOk()) {
                        Log.d(TAG, "SOCKET THREAD: failed to connect, sleeping 5s");
                        statusHandler.obtainMessage(MSG_LOG, "Failed to connect, sleeping").sendToTarget();
                        Thread.sleep(CONNECTION_RETRY_MS);
                    }
                }
                while((line = socketReader.readLine()) != null){
                    if (line.startsWith("@")) {
                        statusHandler.obtainMessage(MSG_LOG, "Command: " + line).sendToTarget();
                        statusHandler.obtainMessage(MSG_COMMAND, line.substring(1)).sendToTarget();
                    }
                    //Log.d(TAG, "SOCKET THREAD: RECEIVED " + line);
                }
            } catch (IOException e) {
            } catch (InterruptedException e) {
                Log.d(TAG, "SOCKET THREAD: interrupted!");
            } finally {
                disconnect(); // closes readers, writers, socket
                Log.d(TAG, "SOCKET THREAD: closing the socket");
                connect();
                Log.d(TAG, "SOCKET THREAD: attempting to reconnect");
            }
        }
    }
}

package com.dnquark.dancelab;

/**
 * Created by lalekseyev on 4/9/15.
 */

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import android.util.Log;

import android.os.Message;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Looper;


public class DataStreamer implements Callback {

    private Socket socket;
    private PrintWriter socketWriter;

    private static final String TAG = "DataStreamer";

    public HandlerThread handlerThread;
    // private HandlerThread handlerThread;
    private Looper looper;
    private Handler handler;

    private int mcount = 0;


    private int serverPort;
    private String serverIP;

    public DataStreamer() {
        this.serverIP = "10.14.1.102";
        this.serverPort = 5679;
    }

    public DataStreamer(String serverIP, int serverPort) {
        this.serverIP = serverIP;
        this.serverPort = serverPort;
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
            if (socket == null) {
                Log.d(TAG, "SOCKET THREAD: problem! socket is null");
            } else {
                Log.d(TAG, "SOCKET THREAD: problem! connected? " + Boolean.toString(socket.isConnected()) + "; closed? " + Boolean.toString(socket.isClosed()));
            }
            return false;
        }
    }

    public void connect() {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            Log.d(TAG, "Ignoring connect() request to an already connected socket");
        } else {
            new Thread(new ClientThread()).start();
            prepareDataHandlers();
            Log.d(TAG, "SOCKET THREAD CONNECT: Starting the TCP client thread");
        }
    }

    public void disconnect() {
        if (socket != null) {
            try {
                Log.d(TAG, "SOCKET THREAD DISCONNECT");
                stopDataHandlers();
                socket.close();
            } catch (IOException e) {
            }
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

    public void prepareDataHandlers() {
        handlerThread = new HandlerThread("DataStreamerThread");
        handlerThread.start();
        looper = handlerThread.getLooper();
        handler = new Handler(looper, this);
    }
    public void stopDataHandlers() {
        handlerThread.quit();
    }

    public Handler getHandler() {
        return handler;
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


	class ClientThread implements Runnable {

		@Override
		public void run() {

			try {
				InetAddress serverAddr = InetAddress.getByName(getServerIP());

				socket = new Socket(serverAddr, getServerPort());
                if (socket != null) {
                    socketWriter = new PrintWriter(new BufferedWriter(
                                    new OutputStreamWriter(socket.getOutputStream())),
                            true);
                }

			} catch (UnknownHostException e1) {
                Log.d(TAG, "Caught " + e1.toString());
				e1.printStackTrace();
			} catch (IOException e1) {
                Log.d(TAG, "Caught " + e1.toString());
				e1.printStackTrace();
			}

            // sendStuff("initialized socket");
		}

	}
}

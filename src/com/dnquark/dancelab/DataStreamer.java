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



public class DataStreamer {

    private Socket socket;
    private PrintWriter socketWriter;

    private static final String TAG = "DataStreamer";

    private int serverPort;
    private String serverIP;

    public DataStreamer() {
        this.serverIP = "192.168.1.57";
        this.serverPort = 5678;
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

    public void connect() {
        if (socket != null && socket.isConnected() && !socket.isClosed()) {
            Log.d(TAG, "Ignoring connect() request to an already connected socket");
        } else {
            new Thread(new ClientThread()).start();
            Log.d(TAG, "Starting the TCP client thread");
        }
    }

    public void disconnect() {
        if (socket != null) {
            try {
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

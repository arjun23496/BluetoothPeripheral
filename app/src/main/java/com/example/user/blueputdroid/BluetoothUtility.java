package com.example.user.blueputdroid;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.provider.SyncStateContract;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

/**
 * Created by user on 2/17/2016.
 *
 * Class handles all the connection requirements of the bluetooth
 * networking component
 * 
 */


public class BluetoothUtility {

    //Handler for the calling class
    public final Handler mHandler;

    public final String TAG="Bluetooth Utility";

    //Bluetooth Adapter
    public final BluetoothAdapter mBluetoothAdapter;

    //Thread variables for starting the different threads
    private ManagementThread mManagementThread;
    private ServerThread mServerThread;
    private ClientThread mClientThread;

    //Name and uuid for connection management
    public static final String NAME="BluePutDroid";
    public static final UUID MY_UUID=UUID.fromString("714d9764-afd6-44ca-8913-de270903f1ab");

    //Variables to indicate the state of the program
    public static final int STATE_DISCONNECTED=-1; //The connections are disconnected
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    public static final int STATE_ERROR=4;
    public static int STATE_PRESENT;

    public BluetoothUtility(Handler handler)
    {
        mManagementThread=null; //Inititalise these threads during runtime
        mServerThread=null;
        mClientThread=null;
        mHandler=handler;
        mBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
        STATE_PRESENT=STATE_DISCONNECTED;
    }

//    public BluetoothUtility()
//    {
//        mManagementThread=null; //Inititalise these threads during runtime
//        mServerThread=null;
//        mClientThread=null;
//        mHandler=null;
//        mBluetoothAdapter=BluetoothAdapter.getDefaultAdapter();
//    }


//    Cuurent state manager

    private void setState(int state)
    {
        Log.v(TAG,"STATE CHANGE : "+STATE_PRESENT+" -> "+state);
        STATE_PRESENT=state;
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE,STATE_PRESENT,-1).sendToTarget();
    }

    private void sendMessage(int type,String message)
    {
        Log.v(TAG,"SEND MESSAGE : "+type+" : "+message);
        mHandler.obtainMessage(type,message).sendToTarget();
    }

    //Main ThreadManager functions

    public void start() //Starts the server thread
    {
        if(mServerThread!=null)
            mServerThread.cancel();

        if(mServerThread==null) {
            mServerThread=new ServerThread();
            mServerThread.start();
            Log.v(TAG, "ServerThread started");
        }
    }

    public void connect(BluetoothDevice device) //Starts the clientThread
    {
        if(mClientThread!=null)
            mClientThread.cancel();

        if(mClientThread==null)
        {
            mClientThread=new ClientThread(device);
            mClientThread.start();
            Log.v(TAG,"ClientThread Started");
        }
    }

    public void connected(BluetoothDevice device,BluetoothSocket socket) //Starts the managementThread
    {
//        if(mManagementThread!=null)
//            mManagementThread.cancel();

//        if(mManagementThread==null)
        {
            mManagementThread=new ManagementThread(socket);
            mManagementThread.start();
            Log.v(TAG, "Management Thread Started");
        }
    }

    public void write(byte[] bytes)
    {
        mManagementThread.write(bytes);
    }

    public void cancel()
    {
        if(mManagementThread!=null)
        {
            mManagementThread.cancel();
        }
        if(mClientThread!=null)
        {
            mClientThread.cancel();
        }
        if(mServerThread!=null)
        {
            mServerThread.cancel();
        }
    }
    /*

        Threads for bluetooth servers clients and managing connections

     */

//Thread for the connection management
    public class ManagementThread extends Thread{

        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ManagementThread(BluetoothSocket socket)
        {
            mmSocket=socket;
            InputStream in=null;
            OutputStream out=null;

            try{
                in=socket.getInputStream();
                out=socket.getOutputStream();
                Log.v(TAG,"Streams successfully created");
                setState(STATE_CONNECTED);
            }
            catch(IOException e)
            {
                Log.v(TAG, "Error creating streams ManagementThread:ManagementThread()");
                setState(STATE_DISCONNECTED);
            }

            mmInStream=in;
            mmOutStream=out;
        }

        public void run()
        {
            byte[] buffer = new byte[20];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {

                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);

                    String result=new String(buffer, Charset.defaultCharset());
                    result=result.substring(0,bytes);
                    Log.v(TAG,"Input Stream Successful");
                    // Send the obtained bytes to the UI activity
                    Log.v(TAG,"The Obtained Bytes are");

                    Log.v("Message", result);
                    sendMessage(Constants.MESSAGE_READ,result);
//                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
//                            .sendToTarget();
                } catch (IOException e) {
                    Log.v(TAG, "Error in reading Input Stream ManagementThread:run()");
                    break;
                }
            }
        }

    /* Call this from the main activity to send data to the remote device */


    public void write(byte[] bytes)
        {
            try {
                  mmOutStream.write(bytes);
                sendMessage(Constants.MESSAGE_WRITE,"Success");
                Log.v(TAG, "Successfully written to outputStream");
             } catch (IOException e) {
                Log.v(TAG,"Error in writing to the OutputStream ManagementThread:write()");
                setState(STATE_DISCONNECTED);
                sendMessage(Constants.MESSAGE_WRITE,"Failure");
            }
        }

        //Shutdown the connection from the main activity

        public void cancel()
        {
            try {
                mmSocket.close();
                Log.v(TAG, "Management Thread Socket cancelled");
                setState(STATE_DISCONNECTED);
            }catch(IOException e)
            {
                Log.v(TAG, "Error in closing the main Socket ManagemntThread:cancel()");
                sendMessage(Constants.MESSAGE_ERROR,"Error in closing Socket");
            }
        }



    }



//Thread For Server
    public class ServerThread extends Thread {

        public final BluetoothServerSocket mmServerSocket;

        public ServerThread() {
            //temporary objet to later inititalise mmServerSocket
            BluetoothServerSocket tmp = null;

            try {
                tmp =mBluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME,MY_UUID); //Listen to a secure bluetooth connection
                Log.v(TAG,"Successfully created serverSocket");
            }
            catch (IOException e)
            {
                Log.v(TAG, "Error in creating Server Socket ServerThread:ServerThread()");
                setState(STATE_DISCONNECTED);
            }
            mmServerSocket=tmp;
        }

        //Running the thread

        public void run()
        {
            BluetoothSocket socket=null;

            //Listen till exception is thrown  or a connection is established
            while(true)
            {
                try{
                    socket=mmServerSocket.accept();
                    Log.v(TAG, "Succeeded in listening to socket serverThread");
                }catch (IOException e)
                {
                    Log.v(TAG, "Error in listening to the serversocket ServerThread:run()");
                    setState(STATE_DISCONNECTED);
                    break;
                }
                if(socket!=null)
                {
                   try {

                   //do the work to manage connection here by sending mmServerSocket for managing connection
                       connected(socket.getRemoteDevice(), socket);
                       mmServerSocket.close();
                       Log.v(TAG, "Successfully closed ServerSocket");
                       break;
                   }
                   catch (IOException e)
                   {
                       Log.v(TAG, "Error in closing server Socket ServerThread:run()");
                   }
                }
            }
        }


        //Cancel listening and close the ServerSocket
        public void cancel()
        {
            try{
                mmServerSocket.close();
                Log.v(TAG, "Successfully cancelled ServerSocket");
            }catch (IOException e)
            {
                Log.v(TAG,"Error in closing server socket close() ServerThread:cancel()");
            }
        }
    }
//Thread for client
    public class ClientThread extends Thread
    {
        private final BluetoothSocket mmSocket; //Socket variable
        private final BluetoothDevice mmDevice;//Variable containing the connected device

        public ClientThread(BluetoothDevice device)
        {
            BluetoothSocket tmp=null;
            mmDevice=device;

            try{
                tmp=mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
                Log.v(TAG,"Error in creating socket ClientThread");
            }catch (IOException e)
            {
                Log.v(TAG,"Error in creating Socket ClientThread:ClientThread()");
                setState(STATE_DISCONNECTED);
            }


            mmSocket=tmp;
        }

        public void run()
        {
            mBluetoothAdapter.cancelDiscovery(); //Cancel discovery because it will slow down the connection

            try{
                mmSocket.connect();
                Log.v(TAG, "Successfully connected to client");
                // DO work here by passing the mmsocket for managing connection
                connected(mmDevice, mmSocket);

            }catch(IOException e)
            {
                Log.v(TAG,mmSocket.toString());
                Log.v(TAG, "Error in connecting to the client ClientThread:run()");
                e.printStackTrace();
                setState(STATE_DISCONNECTED);
            }

        }

        //Cancel an in progress connection and close the socket

        public void cancel()
        {
            try{
                mmSocket.close();
                Log.v(TAG, "Successfully cancelled socket ClientThread");

            }catch(IOException e)
            {
                Log.v(TAG,"Error in closing socket ClientThread:cancel()");
            }
        }
    }
}
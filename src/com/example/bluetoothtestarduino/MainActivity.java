package com.example.bluetoothtestarduino;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import java.util.Set;
import java.util.UUID;

import android.nfc.Tag;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.SyncStateContract.Constants;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.webkit.ConsoleMessage.MessageLevel;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity {

 
	private BluetoothSocket btSocket;
    private TextView textView;
    
	private static final int REQUEST_ENABLE_BT = 1;
	final int RECIEVE_MESSAGE = 1;        // Status  for Handler
	private BluetoothAdapter btAdapter = null;
	private StringBuilder sb = new StringBuilder();
	private ConnectedThread mConnectedThread;
	private Handler h;
	private final static UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		textView = (TextView) findViewById(R.id.textView2);
		
		   h = new Handler() {
		        public void handleMessage(android.os.Message msg) {
		            switch (msg.what) {
		            case RECIEVE_MESSAGE:                                                   // if receive massage
		                byte[] readBuf = (byte[]) msg.obj;
		                String strIncom = new String(readBuf, 0, msg.arg1);                 // create string from bytes array
		                sb.append(strIncom); 
		                //textView.setText(sb.toString());
		                //sb.delete(0, sb.length());
		                int endOfLineIndex = sb.indexOf("*");                            // determine the end-of-line
		                if (endOfLineIndex > 0) {                                            // if end-of-line,
		                    String sbprint = sb.substring(0, endOfLineIndex);               // extract string
		                    sb.delete(0, sb.length());  //clear
		                    textView.setText("Data from Arduino: " + sbprint);            // update TextView
		                }
		                Log.d("Recieved from arduino: ", "...String:"+ sb.toString() +  "Byte:" + msg.arg1 + "...");
		                break;
		            }
		        };
		    };
		
		
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		checkBTState();
		
	}
	
	@Override
	  public void onResume() {
	    super.onResume();
	  
	    Log.d("TAG", "...onResume - try connect...");
	    
	    // Set up a pointer to the remote node using it's address.
	    BluetoothDevice device = getDevice("BTJacket");
	    
	    // Two things are needed to make a connection:
	    //   A MAC address, which we got above.
	    //   A Service ID or UUID.  In this case we are using the
	    //     UUID for SPP.
	    try {
	    	
	      btSocket = device.createRfcommSocketToServiceRecord(uuid);
	    } catch (IOException e) {
	      errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
	    }
	    
	    // Discovery is resource intensive.  Make sure it isn't going on
	    // when you attempt to connect and pass your message.
	    btAdapter.cancelDiscovery();
	    
	    // Establish the connection.  This will block until it connects.
	    Log.d("TAG ","...Connecting...");
	    try {
	      btSocket.connect();
	      Log.d("TAG", "....Connection ok...");
	    } catch (IOException e) {
	      try {
	        btSocket.close();
	      } catch (IOException e2) {
	        errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
	      }
	    }
	      
	    // Create a data stream so we can talk to server.
	    Log.d("TAG", "...Create Socket...");
	    
	    mConnectedThread = new ConnectedThread(btSocket);
	    mConnectedThread.start();
	  }
	
	
	 @Override
	  public void onPause() {
	    super.onPause();
	  
	    Log.d("TAG", "...In onPause()...");
	   
	    try     {
	      btSocket.close();
	    } catch (IOException e2) {
	      errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
	    }
	  }
	
	
	  private void checkBTState() {
		    // Check for Bluetooth support and then check to make sure it is turned on
		    // Emulator doesn't support Bluetooth and will return null
		    if(btAdapter==null) { 
		      errorExit("Fatal Error", "Bluetooth not support");
		    } else {
		      if (btAdapter.isEnabled()) {
		        Log.d(" tag", "...Bluetooth ON...");
		      } else {
		        //Prompt user to turn on Bluetooth
		        Intent enableBtIntent = new Intent(btAdapter.ACTION_REQUEST_ENABLE);
		        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		        //Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
				   //startActivityForResult(enableBluetooth, 0);
		      }
		    }
		  }
	  
	  private void errorExit(String title, String message){
		    Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
		    finish();
		  }
	
	
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}
	public void buttonClick(View view) {
		EditText editText = (EditText) findViewById(R.id.editText1);
        String msg = editText.getText().toString() +"\n";
        mConnectedThread.write(msg);
	}
	
	   private BluetoothDevice getDevice(String deviceName) {
       	BluetoothDevice mDevice = null;
   		Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
   		if(pairedDevices.size() > 0)
           {
               for(BluetoothDevice device : pairedDevices)
               {
                   if(device.getName().equals(deviceName)) mDevice = device;
               }
           }
   		return mDevice;
       }


	    
	    /* Call this from the main activity to shutdown the connection */
	    public void cancel() {
	        try {
	            btSocket.close();
	        } catch (IOException e) { }
	    } 
	    
	    private class ConnectedThread extends Thread {
	        private final BluetoothSocket mmSocket;
	        private final InputStream mmInStream;
	        private final OutputStream mmOutStream;
	      
	        public ConnectedThread(BluetoothSocket socket) {
	        	Log.d("THREAD", "constructor");
	            mmSocket = socket;
	            InputStream tmpIn = null;
	            OutputStream tmpOut = null;
	            
	            // Get the input and output streams, using temp objects because
	            // member streams are final
	            try {
	                tmpIn = socket.getInputStream();
	                tmpOut = socket.getOutputStream();
	            } catch (IOException e) { }
	      
	            mmInStream = tmpIn;
	            mmOutStream = tmpOut;
	        }
	      
	        public void run() {
	        	Looper.prepare();
	        	
	        	Log.d("THREAD", "inside run" );
	            byte[] buffer = new byte[1024];  // buffer store for the stream
	            int bytes; // bytes returned from read()
	 
	            // Keep listening to the InputStream until an exception occurs
	            while (true) {
	            	
	            	Log.d("in loop", "waiting for data");
	                try {
	                    // Read from the InputStream
	                    bytes = mmInStream.read(buffer);        // Get number of bytes and message in "buffer"
	                    Toast.makeText(getBaseContext(), "ARDUINO", Toast.LENGTH_SHORT).show();
	                    h.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();     // Send to message queue Handler
	                    Log.d("recieve", "b " + bytes);
	                } catch (Exception e) {
	                	e.printStackTrace();
	                	Log.d("thread RUN", "error:  " + e);
	                	
	                    break;
	                }
	            }
	        }
	      
	        /* Call this from the main activity to send data to the remote device */
	        public void write(String message) {
	            Log.d("TAG", "...Data to send: " + message + "...");
	            byte[] msgBuffer = message.getBytes();
	            try {
	                mmOutStream.write(msgBuffer);
	            } catch (IOException e) {
	                Log.d("TAG", "...Error data send: " + e.getMessage() + "...");     
	              }
	        }
	      
	        /* Call this from the main activity to shutdown the connection */
	        public void cancel() {
	            try {
	                mmSocket.close();
	            } catch (IOException e) { }
	        }
	        
	        
	        
	    }
	    
	  
		
		
	    
}

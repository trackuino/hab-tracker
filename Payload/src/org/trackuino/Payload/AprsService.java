package org.trackuino.Payload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;

// Timer:
// http://www.brighthub.com/mobile/google-android/articles/34861.aspx#ixzz1Kiqvu1Pt

public class AprsService extends Service {
	private final Uri packetsUri = Uri.withAppendedPath(
			PayloadContentProvider.CONTENT_URI, Database.Packets.name);

	private static final Pattern rePacket = Pattern.compile("(.*?),(.*)");

	private Timer timer;
	private Aprs mAprs = null;
	private SharedPreferences mSettings;
	private Prediction mPrediction;
	private String callsign;
	private String server;
	// private boolean simulation;
	private String lastTime;
	
	private final IBinder mBinder = new AprsServiceBinder();
	
	public class AprsServiceBinder extends Binder {
        Prediction getPrediction() {
            // Return this instance of LocalService so clients can call public methods
        	// Useless since Prediction is now a singleton and can be shared between the
        	// service and the activities.
            return mPrediction;
        }
        
        void reload() {
        	// Reload the service settings (callsign, server, etc.)
        	synchronized(AprsService.this) {
        		readSettings();
        	}
        }

		public void registerObserver(TheMapActivity payloadActivity) {
			// Flush stragglers
			mPrediction.deleteObservers();
			mPrediction.addObserver(payloadActivity);
		}
    }
	
	public void processLine(String line) {
		Aprs.Packet p = mAprs.new Packet(line);
		if (p.getTag() != Aprs.Tag.DUP) {
			ContentResolver cr = getContentResolver();
			p.save(cr);

			synchronized (mPrediction) {
				mPrediction.update(p);
				mPrediction.run();
			}
		}
	}

	class ReaderTask extends TimerTask {
		public void run() {
			synchronized(AprsService.this) {
//			new Thread(new Runnable() {
//			    public void run() {
			    	getPackets();
//			    }
//			}).start();
			}
		}
		
		public void getPackets() {
			try {
				Downloader d = new Downloader();
				String url = "http://" + server;
				url += "?c=" + callsign;
				if (lastTime != null)
					url += "&t=" + lastTime;
				d.get(url);
				ArrayList<String> lines = d.asArrayList();
				
				for (String line : lines) {
				    Matcher m = rePacket.matcher(line);
				    if (m.find()) {
				    	lastTime = m.group(1);
				    	String packet = m.group(2);
				    	processLine(packet);
				    }
				}
			} catch (IOException e) {
				// TODO: it would be nice to display the exception's message on the notification bar
				// e.printStackTrace();
			}
			// Schedule the next alarm
			timer.schedule(new ReaderTask(), 1000);
		}
	}


	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}
	
	public void readSettings() {
		// Cancel the timer if there is one
		if (timer != null)
			timer.cancel();
		
		// Get shared preferences
		mSettings =	PreferenceManager.getDefaultSharedPreferences(getApplicationContext());  
		callsign = mSettings.getString("callsign", null);
		server = mSettings.getString("aprslog_server", null);
		// simulation = mSettings.getBoolean("simulation", false);

		// Get an APRS engine
		mAprs = new Aprs();

		// Clear the prediction
		synchronized(mPrediction) {
			mPrediction.clear();
		}
		
		// Re-read all the packets back into the Aprs engine and predictor
		Cursor c = getContentResolver().query(packetsUri, null, null, null, null);
		c.moveToFirst();
		while (c.isAfterLast() == false) {
			Aprs.Packet p = mAprs.new Packet(c);
			synchronized (mPrediction) {
				mPrediction.update(p);
			}
			c.moveToNext();
		}
		// Run the simulation with the database packets
		synchronized(mPrediction) {
			mPrediction.run();
		}
		
		lastTime = null;
		
		// Schedule the aprs-log reader
		timer = new Timer();
		timer.schedule(new ReaderTask(), 0);
	}
	
	public void onCreate() {

		// Get the prediction engine
		mPrediction = Prediction.getInstance();
		
		// Read settings
		readSettings();
		
		// TODO: become foreground service:
		// startForeground(1, notification)
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	    // We want this service to continue running until it is explicitly
	    // stopped, so return sticky.
	    return START_STICKY;
	}	
}

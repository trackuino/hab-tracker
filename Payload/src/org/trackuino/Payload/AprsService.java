package org.trackuino.Payload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;

// Timer:
// http://www.brighthub.com/mobile/google-android/articles/34861.aspx#ixzz1Kiqvu1Pt

public class AprsService extends Service implements
		OnSharedPreferenceChangeListener {
	private static final Pattern rePacket = Pattern.compile("(.*?),(.*)");

	private final Uri packetsUri = Uri.withAppendedPath(
			PayloadContentProvider.CONTENT_URI, Database.Packets.name);
	private final int NOTIFICATION_RUNNING = 1;		// must be unique within app 
	private final IBinder mBinder = new AprsServiceBinder();

	private Timer timer;
	private Aprs aprs = null;
	private SharedPreferences settings;
	private Prediction prediction;
	private boolean connect = false;
	private String callsign;
	private int pollInterval;
	private long launchTime;
	private String server;
	private float burstAltitude;
	private float ascentRate;
	private float descentRate;
	private float launchAltitude;

	// private boolean simulation;
	private String lastTime;
	NotificationManager nm;
	
	public class AprsServiceBinder extends Binder {
		AprsService getService() {
			return AprsService.this;
		}
		

		public void registerObserver(TheMapActivity payloadActivity) {
			// Flush stragglers
			prediction.deleteObservers();
			prediction.addObserver(payloadActivity);
		}
    }
	
	public void processLine(String line) {
		Aprs.Packet p = aprs.new Packet(line);
		if (p.getTag() != Aprs.Tag.DUP) {
			ContentResolver cr = getContentResolver();
			p.save(cr);

			synchronized (prediction) {
				prediction.update(p);
				prediction.run();
			}
		}
	}

	class ReaderTask extends TimerTask {
		// TimerTask's run() runs in its own thread, so no need for a runnable here.
		public void run() {
			synchronized(AprsService.this) {
		    	getPackets();
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
			timer.schedule(new ReaderTask(), pollInterval * 1000);
		}
	}


	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}
	
	
	@Override
	public void onCreate() {

		// Get notification manager
		nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		// Get the prediction engine
		prediction = Prediction.getInstance();
		
		PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
        	.registerOnSharedPreferenceChangeListener(this);
		
		// Read settings
		synchronized(this) {
			readSettings();
		}
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	    // We want this service to continue running until it is explicitly
	    // stopped, so return sticky.
	    return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
    		.unregisterOnSharedPreferenceChangeListener(this);

		removeNotification();
	}
	
	// Methods from OnSharedPreferenceListener
	@Override
	public void onSharedPreferenceChanged(SharedPreferences arg0, String arg1) {
		reload();
	}

	public void reload() {
    	// Reload the service settings (callsign, server, etc.)
		// TODO: if the downloader gets stuck, it will lock readSettings out until it finishes,
		// potentially making the UI unresponsive. Not sure how to fix it, though. Maybe 1a)
		// setting the downloader to timeout faster and 1b) popping a progress dialog to entertain
		// the user?, or 2) killing the timer, hope it will kill the downloader too, and mutex
		// only around variable gets/sets?
    	synchronized(this) {
    		readSettings();
    	}
    }

	public void readSettings() {
		boolean reloadAprs = false;
		
		// Cancel the timer if there is one
		if (timer != null)
			timer.cancel();
		
		// Get shared preferences
		settings =	PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		boolean newConnect = settings.getBoolean("connect", false);
		// Not sure why getInt doesn't work with poll_interval, but whatever...
		int newPollInterval = Integer.parseInt(settings.getString("poll_interval", null));
		String newCallsign = settings.getString("callsign", null);
		long newLaunchTime = settings.getLong("launch_time", 0);
		String newServer = settings.getString("aprslog_server", null);
		float newBurstAltitude = Float.parseFloat(settings.getString("burst_altitude", null));
		float newAscentRate = Float.parseFloat(settings.getString("ascent_rate", null));
		float newDescentRate = Float.parseFloat(settings.getString("descent_rate", null));
		float newLaunchAltitude = Float.parseFloat(settings.getString("launch_altitude", null));
		
		// See what changed and what needs to be reloaded
		if (!newCallsign.equals(callsign) || 
				!newServer.equals(server) ||
				newLaunchTime != launchTime) {
			// Reload APRS when we need to kill the current packet database and reload from 
			// the aprslog server, ie. when one of callsign, launch time or server change.
			reloadAprs = true;
		}
		
		// Keep new settings
		connect = newConnect;
		pollInterval = newPollInterval;
		callsign = newCallsign;
		launchTime = newLaunchTime;
		server = newServer;
		burstAltitude = newBurstAltitude;
		ascentRate = newAscentRate;
		descentRate = newDescentRate;
		launchAltitude = newLaunchAltitude;
		
		// Set prediction data 
		prediction.reloadSoundingWinds(getContentResolver());
		prediction.setBurstAltitude(burstAltitude);
		prediction.setAscentRate(ascentRate);
		prediction.setDescentRate(descentRate);
		prediction.setLaunchAltitude(launchAltitude);

		if (reloadAprs) {
			
			// Destroy all received packets so far
			ContentResolver cr = getContentResolver();
			cr.delete(Uri.withAppendedPath(
					PayloadContentProvider.CONTENT_URI, Database.Packets.name), null, null);

			// Get an APRS engine
			aprs = new Aprs();

			// Clear the prediction
			synchronized(prediction) {
				prediction.clear();
			}

			// Re-read all the packets back into the Aprs engine and predictor
			Cursor c = getContentResolver().query(packetsUri, null, null, null, null);
			c.moveToFirst();
			while (c.isAfterLast() == false) {
				Aprs.Packet p = aprs.new Packet(c);
				synchronized (prediction) {
					prediction.update(p);
				}
				c.moveToNext();
			}
			
			lastTime = Long.toString(launchTime / 1000);
		}
		
		// Run the simulation with the new settings
		synchronized(prediction) {
			prediction.run();
		}

		// Schedule the aprs-log reader
		if (connect) {
			timer = new Timer();
			timer.schedule(new ReaderTask(), 0);
			showNotification();
		}
		else {
			removeNotification();
		}
	}

	private void showNotification() {
		
		CharSequence text = getText(R.string.notification_running);
		Notification notification = new Notification(
				R.drawable.logo_mono, 
				text,
				System.currentTimeMillis());
		Intent notifyIntent = new Intent(this, SettingsActivity.class);
		notifyIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notifyIntent, 0);
		notification.setLatestEventInfo(
				this,
				text,
				getText(R.string.notificacion_configure),
				contentIntent);
		notification.flags |= Notification.FLAG_NO_CLEAR;
		
		startForeground(NOTIFICATION_RUNNING, notification);
	}
	
	private void removeNotification() {
		// Go to background and remove notification
		stopForeground(true);
	}

}

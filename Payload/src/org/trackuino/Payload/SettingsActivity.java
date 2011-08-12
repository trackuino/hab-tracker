package org.trackuino.Payload;

import org.trackuino.Payload.AprsService.AprsServiceBinder;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;

public class SettingsActivity extends PreferenceActivity {

	private EditTextPreference prefCallsign;
	private CheckBoxPreference prefSimulation;
	private EditTextPreference prefAprslogServer;
	private Preference prefCleanDatabase;
	private EditTextPreference prefBurstAltitude;
	private EditTextPreference prefAscentRate;
	private EditTextPreference prefDescentRate;
	private EditTextPreference prefLaunchAltitude;
	private Preference prefResetDefaults;
	private Prediction prediction;
	private Intent connector;
	private boolean bound = false;
	private AprsServiceBinder aprsServiceBinder = null;
	
	public final static int RESULT_NO_RELOAD = RESULT_FIRST_USER;
	public final static int RESULT_RELOAD = RESULT_FIRST_USER + 1;
	
	private ServiceConnection connection = new ServiceConnection() {
		@Override
		//public void onServiceConnected(ComponentName className, IBinder service) {
		public void onServiceConnected(ComponentName className, IBinder service) {
			aprsServiceBinder = (AprsServiceBinder) service;
			bound = true;
		}
		
		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			bound = false;
		}
	};

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Get a the service binder
		// TODO: maybe we should unbind on destroy? (search for doBind here:)
		// http://developer.android.com/reference/android/app/Service.html
		connector = new Intent(this, AprsService.class);
		startService(connector);
		bindService(connector, connection, Context.BIND_AUTO_CREATE);
		
		// Set default result action to not reload
		setResult(RESULT_NO_RELOAD);
		
		// Get a prediction object
		prediction = Prediction.getInstance();
		
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.settings);
		prefSimulation = (CheckBoxPreference) findPreference("simulation");
		prefCallsign = (EditTextPreference) findPreference("callsign");
		prefAprslogServer = (EditTextPreference) findPreference("aprslog_server");
		prefCleanDatabase = (Preference) findPreference("clean_database");
		prefBurstAltitude = (EditTextPreference) findPreference("burst_altitude");
		prefAscentRate = (EditTextPreference) findPreference("ascent_rate");
		prefDescentRate = (EditTextPreference) findPreference("descent_rate");
		prefLaunchAltitude = (EditTextPreference) findPreference("launch_altitude");
		prefResetDefaults = (Preference) findPreference("reset_defaults");
		
		// Fill out some initial titles, summaries, etc.
		prefCallsign.setSummary(prefCallsign.getText());
		prefAprslogServer.setSummary(prefAprslogServer.getText());
		prefBurstAltitude.setSummary(prefBurstAltitude.getText());
		prefAscentRate.setSummary(prefAscentRate.getText());
		prefDescentRate.setSummary(prefDescentRate.getText());
		prefLaunchAltitude.setSummary(prefLaunchAltitude.getText());
		
		// Define events on changing things
		prefSimulation.setOnPreferenceChangeListener(new CheckBoxPreference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object	newValue) {
				// Do something with the service
				return true;
			}
        });
		
		// Callsign
		prefCallsign.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				prefCallsign.setSummary((String)newValue);
				// TODO: all these reloads might need to be synchronized for mutex access to
				// internal variables of the service
				setResult(RESULT_RELOAD);	// only the service really (TODO)
				return true;
			}
		});
		
		// Log server
		prefAprslogServer.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				prefAprslogServer.setSummary((String)newValue);
				setResult(RESULT_RELOAD);	// only the service (TODO)
				return true;
			}
		});
		
		// Clean database
		prefCleanDatabase.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				// Reset prefs here, and rerun prediction
				ContentResolver cr = getContentResolver();
				cr.delete(Uri.withAppendedPath(
						PayloadContentProvider.CONTENT_URI, Database.Packets.name), null, null);
				setResult(RESULT_RELOAD);	// both prediction and service
				return true;
			}
		});


		// TODO: recalculate units when switching between metric <-> imperial
		

		// Define events on clicking things

		
		// Burst altitude
		prefBurstAltitude.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				float burstAltitude = Float.parseFloat((String) newValue);
				prefBurstAltitude.setSummary((String) newValue);
				setResult(RESULT_RELOAD);	// prediction only
				return true;
			}
		});
		
		// Ascent rate
		prefAscentRate.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				float ascentRate = Float.parseFloat((String) newValue);
				prefAscentRate.setSummary((String) newValue);
				setResult(RESULT_RELOAD);	// prediction only
				return true;
			}
		});

		// Descent rate
		prefDescentRate.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				float descentRate = Float.parseFloat((String) newValue);
				prefDescentRate.setSummary((String) newValue);
				setResult(RESULT_RELOAD);	// prediction only
				return true;
			}
		});

		// Launch altitude
		prefLaunchAltitude.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				float launchAltitude = Float.parseFloat((String) newValue);
				prefLaunchAltitude.setSummary((String) newValue);
				setResult(RESULT_RELOAD);	// prediction only
				return true;
			}
		});
		
		// Reset default settings
		prefResetDefaults.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				// TODO: Reset prefs here, and rerun prediction
				return false;
			}
		});
	}
	
	
}

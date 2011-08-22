package org.trackuino.Payload;

import org.trackuino.Payload.AprsService.AprsServiceBinder;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class SettingsActivity 
		extends PreferenceActivity 
		implements OnSharedPreferenceChangeListener {

	private EditTextPreference prefCallsign;
	private EditTextPreference prefAprslogServer;
	private ListPreference prefPollInterval;
	private CheckBoxPreference prefConnect;
	private Preference prefCleanDatabase;
	private EditTextPreference prefBurstAltitude;
	private EditTextPreference prefAscentRate;
	private EditTextPreference prefDescentRate;
	private EditTextPreference prefLaunchAltitude;
	private ListPreference prefUnits;
	private Preference prefResetDefaults;
	private Prediction prediction;
	private Intent connector;
	private boolean bound = false;
	private AprsServiceBinder aprsServiceBinder = null;
	
	private String unitVerticalRateMetric;
	private String unitVerticalRateImperial;
	private String unitSpaceMetric;
	private String unitSpaceImperial;
	
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

		// Get some strings
		unitVerticalRateMetric = getString(R.string.unit_vertical_rate_metric);
		unitVerticalRateImperial = getString(R.string.unit_vertical_rate_imperial);
		unitSpaceMetric = getString(R.string.unit_space_metric);
		unitSpaceImperial = getString(R.string.unit_space_imperial);

		// Get a prediction object
		prediction = Prediction.getInstance();
		
		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.settings);
		prefPollInterval = (ListPreference) findPreference("poll_interval");
		prefConnect = (CheckBoxPreference) findPreference("connect");
		prefCallsign = (EditTextPreference) findPreference("callsign");
		prefAprslogServer = (EditTextPreference) findPreference("aprslog_server");
		prefCleanDatabase = (Preference) findPreference("clean_database");
		prefBurstAltitude = (EditTextPreference) findPreference("burst_altitude");
		prefAscentRate = (EditTextPreference) findPreference("ascent_rate");
		prefDescentRate = (EditTextPreference) findPreference("descent_rate");
		prefLaunchAltitude = (EditTextPreference) findPreference("launch_altitude");
		prefUnits = (ListPreference) findPreference("units");
		prefResetDefaults = (Preference) findPreference("reset_defaults");
		
		// Fill out some initial titles, summaries, etc.
    	setPollIntervalSummary();
    	setCallsignSummary();
    	setAprslogServerSummary();
    	setBurstAltitudeSummary();
    	setAscentRateSummary();
    	setDescentRateSummary();
    	setLaunchAltitudeSummary();

    	// Define events on changing things
		prefUnits.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference arg0, Object arg1) {
				// TODO: recalculate units when switching between metric <-> imperial
				// This needs to execute before registered listeners are fired up (ie. the service)
				return false;
			}
		});

		// Define events on clicking things
		
		// Reset default settings
		// Pointless: just delete user data from android's app manager
		/*
		prefResetDefaults.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			public boolean onPreferenceClick(Preference preference) {
				PreferenceManager.setDefaultValues(SettingsActivity.this, R.xml.settings, true);
				return true;
			}
		});
		*/
	}
	
	@Override
	public void onStart()
	{
		super.onStart();

		// Get a the service binder (search for doBind here:)
		// http://developer.android.com/reference/android/app/Service.html
		connector = new Intent(this, AprsService.class);
		startService(connector);
		bindService(connector, connection, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
    		.registerOnSharedPreferenceChangeListener(this);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
			.unregisterOnSharedPreferenceChangeListener(this);
	}
	
	@Override
	public void onStop()
	{
		super.onStop();
		// try {
		unbindService(connection);
		// } catch (java.lang.IllegalArgumentException e) { }
	}

	// Methods from OnSharedPreferenceChangeListener
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
	     // Let's do something a preference value changes
        if (key.equals("poll_interval")) {
        	setPollIntervalSummary();
        } else if (key.equals("callsign")) {
        	setCallsignSummary();
        } else if (key.equals("aprslog_server")) {
        	setAprslogServerSummary();
        } else if (key.equals("burst_altitude")) {
        	setBurstAltitudeSummary();
        } else if (key.equals("ascent_rate")) {
        	setAscentRateSummary();
        } else if (key.equals("descent_rate")) {
        	setDescentRateSummary();
        } else if (key.equals("launch_altitude")) {
        	setLaunchAltitudeSummary();
        }
	}
	
	private void setPollIntervalSummary() {
		prefPollInterval.setSummary(prefPollInterval.getEntry());
	}
	
	private void setCallsignSummary() {
		prefCallsign.setSummary(prefCallsign.getText());
	}
	
	private void setAprslogServerSummary() {
		prefAprslogServer.setSummary(prefAprslogServer.getText());
	}
	
	private void setBurstAltitudeSummary() {
		String s = prefBurstAltitude.getText() + " ";
		String units = prefUnits.getValue();

		if (units.equals("m"))
			s += unitSpaceMetric;
		else if (units.equals("i"))
			s += unitSpaceImperial;
		
		prefBurstAltitude.setSummary(s);
	}
	
	private void setAscentRateSummary() {
		String s = prefAscentRate.getText() + " ";
		String units = prefUnits.getValue();

		if (units.equals("m"))
			s += unitVerticalRateMetric;
		else if (units.equals("i"))
			s += unitVerticalRateImperial;
		
		prefAscentRate.setSummary(s);
	}
	
	private void setDescentRateSummary() {
		String s = prefDescentRate.getText() + " ";
		String units = prefUnits.getValue();

		if (units.equals("m"))
			s += unitVerticalRateMetric;
		else if (units.equals("i"))
			s += unitVerticalRateImperial;
		
		prefDescentRate.setSummary(s);
	}
	
	private void setLaunchAltitudeSummary() {
		String s = prefLaunchAltitude.getText() + " ";
		String units = prefUnits.getValue();

		if (units.equals("m"))
			s += unitSpaceMetric;
		else if (units.equals("i"))
			s += unitSpaceImperial;
		
		prefLaunchAltitude.setSummary(s);
	}
}

package org.trackuino.Payload;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

import org.trackuino.Payload.R.id;

import com.google.android.maps.GeoPoint;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Images.ImageColumns;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

public class NoaaActivity extends Activity {
	Spinner modelSpinner;
	Spinner forecastCycleSpinner;
	Spinner launchTimeSpinner;
	ImageView captchaImage;
	TextView captchaText;
	EditText captchaEdit;
	TextView debugText;
	GeoPoint point;
	NoaaScraper scraper = new NoaaScraper();
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Bundle b = getIntent().getExtras();
        point = new GeoPoint(
        		b.getInt("org.trackuino.Payload.lat"),
        		b.getInt("org.trackuino.Payload.lon"));

        // Hide the IME unless the edittext is focused
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // Enable spinning progress circle
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        // Set content view. This must go before the spinner thing.
        setContentView(R.layout.noaa);

        // Find widgets
        modelSpinner = (Spinner) findViewById(R.id.spinner_noaa_model);
        forecastCycleSpinner = (Spinner) findViewById(R.id.spinner_noaa_forecast_cycle);
        launchTimeSpinner = (Spinner) findViewById(id.spinner_noaa_launch_time);
        captchaImage = (ImageView) findViewById(R.id.image_noaa_captcha);
        captchaText = (TextView) findViewById(R.id.text_noaa_captcha);
        captchaEdit = (EditText) findViewById(R.id.edit_noaa_captcha);
        debugText = (TextView) findViewById(R.id.text_noaa_debug);
        

        step1SelectModel();
    }
    
    private void showProgress() {
    	ProgressDialog dialog = new ProgressDialog(this);
		dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dialog.setMessage("Loading. Please wait...");
		dialog.setIndeterminate(true);
    }
    
    private void step1SelectModel() {
        // Fill out the model spinner
    	ArrayList<String> models;
    	try {
			models = scraper.fetchModels(point);
			models.add(0, getString(R.string.text_noaa_model));
			// TODO: comment this out in production?
	        debugText.setText(scraper.getBody());
		} catch (IOException e) {
			e.printStackTrace();
			models = new ArrayList<String>();
			models.add("-- ERROR --");
		}
		ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(
				this, R.layout.spinner_item, models);
		modelSpinner.setAdapter(spinnerArrayAdapter);
        
        // Capture spinner events
        modelSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
        	@Override
        	public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
        		step1ModelSelected(pos);
        	}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
			}
		});

    }
    
    private void step1ModelSelected(int modelPlus1) {
		if (modelPlus1 == 0) {
	        // Hide everything until the user has decided on a model
	        forecastCycleSpinner.setVisibility(View.GONE);
	        launchTimeSpinner.setVisibility(View.GONE);
	        captchaImage.setVisibility(View.GONE);
	        captchaText.setVisibility(View.GONE);
	        captchaEdit.setVisibility(View.GONE);
		}
		else {
	        step2SelectForecastCycle(modelPlus1);
		}
    }
    
    private void step2SelectForecastCycle(int modelPlus1) {
    	ArrayList<String> fc;
		// Keep the user entertained
		setProgressBarIndeterminateVisibility(true);
        try {
			fc = scraper.fetchForecastCycles(modelPlus1 - 1);
			fc.add(0, getString(R.string.text_noaa_forecast_cycle));
			// TODO: comment this out in production?
	        debugText.setText(scraper.getBody());
		} catch (IOException e) {
			e.printStackTrace();
			fc = new ArrayList<String>();
			fc.add("-- ERROR --");
		}
		setProgressBarIndeterminateVisibility(false);
		
		ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(
				this, R.layout.spinner_item, fc);
		forecastCycleSpinner.setAdapter(spinnerArrayAdapter);
        
        // Capture spinner events
        forecastCycleSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
        	@Override
        	public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
        		step2ForecastCycleSelected(pos);
        	}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
			}
		});
        
        // Show spinner
		forecastCycleSpinner.setVisibility(View.VISIBLE);
    }

    
	private void step2ForecastCycleSelected(int forecastCyclePlus1) {
		if (forecastCyclePlus1 == 0) {
	        // Hide everything below us until the user has decided on a model
	        launchTimeSpinner.setVisibility(View.GONE);
	        captchaImage.setVisibility(View.GONE);
	        captchaText.setVisibility(View.GONE);
	        captchaEdit.setVisibility(View.GONE);
		}
		else {
	        step3SelectLaunchTime(forecastCyclePlus1);
		}
	}

	private void step3SelectLaunchTime(int forecastCyclePlus1) {
    	ArrayList<String> lt;
		// Keep the user entertained
		setProgressBarIndeterminateVisibility(true);
        try {
			lt = scraper.fetchLaunchTimes(forecastCyclePlus1 - 1);
			lt.add(0, getString(R.string.text_noaa_launch_time));
			// TODO: comment this out in production?
	        debugText.setText(scraper.getBody());
		} catch (IOException e) {
			e.printStackTrace();
			lt = new ArrayList<String>();
			lt.add("-- Error fetching launch times");
		}
		setProgressBarIndeterminateVisibility(false);
		
		ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(
				this, R.layout.spinner_item, lt);
		launchTimeSpinner.setAdapter(spinnerArrayAdapter);
        
        // Capture spinner events
        launchTimeSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
        	@Override
        	public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
        		step3LaunchTimeSelected(pos);
        	}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
				// TODO Auto-generated method stub
			}
		});
        
        // Show spinner
		launchTimeSpinner.setVisibility(View.VISIBLE);
		
	}

	private void step3LaunchTimeSelected(int launchTimePlus1) {
		if (launchTimePlus1 == 0) {
	        // Hide everything below us until the user has decided on a model
	        captchaImage.setVisibility(View.GONE);
	        captchaText.setVisibility(View.GONE);
	        captchaEdit.setVisibility(View.GONE);
		}
		else {
	        step4EnterCaptcha(launchTimePlus1);
		}
	}


	private void step4EnterCaptcha(final int launchTimePlus1) {
		// TODO Auto-generated method stub
		captchaText.setVisibility(View.VISIBLE);
		captchaImage.setVisibility(View.VISIBLE);
		captchaEdit.setVisibility(View.VISIBLE);
		setProgressBarIndeterminateVisibility(true);

		try {
			Bitmap b = scraper.fetchCaptcha();
			captchaImage.setImageBitmap(b);
			// TODO: comment this out in production?
	        debugText.setText(scraper.getBody());
		} catch (IOException e) {
			e.printStackTrace();
			captchaText.setText("-- ERROR --");
		}
		setProgressBarIndeterminateVisibility(false);
		
		captchaEdit.setOnEditorActionListener(new OnEditorActionListener() {
			
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_GO) {
					InputMethodManager imm = (InputMethodManager) getSystemService(
						    INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(captchaEdit.getWindowToken(), 0);
					step4CaptchaEntered(launchTimePlus1, v.getText().toString());
				}
				return true;
			}
		});
	}

	private void step4CaptchaEntered(int launchTimePlus1, String captcha) {
		LinkedList<Wind> sounding;
		try {
			sounding = scraper.fetchSounding(launchTimePlus1, captcha);
			debugText.setText(scraper.getBody());
			saveSounding(sounding);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void saveSounding(LinkedList<Wind> sounding) {
		ContentResolver cr = getContentResolver();
		cr.delete(Uri.withAppendedPath(
				PayloadContentProvider.CONTENT_URI, Database.Winds.name), null, null);

		for (Wind w : sounding) {
			w.save(cr);
		}
		
		Prediction prediction = Prediction.getInstance();
		
		// Relaunch prediction
		synchronized (prediction) {
			prediction.setLaunchPoint(point);
			prediction.reloadSoundingWinds(getContentResolver());
			prediction.run();
		}
	}
}

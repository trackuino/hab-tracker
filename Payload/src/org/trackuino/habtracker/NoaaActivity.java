package org.trackuino.habtracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.google.android.maps.GeoPoint;

public class NoaaActivity extends Activity {
	Button modelButton;
	Button forecastCycleButton;
	Button launchTimeButton;
	ImageView captchaImage;
	TextView captchaText;
	EditText captchaEdit;
	TextView debugText;
	GeoPoint point;
	NoaaScraper scraper = new NoaaScraper();
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable spinning progress circle
        // TODO: this won't work unless the ui-locking parts (ie. the scrapers) are
        // wrapped around asynctasks or runnables.
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        
        Bundle b = getIntent().getExtras();
        point = new GeoPoint(
        		b.getInt("org.trackuino.habtracker.lat"),
        		b.getInt("org.trackuino.habtracker.lon"));

        // Hide the IME unless the edittext is focused
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        // Set content view. This must go before the spinner thing.
        setContentView(R.layout.noaa);

        // Find widgets
        modelButton = (Button) findViewById(R.id.button_noaa_model);
        forecastCycleButton = (Button) findViewById(R.id.button_noaa_forecast_cycle);
        launchTimeButton = (Button) findViewById(R.id.button_noaa_launch_time);
        captchaImage = (ImageView) findViewById(R.id.image_noaa_captcha);
        captchaText = (TextView) findViewById(R.id.text_noaa_captcha);
        captchaEdit = (EditText) findViewById(R.id.edit_noaa_captcha);
        debugText = (TextView) findViewById(R.id.text_noaa_debug);
        
    	// Hide everything except what matters now
    	modelButton.setVisibility(View.VISIBLE);
        forecastCycleButton.setVisibility(View.GONE);
        launchTimeButton.setVisibility(View.GONE);
        captchaImage.setVisibility(View.GONE);
        captchaText.setVisibility(View.GONE);
        captchaEdit.setVisibility(View.GONE);

        step1SelectModel();
    }
    
    private void showProgress() {
    	ProgressDialog dialog = new ProgressDialog(this);
		dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
		dialog.setMessage("Loading. Please wait...");
		dialog.setIndeterminate(true);
    }
    
    private void step1SelectModel() {

        // We're using a button where a spinner seems more sensible because the spinner
        // always has its first element selected by default, which is displayed in the
        // dropdown button. Instead, we want the button to say "pick one" and then have
        // all the available options on a dialog without having any selected by default.
        // Discussion here:
        // http://stackoverflow.com/questions/867518/how-to-make-an-android-spinner-with-initial-text-select-one
		modelButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
		    	try {
					final ArrayList<String> models = scraper.fetchModels(point);
					// TODO: comment this out in production?
			        debugText.setText(scraper.getBody());
			        
					ArrayAdapter<String> adapter = new ArrayAdapter<String>(
							NoaaActivity.this, R.layout.spinner_item, models);

					Builder a = new AlertDialog.Builder(NoaaActivity.this);
					a.setTitle(getString(R.string.text_noaa_model));
					a.setAdapter(adapter, new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							modelButton.setText(models.get(which));
							step1ModelSelected(which);
						}
					});
					a.create();
					a.show();
		    	
		    	} catch (IOException e) {
					e.printStackTrace();
					modelButton.setText("Connection error");
				}
			}
		});
    }
    
    private void step1ModelSelected(int model) {
    	modelButton.setEnabled(false);
    	forecastCycleButton.setVisibility(View.VISIBLE);
        step2SelectForecastCycle(model);
    }
    
    private void step2SelectForecastCycle(final int model) {
		forecastCycleButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
		    	try {
		    		// Keep the user entertained
		    		setProgressBarIndeterminateVisibility(true);

					final ArrayList<String> fc = scraper.fetchForecastCycles(model);
					
					setProgressBarIndeterminateVisibility(false);

					// TODO: comment this out in production?
			        debugText.setText(scraper.getBody());
			        
					ArrayAdapter<String> adapter = new ArrayAdapter<String>(
							NoaaActivity.this, R.layout.spinner_item, fc);

					Builder a = new AlertDialog.Builder(NoaaActivity.this);
					a.setTitle(getString(R.string.text_noaa_forecast_cycle));
					a.setAdapter(adapter, new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							forecastCycleButton.setText(fc.get(which));
							step2ForecastCycleSelected(which);
						}
					});
					a.create();
					a.show();
		    	
		    	} catch (IOException e) {
					e.printStackTrace();
					forecastCycleButton.setText("Connection error");
				}
			}
		});
    }

	private void step2ForecastCycleSelected(int forecastCycle) {
    	forecastCycleButton.setEnabled(false);
    	launchTimeButton.setVisibility(View.VISIBLE);
        step3SelectLaunchTime(forecastCycle);
	}

	private void step3SelectLaunchTime(final int forecastCycle) {
		launchTimeButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
		    	try {
		    		// Keep the user entertained
		    		setProgressBarIndeterminateVisibility(true);

					final ArrayList<String> lt = scraper.fetchLaunchTimes(forecastCycle);
					
					setProgressBarIndeterminateVisibility(false);

					// TODO: comment this out in production?
			        debugText.setText(scraper.getBody());
			        
					ArrayAdapter<String> adapter = new ArrayAdapter<String>(
							NoaaActivity.this, R.layout.spinner_item, lt);

					Builder a = new AlertDialog.Builder(NoaaActivity.this);
					a.setTitle(getString(R.string.text_noaa_launch_time));
					a.setAdapter(adapter, new DialogInterface.OnClickListener() {
						
						@Override
						public void onClick(DialogInterface dialog, int which) {
							launchTimeButton.setText(lt.get(which));
							step3LaunchTimeSelected(which);
						}
					});
					a.create();
					a.show();
		    	
		    	} catch (IOException e) {
					e.printStackTrace();
					launchTimeButton.setText("Connection error");
				}
			}
		});
		
	}

	private void step3LaunchTimeSelected(int launchTimePlus1) {
		launchTimeButton.setEnabled(false);
		captchaText.setVisibility(View.VISIBLE);
		captchaImage.setVisibility(View.VISIBLE);
		captchaEdit.setVisibility(View.VISIBLE);
        step4EnterCaptcha(launchTimePlus1);
	}


	private void step4EnterCaptcha(final int launchTimePlus1) {
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
			if (sounding.size() > 0) {
				finish();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void saveSounding(LinkedList<Wind> sounding) {
		ContentResolver cr = getContentResolver();
		cr.delete(Uri.withAppendedPath(
				HabContentProvider.CONTENT_URI, Database.Winds.name), null, null);

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

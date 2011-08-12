package org.trackuino.Payload;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Observable;
import java.util.Observer;

import org.trackuino.Payload.AprsService.AprsServiceBinder;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Paint.Style;
import android.graphics.Path.Direction;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

// Observing a cursor:
// http://mylifewithandroid.blogspot.com/2008/03/observing-content.html

public class TheMapActivity extends MapActivity 
		implements Observer, OnGestureListener, OnDoubleTapListener {

	private final Uri PROVIDER_URI = Uri.withAppendedPath(
			PayloadContentProvider.CONTENT_URI, 
			Database.Packets.name);
	private MapView mapView;
	private MapController mapController;
	private Intent connector;
	private Handler handler = new Handler();
	private PacketsObserver packetsObserver;
	private Bitmap aprsSym1;
	private Bitmap aprsSym2;
	private Prediction prediction;
	//private ConnectorService mService;
	private boolean bound = false;
	private GeoPoint contextPoint;
	private SharedPreferences mSettings;
	AprsServiceBinder aprsServiceBinder = null;
	private final int SETTINGS_CODE = 1;


	class PacketsObserver extends ContentObserver {
		public PacketsObserver(Handler handler) {
			super(handler);
		}
		
		public void onChange(boolean selfChange) {
			// Since we might be called from a non-UI thread (ie. the service),
			// call postInvalidate instead of invalidate.
			mapView.postInvalidate();
		}
	}

	
	class MapOverlay extends com.google.android.maps.Overlay {
		
		@Override
		public void draw(Canvas canvas, MapView mapView, boolean shadow) {
			super.draw(canvas, mapView, shadow);
			
			if (shadow) return;		// No shadows to draw

			Bitmap icon = Bitmap.createBitmap(aprsSym1, 1, 1, 20, 20);
			icon.setDensity(DisplayMetrics.DENSITY_LOW);
			synchronized(prediction) {
				paintPrediction(canvas, mapView, icon);
			}
		}
		
		public void paintPrediction(Canvas canvas, MapView mapView, Bitmap icon) {

			// Colors
			final int FLIGHT_DOTS_COLOR = 0xFF00FFFF;		// Cyan (ARGB)
			final int PREDICTED_DOTS_COLOR = 0xFFFFFF00;	// Yellow
			final int ASCENT_LINES_COLOR = 0xFF008000;		// Green
			final int DESCENT_LINES_COLOR = 0xFF800000;		// Red

			final int LAUNCH_TARGET_COLOR = ASCENT_LINES_COLOR;
			final int LANDING_TARGET_COLOR = DESCENT_LINES_COLOR;
			final int BURST_TARGET_COLOR = 0xFFFF8000;		// Orange
			
			// Trajectory brush
			Paint tp = new Paint();
			tp.setStyle(Style.STROKE);
			tp.setStrokeWidth(3);
			
			// Dots brush
			Paint dp = new Paint();
			dp.setColor(FLIGHT_DOTS_COLOR);
			dp.setStyle(Style.FILL);
			dp.setStrokeWidth(1);

			ListIterator<Waypoint> it = null;
			Projection proj = mapView.getProjection();
			int maxPoints = prediction.getFlightPath().size() + prediction.getPredictedPath().size();
			float [] ascentLines = new float[maxPoints * 4];
			float [] descentLines = new float[maxPoints * 4];
			Point [] flightDots = new Point[maxPoints];
			Point [] predictedDots = new Point[maxPoints];
			int numAscentLines = 0;
			int numDescentLines = 0;
			int numFlightDots = 0;
			int numPredictedDots = 0;

			Waypoint wp0 = null;
			Point sp0 = null;
			Point launchPoint = null;
			Point burstPoint = null;
			Point landingPoint = null;

			for (int i = 0; i < 2; i++) {
				switch (i) {
				case 0:
					it = prediction.getFlightPath().listIterator();
					break;
				case 1:
					// Draw the APRS symbol centered at the coordinates of the last reported position
					if (sp0 != null) {
						canvas.drawBitmap(
								icon, 
								sp0.x - icon.getScaledWidth(canvas) / 2, 
								sp0.y - icon.getScaledHeight(canvas) / 2,
								null);
					}
					it = prediction.getPredictedPath().listIterator();
					break;
				}
				while (it.hasNext()) {
					Waypoint wp1 = it.next();
					GeoPoint gp = new GeoPoint(wp1.getLat(), wp1.getLon());
					Point sp1 = new Point();	// Screen point
					proj.toPixels(gp, sp1);
					if (i == 0) {
						flightDots[numFlightDots] = sp1;
						numFlightDots++;
					} else if (i == 1) {
						predictedDots[numPredictedDots] = sp1;
						numPredictedDots++;
					}
					if (wp0 != null) {
						if (wp1.getAltitude() - wp0.getAltitude() > 0) {
							ascentLines[numAscentLines] = sp0.x;
							numAscentLines++;
							ascentLines[numAscentLines] = sp0.y;
							numAscentLines++;
							ascentLines[numAscentLines] = sp1.x;
							numAscentLines++;
							ascentLines[numAscentLines] = sp1.y;
							numAscentLines++;
						} else {
							descentLines[numDescentLines] = sp0.x;
							numDescentLines++;
							descentLines[numDescentLines] = sp0.y;
							numDescentLines++;
							descentLines[numDescentLines] = sp1.x;
							numDescentLines++;
							descentLines[numDescentLines] = sp1.y;
							numDescentLines++;
						}
					}
					else {
						launchPoint = sp1;
					}
					wp0 = wp1;
					sp0 = sp1;
				}
				// Draw trajectory
				tp.setColor(ASCENT_LINES_COLOR);
				canvas.drawLines(ascentLines, 0, numAscentLines, tp);
				tp.setColor(DESCENT_LINES_COLOR);
				canvas.drawLines(descentLines, 0, numDescentLines, tp);
				
				// Draw dots on top
				dp.setColor(FLIGHT_DOTS_COLOR);
				for (int j = 0; j < numFlightDots; j++)
					canvas.drawCircle(flightDots[j].x, flightDots[j].y, 4, dp);
				dp.setColor(PREDICTED_DOTS_COLOR);
				for (int j = 0; j < numPredictedDots; j++)
					canvas.drawCircle(predictedDots[j].x, predictedDots[j].y, 4, dp);

				landingPoint = sp0;
			}

			
			if (launchPoint != null) {
				tp.setColor(LAUNCH_TARGET_COLOR);
				for (int i = 4; i <= 24; i += 6) {
					canvas.drawCircle(launchPoint.x, launchPoint.y, i, tp);
				}
			}
			
			if (burstPoint != null) {
				tp.setColor(LAUNCH_TARGET_COLOR);
				for (int i = 4; i <= 24; i += 6) {
					canvas.drawCircle(burstPoint.x, burstPoint.y, i, tp);
				}
			}

			if (landingPoint != null) {
				tp.setColor(LANDING_TARGET_COLOR);
				for (int i = 4; i <= 24; i += 6) {
					canvas.drawCircle(landingPoint.x, landingPoint.y, i, tp);
				}
			}

		}
		

		@Override
		public boolean onTouchEvent(MotionEvent event, MapView mapView) {
/*
 * 			// This can be used to see to see if the balloon has been touched, for example 
			if (gestureDetector.onTouchEvent(event)) {
				return true;
			}
			return false;
*/
//			if (event.getAction() == MotionEvent.ACTION_UP) {
//			}
			return false;
		}

	}

	private ServiceConnection mConnection = new ServiceConnection() {
		@Override
		//public void onServiceConnected(ComponentName className, IBinder service) {
		public void onServiceConnected(ComponentName className, IBinder service) {
			aprsServiceBinder = (AprsServiceBinder) service;
			prediction = aprsServiceBinder.getPrediction();
			
			// TODO: unregister the observer on pause/stop and re-register on resume/start?
			aprsServiceBinder.registerObserver(TheMapActivity.this);
			bound = true;
		}
		
		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			aprsServiceBinder = null;
			bound = false;
		}
	};
	
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		// Load the default settings from the xml. By setting the last parameter to false,
		// it will read it only once, so that user defined params won't be overwritten.
		PreferenceManager.setDefaultValues(this, R.xml.settings, false);
		
		// Read settings
		mSettings =	PreferenceManager.getDefaultSharedPreferences(getApplicationContext());  
		float burstAltitude = Float.parseFloat(mSettings.getString("burst_altitude", null));
		float ascentRate = Float.parseFloat(mSettings.getString("ascent_rate", null));
		float descentRate = Float.parseFloat(mSettings.getString("descent_rate", null));
		float launchAltitude = Float.parseFloat(mSettings.getString("launch_altitude", null));
		
		// Create a new prediction or get the existing one
		prediction = Prediction.getInstance();
		
		// Set initial prediction data 
		prediction.reloadSoundingWinds(getContentResolver());
		prediction.setBurstAltitude(burstAltitude);
		prediction.setAscentRate(ascentRate);
		prediction.setDescentRate(descentRate);
		prediction.setLaunchAltitude(launchAltitude);
		
		// Create the service that gathers aprs packets
		// TODO: we're not creating this service multiple times, are we?
		connector = new Intent(this, AprsService.class);
		startService(connector);
		bindService(connector, mConnection, Context.BIND_AUTO_CREATE);
		setContentView(R.layout.map);
		mapView = (MapView) findViewById(R.id.mapView);
		mapView.setBuiltInZoomControls(true);
		
		mapController = mapView.getController();
		
		// Preload symbol bitmaps
		aprsSym1 = BitmapFactory.decodeResource(getResources(), R.drawable.aprssym1);
		aprsSym2 = BitmapFactory.decodeResource(getResources(), R.drawable.aprssym2);
		
		// Get a cursor with all the packets
		String [] proj = new String[] { 
				"_id",
				Database.Packets.LAT,
				Database.Packets.LON
		};
		
		// registerContentObservers();
				
		// Add the trajectory overlay
		MapOverlay mapOverlay = new MapOverlay();
		List<Overlay> listOfOverlays = mapView.getOverlays();
		listOfOverlays.clear();
		listOfOverlays.add(mapOverlay);
		
		// Register context menu
		registerForContextMenu(mapView);

		mapView.invalidate();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == SETTINGS_CODE) {
			if (resultCode == SettingsActivity.RESULT_RELOAD) {
				aprsServiceBinder.reload();
			}
		}
	}
	
	@Override
	protected boolean isRouteDisplayed() {
		// always return false per Google EULA
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Intent i;
		switch (item.getItemId()) {
		case R.id.settings:
			i = new Intent(TheMapActivity.this, SettingsActivity.class);
			startActivityForResult(i, SETTINGS_CODE);
			return true;
		case R.id.monitor:
			i = new Intent(TheMapActivity.this, MonitorActivity.class);
			startActivity(i);
			return true;
		case R.id.navigate_to_landing:
			GeoPoint landing = prediction.getLandingPoint();
			if (landing != null) {
		    	i = new Intent(Intent.ACTION_VIEW, Uri.parse(
		    			"google.navigation:q=" + 
		    			(landing.getLatitudeE6() / 1E6) + "," +
		    			(landing.getLongitudeE6() / 1E6)));
		    	startActivity(i);
			}
 
		case R.id.more:
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	@Override
	protected void onStart() {
		super.onStart();
		//registerContentObservers();
	}

	@Override
	protected void onStop() {
		super.onStop();
		//unregisterContentObservers();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		// TODO: kill service? No, because this might be triggered by a change in resolution,
		// language, input devices, 
		// Activity lifecycle: http://developer.android.com/reference/android/app/Activity.html
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		
		menu.setHeaderTitle("Lat/lon");
		
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.map_popup_menu, menu);
		/*
	    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
	    dialog.setTitle("Create ctxt menu");
	    dialog.setMessage("Location: " + contextPoint.getLatitudeE6() + ", " + contextPoint.getLongitudeE6());
	    dialog.show();*/
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
	    Intent i;

	    switch (item.getItemId()) {
	    case R.id.predict_from_here: 
	    	synchronized (prediction) {
	    		prediction.setLaunchPoint(contextPoint);
	    		prediction.run();
	    	}
	    	return true;
	    	
	    case R.id.get_sounding_winds:
			i = new Intent(TheMapActivity.this, NoaaActivity.class);
			i.putExtra("org.trackuino.Payload.lat", contextPoint.getLatitudeE6());
			i.putExtra("org.trackuino.Payload.lon", contextPoint.getLongitudeE6());
			startActivity(i);
	        return true;

	    case R.id.navigate_here:
	    	i = new Intent(Intent.ACTION_VIEW, Uri.parse(
	    			"google.navigation:q=" + 
	    			(contextPoint.getLatitudeE6() / 1E6) + "," +
	    			(contextPoint.getLongitudeE6() / 1E6)));
	    	startActivity(i);
	        return true;

	    case R.id.google_map_this:
	    	i = new Intent(Intent.ACTION_VIEW, Uri.parse(
	    			"geo:" +
	    			(contextPoint.getLatitudeE6() / 1E6) + "," +
	    			(contextPoint.getLongitudeE6() / 1E6)));
    		startActivity(i);
	    	return true;

	    default: 
	    	return super.onContextItemSelected(item);
	    }
	}
	
	// Observer methods

	@Override
	public void update(Observable observable, Object data) {
		prediction = (Prediction)data;
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mapView.invalidate();
			}
		});
	}
	
	// OnGestureListener methods
	
	@Override
	public boolean onDown(MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onFling(MotionEvent arg0, MotionEvent arg1, float arg2,
			float arg3) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onLongPress(MotionEvent e) {
		// TODO Auto-generated method stub
		contextPoint = mapView.getProjection().fromPixels((int)e.getX(), (int)e.getY());
		openContextMenu(mapView);
		
		// Toast.makeText(getBaseContext(), "WTF", Toast.LENGTH_SHORT).show();
	}

	@Override
	public boolean onScroll(MotionEvent arg0, MotionEvent arg1, float arg2,
			float arg3) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onShowPress(MotionEvent arg0) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean onSingleTapUp(MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onDoubleTap(MotionEvent e) {
		GeoPoint p = mapView.getProjection().fromPixels((int)e.getX(), (int)e.getY());
		 
	    AlertDialog.Builder dialog = new AlertDialog.Builder(this);
	    dialog.setTitle("Double Tap");
	    dialog.setMessage("Location: " + p.getLatitudeE6() + ", " + p.getLongitudeE6());
	    dialog.show();
	 
	    return true;
	}

	// OnDoubleTapListener methods
	
	@Override
	public boolean onDoubleTapEvent(MotionEvent e) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

}
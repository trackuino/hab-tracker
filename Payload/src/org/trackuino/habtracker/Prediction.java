package org.trackuino.habtracker;

import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Observable;

import android.content.ContentResolver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Point;
import android.net.Uri;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;

/* 	
 
Model: http://en.wikipedia.org/wiki/U.S._Standard_Atmosphere
Barometric formula: http://en.wikipedia.org/wiki/Barometric_formula

Parachute speed
---------------
v = sqrt((2*m*g) / (RO * A * w))

m = mass
g = gravity
A = transversal surface exposed to air
w = drag coefficient (depends on the parachute)
RO = air density


Density of air (kg / m3):
--------------------------
RO = (P * M) / (R * T)

RO = air density (kg / m3)
P = pressure (Pa)
M = 0.0289644 kg/mol
R = 8.31447 g/(mol * K)  universal gas constant
T = temperature (K)


*/

// Usually calls to Prediction methods is synchronized so that the UI thread and the
// service's timer task can exclude each other.
public class Prediction extends Observable {

	enum Status {
		NO_DATA,
		GROUND,
		UP,
		DOWN,
		LANDED
	}
	
	private LinkedList<Wind> soundingWinds = new LinkedList<Wind>();
	private LinkedList<Wind> flightWinds;
	// private Waypoint wp0 = null;
	// private Waypoint wp1 = null;
	private Waypoint lastWp = null;
	private LinkedList<Waypoint> flightPath = null;
	private LinkedList<Waypoint> predictedPath = null;
	private float maxAltitude = 0;
	private float simulatedBurstAltitude = 0;
	private float ascentRate = 0;
	private float descentRate = 0;
	private float launchAltitude = 0;
	private GeoPoint launchPoint = null;
	private Status status = Status.NO_DATA;
	private GeoPoint landingPoint = null;
	
	// Singleton object
	private static Prediction prediction = null;
	
	static Prediction getInstance() {
		if (prediction == null) {
			prediction = new Prediction();
		}
		return prediction;
	}
	
	public void reloadSoundingWinds(ContentResolver cr) {
		Cursor c = cr.query(Uri.withAppendedPath(
				HabContentProvider.CONTENT_URI, Database.Winds.name), null, null, null, null);

		soundingWinds = new LinkedList<Wind>();
		c.moveToFirst();
		while (c.isAfterLast() == false) {
			Wind w = new Wind(c);
			soundingWinds.add(w);
			c.moveToNext();
		}
		setChanged();
	}
	
	public Prediction() {
		clear();
	}
	
	public void clear() {
		flightWinds = new LinkedList<Wind>();
		flightPath = new LinkedList<Waypoint>();
		predictedPath = new LinkedList<Waypoint>();
		lastWp = null;
		maxAltitude = 0;
		status = Status.NO_DATA; 
		landingPoint = null;
	}
	
	public void update(Aprs.Packet packet) {
		// Ignore bad packets (bad position, dupes and out-of-order)
		if (packet.getTag() != Aprs.Tag.OK)
			return;
		
		// Add the packet to the path and insert the wind data reported by the packet
		Waypoint wp = new Waypoint(packet);

		// Update highest altitude so far
		if (wp.getAltitude() > maxAltitude)
			maxAltitude = wp.getAltitude();
			
		// Update the state machine
		Wind w;
		switch (status) {
		case NO_DATA:
			// As soon as we receive a valid position packet, we're on the ground
			flightPath.add(wp);
			status = Status.GROUND;
			setChanged();		// Will notify observers later
			break;
			
		case GROUND:
			w = new Wind(lastWp, wp);
			if (w.isFlying() && ! w.isDescending()) {
				// As soon as we start flying, keep the previous waypoint 
				// as the launch point and add the new one
				flightWinds.add(w);
				status = Status.UP;
			} else {
				// Not moving: clear the flight path so that there is only one ground
				// waypoint in the list.
				flightPath.clear();
			}
			flightPath.add(wp);
			setChanged();		// Will notify observers later
			break;

		case UP:
			w = new Wind(lastWp, wp);
			if (w.isFlying() && w.isDescending())
				// As soon as we start the descent, move to the DOWN state
				status = Status.DOWN;
			else {
				// If we're still ascending, add the current position to the flight path
				// and store the wind as this will override sounding winds when calculating
				// the descent leg.
				flightWinds.add(w);
			}
			flightPath.add(wp);
			setChanged();
			break;

		case DOWN:
			// As soon as we hit the ground, move to the LANDED state
			w = new Wind(lastWp, wp);
			if (! w.isFlying())
				status = Status.LANDED;
			flightPath.add(wp);
			setChanged();
			break;

		case LANDED:
			break;
		}

		lastWp = wp;
	}

	public void run() {
		// If nothing changed, there is nothing to run
		if (! hasChanged())
			return;

		// Prediction starts from the current waypoint.
		Waypoint wp0;
		if (lastWp != null) {
			// If we have a reported position, pick up from there
			wp0 = lastWp;
		} else if (launchPoint != null) {
			// If no incoming packets but we have a preset launch point, use that instead
			wp0 = new Waypoint(launchPoint, launchAltitude);
		} else {
			// If we have neither packets nor preset launch point, don't do anything
			return;
		}

		
		// Start a new path: the predicted path
		predictedPath = new LinkedList<Waypoint>();
		
		// Merge the sounding winds and the inflight winds
		LinkedList<Wind> winds = new LinkedList<Wind>();
		ListIterator<Wind> it = flightWinds.listIterator();
		while (it.hasNext()) {
			Wind w = it.next();
			winds.add(w);
		}
		it = soundingWinds.listIterator();
		while (it.hasNext()) {
			Wind w = it.next();
			if (w.getHighAltitude() <= maxAltitude)
				continue;
			else
				winds.add(w);
		}
		

		it = winds.listIterator();
		Wind w0 = null;
		
		// Skip winds up the current altitude
		while (it.hasNext()) {
			w0 = it.next();
			if (w0.getHighAltitude() > wp0.getAltitude()) {
				it.previous();
				break;
			}
		}
		
		// Ascent leg
		if (status == Status.NO_DATA || status == Status.GROUND || status == Status.UP) {
			while (it.hasNext()) {
				w0 = it.next();
				float targetAltitude = w0.getHighAltitude();
				if (targetAltitude > simulatedBurstAltitude) {
					targetAltitude = simulatedBurstAltitude;
				}
				// Climb to the target altitude. Vertical speed will be the preset ascent rate
				// TODO: average out the real ascent rate and use that instead
				Waypoint wp1 = new Waypoint(
						wp0, w0.getDirection(), w0.getSpeed(), targetAltitude, ascentRate);
				predictedPath.add(wp1);
				wp0 = wp1;
				
				// If reached burst altitude, start descent
				if (targetAltitude == simulatedBurstAltitude)
					break;
			}
		}
		
		// Descent leg
		if (it.hasPrevious())
			it.previous();		// previous points at w0; skip it.
		
		while (it.hasPrevious()) {
			Wind w1 = it.previous();
				
			// Cap target altitude to launch point
			// TODO (priority---): we could query google maps to see what the altitude looks 
			// like at the currently simulated position and cap to a more realistic altitude.
			float targetAltitude = w1.getHighAltitude();
			if (targetAltitude < launchAltitude) {
				targetAltitude = launchAltitude;
			}
			
			// Use the high wind to reach the altitude of the low wind
			Waypoint wp1 = new Waypoint(
					wp0, w0.getDirection(), w0.getSpeed(), targetAltitude, -descentRate);
			predictedPath.add(wp1);
			wp0 = wp1;
			w0 = w1;
		}
		
		landingPoint = new GeoPoint(wp0.getLat(), wp0.getLon());
		notifyObservers(this);
	}

	public LinkedList<Waypoint> getFlightPath() {
		return flightPath;
	}
	
	public LinkedList<Waypoint> getPredictedPath() {
		return predictedPath;
	}

	void setBurstAltitude(float burstAltitude) {
		this.simulatedBurstAltitude = burstAltitude;
		setChanged();
	}
	
	void setAscentRate(float ascentRate) {
		this.ascentRate = ascentRate;
		setChanged();
	}
	
	void setDescentRate(float descentRate) {
		this.descentRate = descentRate;
		setChanged();
	}
	
	void setLaunchAltitude(float launchAltitude) {
		this.launchAltitude = launchAltitude;
		setChanged();
	}
	
	void setLaunchPoint(GeoPoint launchPoint) {
		this.launchPoint = new GeoPoint(
				launchPoint.getLatitudeE6(),
				launchPoint.getLongitudeE6());
		setChanged();
	}

	public GeoPoint getLandingPoint() {
		return landingPoint;
	}
}
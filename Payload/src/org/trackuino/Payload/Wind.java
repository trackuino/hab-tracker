package org.trackuino.Payload;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.util.FloatMath;

public class Wind {
	private static final float PI = 3.1415926539f;
	private final static float MOVING_SPEED = 2.0f;		// m/s
	private final static float REARTH = 6356766;		// m

	private float highAltitude;		// m
	private float speed;			// m/s
	private int direction;			// degE6
	private boolean isFlying;		// true/false
	private boolean isDescending;	// true/false

	public Wind(String highAltitude, String direction, String speed) {
		// altitude is in meters, direction is where the wind is coming from (degrees),
		// and speed is in m/s.
		this.highAltitude = Float.parseFloat(highAltitude);
		this.direction = (int) (Float.parseFloat(direction) * 1E6);
		this.speed = Float.parseFloat(speed);
	}
	
	public Wind (Cursor c) {
		getFromCursor(c);
	}
	
	public Wind(Waypoint wp0, Waypoint wp1) {
		calculateWind(wp0, wp1);
	}
	
	public void calculateWind(Waypoint wp0, Waypoint wp1) {
		// Coordinates
		float lat0 = wp0.getLatRad();
		float lon0 = wp0.getLonRad();
		float lat1 = wp1.getLatRad();
		float lon1 = wp1.getLonRad();

		// Coordinate deltas
		float dLat = lat1 - lat0;
		float dLon = lon1 - lon0;

		// Elapsed time, watch for 23:59:59 -> 00:00:00 rollovers
		float dTime = (wp1.getTime() - wp0.getTime() + 86400) % 86400;
		
		// Altitude delta
		float dAltitude = (wp1.getAltitude() - wp0.getAltitude());
		
		// Vertical speed (+ up, - down)
		float verticalRate = dAltitude / dTime;

		// === Are we flying? ===
		// That means the trajectory from wp0 to wp1 can be used to compute a valid wind
		if (verticalRate > MOVING_SPEED || verticalRate < -MOVING_SPEED) 
			isFlying = true;
		else
			isFlying = false;
		
		// === Up or down? ===
		if (verticalRate < 0)
			isDescending = true;
		else
			isDescending = false;

		// === Distance ===
		// Uses the haversine formula to calculate the distance between two points
		// http://www.movable-type.co.uk/scripts/latlong.html
		
		// Square of half the chord length between points
		float a = FloatMath.sin(dLat/2) * FloatMath.sin(dLat/2) +
		        FloatMath.cos(lat0) * FloatMath.cos(lat1) * 
		        FloatMath.sin(dLon/2) * FloatMath.sin(dLon/2);
		
		// Angular distance in radians
		float c = (float) (2 * Math.atan2(FloatMath.sqrt(a), FloatMath.sqrt(1-a)));
		
		// Distance in meters. This is the distance between the projection of the two
		// waypoints on the earth's surface, which assumes a constant radius of the
		// trajectory equal to the average radius of the earth's surface. It is OKAY to
		// make such assumption, as long as we do the same in the calculation of destination
		// coordinates from a start point given this distance.
		float distance = REARTH * c;
		speed = distance / dTime;

		// === Bearing ===
		float y = FloatMath.sin(dLon) * FloatMath.cos(lat1);
		float x = FloatMath.cos(lat0) * FloatMath.sin(lat1) -
				FloatMath.sin(lat0) * FloatMath.cos(lat1) * FloatMath.cos(dLon);
		float bearing = (float) Math.atan2(y, x);
		
		// Reverse the bearing, since we want the direction the wind is coming from
		if (bearing < PI)
			direction = Waypoint.radToDegE6(bearing + PI);
		else
			direction = Waypoint.radToDegE6(bearing - PI);
		
		// === Altitude ===
		highAltitude = wp1.getAltitude();
	}
	
	
	public void getFromCursor(Cursor c) {
		highAltitude = c.getFloat(Database.Winds.HIGH_ALTITUDE_COL);
		speed = c.getFloat(Database.Winds.SPEED_COL);
		direction = c.getInt(Database.Winds.DIRECTION_COL);
	}
	
	public void save(ContentResolver cr) {
		ContentValues cv = new ContentValues();
		cv.put(Database.Winds.HIGH_ALTITUDE, highAltitude);
		cv.put(Database.Winds.SPEED, speed);
		cv.put(Database.Winds.DIRECTION, direction);
		cr.insert(Uri.withAppendedPath(
				PayloadContentProvider.CONTENT_URI, Database.Winds.name), cv);
	}
	
	// Getters and setters


	public void setHighAltitude(float altitude) {
		this.highAltitude = altitude;
	}

	public float getHighAltitude() {
		return highAltitude;
	}

	public void setSpeed(float mSpeed) {
		this.speed = mSpeed;
	}

	public float getSpeed() {
		return speed;
	}

	public void setDirection(int mDirection) {
		this.direction = mDirection;
	}

	public int getDirection() {
		return direction;
	}

	public boolean isFlying() {
		return isFlying;
	}

	public boolean isDescending() {
		return isDescending;
	}
}

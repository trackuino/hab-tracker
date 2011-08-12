package org.trackuino.Payload;

import com.google.android.maps.GeoPoint;

import android.util.FloatMath;

public class Waypoint {
	private static final float PI = 3.1415926539f;
	private final static float REARTH = 6356766;		// m
	private final static float GRAVITY = 9.80665f;		// m/s^2
	private final static float MOLWEIGHT = 0.0289644f;	// molar mass of dry air (kg/mol)
	private final static float RGAS = 8.31432f;		// universal gas constant (N * m / (mol * degK))

	// Standard Atmosphere (1976) model:
	private final static float [] HEIGHT = new float [] {		// m 
			0, 11000, 20000, 32000, 47000, 51000, 71000, 95000 };
	private final static float [] TLAPSE = new float [] {		// degK / m
			-.0065f, 0, .001f, .0028f, 0, -.0028f, -.002f };
	// T = T0 + L*h. Precalculated to speed up calculations: 
	private final static float [] BASETEMP = new float [] {	// degK
			288.15f, 216.65f, 216.65f, 228.65f, 270.65f, 270.65f, 214.65f };
	// P = P0 * (1- (L*h) / T0) ^ ( (g*M) / (R*L) ), or precalculated...
	private final static float [] PRESS = new float [] { 		// pascals
			101325, 22632.1f, 5474.89f, 868.019f, 110.906f, 66.9389f, 3.95642f };

	// private final static float mAVOGADRO = 6.022169E+23f;	// Avogadro's number

	private float time;
	private int lat;						// degE6
	private int lon;						// degE6
	private float altitude;
	private Source source;
	private Aprs.Packet packet = null;		// Packet that originated this waypoint
	//private Wind mWind = null;			// Wind data that originated this waypoint
	
	public enum Source {
		APRS,				// Accurate data as much as it's reported by the payload itself
		INFLIGHT_WINDS,		// Prediction of the descent based on ascent's winds
		SOUNDING_WINDS,		// Prediction of the ascent/descent based on sounding winds (NOAA, ...)
		FABRICATED			// Made-up path when no winds are available 
	}
	
	// This creates a new waypoint from an APRS-formatted packet
	public Waypoint(Aprs.Packet packet) {
		setPacket(packet);
		setTime(packet.getTime());
		lat = packet.getLat();
		lon = packet.getLon();
		setAltitude(packet.getAltitude());
	}
	
	// This creates a new waypoint from a GeoPoint and its altitude
	// TODO: could query google maps to find altitude out?
	public Waypoint(GeoPoint point, float altitude) {
		lat = point.getLatitudeE6();
		lon = point.getLongitudeE6();
		this.altitude = altitude;
	}
	
	// This creates a new waypoint from a start waypoint plus speed, direction, target
	// altitude and vertical rate at MSL.
	// Vertical rates are +(up) or -(down).
	public Waypoint(Waypoint wp0, int directionE6, float speed, float targetAltitude,
			float verticalRate) {

		// Convert the vertical rate from m/min to m/sec
		verticalRate /= 60;
		
		if (verticalRate < 0) {
			verticalRate = wp0.descent_rate(verticalRate);
		}

		// Altitude delta (negative if descending, positive if ascending)
		float dAltitude = targetAltitude - wp0.altitude;
		
		// Time to reach the target altitude at the given vertical rate
		// This should always be positive (TODO: assert that?)
		float dTime = dAltitude / verticalRate;
		
		// Set the target time
		time = wp0.getTime() + dTime;
		
		// Distance covered during the given dTime at the high wind's speed
		float distance = speed * dTime;
		
		// Angular distance
		float angDist = distance / REARTH;
		float lat0 = wp0.getLatRad();
		float lon0 = wp0.getLonRad();
		
		// The bearing is the opposite to the wind's direction
		float bearing = degE6toRad(directionE6);
		if (bearing < PI)
			bearing += PI;
		else
			bearing -= PI;
		
		float lat1 = (float) Math.asin(
				FloatMath.sin(lat0) * FloatMath.cos(angDist) + 
                FloatMath.cos(lat0) * FloatMath.sin(angDist) * FloatMath.cos(bearing) );
		float lon1 = (float) (lon0 +	Math.atan2(
				FloatMath.sin(bearing) * FloatMath.sin(angDist) * Math.cos(lat0), 
                FloatMath.cos(angDist) - FloatMath.sin(lat0) * FloatMath.sin(lat1)));

		// Target coordinates
		lat = radToDegE6(lat1);
		lon = radToDegE6(lon1);
		altitude = targetAltitude;
	}

	public float pressure() {

		// Convert geometric altitude into geopotential altitude.
		// http://mtp.jpl.nasa.gov/notes/altitude/altitude.html
		//
		// Also from http://www.mathpages.com/home/kmath054.htm:
		//
		// Three distinct kinds of "altitude" are commonly used when discussing 
		// the vertical heights of objects in the atmosphere above the Earth's
		// surface.  The first is simple GEOMETRIC ALTITUDE, which is what
		// would be measured by an ordinary tape measure.  However, for many
		// purposes we are more interested in the PRESSURE ALTITUDE, which is 
		// actually an indication of the ambient pressure, expressed in terms
		// of the altitude at which that pressure would exist on a "standard
		// day".  Finally, there is the so-called GEOPOTENTIAL ALTITUDE, which 
		// is really a measure of the specific potential energy at the given 
		// height (relative to the Earth's surface), converted into a distance 
		// using the somewhat peculiar assumption that the acceleration of 
		// gravity is constant, equal to the value it has at the Earth's 
		// surface.
		
		
		// Geographic (GPS) -> geopotential (radiosondes)
		float height = (REARTH * getAltitude()) / (REARTH + getAltitude());
		
		// Find out the layer we're in
		int layer = 0;
		while (layer < 7 && height > HEIGHT[layer+1])
			layer++;
		
		if (layer == 7)
			throw new IllegalArgumentException("Altitude is too high (> 95 Km)");
		
		// Calculate temperature at height 'h'
		float temperature = BASETEMP[layer] + TLAPSE[layer] * (height - HEIGHT[layer]);
		
		// Calculate pressure at height 'h' using the barometric formulas. The actual
		// formula depends on whether the temperature lapse for that layer is 0 or not.
		// http://en.wikipedia.org/wiki/Barometric_formula
		float pressure;
		if (TLAPSE[layer] == 0) {
			pressure = (float) (PRESS[layer] * Math.exp(
					-MOLWEIGHT * GRAVITY * (height - HEIGHT[layer]) / (RGAS * BASETEMP[layer])));
		} else {
			pressure = (float) (PRESS[layer] * Math.pow(
					BASETEMP[layer] / temperature,
					MOLWEIGHT * GRAVITY / (RGAS * TLAPSE[layer])));
		}
		return pressure;
	}
	
	float descent_rate(float msl_descent_rate) {
		// TODO: use air density, rather than pressure - 2011/05/15: Why was that again..?
		return msl_descent_rate * FloatMath.sqrt(101325 / pressure());
	}

	static int radToDegE6(float rad) {
		return (int) (rad * 180E6f / PI);
	}
	
	static float degE6toRad(int degE6) {
		return degE6 * PI / 180E6f;
	}
	
	static float uselessRadToDeg(float rad) {
		return rad * 180 / PI;
	}
	
	static float uselessDegToRad(float deg) {
		return deg * PI / 180;
	}
	

	// Getters and setters

	public void setLat(int lat) {
		this.lat = lat;
	}
	public int getLat() {
		return lat;
	}
	public float getLatRad() {
		return degE6toRad(lat);
	}
	public void setLon(int lon) {
		this.lon = lon;
	}
	public int getLon() {
		return lon;
	}
	public float getLonRad() {
		return degE6toRad(lon);
	}
	public void setPacket(Aprs.Packet packet) {
		this.packet = packet;
	}
	public Aprs.Packet getPacket() {
		return packet;
	}

	public void setTime(float time) {
		this.time = time;
	}

	public float getTime() {
		return time;
	}

	public void setAltitude(float altitude) {
		this.altitude = altitude;
	}

	public float getAltitude() {
		return altitude;
	}

	public void setSource(Source source) {
		this.source = source;
	}

	public Source getSource() {
		return source;
	}
}
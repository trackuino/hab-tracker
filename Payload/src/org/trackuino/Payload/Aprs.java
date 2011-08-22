package org.trackuino.Payload;

import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

public class Aprs {
	// TNC2 packet format
	private final static String HEADER = "(.+?):";
	private final static String DATA = "(.*)";
	
	// APRS format (trackuino specific)
	private final static String BEGIN = "/";
	private final static String TIME = "(\\d+?)h";
	private final static String LAT = "(\\d{4}\\.\\d{2})([NS])";
	private final static String TABLE = "([/\\\\])";
	private final static String LON = "(\\d{5}\\.\\d{2})([EW])";
	private final static String SYM = "(.)";
	private final static String COURSE = "(\\d\\d\\d)";
	private final static String CSSEP = "(/)";
	private final static String SPEED = "(\\d\\d\\d)";
	private final static String COMMENT = "(.*/A=(\\d+).*)";
	private final static int SECONDS_IN_A_DAY = 24 * 60 * 60;
	
	private static final Pattern REPACKET = Pattern.compile(
			HEADER + DATA);
	
	private static final Pattern REAPRS = Pattern.compile(
			BEGIN + TIME + LAT + TABLE + LON + SYM +
			COURSE + CSSEP + SPEED + COMMENT);
	
	public enum Tag {
		OK,
		DUP,
		BAD_POS,
		OLD,
		TOO_FAST
	}

	private HashSet<String> packetsSet = new HashSet<String>();
	private int lastPacketTime = -1;
	
	public class Packet {
		private String mHeader;
		private String mData;
		private int mTime;
		private int mLat;
		private String mTable;
		private int mLon;
		private String mSym;
		private int mCourse;
		private String mCsSep;
		private float mSpeed;		// m/s
		private String mComment;
		private float mAltitude;	// m
		private Tag mTag;
		
		public Packet(String line) {
			decode(line);
			tag();
		}
		
		private void tag() {
			// Filter out bad position packets first, so that the other checks don't get
			// confused with bad data.
			if (mLat == 0 && mLon == 0 && mTime == 0) {
				mTag = Tag.BAD_POS;
			} 
			else if (! packetsSet.add(mData)) {
				// It's already in the set, so tag it as duplicate
				mTag = Tag.DUP;
			}
			else if (oldPacket()) {
				mTag = Tag.OLD;
			}
			else
				mTag = Tag.OK;
		}
		
		private boolean oldPacket() {
			boolean r = false;
			// If the packet is older than the previous one, tag it as old. We use
			// modular arithmetic to account for 24h rollovers (23:59:59 -> 00:00:00)
			if (lastPacketTime != -1 && 
				Util.mod(mTime - lastPacketTime, SECONDS_IN_A_DAY) > SECONDS_IN_A_DAY / 2) 
			{
				r = true;
			}
			lastPacketTime = mTime;
			return r;
		}

		public Packet(Cursor c) {
			getFromCursor(c);
		}
		
		public void decode(String line) {
			Matcher m = REPACKET.matcher(line);
			if (m.matches()) {
				// Store the packet's header/data
				mHeader = m.group(1);
				mData = m.group(2);
				
				// See if it looks like APRS and decode it
				m = REAPRS.matcher(mData);
				if (m.matches()) {
					mTime = decode_time(m.group(1));
					mLat = decode_lat(m.group(2), m.group(3));
					mTable = m.group(4);
					mLon = decode_lon(m.group(5), m.group(6));
					mSym = m.group(7);
					mCourse = Integer.parseInt(m.group(8));
					mCsSep = m.group(9);
					mSpeed = decode_speed(m.group(10));
					mComment = m.group(11);
					mAltitude = decode_altitude(m.group(12));
				}
			}
		}
		
		
		private int decode_time(String time) {
			// Return the number of seconds since 00:00:00
			int h = Integer.parseInt(time.substring(0, 2));
			int m = Integer.parseInt(time.substring(2, 4));
			int s = Integer.parseInt(time.substring(4, 6));
			return h * 3600 + m * 60 + s;
		}

		private int decode_lat(String lat, String ns) {
			// DDMM.MM
			int d = Integer.parseInt(lat.substring(0, 2));
			int m = Integer.parseInt(lat.substring(2, 4));
			int hm = Integer.parseInt(lat.substring(5, 7));
			int l = (d * 1000000) + (m * 1000000 + hm * 10000) / 60;
			if (ns.equals("S")) l = -l;
			return l;
		}

		private int decode_lon(String lon, String ew) {
			// DDDMM.MM
			int d = Integer.parseInt(lon.substring(0, 3));
			int m = Integer.parseInt(lon.substring(3, 5));
			int hm = Integer.parseInt(lon.substring(6, 8));
			int l = (d * 1000000) + (m * 1000000 + hm * 10000) / 60;
			if (ew.equals("W")) l = -l;
			return l;
		}

		private float decode_altitude(String altitude) {
			float a = Float.valueOf(altitude).floatValue();
			a *= 0.3048;	// Convert 
			return a;
		}

		private float decode_speed(String speed) {
			float s = Float.valueOf(speed).floatValue();
			s *= 1.852;	// Convert to km/h
			s /= 3.6;	// Convert to m/s
			return s;
		}

		public void save(ContentResolver cr) {
			// TODO Auto-generated method stub
			ContentValues cv = new ContentValues();
			cv.put(Database.Packets.HEADER, mHeader);
			cv.put(Database.Packets.DATA, mData);
			cv.put(Database.Packets.TIME, mTime);
			cv.put(Database.Packets.LAT, mLat);
			cv.put(Database.Packets.TABLE, mTable);
			cv.put(Database.Packets.LON, mLon);
			cv.put(Database.Packets.SYM, mSym);
			cv.put(Database.Packets.COURSE, mCourse);
			cv.put(Database.Packets.CSSEP, mCsSep);
			cv.put(Database.Packets.SPEED, mSpeed);
			cv.put(Database.Packets.COMMENT, mComment);
			cv.put(Database.Packets.ALTITUDE, mAltitude);
			cv.put(Database.Packets.TAG, mTag.ordinal());
			cr.insert(Uri.withAppendedPath(
					PayloadContentProvider.CONTENT_URI, Database.Packets.name), cv);
		}
		
		public void getFromCursor(Cursor c) {
			mHeader = c.getString(Database.Packets.HEADER_COL);
			mData = c.getString(Database.Packets.DATA_COL);
			mTime = c.getInt(Database.Packets.TIME_COL);
			mLat = c.getInt(Database.Packets.LAT_COL);
			mTable = c.getString(Database.Packets.TABLE_COL);
			mLon = c.getInt(Database.Packets.LON_COL);
			mSym = c.getString(Database.Packets.SYM_COL);
			mCourse = c.getInt(Database.Packets.COURSE_COL);
			mCsSep = c.getString(Database.Packets.CSSEP_COL);
			mSpeed = c.getFloat(Database.Packets.SPEED_COL);
			mComment = c.getString(Database.Packets.COMMENT_COL);
			mAltitude = c.getFloat(Database.Packets.ALTITUDE_COL);
			mTag = Tag.values()[c.getInt(Database.Packets.TAG_COL)];
			
			// Add to the dupes hash set
			packetsSet.add(c.getString(Database.Packets.DATA_COL));
		}

		// Setters and getters...
		
		public void setHeader(String mHeader) {
			this.mHeader = mHeader;
		}

		public String getHeader() {
			return mHeader;
		}

		public void setTime(int mTime) {
			this.mTime = mTime;
		}

		public int getTime() {
			return mTime;
		}

		public void setLat(int mLat) {
			this.mLat = mLat;
		}

		public int getLat() {
			return mLat;
		}

		public void setTable(String mTable) {
			this.mTable = mTable;
		}

		public String getTable() {
			return mTable;
		}

		public void setLon(int mLon) {
			this.mLon = mLon;
		}

		public int getLon() {
			return mLon;
		}

		public void setSym(String mSym) {
			this.mSym = mSym;
		}

		public String getSym() {
			return mSym;
		}

		public void setCourse(int mCourse) {
			this.mCourse = mCourse;
		}

		public int getCourse() {
			return mCourse;
		}

		public void setCsSep(String mCsSep) {
			this.mCsSep = mCsSep;
		}

		public String getCsSep() {
			return mCsSep;
		}

		public void setSpeed(float mSpeed) {
			this.mSpeed = mSpeed;
		}

		public float getSpeed() {
			return mSpeed;
		}

		public void setComment(String mComment) {
			this.mComment = mComment;
		}

		public String getComment() {
			return mComment;
		}

		public void setAltitude(float mAltitude) {
			this.mAltitude = mAltitude;
		}

		public float getAltitude() {
			return mAltitude;
		}

		public void setData(String mData) {
			this.mData = mData;
		}

		public String getData() {
			return mData;
		}

		public void setTag(Tag mTag) {
			this.mTag = mTag;
		}

		public Tag getTag() {
			return mTag;
		}

	}

}

package org.trackuino.Payload;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class Database extends SQLiteOpenHelper {

	static final int mVERSION = 14;
	static final String mDBNAME = "payload";

	public static class Servers {
		public final static String name = "servers";
		public final static String SERVER = "server";
		public final static String PORT = "port";
		public final static String COMMENT = "comment";
	}
	public static class Winds {
		public final static String name = "winds";
		public final static String HIGH_ALTITUDE = "high_altitude";
		public final static String SPEED = "speed";
		public final static String DIRECTION = "direction";
		public final static String SOURCE = "source";
		
		public final static int HIGH_ALTITUDE_COL = 1;
		public final static int SPEED_COL = 2;
		public final static int DIRECTION_COL = 3;
		public final static int SOURCE_COL = 4;
	}
	public static class Packets {
		public final static String name = "packets";
		public final static String HEADER = "header";
		public final static String DATA = "data";
		public final static String TIME = "time";
		public final static String LAT = "lat";
		public final static String TABLE = "_table";
		public final static String LON = "lon";
		public final static String SYM = "sym";
		public final static String COURSE = "course";
		public final static String CSSEP ="cssep";
		public final static String SPEED = "speed";
		public final static String COMMENT = "comment";
		public final static String ALTITUDE = "altitude";
		public final static String TAG = "tag";
		
		public final static int HEADER_COL = 1;
		public final static int DATA_COL = 2;
		public final static int TIME_COL = 3;
		public final static int LAT_COL = 4;
		public final static int TABLE_COL = 5;
		public final static int LON_COL = 6;
		public final static int SYM_COL = 7;
		public final static int COURSE_COL = 8;
		public final static int CSSEP_COL = 9;
		public final static int SPEED_COL = 10;
		public final static int COMMENT_COL = 11;
		public final static int ALTITUDE_COL = 12;
		public final static int TAG_COL = 13;
	}
	public static class Path {
		public final static String name = "path";
		public final static String LAT = "lat";
		public final static String LON = "lon";
		public final static String ALTITUDE = "altitude";
		public final static String LEG = "leg";
		public final static String SOURCE = "source";
		
	}
	
	public Database(Context context) {
		super(context, mDBNAME, null, mVERSION);
	}
	
	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL("CREATE TABLE servers (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
				Servers.SERVER   + " TEXT, " +
				Servers.PORT     + " INTEGER, " +
				Servers.COMMENT  + " TEXT);");
		db.execSQL("CREATE TABLE winds (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
				Winds.HIGH_ALTITUDE  + " REAL, " +
				Winds.SPEED      + " REAL, " +
				Winds.DIRECTION  + " INTEGER, " +
				Winds.SOURCE     + " INTEGER);");
		db.execSQL("CREATE TABLE packets (_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
				Packets.HEADER   + " TEXT, " +
				Packets.DATA     + " TEXT, " +
				Packets.TIME     + " INTEGER, " +
				Packets.LAT      + " INTEGER, " +
				Packets.TABLE    + " TEXT, " +
				Packets.LON      + " INTEGER, " +
				Packets.SYM      + " TEXT, " +
				Packets.COURSE   + " INTEGER, " +
				Packets.CSSEP    + " TEXT, " +
				Packets.SPEED    + " INTEGER, " +
				Packets.COMMENT  + " TEXT, " +
				Packets.ALTITUDE + " REAL, " +
				Packets.TAG      + " INTEGER);");
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int from, int to) {
		
		db.execSQL("DROP TABLE IF EXISTS servers;");
		db.execSQL("DROP TABLE IF EXISTS winds;");
		db.execSQL("DROP TABLE IF EXISTS packets;");
		onCreate(db);
	}
}

package org.trackuino.Payload;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;

// With help from:
// http://www.devx.com/wireless/Article/41133/1763/page/2
// http://stackoverflow.com/questions/3544913/android-how-to-use-cursoradapter
// http://stackoverflow.com/questions/5652091/fill-listview-from-a-cursor-in-android

public class PayloadContentProvider extends ContentProvider {

	public static final String PROVIDER_NAME = "org.trackuino.payload";
	public static final Uri CONTENT_URI = Uri.parse("content://" + PROVIDER_NAME);
	
	private static final int SERVERS = 1;
	private static final int SERVER = 2;
	private static final int PACKETS = 3;
	private static final int PACKET = 4;
	private static final int WINDS = 5;
	private static final int WIND = 6;

	private static final UriMatcher uriMatcher;
	static {
		uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		uriMatcher.addURI(PROVIDER_NAME, "servers", SERVERS);
		uriMatcher.addURI(PROVIDER_NAME, "servers/#", SERVER);
		uriMatcher.addURI(PROVIDER_NAME, "packets", PACKETS);
		uriMatcher.addURI(PROVIDER_NAME, "packets/#", PACKET);
		uriMatcher.addURI(PROVIDER_NAME, "winds", WINDS);
		uriMatcher.addURI(PROVIDER_NAME, "winds/#", WIND);
	}
	
	private Database mDb;
	
	@Override
	public boolean onCreate() {
		Context context = getContext();
		mDb = new Database(context);
		return true;
	}


	@Override
	public String getType(Uri uri) {
		switch (uriMatcher.match(uri)) {
		case SERVERS:
		case PACKETS:
		case WINDS:
			return "vnc.android.cursor.dir/vnd.trackuino." + uri.getPathSegments().get(0);
		case SERVER:
		case PACKET:
		case WIND:
			return "vnd.android.cursor.item/vnd.trackuino." + uri.getPathSegments().get(0);
		default:
			throw new IllegalArgumentException("Unsupported URI: " + uri);
		}
	}


	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
			String sortOrder) {
		SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
		switch(uriMatcher.match(uri)) {
		case SERVERS:
		case PACKETS:
		case WINDS:
			break;
		case SERVER:
		case PACKET:
		case WIND:
			selection = "_id = ?";
			selectionArgs = new String[] { uri.getPathSegments().get(1) };
			break;
		default:
			throw new IllegalArgumentException("Unsupported URI: " + uri);
		}

		builder.setTables(uri.getPathSegments().get(0));
		if (sortOrder==null || sortOrder=="")
	         sortOrder = "_id";
	    Cursor c = builder.query(
	    		mDb.getReadableDatabase(),
	    		projection,
	    		selection,
	    		selectionArgs,
	    		null,
	    		null,
	    		sortOrder);
	    
	    // register to watch a content URI for changes (TODO: wtf is this for)
	    c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		String table = null;
		switch (uriMatcher.match(uri)) {
		case SERVERS:
		case PACKETS:
		case WINDS:
			break;
		default:
			throw new IllegalArgumentException("Unsupported URI: " + uri);
		}

		table = uri.getPathSegments().get(0);

		long new_id = mDb.getWritableDatabase().insert(table, null, values);
		if (new_id > 0) {
			Uri u = Uri.withAppendedPath(CONTENT_URI, table);
			getContext().getContentResolver().notifyChange(u, null);
		}
		return null;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		String table;
		switch (uriMatcher.match(uri)) {
		case PACKETS:
		case SERVERS:
		case WINDS:
			break;
		case PACKET:
		case SERVER:
		case WIND:
			selection = "_id = ?";
			selectionArgs = new String[] { uri.getPathSegments().get(1) };
			break;
		default:
			throw new IllegalArgumentException("Unsupported URI: " + uri);
		}

		table = uri.getPathSegments().get(0);
		int ret = mDb.getWritableDatabase().update(table, values, selection, selectionArgs);
		Uri u = Uri.withAppendedPath(CONTENT_URI, table);
        getContext().getContentResolver().notifyChange(u, null);
        return ret;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
        String table = null;
        switch (uriMatcher.match(uri)) {
        case PACKETS:
        case SERVERS:
        case WINDS:
        	break;
        case PACKET:
        case SERVER:
        case WIND:
			selection = "_id = ?";
			selectionArgs = new String[] { uri.getPathSegments().get(1) };
			break;
		default:
            throw new IllegalArgumentException("Unknown URL " + uri);
        }
        
        table = uri.getPathSegments().get(0);
        int ret = mDb.getWritableDatabase().delete(table, selection, selectionArgs);
        Uri u = Uri.withAppendedPath(CONTENT_URI, table);
        getContext().getContentResolver().notifyChange(u, null);
        return ret;     
    }
}

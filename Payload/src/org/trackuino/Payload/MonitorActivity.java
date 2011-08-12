package org.trackuino.Payload;

import android.app.ListActivity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;


public class MonitorActivity extends ListActivity {
	// private ArrayList<AprsPacket> mPackets = null;
	// private MonitorListViewAdapter mAdapter;
	private MonitorCursorAdapter mAdapter;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Use a cursor adapter to bind the content provider's data to the listview
        Uri u = Uri.withAppendedPath(
        		PayloadContentProvider.CONTENT_URI, 
        		Database.Packets.name);
        Cursor c = managedQuery(u, null, null, null, null);
        mAdapter = new MonitorCursorAdapter(
        		this, 
        		R.layout.monitor_row, 
        		c, 
        		new String[] { Database.Packets.HEADER, Database.Packets.DATA },
        		new int[] { R.id.l1, R.id.l2 });
        setListAdapter(mAdapter);
        
/*
		// Use a regular ListViewAdapter to bind data from an arraylist        
        mPackets = new ArrayList<AprsPacket>();
        mAdapter = new MonitorListViewAdapter(this, R.layout.monitor_row, mPackets);
        setListAdapter(mAdapter);

        // Debug dummy rows
        AprsPacket p;
        p = new AprsPacket("KJ6KUV-11>APRS,K6ERN*,WIDE2,qAR,K6TZ-10:/152026h3426.53N/11916.08WO299/000/A=000764/Ti=29/Te=25 Trackuino-WEB MKI HAB");
        mPackets.add(p);
        p = new AprsPacket("KJ6KUV-11>APRS,WIDE2-1,qAR,K6TZ-10:/152203h3426.34N/11916.06WO181/001/A=002638/Ti=30/Te=23 Trackuino-WEB MKI HAB");
        mPackets.add(p);
*/
        
        ListView lv = getListView();
        lv.setTextFilterEnabled(true);
        setContentView(R.layout.monitor);

    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
    	Cursor c = (Cursor) l.getItemAtPosition(position);
    	Toast.makeText(
    			this, 
    			c.getString(c.getColumnIndex(Database.Packets.DATA)),
    			Toast.LENGTH_LONG).show();
    	
    }
}

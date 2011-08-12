package org.trackuino.Payload;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Filterable;
import android.widget.SectionIndexer;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

// Reference for most of this: 
// http://stackoverflow.com/questions/4330565/using-cursor-with-listview-adapter-for-a-large-amount-of-data

public class MonitorCursorAdapter extends SimpleCursorAdapter 
	implements OnClickListener, android.widget.AdapterView.OnItemClickListener {

	private Context mContext;
	private int mLayout;
	private LayoutInflater mInflater;

	public MonitorCursorAdapter (Context context, int layout, Cursor c, String[] from, int[] to) {
		super(context, layout, c, from, to);
		mContext = context;
		mLayout = layout;
		mInflater = LayoutInflater.from(context);	// do this here for performance (read somewhere)
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent) {

		View v = mInflater.inflate(mLayout, parent, false);
		bindView(v, context, cursor);
		return v;
	}

	@Override
	public void bindView(View v, Context context, Cursor c) {
		int header_col = c.getColumnIndex(Database.Packets.HEADER);
		int data_col = c.getColumnIndex(Database.Packets.DATA);
		TextView l1 = (TextView) v.findViewById(R.id.l1);
		TextView l2 = (TextView) v.findViewById(R.id.l2);

		l1.setText(c.getString(header_col));
		l2.setText(c.getString(data_col));
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		Log.e("item Click", arg1.toString()+ " position> " +arg2);
	}

	@Override
	public void onClick(View v) {
		/*
		if(v.getId()==R.id.Button01){
			//Log.e("Button Click", v.toString()+ " position> " +v.getTag().toString());
			v.setVisibility(View.INVISIBLE);
			DataBaseNamesHelper dbNames = new DataBaseNamesHelper(context);
			dbNames.setFavouritesFlag(v.getTag().toString());
		}
		*/
	}
}

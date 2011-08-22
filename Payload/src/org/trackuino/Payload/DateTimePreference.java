package org.trackuino.Payload;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.TimePicker;

public class DateTimePreference extends DialogPreference {

	private Context context;
	private DatePicker datePicker;
	private TimePicker timePicker;
	private Button nowButton;
	private Calendar cal;

	public DateTimePreference(Context context, AttributeSet attributes) {
		super(context, attributes);
		this.context = context;
	}
	
	@Override
	public void onBindDialogView(View view) {
		
		datePicker = (DatePicker) view.findViewById(R.id.DatePicker);
		timePicker = (TimePicker) view.findViewById(R.id.TimePicker);
		nowButton = (Button) view.findViewById(R.id.SetToNow);

		cal = new GregorianCalendar(TimeZone.getDefault());
		cal.setTimeInMillis(getSharedPreferences().getLong(getKey(), 0));

		nowButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				cal.setTime(new Date());
				setWidgets();
			}
		});
		
		setWidgets();
	}
	
	@Override
	public void onClick(DialogInterface dialog, int button) {
		if (button == Dialog.BUTTON_POSITIVE) {
			cal.set(datePicker.getYear(),
					datePicker.getMonth(),
					datePicker.getDayOfMonth(),
					timePicker.getCurrentHour(),
					timePicker.getCurrentMinute(),
					0);
			cal.set(Calendar.MILLISECOND, 0);

			SharedPreferences.Editor editor = getEditor();
			long millis = cal.getTimeInMillis();
		    editor.putLong(getKey(), millis);
		    editor.commit();
		}
	}
	
	private void setWidgets() {
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH);
		int day = cal.get(Calendar.DAY_OF_MONTH);
		int hour = cal.get(Calendar.HOUR_OF_DAY);
		int min = cal.get(Calendar.MINUTE);
		
		datePicker.updateDate(year, month, day);
		timePicker.setCurrentHour(hour);
		timePicker.setCurrentMinute(min);
	}
}

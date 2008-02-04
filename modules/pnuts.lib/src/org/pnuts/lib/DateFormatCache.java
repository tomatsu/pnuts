package org.pnuts.lib;

import pnuts.lang.Context;
import java.util.Date;
import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

class DateFormatCache extends ResourceCache {
	private final static boolean DEBUG = false;

	Context context;

	DateFormatCache(Context context){
		this.context = context;
	}

	protected Object createResource(Object pattern){
		if (DEBUG){
			System.out.println("createResorce " + pattern);
		}
		DateFormat df = new SimpleDateFormat((String)pattern, locale.getLocale(context));
		df.setTimeZone(date.getTimeZone(context));
		return df;
	}

	DateFormat get(String pattern){
		return (DateFormat)super.getResource(pattern);
	}
}

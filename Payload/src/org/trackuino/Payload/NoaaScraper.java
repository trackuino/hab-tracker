package org.trackuino.Payload;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import android.graphics.Bitmap;

import com.google.android.maps.GeoPoint;

public class NoaaScraper {
	// 1st step: fetch the forecast models (GFS, RUC, etc.). These vary according to the lat/lon
	private ArrayList<String> models;
	private ArrayList<String> modelUrls;
	private static final Pattern reModels = Pattern.compile(
			"\"(/ready2-bin/metcycle.pl\\?product=profile1.*?)\">(.*?)</option>");
	
	// 2nd step: fetch the forecast cycles and session data (userid, etc.)
	private ArrayList<String> forecastCycles;
	private ArrayList<String> forecastCycleValues;
	private ArrayList<NameValuePair> sessionData;
	private static final Pattern reForecastCycles = Pattern.compile(
			"option value=\"(.*?)\">(.*?)</option>");
	private static final Pattern reSessionData = Pattern.compile(
			"<input type=\"HIDDEN\" name=\"(.*?)\" value=\"(.*?)\"");
	
	// 3rd step: fetch the launch times and image URL
	private ArrayList<String> launchTimes;
	private static final Pattern reLaunchTimes = Pattern.compile(
			"select name=\"metdate\" .*?</select>", Pattern.DOTALL);
	private static final Pattern reLaunchTime = Pattern.compile(
			"option>\\s*(.*?)\\s*(</option>)?\\s*<", Pattern.DOTALL);
	private static final Pattern reCaptchaUrl = Pattern.compile(
			"<img src=\"(.*?)\" ALT=\"Security Code\"");
	
	// 4th step: fetch the captcha
	private String captchaUrl;

	// 5th step: fetch the sounding
    private static final String WINDSTART = "^";
    private static final String PRESS = " +\\d+\\.";
    private static final String HEIGHT = " +(\\d+)\\.";
    private static final String TEMP = " +[\\d\\.-]+";
    private static final String DEWPT = TEMP;
    private static final String DIR = " +([\\d\\.-]+)";
    private static final String SPEED = DIR;
    private static final String WINDSTOP = " *$";
    private static Pattern reWind = Pattern.compile(
    		WINDSTART + PRESS + HEIGHT + TEMP + DEWPT + DIR + SPEED + WINDSTOP, 
    		Pattern.MULTILINE);
	
	// Other useful variables
	private String body;

	public ArrayList<String> fetchModels(GeoPoint point) throws IOException {
		String url = "http://ready.arl.noaa.gov/ready2-bin/main.pl";
	    ArrayList<NameValuePair> p = new ArrayList<NameValuePair>(3);
	    p.add(new BasicNameValuePair("Lat", String.valueOf(point.getLatitudeE6() / 1E6)));
	    p.add(new BasicNameValuePair("Lon", String.valueOf(point.getLongitudeE6() / 1E6)));
	    p.add(new BasicNameValuePair("Continue", "Continue"));

		Downloader d = new Downloader();
		d.post(url, p);
		body = d.asString();
		
		models = new ArrayList<String>();
		modelUrls = new ArrayList<String>();
		Matcher m = reModels.matcher(this.body);
		while (m.find()) {
			modelUrls.add(m.group(1));
			models.add(m.group(2));
		}
		return models;
	}

	public ArrayList<String> fetchForecastCycles(int model) throws IOException {
		String url = "http://ready.arl.noaa.gov" + modelUrls.get(model);
		Downloader d = new Downloader();
		d.get(url);
		body = d.asString();
		
		// Get forecast cycles
		forecastCycles = new ArrayList<String>();
		forecastCycleValues = new ArrayList<String>();
		Matcher m = reForecastCycles.matcher(this.body);
		while (m.find()) {
			forecastCycleValues.add(m.group(1));
			forecastCycles.add(m.group(2));
		}
		
		// Scrape session data
		sessionData = new ArrayList<NameValuePair>();
		m = reSessionData.matcher(this.body);
		while (m.find()) {
			sessionData.add(new BasicNameValuePair(m.group(1), m.group(2)));
		}
		return forecastCycles;
	}

	
	public ArrayList<String> fetchLaunchTimes(int cycle) throws IOException {
		String url = "http://ready.arl.noaa.gov/ready2-bin/profile1.pl";
		Downloader d = new Downloader();
		sessionData.add(new BasicNameValuePair("metcyc", forecastCycleValues.get(cycle)));
		d.post(url, sessionData);
		body = d.asString();

		// Scrape launch times from the form
		launchTimes = new ArrayList<String>();
	    Matcher m = reLaunchTimes.matcher(body);
	    if (m.find()) {
	    	String times = m.group(0);
	    	m = reLaunchTime.matcher(times);
	    	while (m.find()) {
	    		launchTimes.add(m.group(1));
	    	}
	    }
	    
	    // Scrape session data from the form
	    sessionData.clear();
	    m = reSessionData.matcher(body);
	    while (m.find()) {
	    	sessionData.add(new BasicNameValuePair(m.group(1), m.group(2)));
	    }
	    
	    // Scrape the captcha URL
	    m = reCaptchaUrl.matcher(body);
	    if (m.find()) {
	    	captchaUrl = "http://ready.arl.noaa.gov" + m.group(1);
	    }
	    
	    return launchTimes;
	}
	
	public Bitmap fetchCaptcha() throws IOException {
		Downloader d = new Downloader();
		d.get(captchaUrl);
		return d.asBitmap();
	}
	
	public LinkedList<Wind> fetchSounding(int launchTime, String captcha) throws IOException {
		String url = "http://ready.arl.noaa.gov/ready2-bin/profile2.pl";
		Downloader d = new Downloader();
		sessionData.add(new BasicNameValuePair("type", "0"));	// Animation
		sessionData.add(new BasicNameValuePair("nhrs", "24"));	// Duration
		sessionData.add(new BasicNameValuePair("hgt", "0"));	// Full sounding
		sessionData.add(new BasicNameValuePair("textonly", "Yes"));
		sessionData.add(new BasicNameValuePair("skewt", "0"));	// Text listing
		sessionData.add(new BasicNameValuePair("gsize", "96"));	// DPI
		sessionData.add(new BasicNameValuePair("pdf", "no"));	// Create pdf
		sessionData.add(new BasicNameValuePair("metdate", launchTimes.get(launchTime)));
		sessionData.add(new BasicNameValuePair("password1", captcha));
		
		d.post(url, sessionData);
		body = d.asString();
		LinkedList<Wind> sounding = new LinkedList<Wind>();
		
		Matcher m = reWind.matcher(body);
		while (m.find()) {
			Wind w = new Wind(m.group(1), m.group(2), m.group(3));
	    	sounding.add(w);
	    }
		return sounding;
	}
	
	public void setBody(String body) {
		this.body = body;
	}

	public String getBody() {
		return body;
	}
}

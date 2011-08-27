package org.trackuino.habtracker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

import com.google.android.maps.GeoPoint;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

// TODO: test all this without an internet connection. it'll probably blow up
public class Downloader {
	String url;
	HttpClient client;
	HttpResponse response;
	
    public boolean get(String url) throws IOException {
    	this.url = url;
		client = new DefaultHttpClient();
		HttpGet request = new HttpGet(url);
		response = client.execute(request);
	    final int statusCode = response.getStatusLine().getStatusCode();
	    if (statusCode != HttpStatus.SC_OK) { 
	        Log.w("HabTracker", "Error " + statusCode + " while retrieving bitmap from " + url); 
	        return false;
	    }
	    return true;
    }

	public boolean post(String url, List<NameValuePair> params) throws IOException {
	    // Usage: pass parameters like this:
	    // List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(2);
	    // nameValuePairs.add(new BasicNameValuePair("id", "12345"));
	    // nameValuePairs.add(new BasicNameValuePair("stringdata", "AndDev is Cool!"));

		this.url = url;
		client = new DefaultHttpClient();
	    HttpPost request = new HttpPost(url);
        request.setEntity(new UrlEncodedFormEntity(params));
        response = client.execute(request);
	    final int statusCode = response.getStatusLine().getStatusCode();
	    if (statusCode != HttpStatus.SC_OK) { 
	        Log.w("HabTracker", "Error " + statusCode + " while retrieving bitmap from " + url); 
	        return false;
	    }
	    return true;
	}
	
	public String asString() throws IllegalStateException, IOException {
		BufferedReader in = new BufferedReader(
				new InputStreamReader(response.getEntity().getContent()));
	    char[] buf = new char[1024];
	    int numRead = 0;
	    StringBuffer r = new StringBuffer();
	    try {
		    while((numRead = in.read(buf)) != -1) {
		    	r.append(buf, 0, numRead);
		    }
	    } finally {
	        if (in != null) try { in.close(); } catch (IOException ignored) { }
		}
	    return r.toString();
	}
	
	public ArrayList<String> asArrayList() throws IllegalStateException, IOException {
		BufferedReader in = new BufferedReader(
				new InputStreamReader(response.getEntity().getContent()));
		ArrayList<String> a = new ArrayList<String>();
		String line;

		try {
			while ((line = in.readLine()) != null) {
			    a.add(line);
			}
		} finally {
	        if (in != null) try { in.close(); } catch (IOException ignored) { }
		}
		return a;
	}
	
	public Bitmap asBitmap() throws IllegalStateException, IOException {
	    InputStream is = response.getEntity().getContent();
	    Bitmap bitmap = null;
	    try {
	    	bitmap = BitmapFactory.decodeStream(is);
		} finally {
	        if (is != null) try { is.close(); } catch (IOException ignored) { }
		}
        return bitmap;
	}
}
/*
 * 
 * From: http://android-developers.blogspot.com/2010/07/multithreading-for-performance.html
 * 
static Bitmap downloadBitmap(String url) {
final AndroidHttpClient client = AndroidHttpClient.newInstance("Android");
final HttpGet getRequest = new HttpGet(url);

try {
    HttpResponse response = client.execute(getRequest);
    final int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode != HttpStatus.SC_OK) { 
        Log.w("ImageDownloader", "Error " + statusCode + " while retrieving bitmap from " + url); 
        return null;
    }
    
    final HttpEntity entity = response.getEntity();
    if (entity != null) {
        InputStream inputStream = null;
        try {
            inputStream = entity.getContent(); 
            final Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            return bitmap;
        } finally {
            if (inputStream != null) {
                inputStream.close();  
            }
            entity.consumeContent();
        }
    }
} catch (Exception e) {
    // Could provide a more explicit error message for IOException or IllegalStateException
    getRequest.abort();
    Log.w("ImageDownloader", "Error while retrieving bitmap from " + url, e.toString());
} finally {
    if (client != null) {
        client.close();
    }
}
return null;
}


class BitmapDownloaderTask extends AsyncTask<String, Void, Bitmap> {
    private String url;
    private final WeakReference<ImageView> imageViewReference;

    public BitmapDownloaderTask(ImageView imageView) {
        imageViewReference = new WeakReference<ImageView>(imageView);
    }

    @Override
    // Actual download method, run in the task thread
    protected Bitmap doInBackground(String... params) {
         // params comes from the execute() call: params[0] is the url.
         return downloadBitmap(params[0]);
    }

    @Override
    // Once the image is downloaded, associates it to the imageView
    protected void onPostExecute(Bitmap bitmap) {
        if (isCancelled()) {
            bitmap = null;
        }

        if (imageViewReference != null) {
            ImageView imageView = imageViewReference.get();
            if (imageView != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }
}
*/
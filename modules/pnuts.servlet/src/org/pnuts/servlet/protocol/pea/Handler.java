package org.pnuts.servlet.protocol.pea;

import java.io.*;
import java.net.*;
import java.util.*;
import pnuts.lang.Context;

public class Handler extends URLStreamHandler {
	private Context context;
       private Set scriptURLs;

	public Handler(Context context, Set scriptURLs){
		this.context = context;
		this.scriptURLs = scriptURLs;
	}
	protected URLConnection openConnection(URL url) throws IOException {
		return new DynamicPageURLConnection(url, context, scriptURLs);
	}
}

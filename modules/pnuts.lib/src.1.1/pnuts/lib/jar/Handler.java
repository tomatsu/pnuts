package pnuts.lib.jar;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.io.IOException;

/**
 * a URLStreamHandler for JarResourceConnection
 */
public class Handler extends URLStreamHandler {
	public URLConnection openConnection(URL u) throws IOException {
		return new JarResourceConnection(u);
	}
}

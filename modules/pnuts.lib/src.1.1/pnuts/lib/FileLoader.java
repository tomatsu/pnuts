package pnuts.lib;

import java.io.*;
import java.net.*;

class FileLoader implements Loader {
	private File dir;
	private URL base;

	FileLoader(URL url) throws IOException {
		base = url;
		if (!"file".equals(url.getProtocol())) {
			throw new IllegalArgumentException("url");
		}
		dir = new File(url.getFile().replace('/', File.separatorChar));
	}

	public Resource getResource(final String name) {
		final URL url;
		try {
			url = new URL(base, name);
			if (url.getFile().startsWith(base.getFile()) == false) {
				return null;
			}
			final File file = new File(dir, name.replace('/', File.separatorChar));
			if (file.exists()) {
				return new Resource() {
						public URL getURL(){
							return url;
						}
						public InputStream getInputStream() throws IOException {
							return new FileInputStream(file);
						}
						public int getContentLength() throws IOException {
							return (int)file.length();
						}
					};
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}
}


package pnuts.lang;

class ConfigurationConstants {
	public static Configuration NORMAL_CONFIGURATION;
	static {
		boolean hasIterable = false;
		try {
			Class.forName("java.lang.Iterable");
			hasIterable = true;
		} catch (Exception e){
		}
		
		boolean hasCharSequence = false;
		try {
			Class.forName("java.lang.CharSequence");
			hasCharSequence = true;
		} catch (Exception e) {
		}
		if (hasIterable){
			NORMAL_CONFIGURATION = new TigerConfiguration();
		} else if (hasCharSequence) {
			NORMAL_CONFIGURATION = new MerlinConfiguration();
		} else {
			NORMAL_CONFIGURATION = new Java2Configuration();
		}
	}
}

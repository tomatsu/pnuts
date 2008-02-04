package pnuts.servlet;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.Set;
import pnuts.lang.Pnuts;
import pnuts.lang.Context;
import pnuts.lang.Package;

class PnutsServletContext {
	Context context;
	Package basePackage;
	Pnuts script;
	long time;
        Set/*<URL>*/ scriptURLs;
        
	PnutsServletContext(Context context, Package basePackage){
		this.context = context;
		this.basePackage = basePackage;
	}
        
        public boolean needToUpdate(){
            if (script == null){
                return true;
            }
            if (scriptURLs != null && time != 0L){
                try {
                    for (Iterator it = scriptURLs.iterator(); it.hasNext();){
                        URL url = (URL)it.next();
                        long modified = url.openConnection().getLastModified();
                        if (modified > time){
                            return true;
                        }
                    }
                } catch (IOException e){
                    // ignore
                }
            }
            return false;
        }
}

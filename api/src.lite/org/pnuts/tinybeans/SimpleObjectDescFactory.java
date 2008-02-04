package org.pnuts.tinybeans;

import org.pnuts.lang.ObjectDesc;
import org.pnuts.lang.ObjectDescFactory;

public class SimpleObjectDescFactory extends ObjectDescFactory {

    public ObjectDesc create(Class cls){
	return new SimpleObjectDesc(cls);
    }
}

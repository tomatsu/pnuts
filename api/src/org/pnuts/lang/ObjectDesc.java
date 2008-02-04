package org.pnuts.lang;

import java.lang.reflect.*;
import java.util.*;

public interface ObjectDesc {

    public Method[] getMethods();

    public void handleProperties(PropertyHandler handler);
}

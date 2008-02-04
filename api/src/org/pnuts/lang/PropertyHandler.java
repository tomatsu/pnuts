package org.pnuts.lang;

import java.lang.reflect.*;

public interface PropertyHandler {

    /**
     * Called by ObjectDesc.handleProperties()
     *
     * @param propertyName a property name
     * @param type the type of the property
     * @param readMethod the read method for the property
     * @param writeMethod the write method for the property
     */
    void handle(String propertyName, Class type, Method readMethod, Method writeMethod);
}

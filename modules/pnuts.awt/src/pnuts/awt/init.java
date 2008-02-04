package pnuts.awt;

import pnuts.lang.Runtime;
import pnuts.lang.Package;
import pnuts.lang.Context;
import pnuts.ext.ModuleBase;

/**
 * Initialization of the pnuts.awt module
 */
public class init extends ModuleBase {

	static String[] javaFunctions = {
		"useEventThread"
	};

	static String[] files  = {
		"pnuts/awt/lout",
		"pnuts/awt/key",
		"pnuts/awt/frame",
		"pnuts/awt/toolkit",
		"pnuts/awt/menu",
		"pnuts/awt/image",
		"pnuts/awt/clipboard"
	};

	static String[][] functions = {
		{
			"layout",
		},
		{
			"defineKey",
			"getKeyStroke"
		},
		{
			"frame",
			"dialog",
			"centerPosition",
			"getWindowCloseOperation",
			"setWindowCloseOperation"
		},
		{
			"getToolkit"
		},
		{
			"menubar",
			"menu",
			"getMenuItem"
		},
		{
			"makeImage",
			"readImage",
			"writeImage",
			"resizeImage",
			"showImage",
			"filterImage",
			"cropImage",
			"shearImage",
			"rotateImage",
			"flipImage",
			"scaleImage",
			"grayImage"
		},
		{
			"getClipboard",
			"setClipboard"
		}
	};

	static String[] requiredModules  = {
	    "pnuts.lib",
	    "pnuts.io",
	    "pnuts.beans"
	};

	protected String[] getRequiredModules(){
		return requiredModules;
	}

	public Object execute(Context context){
		for (int i = 0; i < files.length; i++){
			autoload(functions[i], files[i], context);
		}
		for (int i = 0; i < javaFunctions.length; i++){
			autoloadFunction(javaFunctions[i], context);
		}
		return null;
	}
}

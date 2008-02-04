package org.pnuts.tools;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JTextField;
import pnuts.tools.ContextEvent;
import pnuts.tools.ContextListener;

public class ClassLoaderView extends JTextField implements ContextListener {
    private final static Font monospaced = Font.getFont("monospaced");

    public ClassLoaderView(){
	super(20);
	setFont(monospaced);
	setAutoscrolls(true);
	setEditable(false);
	setSelectionColor(Color.white);
	setBackground(Color.white);
    }
    
    public void update(ContextEvent event){
        setText(String.valueOf(event.getContext().getClassLoader()));
    }
}

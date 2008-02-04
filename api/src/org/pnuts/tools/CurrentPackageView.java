package org.pnuts.tools;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JTextField;
import pnuts.tools.ContextEvent;
import pnuts.tools.ContextListener;

public class CurrentPackageView extends JTextField implements ContextListener {
    private final static Font monospaced = Font.getFont("monospaced");

    public CurrentPackageView(){
	super(20);
	setEditable(false);
	setBackground(Color.white);
	setSelectionColor(Color.white);
	setFont(monospaced);
	setAutoscrolls(true);
    }
     
    public void update(ContextEvent event){
        setText(String.valueOf(event.getContext().getCurrentPackage()));
    }   
}

package com.codealike.client.eclipse.views;


import java.net.MalformedURLException;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import com.codealike.client.eclipse.internal.utils.LogManager;
import com.codealike.client.eclipse.internal.utils.ResourcesUtils;

public class ErrorDialogView extends Dialog {

  private String buttonText;
  private String text;
  private String title;
  private ImageData imageData;
  private Runnable buttonAction;
	
  public ErrorDialogView(Shell parentShell, String title, String text, String buttonText, String imageFile) {
    super(parentShell);
    this.buttonText = buttonText;
    this.text = text;
    this.title = title;
	try {
		this.imageData = ResourcesUtils.loadImageData(imageFile);
	}
	catch (MalformedURLException e) {
		LogManager.INSTANCE.logWarn(e, "Could not load error dialog image.");
	}
	this.buttonAction = new Runnable() {
		
		@Override
		public void run() {
			close();
		}
	};
  }
  
  public ErrorDialogView(Shell parentShell, String title, String text, String buttonText, String imageFile, Runnable buttonAction) {
	    super(parentShell);
	    this.buttonText = buttonText;
	    this.text = text;
	    this.title = title;
		try {
			this.imageData = ResourcesUtils.loadImageData(imageFile);
		}
		catch (MalformedURLException e) {
			LogManager.INSTANCE.logWarn(e, "Could not load error dialog image.");
		}
		this.buttonAction = buttonAction;
	  }

  @Override
  protected Control createDialogArea(Composite parent) {
	Composite container = (Composite) super.createDialogArea(parent);
	Color white = container.getDisplay().getSystemColor(SWT.COLOR_WHITE);
    GridLayout gridLayout = new GridLayout(2, false);
    gridLayout.marginHeight = 15;
    gridLayout.marginWidth = 15;
	container.setLayout(gridLayout);
    container.setBackground(white);
    Label image = new Label(container, SWT.PUSH);
    image.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false,
        false));
	if (imageData != null) {
		image.setImage(new Image(parent.getDisplay(), imageData));
	}
    Label text = new Label(container, SWT.NONE);
    text.setBackground(white);
    text.setLayoutData(new GridData(SWT.BEGINNING, SWT.BOTTOM, false,
            false));
    text.setText(this.text);
    
    return container;
  }
  
  @Override
  protected Control createContents(Composite parent) {
    Control contents = super.createContents(parent);
    contents.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
    return contents;
  }
  
  @Override
  protected void createButtonsForButtonBar(Composite parent) {
	  parent.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_WHITE));
	  Button button = createButton(parent, IDialogConstants.OK_ID, 
			  buttonText, 
			  true); 
	  
	  button.addSelectionListener(new SelectionListener() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				buttonAction.run();
			}
			
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				buttonAction.run();
			}
		});
  }

  // overriding this methods allows you to set the
  // title of the custom dialog
  @Override
  protected void configureShell(Shell newShell) {
    super.configureShell(newShell);
    newShell.setBackground(newShell.getDisplay().getSystemColor(SWT.COLOR_WHITE));
    newShell.setText(this.title);
    ImageData codealikeIcon;
	try {
		codealikeIcon = ResourcesUtils.loadImageData("icons/Codealike.jpg");
		newShell.setImage(new Image(newShell.getDisplay(), codealikeIcon));
	} catch (MalformedURLException e) {
		LogManager.INSTANCE.logError(e, "Could not show error dialog.");
	}
  }

} 
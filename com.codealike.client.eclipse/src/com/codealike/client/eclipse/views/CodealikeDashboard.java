package com.codealike.client.eclipse.views;


import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.*;
import org.eclipse.core.databinding.beans.typed.BeanProperties;
import org.eclipse.core.databinding.observable.list.IObservableList;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.jface.action.*;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.*;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;

import com.codealike.client.eclipse.internal.model.TrackedProjectManager;
import com.codealike.client.eclipse.internal.services.IdentityService;
import com.codealike.client.eclipse.internal.services.ServiceListener;
import com.codealike.client.eclipse.internal.services.TrackingService;
import com.codealike.client.eclipse.internal.startup.PluginContext;
import com.codealike.client.eclipse.internal.utils.Configuration;


public class CodealikeDashboard extends ViewPart {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "com.codealike.client.eclipse.views.CodealikeDashboard";

	public static Font AVENIR_12 = new Font(Display.getCurrent(), "Avenir", 12, SWT.BOLD);
	public static Font AVENIR_10 = new Font(Display.getCurrent(), "Avenir", 10, SWT.NORMAL);
	
	private TrackingService trackingService;
	private TableViewer viewer;
	private PluginContext context;
	private StyledText signedAsLabel;
	private Link signOut;
	private Label signedOutLabel;
	private Link signInOrRegister;
	private CLabel onOffButton;
	private Group trackingGroup;
	private Text trackingText;
	private Clipboard cb;
	private Composite parentComponent;
	
	private ServiceListener authObserver = new ServiceListener() {
		
		@Override
		public void onEvent() {
			setSignedAsLabelText();
			if (context.getIdentityService().isAuthenticated()) {
				showTrackedProjects();
				signedAsLabel.getParent().layout();
				signOut.setVisible(true);
			}
			
			// refresh view
			parentComponent.pack();
			parentComponent.layout(true);
		}
	};
	
	private ServiceListener trackingObserver = new ServiceListener() {
		
		@Override
		public void onEvent() {
			if (onOffButton != null && !onOffButton.isDisposed()) {
				enableDisableTracking(onOffButton.getParent());
			}
			
			if (!parentComponent.isDisposed()) {
				// refresh view
				parentComponent.pack();
				parentComponent.layout(true);
			}
		}
	};	


	/**
	 * The constructor.
	 */
	public CodealikeDashboard() {
		this.context = PluginContext.getInstance();
		this.context.getIdentityService().addListener(authObserver);
		this.trackingService = this.context.getTrackingService();
		this.trackingService.addListener(trackingObserver);
		
		this.cb = new Clipboard(Display.getCurrent());
	}

	/**
	 * This is a callback that will allow us
	 * to create the viewer and initialize it.
	 */
	public void createPartControl(final Composite parent) {
		this.parentComponent = parent;
		GridLayout layout = new GridLayout(5, false);
		parent.setLayout(layout);
		signOut = new Link(parent, SWT.NONE);

		signOut.setText("<a>Codealike is connected</a>");
		signOut.setFont(AVENIR_10);
		signOut.addMouseListener(new MouseListener() {
			
			@Override
			public void mouseUp(MouseEvent e) { 
				//hideOrShowButtons(false);
				//AuthenticationBrowserView view = new AuthenticationBrowserView();
				//view.showLogOff(true);
				authenticate();
			}
			
			@Override
			public void mouseDown(MouseEvent e) {}
			
			@Override
			public void mouseDoubleClick(MouseEvent e) {}
		});
		signedAsLabel = new StyledText(parent, SWT.NONE);
		signedAsLabel.setFont(AVENIR_10);
		GridData signedAsLabelLayout = setHorizontalSpan(signedAsLabel, 4);
		signedAsLabelLayout.horizontalAlignment = SWT.BEGINNING;
		
		
		signedOutLabel = new Label(parent, SWT.NONE);
		signedOutLabel.setText("The configuration panel requires a valid token.");
		signedOutLabel.setFont(AVENIR_10);
		setHorizontalSpan(signedOutLabel, 3);
		
		new Label(parent, SWT.NONE);
		signInOrRegister = new Link(parent, SWT.NONE);
		signInOrRegister.setText("<a>Click here to configure Codealike</a>");
		signInOrRegister.setFont(AVENIR_10);
		signInOrRegister.addListener(SWT.Selection, new Listener() {

			@Override
			public void handleEvent(Event event) {
				authenticate();
			}});
		
		setSignedAsLabelText();

		new Label(parent, SWT.NONE);		
		new Label(parent, SWT.NONE);

		Color gray = new Color(Display.getCurrent(), 225, 225, 225);
		this.trackingGroup = new Group(parent, SWT.CENTER);
		trackingGroup.setFont(AVENIR_12);
		trackingGroup.setBackground(gray);
	    GridData groupGridData = new GridData(SWT.FILL, SWT.FILL, false, false);
	    groupGridData.horizontalSpan = 5;
	    trackingGroup.setLayoutData(groupGridData);
	    trackingGroup.setLayout(new GridLayout(1, false));
	    trackingText = new Text(trackingGroup, SWT.CENTER);
	    trackingText.setFont(AVENIR_10);
	    trackingText.setBackground(gray);   
	    
	    new Label(this.trackingGroup, SWT.NONE);
	    onOffButton = new CLabel(this.trackingGroup, SWT.CENTER);
	    onOffButton.setTopMargin(5);
	    onOffButton.setAlignment(SWT.CENTER);
	    onOffButton.setFont(new Font(Display.getCurrent(), "Avenir", 10, SWT.NORMAL));
	    enableDisableTracking(parent);
	    GridData onOffButtonLayout = new GridData(SWT.CENTER, SWT.CENTER, false, false);
	    onOffButtonLayout.heightHint = 25;
	    onOffButtonLayout.widthHint = 100;
	    onOffButton.setLayoutData(onOffButtonLayout);
	    onOffButton.addMouseListener(new MouseListener() {
			
			@Override
			public void mouseUp(MouseEvent e) {
				if (trackingService.isTracking()) {
					trackingService.disableTracking();
				}
				else {
					trackingService.enableTracking();
				}

			}
			
			@Override
			public void mouseDown(MouseEvent e) {}
			
			@Override
			public void mouseDoubleClick(MouseEvent e) {}
		});

		
		viewer = new TableViewer(parent, SWT.NONE);
		final Table activeTable = viewer.getTable();
		activeTable.setFont(AVENIR_10);

		GridData tableGridData = new GridData(GridData.FILL, GridData.FILL, false, true);
		tableGridData.horizontalSpan = 5;
		activeTable.setLayoutData(tableGridData);
		activeTable.setBackground(gray);
	    
		if (context.isAuthenticated()) {
			showTrackedProjects();
		}
	}
	
	private void enableDisableTracking(Composite parent) {
		Color green = parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN);
		Color red = parent.getDisplay().getSystemColor(SWT.COLOR_RED);
		Color white = parent.getDisplay().getSystemColor(SWT.COLOR_WHITE);
		onOffButton.setForeground(white);
	    this.onOffButton.setSize(100, 30);
	    
		if (trackingService.isTracking()) {
			this.trackingGroup.setText("We are tracking these projects");
			this.trackingText.setText("Go to your Web Dashboard and learn about your reports for these projects.");
			onOffButton.setText("ON");
			onOffButton.setBackground(green);
		}
		else {
			this.trackingGroup.setText("We are not tracking any projects");
			this.trackingText.setText("We recommend you to track your projects for awesome reports in action.");
			onOffButton.setText("OFF");
			onOffButton.setBackground(red);
		}
	}

	private GridData setHorizontalSpan(Control element, int span) {
		GridData layout = new GridData();
		layout.horizontalSpan = span;
		element.setLayoutData(layout);
		return layout;
	}
	
	private void hideOrShowButtons(boolean isAuthenticated) {
		try {
			if (signedOutLabel != null && signInOrRegister != null && signOut != null && signedAsLabel != null) {
				this.signedOutLabel.setVisible(!isAuthenticated);
				this.signInOrRegister.setVisible(!isAuthenticated);
				this.signOut.setVisible(isAuthenticated);
				this.signedAsLabel.setVisible(isAuthenticated);
			}
		}
		catch (SWTException e) {
			//swallow
		}
	}

	private void setSignedAsLabelText() {
		hideOrShowButtons(context.getIdentityService().isAuthenticated());
		
		if (context.getIdentityService().isAuthenticated()) {
			
			String name = context.getIdentityService().getProfile().getDisplayName();
			signedAsLabel.setText(name);
			StyleRange styleRange1 = new StyleRange();
		    styleRange1.start = 0;
		    styleRange1.length = name.length();
		    styleRange1.fontStyle = SWT.BOLD;
		    
		    signedAsLabel.setStyleRange(styleRange1);
		}
	}

	private void showTrackedProjects() {
		// use ObservableListContentProvider
		viewer.setContentProvider(new ObservableListContentProvider());

		// wrap the input into a writable list
		IObservableList<Object> input = BeanProperties.list(TrackedProjectManager.class, "trackedProjectsLabels").observe(trackingService.getTrackedProjectManager());
		// set the IObservableList as input for the viewer
		viewer.setInput(input);
		// Create the help context id for the viewer's control
		PlatformUI.getWorkbench().getHelpSystem().setHelp(viewer.getControl(), "com.codealike.client.eclipse.viewer");
		hookContextMenu();
		
		resizeTable(viewer.getTable());
	}
	
	private static void resizeColumn(TableColumn tableColumn)
	{
	    tableColumn.pack();

	}
	private static void resizeTable(Table table)
	{
	    for (TableColumn tc : table.getColumns())
	        resizeColumn(tc);
	}

	private void hookContextMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				CodealikeDashboard.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private void fillContextMenu(IMenuManager manager) {
		manager.add(createCopyAction());
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}

	private IAction createCopyAction() {
		Action action = new Action("Copy to clipboard") {
	            public void run() {
	            	 String uuid = ((StructuredSelection)viewer.getSelection()).getFirstElement().toString().split("- ")[1];
	            	 
	                 TextTransfer textTransfer = TextTransfer.getInstance();
	                 cb.setContents(new Object[] { uuid },
	                     new Transfer[] { textTransfer });
	        }
		};
		
		return action;
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		viewer.getControl().setFocus();
	}
	
	private void authenticate() {
		Configuration configuration = PluginContext.getInstance().getConfiguration();
		String existingToken = configuration.getUserToken();
		
		InputDialog dialog = new InputDialog(null, "Codealike Authentication", "Codealike Token:", existingToken, null);
		int result = dialog.open();
		
		if (result == 0) {
			String token = dialog.getValue();
			
			if (!token.isEmpty()) {
		        String[] split = token.split("/");
		        
		        if (split.length == 2) {
		            if(IdentityService.getInstance().login(split[0], split[1], true, true)) {
		                // nothing to do
		            }
		            else {
		        		MessageDialog.open(MessageDialog.ERROR, 
		        				PlatformUI.getWorkbench().getModalDialogShellProvider().getShell(),
		        				"Codealike Authentication", "We couldn't authenticate you. Please verify your token and try again", SWT.NONE);
		            }
		        }
		        else {
		        	MessageDialog.open(MessageDialog.ERROR, 
	        				PlatformUI.getWorkbench().getModalDialogShellProvider().getShell(),
	        				"Codealike Authentication", "We couldn't authenticate you. Please verify your token and try again", SWT.NONE);
	
		        }
			}
			else {
				// if user removed the token, we just logoff and remove the token from computer
				IdentityService.getInstance().logOff();
			}
		}
	}
}
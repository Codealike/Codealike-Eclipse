package com.codealike.client.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.codealike.client.eclipse.internal.services.IdentityService;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class SettingsCommandHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		
		IdentityService identityService = IdentityService.getInstance();
		String existingToken = identityService.getToken();
		
		InputDialog dialog = new InputDialog(null, "Codealike Authentication", "Codealike Token:", existingToken, null);
		int result = dialog.open();
		
		if (result == 0) {
			String token = dialog.getValue();
			
	        String[] split = token.split("/");
	        if (split.length == 2) {
	            if(identityService.login(split[0], split[1], true, true)) {
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
		
		return null;
	}
}

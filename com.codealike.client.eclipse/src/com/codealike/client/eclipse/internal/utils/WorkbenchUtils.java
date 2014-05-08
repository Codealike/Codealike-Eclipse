package com.codealike.client.eclipse.internal.utils;

import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.PartSite;

import com.codealike.client.eclipse.views.CodealikeDashboard;

@SuppressWarnings("restriction")
public class WorkbenchUtils {

	public static IWorkbenchWindow getActiveWindow() {
		final IWorkbenchWindow[] win = new IWorkbenchWindow[1];
		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				win[0] = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			}
		});
		return win[0];
	}

	public static boolean isActiveShell(IWorkbenchWindow win) {
		final Shell shell = win.getShell();
		final boolean[] result = new boolean[1];
		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
			@Override
			public void run() {
				result[0] = shell.getDisplay().getActiveShell() == shell
						&& !shell.getMinimized();
			}
		});
		return result[0];
	}
	
	public static IEditorPart getActiveEditor() {
		final IEditorPart[] result = new IEditorPart[1];
		PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {

			@Override
			public void run() {
				IWorkbenchWindow activeWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
				if (activeWindow != null) {
					IWorkbenchPage page =  activeWindow.getActivePage();
					result[0] = page.getActiveEditor();
				}
			}
		});
		
		return result[0];
	}
	
	public static void addMessageToStatusBar(final String message) {
		new Thread() {
			public void run() {
				PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
					@Override
					public void run() {
						IWorkbenchSite site = WorkbenchUtils.getActiveWindow().getActivePage().getActivePart().getSite();
						
						// Setting message in status bar if we are outside of CodealikeDashboard view
						if (site instanceof IViewSite) {
							IViewSite vSite = (IViewSite)site;
							vSite.getActionBars().getStatusLineManager().setMessage(message);
						}
						else if (site instanceof PartSite) {
							PartSite pSite = (PartSite)site;
							pSite.getActionBars().getStatusLineManager().setMessage(message);
						}
						
						
						if (WorkbenchUtils.getActiveWindow().getActivePage().findView(CodealikeDashboard.ID) != null) {
							WorkbenchUtils.getActiveWindow().getActivePage().findView(CodealikeDashboard.ID).getViewSite().
							getActionBars().getStatusLineManager().setMessage(message);
						}
					}
				});
			}
		}.start();
	}
	
}

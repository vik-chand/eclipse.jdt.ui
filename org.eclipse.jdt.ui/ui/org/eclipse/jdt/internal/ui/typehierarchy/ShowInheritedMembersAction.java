/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.ui.typehierarchy;

import org.eclipse.jface.action.Action;

import org.eclipse.ui.help.WorkbenchHelp;
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPluginImages;

/**
 * Action to show / hide inherited members in the method view
 * Depending in the action state a different label provider is installed in the viewer
 */
public class ShowInheritedMembersAction extends Action {
	
	private MethodsViewer fMethodsViewer;
	
	/** 
	 * Creates the action.
	 */
	public ShowInheritedMembersAction(MethodsViewer viewer, boolean initValue) {
		super(TypeHierarchyMessages.getString("ShowInheritedMembersAction.label")); //$NON-NLS-1$
		setDescription(TypeHierarchyMessages.getString("ShowInheritedMembersAction.description")); //$NON-NLS-1$
		setToolTipText(TypeHierarchyMessages.getString("ShowInheritedMembersAction.tooltip")); //$NON-NLS-1$
		
		JavaPluginImages.setLocalImageDescriptors(this, "inher_co.gif"); //$NON-NLS-1$

		fMethodsViewer= viewer;
		
		WorkbenchHelp.setHelp(this,	new Object[] { IJavaHelpContextIds.SHOW_INHERITED_ACTION });
 
		setChecked(initValue);
	}
	
	/*
	 * @see Action#actionPerformed
	 */	
	public void run() {
		fMethodsViewer.showInheritedMethods(isChecked());
	}	

	/*
	 * @see Action#setChecked
	 */	
	public void setChecked(boolean checked) {
		if (checked) {
			setToolTipText(TypeHierarchyMessages.getString("ShowInheritedMembersAction.tooltip.checked")); //$NON-NLS-1$
		} else {
			setToolTipText(TypeHierarchyMessages.getString("ShowInheritedMembersAction.tooltip.unchecked")); //$NON-NLS-1$
		}
		super.setChecked(checked);
	}
	
}
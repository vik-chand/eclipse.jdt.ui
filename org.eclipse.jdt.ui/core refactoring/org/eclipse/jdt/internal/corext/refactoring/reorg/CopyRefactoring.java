/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.internal.corext.refactoring.reorg;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.refactoring.Assert;
import org.eclipse.jdt.internal.corext.refactoring.CompositeChange;
import org.eclipse.jdt.internal.corext.refactoring.NullChange;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.IChange;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatus;
import org.eclipse.jdt.internal.corext.refactoring.changes.AddToClasspathChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CopyCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CopyPackageChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CopyResourceChange;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;

public class CopyRefactoring extends ReorgRefactoring {

	private Set fAutoGeneratedNewNames;
	private final ICopyQueries fCopyQueries;
	
	public CopyRefactoring(List elements, ICopyQueries copyQueries){
		super(elements);
		Assert.isNotNull(copyQueries);
		fCopyQueries= copyQueries;
		fAutoGeneratedNewNames=  new HashSet(2);
	}
	
	/* non java-doc
	 * @see IRefactoring#getName()
	 */
	public String getName() {
		return RefactoringCoreMessages.getString("CopyRefactoring.copy_elements"); //$NON-NLS-1$
	}
	
	/* non java-doc
	 * @see Refactoring#checkInput(IProgressMonitor)
	 */
	public final RefactoringStatus checkInput(IProgressMonitor pm) throws JavaModelException {
		pm.beginTask("", 1); //$NON-NLS-1$
		try{
			return new RefactoringStatus();
		} finally{
			pm.done();
		}	
	}
	
	/* non java-doc
	 * @see ReorgRefactoring#isValidDestinationForCusAndFiles(Object)
	 */
	boolean isValidDestinationForCusAndFiles(Object dest) throws JavaModelException {
		return getDestinationForCusAndFiles(dest) != null;
	}
	
	//-----
	private static boolean isNewNameOk(IPackageFragment dest, String newName) {
		return ! dest.getCompilationUnit(newName).exists();
	}
	
	private static boolean isNewNameOk(IContainer container, String newName) {
		return container.findMember(newName) == null;
	}

	private static boolean isNewNameOk(IPackageFragmentRoot root, String newName) {
		return ! root.getPackageFragment(newName).exists() ;
	}	
	
	private String createNewName(ICompilationUnit cu, IPackageFragment dest){
		if (isNewNameOk(dest, cu.getElementName()))
			return null;
		if (! cu.getParent().equals(dest))	
			return null;
		int i= 1;
		while (true){
			String newName;
			if (i == 1)
				newName= RefactoringCoreMessages.getFormattedString("CopyRefactoring.cu.copyOf1", //$NON-NLS-1$
							cu.getElementName());
			else	
				newName= RefactoringCoreMessages.getFormattedString("CopyRefactoring.cu.copyOfMore", //$NON-NLS-1$
							new String[]{String.valueOf(i), cu.getElementName()});
			if (isNewNameOk(dest, newName) && ! fAutoGeneratedNewNames.contains(newName)){
				fAutoGeneratedNewNames.add(newName);
				return newName;
			}
			i++;
		}
	}
	
	private String createNewName(IResource res, IContainer container){
		if (isNewNameOk(container, res.getName()))
			return null;
		if (! res.getParent().equals(container))	
			return null;
		int i= 1;
		while (true){
			String newName;
			if (i == 1)
				newName= RefactoringCoreMessages.getFormattedString("CopyRefactoring.resource.copyOf1", //$NON-NLS-1$
							res.getName());
			else
				newName= RefactoringCoreMessages.getFormattedString("CopyRefactoring.resource.copyOfMore", //$NON-NLS-1$
							new String[]{String.valueOf(i), res.getName()});
			if (isNewNameOk(container, newName) && ! fAutoGeneratedNewNames.contains(newName)){
				fAutoGeneratedNewNames.add(newName);
				return newName;
			}
			i++;
		}	
	}
	
	private String createNewName(IPackageFragment pack, IPackageFragmentRoot root){
		if (isNewNameOk(root, pack.getElementName()))
			return null;
		if (! pack.getParent().equals(root))	
			return null;
			
		int i= 1;
		while (true){
			String newName;
			if (i==0)
				newName= RefactoringCoreMessages.getFormattedString("CopyRefactoring.package.copyOf1", //$NON-NLS-1$
							pack.getElementName());
			else
				newName= RefactoringCoreMessages.getFormattedString("CopyRefactoring.package.copyOfMore", //$NON-NLS-1$
							new String[]{String.valueOf(i), pack.getElementName()});
			if (isNewNameOk(root, newName) && ! fAutoGeneratedNewNames.contains(newName)){
				fAutoGeneratedNewNames.add(newName);
				return newName;
			}
			i++;
		}	
	}

	IChange createChange(IProgressMonitor pm, IPackageFragmentRoot root) throws JavaModelException{
		IResource res= root.getUnderlyingResource();
		IProject project= getDestinationForSourceFolders(getDestination());
		IJavaProject javaProject= JavaCore.create(project);
		CompositeChange result= new CompositeChange(RefactoringCoreMessages.getString("CopyRefactoring.copy_source_folder"), 2); //$NON-NLS-1$
		String newName= createNewName(res, project);
		if (newName == null )
			newName= res.getName();
		result.add(new CopyResourceChange(res, project, fCopyQueries.createStaticQuery(newName)));
		if (javaProject != null)
			result.add(new AddToClasspathChange(javaProject, newName));
		return result;
	}
	
	IChange createChange(IProgressMonitor pm, IPackageFragment pack) throws JavaModelException{
		IPackageFragmentRoot root= getDestinationForPackages(getDestination());
		String newName= createNewName(pack, root);
		if (newName == null || JavaConventions.validatePackageName(newName).getSeverity() < IStatus.ERROR){
			INewNameQuery nameQuery;
			if (newName == null)
				nameQuery= fCopyQueries.createNullQuery();
			else
				nameQuery= 	fCopyQueries.createNewPackageNameQuery(pack);
			return new CopyPackageChange(pack, root, nameQuery);
		} else {
			if (root.getUnderlyingResource() instanceof IContainer){
				IContainer dest= (IContainer)root.getUnderlyingResource();
				IResource res= pack.getCorrespondingResource();
				INewNameQuery nameQuery= fCopyQueries.createNewResourceNameQuery(res);
				return new CopyResourceChange(res, dest, nameQuery);
			}else
				return new NullChange();
		}	
	}

	IChange createChange(IProgressMonitor pm, final IResource res) throws JavaModelException{
		IContainer dest= getDestinationForResources(getDestination());
		INewNameQuery nameQuery;
		if (createNewName(res, dest) == null)
			nameQuery= fCopyQueries.createNullQuery();
		else
			nameQuery= fCopyQueries.createNewResourceNameQuery(res);
		return new CopyResourceChange(res, dest, nameQuery);
	}
	
	IChange createChange(IProgressMonitor pm, ICompilationUnit cu) throws JavaModelException{
		Object dest= getDestinationForCusAndFiles(getDestination());
		if (dest instanceof IPackageFragment){
			String newName= createNewName(cu, (IPackageFragment)dest);
			CopyCompilationUnitChange simpleCopy= new CopyCompilationUnitChange(cu, (IPackageFragment)dest, fCopyQueries.createStaticQuery(newName));
			if (newName == null || newName.equals(cu.getElementName()))
				return simpleCopy;
			
			try {
				IPath newPath= ResourceUtil.getResource(cu).getParent().getFullPath().append(newName);				
				INewNameQuery nameQuery= fCopyQueries.createNewCompilationUnitNameQuery(cu);
				return new CreateCopyOfCompilationUnitChange(newPath, cu.getSource(), cu, nameQuery); //XXX
			} catch(CoreException e) {
				return simpleCopy; //fallback - no ui here
			}
		} else {
			Assert.isTrue(dest instanceof IContainer);//this should be checked before - in preconditions
			IResource res= ResourceUtil.getResource(cu);
			INewNameQuery nameQuery;
			if (createNewName(res, (IContainer)dest) == null)
				nameQuery= fCopyQueries.createNullQuery();
			else	
				nameQuery= fCopyQueries.createNewResourceNameQuery(res);
			return new CopyResourceChange(res, (IContainer)dest, nameQuery);
		}		
	}
}
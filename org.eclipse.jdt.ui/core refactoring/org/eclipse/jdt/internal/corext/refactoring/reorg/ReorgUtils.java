/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.reorg;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.SourceRange;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.WorkingCopyUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;


public class ReorgUtils {

	//workaround for bug 18311
	private static final ISourceRange fgUnknownRange= new SourceRange(-1, 0);

	private ReorgUtils() {
	}

	public static boolean containsOnlyProjects(List elements){
		if (elements.isEmpty())
			return false;
		for(Iterator iter= elements.iterator(); iter.hasNext(); ) {
			if (! isProject(iter.next()))
				return false;
		}
		return true;
	}
	
	public static boolean isProject(Object element){
		return (element instanceof IJavaProject) || (element instanceof IProject);
	}

	public static boolean isInsideCompilationUnit(IJavaElement element) {
		return 	!(element instanceof ICompilationUnit) && 
				hasAncestorOfType(element, IJavaElement.COMPILATION_UNIT);
	}
	
	public static boolean isInsideClassFile(IJavaElement element) {
		return 	!(element instanceof IClassFile) && 
				hasAncestorOfType(element, IJavaElement.CLASS_FILE);
	}
	
	public static boolean hasAncestorOfType(IJavaElement element, int type){
		return element.getAncestor(type) != null;
	}
	
	/**
	 * May be <code>null</code>.
	 */
	public static ICompilationUnit getCompilationUnit(IJavaElement javaElement){
		if (javaElement instanceof ICompilationUnit)
			return (ICompilationUnit) javaElement;
		return (ICompilationUnit) javaElement.getAncestor(IJavaElement.COMPILATION_UNIT);
	}

	/**
	 * some of the returned elements may be <code>null</code>.
	 */
	public static ICompilationUnit[] getCompilationUnits(IJavaElement[] javaElements){
		ICompilationUnit[] result= new ICompilationUnit[javaElements.length];
		for (int i= 0; i < javaElements.length; i++) {
			result[i]= getCompilationUnit(javaElements[i]);
		}
		return result;
	}
		
	public static IResource getResource(IJavaElement element){
		if (element instanceof ICompilationUnit)
			return JavaModelUtil.toOriginal((ICompilationUnit)element).getResource();
		else
			return element.getResource();
	}
	
	public static IResource[] getResources(IJavaElement[] elements) {
		IResource[] result= new IResource[elements.length];
		for (int i= 0; i < elements.length; i++) {
			result[i]= ReorgUtils.getResource(elements[i]);
		}
		return result;
	}
	
	public static String getName(IResource resource) {
		String pattern= createNamePattern(resource);
		String[] args= createNameArguments(resource);
		return MessageFormat.format(pattern, args);
	}
	
	private static String createNamePattern(IResource resource) {
		switch(resource.getType()){
			case IResource.FILE:
				return RefactoringCoreMessages.getString("ReorgUtils.0"); //$NON-NLS-1$
			case IResource.FOLDER:
				return RefactoringCoreMessages.getString("ReorgUtils.1"); //$NON-NLS-1$
			case IResource.PROJECT:
				return RefactoringCoreMessages.getString("ReorgUtils.2"); //$NON-NLS-1$
			default:
				Assert.isTrue(false);
				return null;
		}
	}

	private static String[] createNameArguments(IResource resource) {
		return new String[]{resource.getName()};
	}

	public static String getName(IJavaElement element) throws JavaModelException {
		String pattern= createNamePattern(element);
		String[] args= createNameArguments(element);
		return MessageFormat.format(pattern, args);
	}

	private static String[] createNameArguments(IJavaElement element) {
		switch(element.getElementType()){
			case IJavaElement.CLASS_FILE:
				return new String[]{element.getElementName()};
			case IJavaElement.COMPILATION_UNIT:
				return new String[]{element.getElementName()};
			case IJavaElement.FIELD:
				return new String[]{element.getElementName()};
			case IJavaElement.IMPORT_CONTAINER:
				return new String[0];
			case IJavaElement.IMPORT_DECLARATION:
				return new String[]{element.getElementName()};
			case IJavaElement.INITIALIZER:
				return new String[0];
			case IJavaElement.JAVA_PROJECT:
				return new String[]{element.getElementName()};
			case IJavaElement.METHOD:
				return new String[]{element.getElementName()};
			case IJavaElement.PACKAGE_DECLARATION:
				if (JavaElementUtil.isDefaultPackage(element))
					return new String[0];
				else
					return new String[]{element.getElementName()};
			case IJavaElement.PACKAGE_FRAGMENT:
				return new String[]{element.getElementName()};
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				return new String[]{element.getElementName()};
			case IJavaElement.TYPE:
				return new String[]{element.getElementName()};
			default:
				Assert.isTrue(false);
				return null;
		}
	}

	private static String createNamePattern(IJavaElement element) throws JavaModelException {
		switch(element.getElementType()){
			case IJavaElement.CLASS_FILE:
				return RefactoringCoreMessages.getString("ReorgUtils.3"); //$NON-NLS-1$
			case IJavaElement.COMPILATION_UNIT:
				return RefactoringCoreMessages.getString("ReorgUtils.4"); //$NON-NLS-1$
			case IJavaElement.FIELD:
				return RefactoringCoreMessages.getString("ReorgUtils.5"); //$NON-NLS-1$
			case IJavaElement.IMPORT_CONTAINER:
				return RefactoringCoreMessages.getString("ReorgUtils.6"); //$NON-NLS-1$
			case IJavaElement.IMPORT_DECLARATION:
				return RefactoringCoreMessages.getString("ReorgUtils.7"); //$NON-NLS-1$
			case IJavaElement.INITIALIZER:
				return RefactoringCoreMessages.getString("ReorgUtils.8"); //$NON-NLS-1$
			case IJavaElement.JAVA_PROJECT:
				return RefactoringCoreMessages.getString("ReorgUtils.9"); //$NON-NLS-1$
			case IJavaElement.METHOD:
				if (((IMethod)element).isConstructor())
					return RefactoringCoreMessages.getString("ReorgUtils.10"); //$NON-NLS-1$
				else
					return RefactoringCoreMessages.getString("ReorgUtils.11"); //$NON-NLS-1$
			case IJavaElement.PACKAGE_DECLARATION:
				return RefactoringCoreMessages.getString("ReorgUtils.12"); //$NON-NLS-1$
			case IJavaElement.PACKAGE_FRAGMENT:
				if (JavaElementUtil.isDefaultPackage(element))
					return RefactoringCoreMessages.getString("ReorgUtils.13"); //$NON-NLS-1$
				else
					return RefactoringCoreMessages.getString("ReorgUtils.14"); //$NON-NLS-1$
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				if (isSourceFolder(element))
					return RefactoringCoreMessages.getString("ReorgUtils.15"); //$NON-NLS-1$
				if (isClassFolder(element))
					return RefactoringCoreMessages.getString("ReorgUtils.16"); //$NON-NLS-1$
				return RefactoringCoreMessages.getString("ReorgUtils.17"); //$NON-NLS-1$
			case IJavaElement.TYPE:
				return RefactoringCoreMessages.getString("ReorgUtils.18"); //$NON-NLS-1$
			default:
				Assert.isTrue(false);
				return null;
		}
	}

	public static IJavaElement toWorkingCopy(IJavaElement element){
		if (element instanceof ICompilationUnit)
			return JavaModelUtil.toWorkingCopy((ICompilationUnit)element);
		if (element instanceof IMember)
			return JavaModelUtil.toWorkingCopy((IMember)element);
		if (element instanceof IPackageDeclaration)
			return JavaModelUtil.toWorkingCopy((IPackageDeclaration)element);
		if (element instanceof IImportContainer)
			return JavaModelUtil.toWorkingCopy((IImportContainer)element);			
		if (element instanceof IImportDeclaration)
			return JavaModelUtil.toWorkingCopy((IImportDeclaration)element);	
		return element;
	}
	
	public static IJavaElement[] toWorkingCopies(IJavaElement[] javaElements){
		IJavaElement[] result= new IJavaElement[javaElements.length];
		for (int i= 0; i < javaElements.length; i++) {
			result[i]= ReorgUtils.toWorkingCopy(javaElements[i]);
		}
		return result;
	}
	
	public static IResource[] getResources(List elements) {
		List resources= new ArrayList(elements.size());
		for (Iterator iter= elements.iterator(); iter.hasNext();) {
			Object element= iter.next();
			if (element instanceof IResource)
				resources.add(element);
		}
		return (IResource[]) resources.toArray(new IResource[resources.size()]);
	}

	public static IJavaElement[] getJavaElements(List elements) {
		List resources= new ArrayList(elements.size());
		for (Iterator iter= elements.iterator(); iter.hasNext();) {
			Object element= iter.next();
			if (element instanceof IJavaElement)
				resources.add(element);
		}
		return (IJavaElement[]) resources.toArray(new IJavaElement[resources.size()]);
	}
	
	public static boolean isDeletedFromEditor(IJavaElement elem) throws JavaModelException{
		if (! isInsideCompilationUnit(elem))
			return false;
		if (elem instanceof IMember && ((IMember)elem).isBinary())
			return false;
		ICompilationUnit cu= ReorgUtils.getCompilationUnit(elem);
		if (cu == null)
			return false;
		ICompilationUnit wc= WorkingCopyUtil.getWorkingCopyIfExists(cu);
		if (cu.equals(wc))
			return false;
		IJavaElement wcElement= JavaModelUtil.findInCompilationUnit(wc, elem);
		return wcElement == null || ! wcElement.exists();
	}
	
	public static boolean hasSourceAvailable(IMember member) throws JavaModelException{
		return ! member.isBinary() || 
				(member.getSourceRange() != null && ! fgUnknownRange.equals(member.getSourceRange()));
	}
	
	public static IResource[] setMinus(IResource[] setToRemoveFrom, IResource[] elementsToRemove) {
		Set setMinus= new HashSet(setToRemoveFrom.length - setToRemoveFrom.length);
		setMinus.addAll(Arrays.asList(setToRemoveFrom));
		setMinus.removeAll(Arrays.asList(elementsToRemove));
		return (IResource[]) setMinus.toArray(new IResource[setMinus.size()]);		
	}

	public static IJavaElement[] setMinus(IJavaElement[] setToRemoveFrom, IJavaElement[] elementsToRemove) {
		Set setMinus= new HashSet(setToRemoveFrom.length - setToRemoveFrom.length);
		setMinus.addAll(Arrays.asList(setToRemoveFrom));
		setMinus.removeAll(Arrays.asList(elementsToRemove));
		return (IJavaElement[]) setMinus.toArray(new IJavaElement[setMinus.size()]);		
	}
	
	public static IJavaElement[] union(IJavaElement[] set1, IJavaElement[] set2) {
		Set union= new HashSet(set1.length + set2.length);
		union.addAll(Arrays.asList(set1));
		union.addAll(Arrays.asList(set2));
		return (IJavaElement[]) union.toArray(new IJavaElement[union.size()]);
	}	

	public static IResource[] union(IResource[] set1, IResource[] set2) {
		Set union= new HashSet(set1.length + set2.length);
		union.addAll(Arrays.asList(set1));
		union.addAll(Arrays.asList(set2));
		return (IResource[]) union.toArray(new IResource[union.size()]);
	}	

	public static Set union(Set set1, Set set2){
		Set union= new HashSet(set1.size() + set2.size());
		union.addAll(set1);
		union.addAll(set2);
		return union;
	}

	public static IType[] getMainTypes(IJavaElement[] javaElements) throws JavaModelException {
		List result= new ArrayList();
		for (int i= 0; i < javaElements.length; i++) {
			IJavaElement element= javaElements[i];
			if (element instanceof IType && JavaElementUtil.isMainType((IType)element))
				result.add(element);
		}
		return (IType[]) result.toArray(new IType[result.size()]);
	}
	
	public static IFolder[] getFolders(IResource[] resources) {
		Set result= getResourcesOfType(resources, IResource.FOLDER);
		return (IFolder[]) result.toArray(new IFolder[result.size()]);
	}

	public static IFile[] getFiles(IResource[] resources) {
		Set result= getResourcesOfType(resources, IResource.FILE);
		return (IFile[]) result.toArray(new IFile[result.size()]);
	}
		
	//the result can be cast down to the requested type array
	public static Set getResourcesOfType(IResource[] resources, int typeMask){
		Set result= new HashSet(resources.length);
		for (int i= 0; i < resources.length; i++) {
			if (isOfType(resources[i], typeMask))
				result.add(resources[i]);
		}
		return result;
	}
	
	//the result can be cast down to the requested type array
	//type is _not_ a mask	
	public static List getElementsOfType(IJavaElement[] javaElements, int type){
		List result= new ArrayList(javaElements.length);
		for (int i= 0; i < javaElements.length; i++) {
			if (isOfType(javaElements[i], type))
				result.add(javaElements[i]);
		}
		return result;
	}

	public static boolean hasElementsNotOfType(IResource[] resources, int typeMask) {
		for (int i= 0; i < resources.length; i++) {
			IResource resource= resources[i];
			if (resource != null && ! isOfType(resource, typeMask))
				return true;
		}
		return false;
	}

	//type is _not_ a mask	
	public static boolean hasElementsNotOfType(IJavaElement[] javaElements, int type) {
		for (int i= 0; i < javaElements.length; i++) {
			IJavaElement element= javaElements[i];
			if (element != null && ! isOfType(element, type))
				return true;
		}
		return false;
	}
	
	//type is _not_ a mask	
	public static boolean hasElementsOfType(IJavaElement[] javaElements, int type) {
		for (int i= 0; i < javaElements.length; i++) {
			IJavaElement element= javaElements[i];
			if (element != null && isOfType(element, type))
				return true;
		}
		return false;
	}

	public static boolean hasElementsOfType(IJavaElement[] javaElements, int[] types) {
		for (int i= 0; i < types.length; i++) {
			if (hasElementsOfType(javaElements, types[i])) return true;
		}
		return false;
	}

	public static boolean hasElementsOfType(IResource[] resources, int typeMask) {
		for (int i= 0; i < resources.length; i++) {
			IResource resource= resources[i];
			if (resource != null && isOfType(resource, typeMask))
				return true;
		}
		return false;
	}

	private static boolean isOfType(IJavaElement element, int type) {
		return element.getElementType() == type;//this is _not_ a mask
	}
		
	private static boolean isOfType(IResource resource, int type) {
		return isFlagSet(resource.getType(), type);
	}
		
	private static boolean isFlagSet(int flags, int flag){
		return (flags & flag) != 0;
	}

	public static boolean isSourceFolder(IJavaElement javaElement) throws JavaModelException {
		return (javaElement instanceof IPackageFragmentRoot) &&
				((IPackageFragmentRoot)javaElement).getKind() == IPackageFragmentRoot.K_SOURCE;
	}
	
	public static boolean isClassFolder(IJavaElement javaElement) throws JavaModelException {
		return (javaElement instanceof IPackageFragmentRoot) &&
				((IPackageFragmentRoot)javaElement).getKind() == IPackageFragmentRoot.K_BINARY;
	}
	
	public static boolean isPackageFragmentRoot(IJavaProject javaProject) throws JavaModelException{
		return getCorrespondingPackageFragmentRoot(javaProject) != null;
	}
	
	private static boolean isPackageFragmentRootCorrespondingToProject(IPackageFragmentRoot root) throws JavaModelException {
		return root.getResource() instanceof IProject;
	}

	public static IPackageFragmentRoot getCorrespondingPackageFragmentRoot(IJavaProject p) throws JavaModelException {
		IPackageFragmentRoot[] roots= p.getPackageFragmentRoots();
		for (int i= 0; i < roots.length; i++) {
			if (isPackageFragmentRootCorrespondingToProject(roots[i]))
				return roots[i];
		}
		return null;
	}
		
	public static boolean containsLinkedResources(IResource[] resources){
		for (int i= 0; i < resources.length; i++) {
			if (resources[i].isLinked()) return true;
		}
		return false;
	}
	
	public static boolean containsLinkedResources(IJavaElement[] javaElements){
		for (int i= 0; i < javaElements.length; i++) {
			IResource res= getResource(javaElements[i]);
			if (res != null && res.isLinked()) return true;
		}
		return false;
	}

	public static boolean canBeDestinationForLinkedResources(IResource resource) {
		return resource.isAccessible() && resource instanceof IProject;
	}

	public static boolean canBeDestinationForLinkedResources(IJavaElement javaElement) throws JavaModelException {
		if (javaElement instanceof IPackageFragmentRoot){
			return isPackageFragmentRootCorrespondingToProject((IPackageFragmentRoot)javaElement);
		} else if (javaElement instanceof IJavaProject){
			return true;//XXX ???
		} else return false;
	}
	
	public static boolean isParentInWorkspaceOrOnDisk(IPackageFragment pack, IPackageFragmentRoot root){
		if (pack == null)
			return false;		
		IJavaElement packParent= pack.getParent();
		if (packParent == null)
			return false;		
		if (packParent.equals(root))	
			return true;
		IResource packageResource= ResourceUtil.getResource(pack);
		IResource packageRootResource= ResourceUtil.getResource(root);
		return isParentInWorkspaceOrOnDisk(packageResource, packageRootResource);
	}

	public static boolean isParentInWorkspaceOrOnDisk(IPackageFragmentRoot root, IJavaProject javaProject){
		if (root == null)
			return false;		
		IJavaElement rootParent= root.getParent();
		if (rootParent == null)
			return false;		
		if (rootParent.equals(root))	
			return true;
		IResource packageResource= ResourceUtil.getResource(root);
		IResource packageRootResource= ResourceUtil.getResource(javaProject);
		return isParentInWorkspaceOrOnDisk(packageResource, packageRootResource);
	}

	public static boolean isParentInWorkspaceOrOnDisk(ICompilationUnit cu, IPackageFragment dest){
		if (cu == null)
			return false;
		IJavaElement cuParent= cu.getParent();
		if (cuParent == null)
			return false;
		if (cuParent.equals(dest))	
			return true;
		IResource cuResource= ResourceUtil.getResource(cu);
		IResource packageResource= ResourceUtil.getResource(dest);
		return isParentInWorkspaceOrOnDisk(cuResource, packageResource);
	}
	
	public static boolean isParentInWorkspaceOrOnDisk(IResource res, IResource maybeParent){
		if (res == null)
			return false;
		return areEqualInWorkspaceOrOnDisk(res.getParent(), maybeParent);
	}
	
	public static boolean areEqualInWorkspaceOrOnDisk(IResource r1, IResource r2){
		if (r1 == null || r2 == null)
			return false;
		if (r1.equals(r2))
			return true;
		IPath r1Location= r1.getLocation();
		IPath r2Location= r2.getLocation();
		if (r1Location == null || r2Location == null)
			return false;
		return r1Location.equals(r2Location);
	}
	
	public static boolean equalInWorkspaceOrOnDisk(IResource r1, IResource r2){
		if (r1 == null || r2 == null)
			return false;
		if (r1.equals(r2))
			return true;
		IPath r1Location= r1.getLocation();
		IPath r2Location= r2.getLocation();
		if (r1Location == null || r2Location == null)
			return false;
		return r1Location.equals(r2Location);
	}
	
	public static IResource[] getNotNulls(IResource[] resources) {
		Set set= new HashSet(resources.length);
		for (int i= 0; i < resources.length; i++) {
			IResource resource= resources[i];
			if (resource != null)
				set.add(resource);
		}
		return (IResource[]) set.toArray(new IResource[set.size()]);
	}
}
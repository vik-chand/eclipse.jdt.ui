/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.structure;

import org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring;
import org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.Checks;

/**
 * Refactoring to replace type occurrences by a super type where possible.
 * 
 * @since 3.1
 */
public final class UseSuperTypeRefactoring extends ProcessorBasedRefactoring {

	/**
	 * Creates a new use super type refactoring.
	 * 
	 * @param subType the type to replace its occurrences
	 * @return the created refactoring
	 * @throws JavaModelException if the the refactoring could not be tested for availability
	 */
	public static UseSuperTypeRefactoring create(final IType subType, final IType superType) throws JavaModelException {
		Assert.isNotNull(subType);
		Assert.isNotNull(superType);
		Assert.isTrue(subType.exists() && !subType.isAnonymous() && !subType.isAnnotation());
		Assert.isTrue(superType.exists() && !superType.isAnonymous() && !superType.isAnnotation() && !superType.isEnum());
		return new UseSuperTypeRefactoring(new UseSuperTypeProcessor(subType, superType));
	}

	/**
	 * Is this refactoring available for the specified type?
	 * 
	 * @param type the type to test
	 * @return <code>true</code> if this refactoring is available, <code>false</code> otherwise
	 * @throws JavaModelException if the type could not be tested
	 */
	public static boolean isAvailable(final IType type) throws JavaModelException {
		return Checks.isAvailable(type) && !type.isAnnotation() && !type.isAnonymous();
	}

	/** The processor to use */
	private final UseSuperTypeProcessor fProcessor;

	/**
	 * @param processor
	 */
	public UseSuperTypeRefactoring(final UseSuperTypeProcessor processor) {
		super(processor);

		fProcessor= processor;
	}

	/*
	 * @see org.eclipse.ltk.core.refactoring.participants.ProcessorBasedRefactoring#getProcessor()
	 */
	public final RefactoringProcessor getProcessor() {
		return fProcessor;
	}

	/**
	 * Returns the use super type processor.
	 * 
	 * @return the refactoring processor
	 */
	public final UseSuperTypeProcessor getUseSuperTypeProcessor() {
		return (UseSuperTypeProcessor) getProcessor();
	}
}
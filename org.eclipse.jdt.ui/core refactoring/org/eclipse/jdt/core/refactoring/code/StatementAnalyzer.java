/*
 * Licensed Materials - Property of IBM,
 * WebSphere Studio Workbench
 * (c) Copyright IBM Corp 1999, 2000, 2001
 */
package org.eclipse.jdt.core.refactoring.code;

import java.util.ArrayList;
import java.util.HashMap;import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.eclipse.jdt.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.internal.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.ast.*;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope;
import org.eclipse.jdt.internal.compiler.lookup.MethodScope;
import org.eclipse.jdt.internal.compiler.lookup.Scope;
import org.eclipse.jdt.internal.compiler.lookup.TypeIds;import org.eclipse.jdt.internal.compiler.problem.ProblemHandler;import org.eclipse.jdt.internal.compiler.util.CharOperation;
import org.eclipse.jdt.internal.core.refactoring.ASTEndVisitAdapter;
import org.eclipse.jdt.internal.core.refactoring.Assert;
import org.eclipse.jdt.internal.core.refactoring.AstNodeData;import org.eclipse.jdt.internal.core.refactoring.ExtendedBuffer;
import org.eclipse.jdt.internal.core.refactoring.IParentTracker;import org.eclipse.jdt.internal.core.util.HackFinder;

/**
 * Checks whether the source range denoted by <code>start</code> and <code>end</code>
 * selects a set of statements.
 */
/* package */ class StatementAnalyzer extends ASTEndVisitAdapter {
	
	static final int UNDEFINED=   0;
	static final int BEFORE=	1;
	static final int SELECTED=	2;
	static final int AFTER=	      3;
	
	// Parent tracking interface
	private IParentTracker fParentTracker;
	
	// The selection's start and end position
	private ExtendedBuffer fBuffer;
	private Selection fSelection;
	
	// internal state.
	private int fMode;
	private int fLastEnd;

	// Error handling	
	private RefactoringStatus fStatus= new RefactoringStatus();
	private int[] fLineSeparatorPositions;	
	private String fMessage;
	
	private Statement fFirstSelectedStatement;
	private AstNode fParentOfFirstSelectedStatment;
	private Statement fLastSelectedStatement;
	private Statement fLastSelectedStatementWithSameParentAsFirst;
	private boolean fNeedsSemicolon;
	
	private AbstractMethodDeclaration fEnclosingMethod;
	
	private LocalVariableAnalyzer fLocalVariableAnalyzer;
	private LocalTypeAnalyzer fLocalTypeAnalyzer;
	private ExceptionAnalyzer fExceptionAnalyzer;
	
	// Special return statement handling
	private int fAdjustedSelectionEnd= -1;
	private String fPotentialReturnMessage;
	
	// Handling label and branch statements.
	private Stack fImplicitBranchTargets= new Stack();	
	private List fLabeledStatements= new ArrayList(2);
	
	// A hashtable to add additional data to an AstNode
	private AstNodeData fAstNodeData= new AstNodeData();
	
	private static final int BREAK_LENGTH= "break".length();
	private static final int CONTINUE_LENGTH= "continue".length();
	private static final int DO_LENGTH=    "do".length();
	private static final int ELSE_LENGTH=  "else".length();
	private static final int WHILE_LENGTH= "while".length();
	 
	public StatementAnalyzer(ExtendedBuffer buffer, int start, int length, boolean asymetricAssignment) {
		// System.out.println("Start: " + start + " length: " + length);
		fBuffer= buffer;
		Assert.isTrue(fBuffer != null);
		fSelection= new Selection(start, length);
		fLocalVariableAnalyzer= new LocalVariableAnalyzer(this, asymetricAssignment);
		fLocalTypeAnalyzer= new LocalTypeAnalyzer();
		fExceptionAnalyzer= new ExceptionAnalyzer();
	}

	//---- Parent tracking ----------------------------------------------------------
	
	/**
	 * Sets the parent tracker to access the parent of a active
	 * AST node.
	 */
	public void setParentTracker(IParentTracker tracker) {
		fParentTracker= tracker;
	}
	
	private AstNode getParent() {
		return fParentTracker.getParent();
	}
	
	//---- Precondition checking ----------------------------------------------------
	
	/**
	 * Checks if the refactoring can be activated.
	 */
	public void checkActivation(RefactoringStatus status) {
		if (fEnclosingMethod == null || fLastSelectedStatement == null) {
			if (fMessage == null)
				fMessage= "TextSelection doesn't mark a text range that can be extracted";
			status.addFatalError(fMessage);
		}
		status.merge(fStatus);
		if (fFirstSelectedStatement == fLastSelectedStatementWithSameParentAsFirst &&
				fFirstSelectedStatement instanceof ReturnStatement) {
			status.addFatalError("Can not extract single return statement");
		}
		fLocalVariableAnalyzer.checkActivation(status);
		fLocalTypeAnalyzer.checkActivation(status);
	}
	
	/**
	 * Returns the local variable analyzer used by this statement analyzer.
	 */
	public LocalVariableAnalyzer getLocalVariableAnalyzer() {
		return fLocalVariableAnalyzer;
	}
	 
	/**
	 * Returns the method that encloses the text selection. Returns <code>null</code>
	 * is the text selection isn't enclosed by a method or is the text selection doesn't
	 * mark a valid set of statements.
	 * @return the method that encloses the text selection.
	 */
	public AbstractMethodDeclaration getEnclosingMethod() {
		return fEnclosingMethod;
	}
	
	/**
	 * Returns <code>true</code> if the given AST node is selected in the text editor.
	 * Otherwise <code>false</code> is returned.
	 * @return whether or not the given AST node is selected in the editor.
	 */
	public boolean isSelected(AstNode node) {
		return fSelection.start <= node.sourceStart && node.sourceEnd <= fSelection.end;
	}
	
	/**
	 * Returns the ending position of the last selected statement.
	 */
	public int getLastSelectedStatementEnd() {
		return fLastSelectedStatement.sourceEnd;
	}
	
	public String getSignature(String methodName) {
		String modifier= "";
		if ((fEnclosingMethod.modifiers & AstNode.AccStatic) != 0)
			modifier= "static ";
			
		return modifier + fLocalVariableAnalyzer.getCallSignature(methodName)
		       + fExceptionAnalyzer.getThrowSignature();
	}
	
	/**
	 * Returns true if the last selected statement needs a semicolon to have correct
	 * syntax. For example a block or a try / catch / finnally doesn't need a semicolon
	 * at the end.
	 */
	public boolean getNeedsSemicolon() {
		return fNeedsSemicolon;
	}
	
	/**
	 * Returns the new selection end value, if the selection has to be adjusted. Returns
	 * -1 if the current selection is ok.
	 * 
	 * @return the adjusted selection end or -1 if the selection doesn't have to be
	 *  adjusted
	 */
	public int getAdjustedSelectionEnd() {
		return fAdjustedSelectionEnd;
	}
	
	//---- Helper methods -----------------------------------------------------------------------
	
	private void reset() {
		fMode= UNDEFINED;
		fFirstSelectedStatement= null;
		fLastSelectedStatement= null;
		fEnclosingMethod= null;
		fStatus= new RefactoringStatus();
		fMessage= null;
		fNeedsSemicolon= true;
	}
	
	private void invalidSelection(String message) {
		reset();
		fMessage= message;
		fLastEnd= Integer.MAX_VALUE;
	}
	
	private boolean visitStatement(Statement statement, BlockScope scope) {
		boolean result= true;
		switch(fMode) {
			case UNDEFINED:
				return false;			
			case BEFORE:
				if (fLastEnd < fSelection.start && fSelection.covers(statement)) {
					startFound(statement);
				}
				break;
			case SELECTED:
				if (fSelection.endsIn(statement)) { // Selection ends in the middle of a statement
					invalidSelection("TextSelection ends in the middle of a statement");
					return false;
				} else if (statement.sourceEnd > fSelection.end) {
					fMode= AFTER;
				} else {
					trackLastSelectedStatement(statement);
					fNeedsSemicolon= true;
				}
				break;
			case AFTER:
				break;
		}
		trackLastEnd(statement);
		return result;
	}
	
	private void startFound(Statement statement) {
		fMode= SELECTED;
		fFirstSelectedStatement= statement;
		fParentOfFirstSelectedStatment= getParent();
		fLastSelectedStatement= statement;
		fLastSelectedStatementWithSameParentAsFirst= statement;
	}
	
	private void trackLastSelectedStatement(Statement statement) {
		fLastSelectedStatement= statement;
		if (fParentOfFirstSelectedStatment == getParent())
			fLastSelectedStatementWithSameParentAsFirst= statement;
	}
	
	private void trackLastEnd(Statement statement) {
		if (statement.sourceEnd > fLastEnd)
			fLastEnd= statement.sourceEnd;
	}
	
	private void trackLastEnd(int end) {
		if (end > fLastEnd)
			fLastEnd= end;
	}
	
	private boolean visitAssignment(Assignment assignment, BlockScope scope, boolean compound) {
		if (visitStatement(assignment, scope)) {
			fLocalVariableAnalyzer.visitLhsOfAssignment(assignment.lhs, scope, fMode, compound);
			assignment.expression.traverse(this, scope);
			
		}
		// Since we visited the assigment node by ourselves we have to return false.	
		return false;
	}
	
	private boolean visitAbstractMethodDeclaration(AbstractMethodDeclaration node, Scope scope) {
		boolean result= fSelection.enclosedBy(node);
		if (result) {
			fExceptionAnalyzer.visitAbstractMethodDeclaration(node, scope);
			reset();
			fEnclosingMethod= node;
			fMode= BEFORE;
		}
		return result;	
	}
	
	private void endVisitAbstractMethodDeclaration(AbstractMethodDeclaration node, Scope scope) {
		if (node == fEnclosingMethod) {
			checkLastSelectedStatement();
		}
	}
	
	private boolean visitLocalTypeDeclaration(TypeDeclaration declaration, BlockScope scope) {
		boolean result= visitStatement(declaration, scope);
		fLocalTypeAnalyzer.visitLocalTypeDeclaration(declaration, scope, fMode);
		return result;
	}
	
	private boolean visitTypeReference(TypeReference reference, BlockScope scope) {
		fLocalTypeAnalyzer.visitTypeReference(reference, scope, fMode);
		return false;
	}
	
	private boolean visitImplicitBranchTarget(Statement statement, BlockScope scope) {
		fImplicitBranchTargets.push(statement);
		return visitStatement(statement, scope);
	}
	
	private void endVisitImplicitBranchTarget(Statement statement, BlockScope scope) {
		fImplicitBranchTargets.pop();
	}
	
	private boolean visitBranchStatement(BranchStatement statement, BlockScope scope, String name) {
		boolean result= visitStatement(statement, scope);
		Statement target= findTarget(statement);
		String label= "label"; //* new String(statement.label);
		if (target != null) {
			if (isSelected(target)) {
				if (fMode != SELECTED)
					fStatus.addError("Selected block contains a " + name + " target but not all corresponding " + name + " statements are selected");
			} else {
				if (fMode == SELECTED)
					fStatus.addError("Selected block contains a " + name + " statement but the corresponding " + name + " target isn't selected");
			}
		} else {
			fStatus.addFatalError("Can not find break target");
		}
		return result;
	}
	
	private Statement findTarget(BranchStatement statement) {
		if (statement.label == null)
			return (Statement)fImplicitBranchTargets.peek();
		char[] label= statement.label;
		for (Iterator iter= fLabeledStatements.iterator(); iter.hasNext(); ) {
			LabeledStatement ls= (LabeledStatement)iter.next();
			if (CharOperation.equals(label, ls.label))
				return ls;
		}
		return null;
	}
	
	private void checkLastSelectedStatement() {
		if (fLastSelectedStatementWithSameParentAsFirst instanceof ReturnStatement) {
			ReturnStatement statement= (ReturnStatement)fLastSelectedStatementWithSameParentAsFirst;
			if (statement.expression == null) {
				fPotentialReturnMessage= null;
				fLocalVariableAnalyzer.setExtractedReturnStatement(statement);
			} else {
				invalidSelection("Can only extract void return statement");
			}
		} else {
			handlePotentialReturnMessage();
			fAdjustedSelectionEnd= -1;
		}
	}
	
	private void handlePotentialReturnMessage() {
		if (fPotentialReturnMessage != null) {
			fStatus.addFatalError(fPotentialReturnMessage);
			fPotentialReturnMessage= null;
		}
	}	
	
	//--- node management ---------------------------------------------------------
	
	private boolean isPartOfNodeSelected(AstNode node) {
		return fMode == BEFORE && fSelection.end < fBuffer.indexOfStatementCharacter(node.sourceEnd + 1);
	}
	
	//--- Expression / Condition handling -----------------------------------------

	public boolean visitBinaryExpression(BinaryExpression binaryExpression, BlockScope scope, int returnType) {
		int currentScanPosition= fLastEnd;
		if (!visitStatement(binaryExpression, scope))
			return false;
		if (fMode != SELECTED)
			return true;
			
		if (isTopMostNodeInSelection(currentScanPosition, binaryExpression)) {
			if (returnType == TypeIds.T_undefined)
				returnType= binaryExpression.bits & binaryExpression.ReturnTypeIDMASK;
			if (returnType == TypeIds.T_undefined) {
				invalidSelection("Can not determine return type of the expression to be extracted");
				return false;
			}
			fLocalVariableAnalyzer.setExpressionReturnType(TypeReference.baseTypeReference(returnType, 0));
		}
		return true;	
	}

	private boolean isConditionSelected(AstNode node, Expression condition, int scanStart) {
		if (node == null || condition == null || scanStart < 0)
			return false;
		int conditionStart= fBuffer.indexOf('(', scanStart) + 1;
		int conditionEnd= fBuffer.indexOf(')', condition.sourceEnd + 1) - 1;
		if (fSelection.coveredBy(conditionStart, conditionEnd)) {
			fAstNodeData.put(node, new Boolean(true));
			fLastEnd= conditionStart - 1;
			return true;
		}
		return false;
	}
		
	private boolean isTopMostNodeInSelection(int rangeStart, AstNode node) {
		int nextStart= fBuffer.indexOfStatementCharacter(node.sourceEnd + 1);
		return fSelection.coveredBy(rangeStart, nextStart - 1) && fSelection.covers(node);
	}

	private void endVisitConditionBlock(AstNode node, String statementName) {
		Boolean inCondition= (Boolean)fAstNodeData.get(node);
		if (inCondition != null && inCondition.booleanValue() && fLocalVariableAnalyzer.getExpressionReturnType() == null) {
			invalidSelection("Can not extract the selected statement(s) from the condition part of " + statementName + " statement");
		}	
	}
		
	//---- Problem management -----------------------------------------------------
	
	public void acceptProblem(IProblem problem) {
		if (fMode != UNDEFINED) {
			reset();
			fLastEnd= Integer.MAX_VALUE;
			fStatus.addFatalError("Compilation unit has compile error at line " + problem.getSourceLineNumber() + ": " + problem.getMessage());
		}
	}
	
	private int getLineNumber(AstNode node){
		Assert.isNotNull(fLineSeparatorPositions);
		return ProblemHandler.searchLineNumber(fLineSeparatorPositions, node.sourceStart);
	}
	
	//---- Compilation Unit -------------------------------------------------------
	
	public boolean visit(CompilationUnitDeclaration compilationUnitDeclaration, CompilationUnitScope scope) {
		fLineSeparatorPositions= compilationUnitDeclaration.compilationResult.lineSeparatorPositions;
		return fSelection.enclosedBy(compilationUnitDeclaration);
	}
	
	public boolean visit(ImportReference importRef, CompilationUnitScope scope) {
		return false;
	}
	
	public boolean visit(TypeDeclaration typeDeclaration, CompilationUnitScope scope) {
		return fSelection.enclosedBy(typeDeclaration);
	}
	
	//---- Type -------------------------------------------------------------------
	
	public boolean visit(Clinit clinit, ClassScope scope) {
		return fSelection.enclosedBy(clinit);
	}
	
	public boolean visit(TypeDeclaration typeDeclaration, ClassScope scope) {
		return fSelection.enclosedBy(typeDeclaration);
	}
	
	public boolean visit(MemberTypeDeclaration memberTypeDeclaration, ClassScope scope) {
		return fSelection.enclosedBy(memberTypeDeclaration);
	}
	
	public boolean visit(FieldDeclaration fieldDeclaration, MethodScope scope) {
		return false;
	}
	
	public boolean visit(Initializer initializer, MethodScope scope) {
		return false;
	}
	
	public boolean visit(ConstructorDeclaration constructorDeclaration, ClassScope scope) {
		return visitAbstractMethodDeclaration(constructorDeclaration, scope);
	}
	
	public void endVisit(ConstructorDeclaration constructorDeclaration, ClassScope scope) {
		endVisitAbstractMethodDeclaration(constructorDeclaration, scope);
	}
	
	public boolean visit(MethodDeclaration methodDeclaration, ClassScope scope) {
		return visitAbstractMethodDeclaration(methodDeclaration, scope);
	}
	
	public void endVisit(MethodDeclaration methodDeclaration, ClassScope scope) {
		endVisitAbstractMethodDeclaration(methodDeclaration, scope);
	}
	
	public boolean visit(SingleTypeReference singleTypeReference, ClassScope scope) {
		return false;
	}
	
	public boolean visit(QualifiedTypeReference qualifiedTypeReference, ClassScope scope) {
		return false;
	}
	
	public boolean visit(ArrayTypeReference arrayTypeReference, ClassScope scope) {
		return false;
	}
	
	public boolean visit(ArrayQualifiedTypeReference arrayQualifiedTypeReference, ClassScope scope) {
		return false;
	}
	
	//---- Methods ----------------------------------------------------------------
	
	public boolean visit(LocalTypeDeclaration localTypeDeclaration, MethodScope scope) {
		return visitLocalTypeDeclaration(localTypeDeclaration, scope);
	}
	
	public boolean visit(Argument argument, BlockScope scope) {
		return false;
	}
	
	//---- Methods / Block --------------------------------------------------------
	
	public boolean visit(TypeDeclaration typeDeclaration, BlockScope scope) {
		return visitLocalTypeDeclaration(typeDeclaration, scope);
	}
	
	public boolean visit(AnonymousLocalTypeDeclaration anonymousTypeDeclaration, BlockScope scope) {
		return visitStatement(anonymousTypeDeclaration, scope);
	}
	
	public boolean visit(LocalDeclaration localDeclaration, BlockScope scope) {
		return visitStatement(localDeclaration, scope);
	}

	public boolean visit(FieldReference fieldReference, BlockScope scope) {
		return false;
	}

	public boolean visit(ArrayReference arrayReference, BlockScope scope) {
		return false;
	}

	public boolean visit(ArrayTypeReference arrayTypeReference, BlockScope scope) {
		return visitTypeReference(arrayTypeReference, scope);
	}

	public boolean visit(ArrayQualifiedTypeReference arrayQualifiedTypeReference, BlockScope scope) {
		return visitTypeReference(arrayQualifiedTypeReference, scope);
	}

	public boolean visit(SingleTypeReference singleTypeReference, BlockScope scope) {
		return visitTypeReference(singleTypeReference, scope);
	}

	public boolean visit(QualifiedTypeReference qualifiedTypeReference, BlockScope scope) {
		return visitTypeReference(qualifiedTypeReference, scope);
	}

	public boolean visit(QualifiedNameReference qualifiedNameReference, BlockScope scope) {
		boolean result= visitStatement(qualifiedNameReference, scope);
		if (result) {
			fLocalVariableAnalyzer.visit(qualifiedNameReference, scope, fMode);
		}
		return result;
	}

	public boolean visit(QualifiedSuperReference qualifiedSuperReference, BlockScope scope) {
		return false;
	}

	public boolean visit(QualifiedThisReference qualifiedThisReference, BlockScope scope) {
		return false;
	}

	public boolean visit(SingleNameReference singleNameReference, BlockScope scope) {
		boolean result= visitStatement(singleNameReference, scope);
		if (result) {
			fLocalVariableAnalyzer.visit(singleNameReference, scope, fMode);
			fLocalTypeAnalyzer.visit(singleNameReference, scope, fMode);
		}	
		return result;
	}

	public boolean visit(SuperReference superReference, BlockScope scope) {
		return false;
	}

	public boolean visit(ThisReference thisReference, BlockScope scope) {
		return false;
	}

	//---- Statements -------------------------------------------------------------
	
	public boolean visit(AllocationExpression allocationExpression, BlockScope scope) {
		return visitStatement(allocationExpression, scope);
	}
	public boolean visit(AND_AND_Expression and_and_Expression, BlockScope scope) {
		return visitBinaryExpression(and_and_Expression, scope, TypeIds.T_boolean);
	}
	
	public boolean visit(ArrayAllocationExpression arrayAllocationExpression, BlockScope scope) {
		return visitStatement(arrayAllocationExpression, scope);
	}
	
	public boolean visit(ArrayInitializer arrayInitializer, BlockScope scope) {
		return visitStatement(arrayInitializer, scope);
	}
	
	public boolean visit(Assignment assignment, BlockScope scope) {
		return visitAssignment(assignment, scope, false);
	}

	public boolean visit(BinaryExpression binaryExpression, BlockScope scope) {
		return visitBinaryExpression(binaryExpression, scope, TypeIds.T_undefined);
	}

	public boolean visit(Block block, BlockScope scope) {
		boolean result= visitStatement(block, scope);
		
		if (fSelection.intersects(block)) {
			reset();
			fLastEnd= Integer.MAX_VALUE;
		} else {
			fLastEnd= block.sourceStart;
		}
		
		return result;
	}

	public void endVisit(Block block, BlockScope scope) {
		trackLastEnd(block);
		if (fSelection.covers(block))
			fNeedsSemicolon= false;
	}

	public boolean visit(Break breakStatement, BlockScope scope) {
		HackFinder.fixMeSoon("1GCU7OH: ITPJCORE:WIN2000 - Break.sourceEnd contains tailing comments");
		if (breakStatement.label == null) {
			breakStatement.sourceEnd= breakStatement.sourceStart + BREAK_LENGTH - 1;
		}
		return visitBranchStatement(breakStatement, scope, "break");
	}

	public boolean visit(Case caseStatement, BlockScope scope) {
		return visitStatement(caseStatement, scope);
	}

	public boolean visit(CastExpression castExpression, BlockScope scope) {
		return visitStatement(castExpression, scope);
	}

	public boolean visit(CharLiteral charLiteral, BlockScope scope) {
		return visitStatement(charLiteral, scope);
	}

	public boolean visit(ClassLiteralAccess classLiteral, BlockScope scope) {
		return visitStatement(classLiteral, scope);
	}

	public boolean visit(CompoundAssignment compoundAssignment, BlockScope scope) {
		return visitAssignment(compoundAssignment, scope, true);
	}

	public boolean visit(ConditionalExpression conditionalExpression, BlockScope scope) {
		return visitStatement(conditionalExpression, scope);
	}

	public boolean visit(Continue continueStatement, BlockScope scope) {
		HackFinder.fixMeSoon("1GCU7OH: ITPJCORE:WIN2000 - Break.sourceEnd contains tailing comments");
		if (continueStatement.label == null) {
			continueStatement.sourceEnd= continueStatement.sourceStart + CONTINUE_LENGTH - 1;
		}
		return visitBranchStatement(continueStatement, scope, "continue");
	}

	public boolean visit(DefaultCase defaultCaseStatement, BlockScope scope) {
		return visitStatement(defaultCaseStatement, scope);
	}

	public boolean visit(DoStatement doStatement, BlockScope scope) {
		if (!visitImplicitBranchTarget(doStatement, scope))
			return false;
		
		if (isPartOfNodeSelected(doStatement)) {
			
			// Check if condition is selected
			if (isConditionSelected(doStatement, doStatement.condition, doStatement.action.sourceEnd))
				return true;
				
			// skip the string "do"
			int actionStart= doStatement.sourceStart + DO_LENGTH;		
			int actionEnd= fBuffer.indexOfStatementCharacter(doStatement.action.sourceEnd + 1) - 1;
			
			// Check if action part is selected.
			if (fSelection.coveredBy(actionStart + 1, actionEnd)) {
				fLastEnd= actionStart;
				return true;
			}
				
			if (fSelection.start == actionStart) {
				invalidSelection("Selection may not start right after the do keyword");
				return false;
			}
				
			invalidSelection("Selection must either cover whole do-while statement or parts of the action block");
			return false;
		}
		return true;
	}
	
	public void endVisit(DoStatement doStatement, BlockScope scope) {
		endVisitImplicitBranchTarget(doStatement, scope);
		endVisitConditionBlock(doStatement, "a do-while");
		fLastEnd= doStatement.sourceEnd;
	}

	public boolean visit(DoubleLiteral doubleLiteral, BlockScope scope) {
		return visitStatement(doubleLiteral, scope);
	}

	public boolean visit(EqualExpression equalExpression, BlockScope scope) {
		return visitBinaryExpression(equalExpression, scope, TypeIds.T_boolean);
	}

	public boolean visit(ExplicitConstructorCall explicitConstructor, BlockScope scope) {
		return visitStatement(explicitConstructor, scope);
	}

	public boolean visit(ExtendedStringLiteral extendedStringLiteral, BlockScope scope) {
		return visitStatement(extendedStringLiteral, scope);
	}

	public boolean visit(FalseLiteral falseLiteral, BlockScope scope) {
		return visitStatement(falseLiteral, scope);
	}

	public boolean visit(FloatLiteral floatLiteral, BlockScope scope) {
		return visitStatement(floatLiteral, scope);
	}

	public boolean visit(ForStatement forStatement, BlockScope scope) {
		boolean result= visitImplicitBranchTarget(forStatement, scope);
		if (!result)
			return false;
			
		// forStatement.sourceEnd includes the statement's action. Since the
		// selection can be the statements body adjust last end if so.
		if (isPartOfNodeSelected(forStatement)) {
			if (isConditionSelected(forStatement))
				return true;
				
			int start= forStatement.sourceStart;
			if (forStatement.increments != null) {
				start= forStatement.increments[forStatement.increments.length - 1].sourceEnd;
			} else if (forStatement.condition != null) {
				start= forStatement.condition.sourceEnd;
			} else if (forStatement.initializations != null) {
				start= forStatement.initializations[forStatement.initializations.length - 1].sourceEnd;
			}
			fLastEnd= fBuffer.indexOf(')', start + 1);
		}
		return result;
	}
	
	private boolean isConditionSelected(ForStatement forStatement) {
		if (forStatement.condition == null)
			return false;
			
		int start= forStatement.sourceStart;
		if (forStatement.initializations != null) {
			start= forStatement.initializations[forStatement.initializations.length - 1].sourceEnd;
		}
		int conditionStart= fBuffer.indexOf(';', start) + 1;
		int conditionEnd= fBuffer.indexOf(';', forStatement.condition.sourceEnd) - 1;
		if (fSelection.coveredBy(conditionStart, conditionEnd)) {
			fAstNodeData.put(forStatement, new Boolean(true));
			fLastEnd= conditionStart - 1;
			return true;
		}	
		return false;
	}

	public void endVisit(ForStatement forStatement, BlockScope scope) {
		endVisitImplicitBranchTarget(forStatement, scope);
		endVisitConditionBlock(forStatement, "a for");
	}
	
	public boolean visit(IfStatement ifStatement, BlockScope scope) {
		if (!visitStatement(ifStatement, scope))
			return false;
			
		int nextStart= fBuffer.indexOfStatementCharacter(ifStatement.sourceEnd + 1);
		if (fMode == BEFORE && fSelection.end < nextStart) {
			if (isConditionSelected(ifStatement, ifStatement.condition, ifStatement.sourceStart))
				return true;

			if ((fLastEnd= getIfElseBodyStart(ifStatement, nextStart) - 1) >= 0)
				return true;
				
			invalidSelection("Selection must either cover whole if-then-else statement or parts of then or else block");
			return false;
		}
		return true;
	}
	
	public void endVisit(IfStatement ifStatement, BlockScope scope) {
		endVisitConditionBlock(ifStatement, "an if-then-else");
	}

	private int getIfElseBodyStart(IfStatement ifStatement, int nextStart) {
		int sourceStart;
		int sourceEnd;
		if (ifStatement.thenStatement != null) {
			sourceStart= fBuffer.indexOf(')', ifStatement.condition.sourceEnd + 1) + 1;
			if (ifStatement.elseStatement != null) {
				sourceEnd= ifStatement.elseStatement.sourceStart - 1;
			} else {
				sourceEnd= nextStart - 1;
			}
			if (fSelection.coveredBy(sourceStart, sourceEnd))
				return sourceStart;
		}
		if (ifStatement.elseStatement != null) {
			sourceStart= fBuffer.indexOfStatementCharacter(ifStatement.thenStatement.sourceEnd + 1) + ELSE_LENGTH;
			sourceEnd= nextStart - 1;
			if (fSelection.coveredBy(sourceStart, sourceEnd))
				return sourceStart;
		}
		return -1;
	}
	
	public boolean visit(InstanceOfExpression instanceOfExpression, BlockScope scope) {
		return visitStatement(instanceOfExpression, scope);
	}

	public boolean visit(IntLiteral intLiteral, BlockScope scope) {
		return visitStatement(intLiteral, scope);
	}

	public boolean visit(LabeledStatement labeledStatement, BlockScope scope) {
		fLabeledStatements.add(labeledStatement);
		return visitStatement(labeledStatement, scope);
	}

	public boolean visit(LongLiteral longLiteral, BlockScope scope) {
		return visitStatement(longLiteral, scope);
	}

	public boolean visit(MessageSend messageSend, BlockScope scope) {
		boolean result= visitStatement(messageSend, scope);
		fExceptionAnalyzer.visitMessageSend(messageSend, scope, fMode);
		return result;
	}

	public boolean visit(NullLiteral nullLiteral, BlockScope scope) {
		return visitStatement(nullLiteral, scope);
	}

	public boolean visit(OR_OR_Expression or_or_Expression, BlockScope scope) {
		return visitBinaryExpression(or_or_Expression, scope, TypeIds.T_boolean);
	}

	public boolean visit(PostfixExpression postfixExpression, BlockScope scope) {
		boolean result= visitStatement(postfixExpression, scope);
		fLocalVariableAnalyzer.visitPostfixPrefixExpression(postfixExpression, scope, fMode);
		return result;
	}

	public boolean visit(PrefixExpression prefixExpression, BlockScope scope) {
		boolean result= visitStatement(prefixExpression, scope);
		fLocalVariableAnalyzer.visitPostfixPrefixExpression(prefixExpression, scope, fMode);
		return result;
	}

	public boolean visit(QualifiedAllocationExpression qualifiedAllocationExpression, BlockScope scope) {
		return visitStatement(qualifiedAllocationExpression, scope);
	}

	public boolean visit(ReturnStatement returnStatement, BlockScope scope) {
		fAdjustedSelectionEnd= fBuffer.indexOfLastCharacterBeforeLineBreak(fLastEnd);
		boolean result= visitStatement(returnStatement, scope);
		if (fMode == StatementAnalyzer.SELECTED) {
			if (fPotentialReturnMessage != null) {
				fStatus.addFatalError(fPotentialReturnMessage);
				fPotentialReturnMessage= null;
			}
			String message= "Selected block contains a return statement at line " + getLineNumber(returnStatement);
			if (fParentOfFirstSelectedStatment != getParent()) {
				fStatus.addFatalError(message);
			} else {
				fPotentialReturnMessage= message;
			}
		}
		return result;
	}

	public boolean visit(StringLiteral stringLiteral, BlockScope scope) {
		return visitStatement(stringLiteral, scope);
	}

	public boolean visit(SwitchStatement switchStatement, BlockScope scope) {
		// Include "}" into switch statement
		switchStatement.sourceEnd++;
		if (!visitImplicitBranchTarget(switchStatement, scope))
			return false;
		
		if (isPartOfNodeSelected(switchStatement)) {
			int lastEnd= getCaseBodyStart(switchStatement) - 1;
			if (lastEnd < 0) {
				invalidSelection("Selection must either cover whole switch statement or parts of a single case block");
				return false;
			} else {
				fLastEnd= lastEnd;
			}
		}
		return true;
	}

	public void endVisit(SwitchStatement switchStatement, BlockScope scope) {
		endVisitImplicitBranchTarget(switchStatement, scope);
	}

	private int getCaseBodyStart(SwitchStatement switchStatement) {
		if (switchStatement.statements == null)
			return -1;
		List cases= getCases(switchStatement);
		for (Iterator iter= cases.iterator(); iter.hasNext(); ) {
			Statement kase= (Statement)iter.next();
			Statement last= (Statement)iter.next();
			int sourceStart= fBuffer.indexOf(':', kase.sourceEnd + 1) + 1;
			int sourceEnd= fBuffer.indexOfStatementCharacter(last.sourceEnd + 1) - 1;
			if (fSelection.coveredBy(sourceStart, sourceEnd))
				return sourceStart;
		}
		return -1;	
	}
	
	private List getCases(SwitchStatement switchStatement) {
		List result= new ArrayList();
		Statement[] statements= switchStatement.statements;
		if (statements == null)
			return result;
		for (int i= 0; i < statements.length; i++) {
			Statement statement= statements[i];
			if (statement instanceof Case || statement instanceof DefaultCase) {
				if (i > 0)
					result.add(statements[i - 1]);
				result.add(statement);	
			}
		}
		result.add(statements[statements.length - 1]);
		return result;
	}

	public boolean visit(SynchronizedStatement synchronizedStatement, BlockScope scope) {
		HackFinder.fixMeSoon("1GE2LO2: ITPJCORE:WIN2000 - SourceStart and SourceEnd of synchronized statement");
		if (!visitStatement(synchronizedStatement, scope))
			return false;
		
		int nextStart= fBuffer.indexOfStatementCharacter(synchronizedStatement.sourceEnd + 1);
		if (fMode == BEFORE && fSelection.end < nextStart) {
			if (fSelection.intersects(synchronizedStatement)) {
				invalidSelection("Seleciton must either cover whole synchronized statement or parts of the synchronized block");
				return false;
			} else {
				if (fSelection.enclosedBy(synchronizedStatement.block)) {
					fLastEnd= synchronizedStatement.block.sourceStart;
				}
			}
		}
		return true;
	}

	public boolean visit(ThrowStatement throwStatement, BlockScope scope) {
		return visitStatement(throwStatement, scope);
	}

	public boolean visit(TrueLiteral trueLiteral, BlockScope scope) {
		return visitStatement(trueLiteral, scope);
	}

	public boolean visit(TryStatement tryStatement, BlockScope scope) {
		// Include "}" into sourceEnd;
		tryStatement.sourceEnd++;
		
		if (!visitStatement(tryStatement, scope))
			return false;
			
		fExceptionAnalyzer.visitTryStatement(tryStatement, scope, fMode);
		
		if (isPartOfNodeSelected(tryStatement)) {
			if (fSelection.intersects(tryStatement)) {
				invalidSelection("Selection must either cover whole try statement or parts of try, catch, or finally block");
				return false;
			} else {
				int lastEnd;		
				if (fSelection.covers(tryStatement)) {
					lastEnd= tryStatement.sourceEnd;
				} else if (fSelection.enclosedBy(tryStatement.tryBlock)) {
					lastEnd= tryStatement.tryBlock.sourceStart;
				} else if (tryStatement.catchBlocks != null && (lastEnd= getCatchBodyStart(tryStatement.catchBlocks)) >= 0) {
					// do nothing.
				} else if (tryStatement.finallyBlock != null && fSelection.enclosedBy(tryStatement.finallyBlock)) {
					lastEnd=tryStatement.finallyBlock.sourceStart;
				} else {
					lastEnd= tryStatement.sourceEnd;
				}
				fLastEnd= lastEnd;
			}
		}
		
		return true;
	}

	public void endVisit(TryStatement tryStatement, BlockScope scope) {
		if (tryStatement.catchArguments != null)
			fExceptionAnalyzer.visitCatchArguments(tryStatement.catchArguments, scope, fMode);
		fExceptionAnalyzer.visitEndTryStatement(tryStatement, scope, fMode);
		if (fSelection.covers(tryStatement))
			fNeedsSemicolon= false;
	}
	
	private int getCatchBodyStart(Block[] catchBlocks) {
		for (int i= 0; i < catchBlocks.length; i++) {
			Block catchBlock= catchBlocks[i];
			if (fSelection.enclosedBy(catchBlock))
				return catchBlock.sourceStart;
		}
		return -1;
	}
	
	public boolean visit(UnaryExpression unaryExpression, BlockScope scope) {
		return visitStatement(unaryExpression, scope);
	}

	public boolean visit(WhileStatement whileStatement, BlockScope scope) {
		if (!visitImplicitBranchTarget(whileStatement, scope))
			return false;
			
		if (isPartOfNodeSelected(whileStatement)) {
			if (isConditionSelected(whileStatement, whileStatement.condition, whileStatement.sourceStart + WHILE_LENGTH))
				return true;
				
			fLastEnd= fBuffer.indexOf(')', whileStatement.condition.sourceEnd);
		}
		return true;
	}

	public void endVisit(WhileStatement whileStatement, BlockScope scope) {
		endVisitImplicitBranchTarget(whileStatement, scope);
		endVisitConditionBlock(whileStatement, "a while");
	}
}
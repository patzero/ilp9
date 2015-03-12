/* *****************************************************************
 * ILP9 - Implantation d'un langage de programmation.
 * by Christian.Queinnec@paracamplus.com
 * See http://mooc.paracamplus.com/ilp9
 * GPL version 3
 ***************************************************************** */
package com.paracamplus.ilp9.interfaces;

public interface IASTternaryOperation extends IASToperation {
	IASTexpression getFirstOperand();
	IASTexpression getSecondOperand();
	IASTexpression getThirdOperand();
}

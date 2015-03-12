/* *****************************************************************
 * ILP9 - Implantation d'un langage de programmation.
 * by Christian.Queinnec@paracamplus.com
 * See http://mooc.paracamplus.com/ilp9
 * GPL version 3
 ***************************************************************** */
package com.paracamplus.ilp9.ast;

import com.paracamplus.ilp9.interfaces.IASTbinaryOperation;
import com.paracamplus.ilp9.interfaces.IASTexpression;
import com.paracamplus.ilp9.interfaces.IASToperator;
import com.paracamplus.ilp9.interfaces.IASTternaryOperation;
import com.paracamplus.ilp9.interfaces.IASTvisitor;

public class ASTternaryOperation extends ASTexpression implements IASTternaryOperation {
	

    private final IASToperator operator;
    private final IASTexpression firstOperand;
    private final IASTexpression secondOperand;
    private final IASTexpression thirdOperand;
    
    public ASTternaryOperation (IASToperator operator,IASTexpression firstOperand, IASTexpression secondOperand, IASTexpression thirdOperand ) {
        this.operator = operator;
        this.firstOperand = firstOperand;
        this.secondOperand = secondOperand;
        this.thirdOperand = thirdOperand;
    }
    
    public IASToperator getOperator() {
        return operator;
    }

    public IASTexpression[] getOperands() {
        return new IASTexpression[]{ firstOperand, secondOperand, thirdOperand };
    }

    public IASTexpression getFirstOperand() {
        return firstOperand;
    }

    public IASTexpression getSecondOperand() {
        return secondOperand;
    }
    
    public IASTexpression getThirdOperand() {
        return thirdOperand;
    }
    
    public <Result, Data, Anomaly extends Throwable> 
    Result accept(IASTvisitor<Result, Data, Anomaly> visitor, Data data)
            throws Anomaly {
        return visitor.visit(this, data);
    }

}

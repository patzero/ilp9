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
    private final IASTexpression condition;
    private final IASTexpression firstResult;
    private final IASTexpression secondResult;
    
    public ASTternaryOperation (IASToperator operator,IASTexpression condition, IASTexpression firstResult, IASTexpression secondResult ) {
        this.operator = operator;
        this.condition = condition;
        this.firstResult = firstResult;
        this.secondResult = secondResult;
    }
    
    public IASToperator getOperator() {
        return operator;
    }

    public IASTexpression[] getOperands() {
        return new IASTexpression[]{ condition, firstResult, secondResult };
    }

    public IASTexpression getCondition() {
        return condition;
    }

    public IASTexpression getFirstResult() {
        return firstResult;
    }
    
    public IASTexpression getSecondResult() {
        return secondResult;
    }
    
    public <Result, Data, Anomaly extends Throwable> 
    Result accept(IASTvisitor<Result, Data, Anomaly> visitor, Data data)
            throws Anomaly {
        return visitor.visit(this, data);
    }

}

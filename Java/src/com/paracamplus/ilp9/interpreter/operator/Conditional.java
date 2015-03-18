/* *****************************************************************
 * ILP9 - Implantation d'un langage de programmation.
 * by Christian.Queinnec@paracamplus.com
 * See http://mooc.paracamplus.com/ilp9
 * GPL version 3
 ***************************************************************** */
package com.paracamplus.ilp9.interpreter.operator;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.paracamplus.ilp9.interpreter.interfaces.EvaluationException;

public class Conditional extends TernaryOperator {
    
    public Conditional () {
        super("?:");
    }

	public Object apply(Object condition, Object firstResult, Object secondResult) throws EvaluationException {
		Object result = null;
		if(condition instanceof Boolean) {
			//result = (boolean)firstOperand ? secondOperand : thirdOperator;		
			if ((boolean) condition) {
				result = firstResult;
			} else {
				result = secondResult;
			}
		}
		else {
			//System.out.println(firstOperand + " is not a condition");
			 String msg = "Non conditional argument1";
	         throw new EvaluationException(msg);
		}
	
		return result;
	}

}

package com.paracamplus.ilp9.interpreter;

import java.util.HashMap;
import java.util.Map;

import com.paracamplus.ilp9.interfaces.IASToperator;
import com.paracamplus.ilp9.interpreter.interfaces.EvaluationException;
import com.paracamplus.ilp9.interpreter.interfaces.IOperator;
import com.paracamplus.ilp9.interpreter.interfaces.IOperatorEnvironment;

public class OperatorEnvironment implements IOperatorEnvironment {

    public OperatorEnvironment () {
        this.unaryOperatorEnvironment = new HashMap<>();
        this.binaryOperatorEnvironment = new HashMap<>();
    }
    private final Map<String, IOperator> unaryOperatorEnvironment;
    private final Map<String, IOperator> binaryOperatorEnvironment;
    
    public IOperator getUnaryOperator(IASToperator operator) 
            throws EvaluationException {
        IOperator meaning = unaryOperatorEnvironment.get(operator.getName());
        if ( meaning != null ) {
            return meaning;
        } else {
            String msg = "No such operator " + operator.getName();
            throw new EvaluationException(msg);
        }
    }
    
    public IOperator getBinaryOperator(IASToperator operator) 
            throws EvaluationException {
        IOperator meaning = binaryOperatorEnvironment.get(operator.getName());
        if ( meaning != null ) {
            return meaning;
        } else {
            String msg = "No such operator " + operator.getName();
            throw new EvaluationException(msg);
        }
    }

    public void addOperator(IOperator operator) throws EvaluationException {
        switch (operator.getArity()) {
        case 1: {
            unaryOperatorEnvironment.put(operator.getName(), operator);
            break;
        }
        case 2: {
            binaryOperatorEnvironment.put(operator.getName(), operator);
            break;
        }
        default: {
            String msg = "Unhandled operator arity " + operator.getArity();
            throw new EvaluationException(msg);
        }
        }
    }
}

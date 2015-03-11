package com.paracamplus.ilp9.interpreter.primitive;

import com.paracamplus.ilp9.interpreter.Interpreter;
import com.paracamplus.ilp9.interpreter.interfaces.EvaluationException;


public class Implies extends Primitive{

	public Implies() {
		super("implies");
	}
	@Override
	public int getArity() {
		return 2;
	}

	@Override
	public Object apply(Interpreter interpreter, Object[] argument) throws EvaluationException {
		if (argument[0] instanceof Boolean && argument[1] instanceof Boolean) {
			if (argument[0] == Boolean.TRUE && argument[1] == Boolean.FALSE) {
				return Boolean.FALSE;
			}
		} else {
			String msg = "Wrong arity" + this.getName();
			throw new EvaluationException(msg);
		}
	
		return Boolean.TRUE;
	}


}

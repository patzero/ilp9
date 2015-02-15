package com.paracamplus.ilp9.interpreter.interfaces;


public interface IOperator {
    String getName();
    int getArity();
    Object apply(Object ... argument) throws EvaluationException;
}

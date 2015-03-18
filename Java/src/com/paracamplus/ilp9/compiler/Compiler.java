/* *****************************************************************
 * ILP9 - Implantation d'un langage de programmation.
 * by Christian.Queinnec@paracamplus.com
 * See http://mooc.paracamplus.com/ilp9
 * GPL version 3
 ***************************************************************** */
package com.paracamplus.ilp9.compiler;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.paracamplus.ilp9.ast.ASTboolean;
import com.paracamplus.ilp9.ast.ASTvariable;
import com.paracamplus.ilp9.compiler.interfaces.IASTCclassDefinition;
import com.paracamplus.ilp9.compiler.interfaces.IASTCcodefinitions;
import com.paracamplus.ilp9.compiler.interfaces.IASTCcomputedInvocation;
import com.paracamplus.ilp9.compiler.interfaces.IASTCfieldRead;
import com.paracamplus.ilp9.compiler.interfaces.IASTCfieldWrite;
import com.paracamplus.ilp9.compiler.interfaces.IASTCfunctionDefinition;
import com.paracamplus.ilp9.compiler.interfaces.IASTCglobalFunctionVariable;
import com.paracamplus.ilp9.compiler.interfaces.IASTCglobalInvocation;
import com.paracamplus.ilp9.compiler.interfaces.IASTCglobalVariable;
import com.paracamplus.ilp9.compiler.interfaces.IASTCinstantiation;
import com.paracamplus.ilp9.compiler.interfaces.IASTClambda;
import com.paracamplus.ilp9.compiler.interfaces.IASTClocalFunctionInvocation;
import com.paracamplus.ilp9.compiler.interfaces.IASTClocalFunctionVariable;
import com.paracamplus.ilp9.compiler.interfaces.IASTClocalVariable;
import com.paracamplus.ilp9.compiler.interfaces.IASTCmethodDefinition;
import com.paracamplus.ilp9.compiler.interfaces.IASTCnamedLambda;
import com.paracamplus.ilp9.compiler.interfaces.IASTCprimitiveInvocation;
import com.paracamplus.ilp9.compiler.interfaces.IASTCprogram;
import com.paracamplus.ilp9.compiler.interfaces.IASTCvariable;
import com.paracamplus.ilp9.compiler.interfaces.IASTCvisitor;
import com.paracamplus.ilp9.compiler.interfaces.IDestination;
import com.paracamplus.ilp9.compiler.interfaces.IGlobalVariableEnvironment;
import com.paracamplus.ilp9.compiler.interfaces.IOperatorEnvironment;
import com.paracamplus.ilp9.compiler.interfaces.IOptimizer;
import com.paracamplus.ilp9.compiler.interfaces.IPrimitive;
import com.paracamplus.ilp9.compiler.normalizer.INormalizationFactory;
import com.paracamplus.ilp9.compiler.normalizer.NormalizationFactory;
import com.paracamplus.ilp9.compiler.normalizer.Normalizer;
import com.paracamplus.ilp9.interfaces.IASTalternative;
import com.paracamplus.ilp9.interfaces.IASTassignment;
import com.paracamplus.ilp9.interfaces.IASTbinaryOperation;
import com.paracamplus.ilp9.interfaces.IASTblock;
import com.paracamplus.ilp9.interfaces.IASTblock.IASTbinding;
import com.paracamplus.ilp9.interfaces.IASTboolean;
import com.paracamplus.ilp9.interfaces.IASTcodefinitions;
import com.paracamplus.ilp9.interfaces.IASTexpression;
import com.paracamplus.ilp9.interfaces.IASTfieldRead;
import com.paracamplus.ilp9.interfaces.IASTfieldWrite;
import com.paracamplus.ilp9.interfaces.IASTfloat;
import com.paracamplus.ilp9.interfaces.IASTfunctionDefinition;
import com.paracamplus.ilp9.interfaces.IASTinstantiation;
import com.paracamplus.ilp9.interfaces.IASTinteger;
import com.paracamplus.ilp9.interfaces.IASTinvocation;
import com.paracamplus.ilp9.interfaces.IASTlambda;
import com.paracamplus.ilp9.interfaces.IASTloop;
import com.paracamplus.ilp9.interfaces.IASToperator;
import com.paracamplus.ilp9.interfaces.IASTprogram;
import com.paracamplus.ilp9.interfaces.IASTself;
import com.paracamplus.ilp9.interfaces.IASTsend;
import com.paracamplus.ilp9.interfaces.IASTsequence;
import com.paracamplus.ilp9.interfaces.IASTstring;
import com.paracamplus.ilp9.interfaces.IASTsuper;
import com.paracamplus.ilp9.interfaces.IASTternaryOperation;
import com.paracamplus.ilp9.interfaces.IASTtry;
import com.paracamplus.ilp9.interfaces.IASTunaryOperation;
import com.paracamplus.ilp9.interfaces.IASTvariable;
import com.paracamplus.ilp9.interfaces.Inamed;

public class Compiler 
implements IASTCvisitor<Void, Compiler.Context, CompilationException> {
    
    public static class Context {
        public Context (IDestination destination) {
            this.destination = destination;
        }
        public IDestination destination;
        public static AtomicInteger counter = new AtomicInteger(0);
        
        public IASTvariable newTemporaryVariable () {
            int i = counter.incrementAndGet();
            return new ASTvariable("ilptmp" + i);
        }
        
        public Context redirect (IDestination d) {
            if ( d == destination ) {
                return this;
            } else {
                return new Context(d);
            }
        }
    }
    
    // 
    
    public Compiler (IOperatorEnvironment ioe,
                     IGlobalVariableEnvironment igve ) {
        this.operatorEnvironment = ioe;
        this.globalVariableEnvironment = igve;
    }
    private final IOperatorEnvironment operatorEnvironment;
    private final IGlobalVariableEnvironment globalVariableEnvironment;
    
    public void setOptimizer (IOptimizer optimizer) {
        this.optimizer = optimizer;
    }
    private IOptimizer optimizer;
    
    //

    public void emit (String s) throws CompilationException {
        try {
            out.append(s);
        } catch (IOException e) {
            throw new CompilationException(e);
        }
    }
    public void emit (char c) throws CompilationException {
        try {
            out.append(c);
        } catch (IOException e) {
            throw new CompilationException(e);
        }
    }
    public void emit (int i) throws CompilationException {
        try {
            out.append(Integer.toString(i));
        } catch (IOException e) {
            throw new CompilationException(e);
        }
    }
    
    //
    
    public IASTCprogram normalize(IASTprogram program, 
                                  IASTCclassDefinition objectClass) 
            throws CompilationException {
        INormalizationFactory nf = new NormalizationFactory();
        Normalizer normalizer = new Normalizer(nf, objectClass);
        IASTCprogram newprogram = normalizer.transform(program);
        return newprogram;
    }
   
    public String compile(IASTprogram program, 
                          IASTCclassDefinition objectClass) 
            throws CompilationException {
        
        IASTCprogram newprogram = normalize(program, objectClass);
        newprogram = optimizer.transform(newprogram);

        GlobalVariableCollector gvc = new GlobalVariableCollector();
        Set<IASTCglobalVariable> gvs = gvc.analyze(newprogram);
        newprogram.setGlobalVariables(gvs);
        
        FreeVariableCollector fvc = new FreeVariableCollector(newprogram);
        newprogram = fvc.analyze();
      
        Context context = new Context(NoDestination.NO_DESTINATION);
        StringWriter sw = new StringWriter();
        try {
            out = new BufferedWriter(sw);
            visit(newprogram, context);
            out.flush();
        } catch (IOException exc) {
            throw new CompilationException(exc);
        }
        return sw.toString();
    }
    private Writer out;

    //
    
    public Void visit(IASTCprogram iast, Context context)
            throws CompilationException {
        emit(cProgramPrefix);
        
        emit(cGlobalVariablesPrefix);
        for ( IASTCglobalVariable gv : iast.getGlobalVariables() ) {
            emit("ILP_Object ");
            emit(gv.getMangledName());
            emit(";\n");
        }
        emit(cGlobalVariablesSuffix);
        
        emit(cPrototypesPrefix);
        Context c = context.redirect(NoDestination.NO_DESTINATION);
        for ( IASTfunctionDefinition ifd : iast.getFunctionDefinitions() ) {
            this.emitPrototype(ifd, c);
        }
        for ( IASTClambda closure : iast.getClosureDefinitions() ) {
            this.emitPrototype(closure, c);
        }
        emit(cFunctionsPrefix);
        for ( IASTfunctionDefinition ifd : iast.getFunctionDefinitions() ) {
            this.visit(ifd, c);
            emitClosure(ifd, c);
        }
        for ( IASTClambda closure : iast.getClosureDefinitions() ) {
            this.emitFunction(closure, c);
        }
        emit(cFunctionsSuffix);
        
        emit(cClassPrefix);
        for ( IASTCclassDefinition cd : iast.getClassDefinitions() ) {
            emitClassHeader(cd);
            visit(cd, c);
        }
        for ( IASTCclassDefinition cd : iast.getClassDefinitions() ) {
            for ( IASTCmethodDefinition md : cd.getProperMethodDefinitions() ) {
                visit((IASTCmethodDefinition)md, context);
            }
        }        
        emit(cClassSuffix);
        
        emit(cBodyPrefix);
        Context cr = context.redirect(ReturnDestination.RETURN_DESTINATION);
        iast.getBody().accept(this, cr);
        emit(cBodySuffix);
        
        emit(cProgramSuffix);
        return null;
    }
    protected String cProgramPrefix = ""
            + "#include <stdio.h> \n"
            + "#include <stdlib.h> \n"
            + "#include \"ilp.h\" \n\n";
    protected String cGlobalVariablesPrefix = ""
            + "/* Global variables */ \n";
    protected String cGlobalVariablesSuffix = ""
            + "\n";
    protected String cPrototypesPrefix = ""
            + "/* Global prototypes */ \n";
    protected String cFunctionsPrefix = "\n"
            + "/* Global functions */ \n";
    protected String cFunctionsSuffix = "\n";
    protected String cClassPrefix = "\n"
            + "/* Classes */ \n";
    protected String cClassSuffix = "\n";
    protected String cBodyPrefix = "\n"
            + "ILP_Object ilp_program () \n"
            + "{ \n";
    protected String cBodySuffix = "\n"
            + "} \n";
    protected String cProgramSuffix = "\n"
            + "static ILP_Object ilp_caught_program () {\n"
            + "  struct ILP_catcher* current_catcher = ILP_current_catcher;\n"
            + "  struct ILP_catcher new_catcher;\n\n"
            + "  if ( 0 == setjmp(new_catcher._jmp_buf) ) {\n"
            + "    ILP_establish_catcher(&new_catcher);\n"
            + "    return ilp_program();\n"
            + "  };\n"
            + "  return ILP_current_exception;\n"
            + "}\n\n"
            + "int main (int argc, char *argv[]) \n"
            + "{ \n"
            + "  ILP_START_GC; \n"
            + "  ILP_print(ilp_caught_program()); \n"
            + "  ILP_newline(); \n"
            + "  return EXIT_SUCCESS; \n"
            + "} \n";    
    
    public Void visit(IASTsequence iast, Context context)
            throws CompilationException {
        IASTvariable tmp = context.newTemporaryVariable();
        IASTexpression[] expressions = iast.getExpressions();
        Context c = context.redirect(new AssignDestination(tmp));
        emit("{ \n");
        emit("  ILP_Object " + tmp.getMangledName() + "; \n");
        for ( IASTexpression expr : expressions ) {
            expr.accept(this, c);
        }
        emit(context.destination.compile());
        emit(tmp.getMangledName());
        emit("; \n} \n");
        return null;
    }

    public Void visit(IASTvariable iast, Context context)
            throws CompilationException {
        if ( iast instanceof IASTClocalVariable ) {
            return visit((IASTClocalVariable) iast, context);
        } else if ( iast instanceof IASTCglobalFunctionVariable ) {
            return visit((IASTCglobalFunctionVariable) iast, context);
        } else {
            return visit((IASTCglobalVariable) iast, context);
        }
    }
    public Void visit(IASTCvariable iast, Context context)
            throws CompilationException {
        throw new RuntimeException("should not occur");
    }
    public Void visit(IASTClocalVariable iast, Context context)
            throws CompilationException {
        emit(context.destination.compile());
        if ( iast.isClosed() ) {
            emit("ILP_Box2Value(");
            emit(iast.getMangledName());
            emit(")");
        } else {
            emit(iast.getMangledName());
        }
        emit("; \n");
        return null;
    }
    public Void visit(IASTClocalFunctionVariable iast, Context context)
            throws CompilationException {
        emit(context.destination.compile());
        emit("(ILP_Object)&" + globalVariableEnvironment.getCName(iast));
        emit("_closure_object; \n");
        return null;
    }
    public Void visit(IASTCglobalVariable iast, Context context)
            throws CompilationException {
        emit(context.destination.compile());
        emit(globalVariableEnvironment.getCName(iast));
        emit("; \n");
        return null;
    }
    public Void visit(IASTCglobalFunctionVariable iast, Context context)
            throws CompilationException {
        emit(context.destination.compile());
        emit("(ILP_Object)&" + globalVariableEnvironment.getCName(iast));
        emit("_closure_object; \n");
        return null;
    }

    public Void visit(IASTboolean iast, Context context)
            throws CompilationException {
        emit(context.destination.compile());
        if ( iast.getValue() ) {
            emit("ILP_TRUE");
        } else {
            emit("ILP_FALSE");
        }
        emit("; \n");
        return null;
    }

    public Void visit(IASTinteger iast, Context context)
            throws CompilationException {
        emit(context.destination.compile());
        emit("ILP_Integer2ILP(");
        emit(iast.getValue().toString());
        emit("); \n");
        return null;
    }
    
    public Void visit(IASTfloat iast, Context context)
            throws CompilationException {
        emit(context.destination.compile());
        emit("ILP_Float2ILP(");
        emit(iast.getValue().toString());
        emit("); \n");
        return null;
    }

    public Void visit(IASTstring iast, Context context)
            throws CompilationException {
        emit(context.destination.compile());
        emit(" ILP_String2ILP(\"");
        final String s = iast.getValue();
        for ( int i=0 ; i<s.length() ; i++ ) {
          char c = s.charAt(i);
          switch ( c ) {
          case '\\':
          case '"': {
            emit("\\");
          }
        //$FALL-THROUGH$
        default: {
            emit(c);
          }
          }
        }
        emit("\"); \n");
        return null;
    }

    public Void visit(IASTunaryOperation iast, Context context)
            throws CompilationException {
        IASTvariable tmp1 = context.newTemporaryVariable();
        emit("{ \n");
        emit("  ILP_Object " + tmp1.getMangledName() + "; \n");
        Context c1 = context.redirect(new AssignDestination(tmp1));
        iast.getOperand().accept(this, c1);
        String cName = operatorEnvironment.getUnaryOperator(iast.getOperator());
        emit(context.destination.compile());
        emit(cName);
        emit("(");
        emit(tmp1.getMangledName());
        emit(");\n");
        emit("} \n");
        return null;
    }

    public Void visit(IASTbinaryOperation iast, Context context)
            throws CompilationException {
        IASTvariable tmp1 = context.newTemporaryVariable();
        IASTvariable tmp2 = context.newTemporaryVariable();
        emit("{ \n");
        emit("  ILP_Object " + tmp1.getMangledName() + "; \n");
        emit("  ILP_Object " + tmp2.getMangledName() + "; \n");
        Context c1 = context.redirect(new AssignDestination(tmp1));
        iast.getLeftOperand().accept(this, c1);
        Context c2 = context.redirect(new AssignDestination(tmp2));
        iast.getRightOperand().accept(this, c2);
        String cName = operatorEnvironment.getBinaryOperator(iast.getOperator());
        emit(context.destination.compile());
        emit(cName);
        emit("(");
        emit(tmp1.getMangledName());
        emit(", ");
        emit(tmp2.getMangledName());
        emit(");\n");
        emit("} \n");
        return null;
    }
	public Void visit(IASTternaryOperation iast, Context context)
			throws CompilationException {
		IASTvariable tmp1 = context.newTemporaryVariable();
        IASTvariable tmp2 = context.newTemporaryVariable();
        IASTvariable tmp3 = context.newTemporaryVariable();
        emit("{ \n");
        emit("  ILP_Object " + tmp1.getMangledName() + "; \n");
        emit("  ILP_Object " + tmp2.getMangledName() + "; \n");
        emit("  ILP_Object " + tmp3.getMangledName() + "; \n");
        
        Context c1 = context.redirect(new AssignDestination(tmp1));
        iast.getCondition().accept(this, c1);
        Context c2 = context.redirect(new AssignDestination(tmp2));
        iast.getFirstResult().accept(this, c2);
        Context c3 = context.redirect(new AssignDestination(tmp3));
        iast.getSecondResult().accept(this, c3);
        
        String cName = operatorEnvironment.getTernaryOperator(iast.getOperator());
        emit(context.destination.compile());
        emit(cName);
        emit("(");
        emit(tmp1.getMangledName());
        emit(", ");
        emit(tmp2.getMangledName());
        emit(");\n");
        emit("} \n");
        return null;
	}
    
    public Void visit(IASToperator iast, Context context)
           throws CompilationException {
        throw new RuntimeException("Should never be called");
   }
   
    public Void visit(IASTalternative iast, Context context)
            throws CompilationException {
        IASTvariable tmp1 = context.newTemporaryVariable();
        emit("{ \n");
        emit("  ILP_Object " + tmp1.getMangledName() + "; \n");
        Context c = context.redirect(new AssignDestination(tmp1));
        iast.getCondition().accept(this, c);
        emit("  if ( ILP_isEquivalentToTrue(");
        emit(tmp1.getMangledName());
        emit(" ) ) {\n");
        iast.getConsequence().accept(this, context);
        if ( iast.isTernary() ) {
            emit("\n  } else {\n");
            iast.getAlternant().accept(this, context);
        }
        emit("\n  }\n}\n");
        return null;
    }
    
    public Void visit(IASTassignment iast, Context context)
            throws CompilationException {
        if ( iast.getVariable() instanceof IASTClocalVariable ) {
            return visitLocalAssignment(iast, context);
        } else {
            return visitNonLocalAssignment(iast, context);
        }
    }
    
    private Void visitLocalAssignment(IASTassignment iast, Context context) 
            throws CompilationException {
        IASTvariable tmp1 = context.newTemporaryVariable();
        emit("{ \n");
        emit("  ILP_Object " + tmp1.getMangledName() + "; \n");
        Context c1 = context.redirect(new AssignDestination(tmp1));
        iast.getExpression().accept(this, c1);
        // Cast ensured by visit(IASTassignment...)
        IASTClocalVariable lv = (IASTClocalVariable) iast.getVariable();
        emit(context.destination.compile());
        emit("(");
        if ( lv.isClosed() ) {
            emit("ILP_SetBoxedValue(");
            emit(lv.getMangledName());
            emit(", ");
            emit(tmp1.getMangledName());
            emit(")");
        } else {
            emit(lv.getMangledName());
            emit(" = ");
            emit(tmp1.getMangledName());
        }
        emit("); \n} \n");
        return null;
    }
    
    private Void visitNonLocalAssignment(IASTassignment iast, Context context) 
            throws CompilationException {
        IASTvariable tmp1 = context.newTemporaryVariable();
        emit("{ \n");
        emit("  ILP_Object " + tmp1.getMangledName() + "; \n");
        Context c1 = context.redirect(new AssignDestination(tmp1));
        iast.getExpression().accept(this, c1);
        emit(context.destination.compile());
        emit("(");
        emit(iast.getVariable().getMangledName());
        emit(" = ");
        emit(tmp1.getMangledName());
        emit("); \n} \n");
        return null;
    }
    
    public Void visit(IASTblock iast, Context context)
            throws CompilationException {
        emit("{ \n");
        IASTbinding[] bindings = iast.getBindings();
        IASTvariable[] tmps = new IASTvariable[bindings.length];
        for ( int i=0 ; i<bindings.length ; i++ ) {
            IASTvariable tmp = context.newTemporaryVariable();
            emit("  ILP_Object " + tmp.getMangledName() + "; \n");
            tmps[i] = tmp;
        }
        for ( int i=0 ; i<bindings.length ; i++ ) {
            IASTbinding binding = bindings[i];
            IASTvariable tmp = tmps[i];
            Context c = context.redirect(new AssignDestination(tmp));
            binding.getInitialisation().accept(this, c);
        }
        emit("\n  {\n");
        for ( int i=0 ; i<bindings.length ; i++ ) {
            IASTbinding binding = bindings[i];
            IASTvariable tmp = tmps[i];
            IASTvariable variable = binding.getVariable();
            emit("    ILP_Object ");
            emit(variable.getMangledName());
            emit(" = ");
            if ( variable instanceof IASTClocalVariable ) {
                IASTClocalVariable lv = (IASTClocalVariable) variable;
                if ( lv.isClosed() ) {
                    emit("ILP_Value2Box(");
                    emit(tmp.getMangledName());
                    emit(")");
                } else {
                    emit(tmp.getMangledName());
                }
            } else {
                emit(tmp.getMangledName());
            }
            emit(";\n");
        }
        iast.getBody().accept(this, context);
        emit("\n  }\n}\n");
        return null;
    }
    
    public Void visit(IASTinvocation iast, Context context)
            throws CompilationException {
        if ( iast instanceof IASTClocalFunctionInvocation ) {
            return visitGeneralInvocation(iast, context);
        } else if ( iast instanceof IASTCglobalInvocation ) {
            return visit((IASTCglobalInvocation) iast, context);
        } else if ( iast instanceof IASTCcomputedInvocation ) {
            return visit((IASTCcomputedInvocation) iast, context);
        } else {
            return visitGeneralInvocation(iast, context);
        }
    }
    public Void visit(IASTCprimitiveInvocation iast, Context context)
            throws CompilationException {
        return visitGeneralInvocation(iast, context);
    }
    public Void visit(IASTClocalFunctionInvocation iast, Context context)
            throws CompilationException {
        return visitGeneralInvocation(iast, context);
    }
    public Void visit(IASTCglobalInvocation iast, Context context)
            throws CompilationException {
        emit("{ \n");
        IASTexpression[] arguments = iast.getArguments();
        IASTvariable[] tmps = new IASTvariable[arguments.length];
        for ( int i=0 ; i<arguments.length ; i++ ) {
            IASTvariable tmp = context.newTemporaryVariable();
            emit("  ILP_Object " + tmp.getMangledName() + "; \n");
            tmps[i] = tmp;
        }
        for ( int i=0 ; i<arguments.length ; i++ ) {
            IASTexpression expression = arguments[i];
            IASTvariable tmp = tmps[i];
            Context c = context.redirect(new AssignDestination(tmp));
            expression.accept(this, c);
        }
        emit(context.destination.compile());
        if ( globalVariableEnvironment.isPrimitive(iast.getFunction()) ) {
            // Should check arity statically!
            IPrimitive fun = globalVariableEnvironment
                    .getPrimitiveDescription(iast.getFunction());
            emit(fun.getCName());
            emit("(");
            for ( int i=0 ; i<arguments.length ; i++ ) {
                IASTvariable tmp = tmps[i];
                emit(tmp.getMangledName());
                if ( i < arguments.length-1 ) {
                    emit(", ");
                }
            }
        } else if (iast.getFunction() instanceof IASTCglobalFunctionVariable) {
            // Should check arity statically!
            emit("ilp__" + iast.getFunction().getMangledName());
            emit("(NULL ");
            for ( int i=0 ; i<arguments.length ; i++ ) {
                IASTvariable tmp = tmps[i];
                emit(", ");
                emit(tmp.getMangledName());
            }
        } else {
            emit("ILP_invoke(");
            emit(iast.getFunction().getMangledName());
            emit(", ");
            emit(arguments.length);
            for ( int i=0 ; i<arguments.length ; i++ ) {
                IASTvariable tmp = tmps[i];
                emit(", ");
                emit(tmp.getMangledName());
            }
        }
        emit(");\n}\n");
        return null;        
    }
    public Void visit(IASTCcomputedInvocation iast, Context context)
        throws CompilationException {
        return visitGeneralInvocation(iast, context);
    }
    
    private Void visitGeneralInvocation(IASTinvocation iast, Context context)
            throws CompilationException {
        emit("{ \n");
        IASTexpression fexpr = iast.getFunction();
        IASTvariable tmpf = context.newTemporaryVariable();
        emit("  ILP_Object " + tmpf.getMangledName() + "; \n");
        IASTexpression[] arguments = iast.getArguments();
        IASTvariable[] tmps = new IASTvariable[arguments.length];
        for ( int i=0 ; i<arguments.length ; i++ ) {
            IASTvariable tmp = context.newTemporaryVariable();
            emit("  ILP_Object " + tmp.getMangledName() + "; \n");
            tmps[i] = tmp;
        }
        Context cf = context.redirect(new AssignDestination(tmpf));
        fexpr.accept(this, cf);
        for ( int i=0 ; i<arguments.length ; i++ ) {
            IASTexpression expression = arguments[i];
            IASTvariable tmp = tmps[i];
            Context c = context.redirect(new AssignDestination(tmp));
            expression.accept(this, c);
        }
        emit(context.destination.compile());
        emit("ILP_invoke(");
        emit(tmpf.getMangledName());
        emit(", ");
        emit(arguments.length);
        for ( int i=0 ; i<arguments.length ; i++ ) {
            IASTvariable tmp = tmps[i];
            emit(", ");
            emit(tmp.getMangledName());
        }
        emit(");\n}\n");
        return null;
    }
    
    public Void visit(IASTcodefinitions iast, Context context)
            throws CompilationException {
        if ( iast instanceof IASTCcodefinitions ) {
            return visit((IASTCcodefinitions)iast, context);
        } else {
            throw new RuntimeException("Should not occur");
        }
    }
    public Void visit(IASTCcodefinitions iast, Context context)
            throws CompilationException {
        emit("{ \n");
        IASTCnamedLambda[] functions = iast.getFunctions();
        for ( IASTCnamedLambda ifd : functions ) {
            emit("  ILP_Object ");
            emit(ifd.getFunctionVariable().getMangledName());
            emit(" = ILP_Value2Box(NULL); \n");
        }
        for ( IASTCnamedLambda fun : functions ) {
            emit("ILP_SetBoxedValue(");
            emit(fun.getFunctionVariable().getMangledName());
            emit(", ILP_make_closure(");
            emit(fun.getName());
            emit(", ");
            emit(fun.getVariables().length);
            emit(", ");
            emit(fun.getClosedVariables().size());
            for ( IASTvariable variable : fun.getClosedVariables() ) {
                emit(", ");
                emit(variable.getMangledName());
            }
            emit("));\n");
        }
        iast.getBody().accept(this, context);
        emit("\n} \n");
        return null;    
    }

    private void emitPrototype(IASTfunctionDefinition iast, Context context)
            throws CompilationException {
        if ( iast instanceof IASTCfunctionDefinition ) {
            emitPrototype((IASTCfunctionDefinition)iast, context);
        } else {
            throw new CompilationException("should not occur");
        }
    }
    private void emitPrototype(IASTCfunctionDefinition iast, Context context)
            throws CompilationException {
        emit("ILP_Object ");
        emit(iast.getCName());
        emit("(ILP_Closure ilp_useless\n");
        IASTvariable[] variables = iast.getVariables();
        for ( int i=0 ; i< variables.length ; i++ ) {
            IASTvariable variable = variables[i];
            emit(",\n    ILP_Object ");
            emit(variable.getMangledName());
        }
        emit(");\n");
    }
    private Void visit(IASTfunctionDefinition iast, Context context)
            throws CompilationException {
        if ( iast instanceof IASTCfunctionDefinition ) {
            return visit((IASTCfunctionDefinition)iast, context);
        } else {
            throw new CompilationException("should not occur");
        }
    }
    public Void visit(IASTCfunctionDefinition iast, Context context)
            throws CompilationException {
        emit("\nILP_Object ");
        emit(iast.getCName());
        emit("(ILP_Closure ilp_useless\n");
        IASTvariable[] variables = iast.getVariables();
        for ( int i=0 ; i< variables.length ; i++ ) {
            IASTvariable variable = variables[i];
            emit(",\n    ILP_Object ");
            emit(variable.getMangledName());
        }
        emit(") {\n");
        for ( IASTvariable variable : variables ) {
            try {
                // Cast ensured by normalizer:
                IASTClocalVariable lv = (IASTClocalVariable) variable;
                if ( lv.isClosed() ) {
                    emit(lv.getMangledName());
                    emit(" = ");
                    emit("ILP_Value2Box(");
                    emit(lv.getMangledName());
                    emit("); \n");
                }
            } catch (ClassCastException exc) {
                throw new RuntimeException("Should not occur");
            }
        }
        Context c = context.redirect(ReturnDestination.RETURN_DESTINATION);
        iast.getBody().accept(this, c);
        emit("}\n");
        return null;
    }
    private void emitClosure(IASTfunctionDefinition iast, Context context)
            throws CompilationException {
        emit("struct ILP_Closure ");
        emit(iast.getMangledName());
        emit("_closure_object = { \n");
        emit("   &ILP_object_Closure_class, \n");
        emit("   { { ilp__");
        emit(iast.getMangledName());
        emit(", \n");
        emit("       " + iast.getVariables().length + ", \n");
        emit("       { NULL } } } \n");
        emit("}; \n");      
    }
    
    private void emitPrototype(IASTClambda iast, Context context)
            throws CompilationException {
        emit("ILP_Object ");
        emit(iast.getMangledName());
        emit("(ILP_Closure ilp_closure");
        IASTvariable[] variables = iast.getVariables();
        for ( int i=0 ; i< variables.length ; i++ ) {
            IASTvariable variable = variables[i];
            emit(",\n    ILP_Object ");
            emit(variable.getMangledName());
        }
        emit(");\n");
    }
    
    private void emitFunction(IASTClambda iast, Context context)
            throws CompilationException {
        emit("ILP_Object ");
        emit(iast.getMangledName());
        emit("(ILP_Closure ilp_closure");
        IASTvariable[] variables = iast.getVariables();
        for ( int i=0 ; i< variables.length ; i++ ) {
            IASTvariable variable = variables[i];
            emit(",\n    ILP_Object ");
            emit(variable.getMangledName());
        }
        emit(") {\n");
        int i = 0;
        for ( IASTvariable variable : iast.getClosedVariables() ) {
            emit("ILP_Object ");
            emit(variable.getMangledName());
            emit(" = ilp_closure->_content.asClosure.closed_variables[");
            emit(i++);
            emit("]; \n");
        }
        for ( IASTvariable variable : variables ) {
            try {
                // Cast ensured by normalizer:
                IASTClocalVariable lv = (IASTClocalVariable) variable;
                if ( lv.isClosed() ) {
                    emit(lv.getMangledName());
                    emit(" = ");
                    emit("ILP_Value2Box(");
                    emit(lv.getMangledName());
                    emit("); \n");
                }
            } catch (ClassCastException exc) {
                throw new RuntimeException("Should not occur");
            }
        }
        Context c = context.redirect(ReturnDestination.RETURN_DESTINATION);
        iast.getBody().accept(this, c);
        emit("}\n");
    }
    
    public Void visit(IASTlambda iast, Context context)
            throws CompilationException {
        if ( iast instanceof IASTClambda ) {
            return visit((IASTClambda)iast, context);
        } else {
            throw new RuntimeException("Should not occur");
        }
    }
    public Void visit(IASTClambda iast, Context context)
            throws CompilationException {
        emit(context.destination.compile());
        emit("ILP_make_closure(");
        emit(iast.getMangledName());
        emit(", ");
        emit(iast.getVariables().length);
        emit(", ");
        emit(iast.getClosedVariables().size());
        for ( IASTvariable variable : iast.getClosedVariables() ) {
            emit(", ");
            emit(variable.getMangledName());
        }
        emit(");\n");
        return null;
    }
    
    public Void visit(IASTloop iast, Context context)
            throws CompilationException {
        emit("while ( 1 ) { \n");
        IASTvariable tmp = context.newTemporaryVariable();
        emit("  ILP_Object " + tmp.getMangledName() + "; \n");
        Context c = context.redirect(new AssignDestination(tmp));
        iast.getCondition().accept(this, c);
        emit("  if ( ILP_isEquivalentToTrue(");
        emit(tmp.getMangledName());
        emit(") ) {\n");
        Context cb = context.redirect(VoidDestination.VOID_DESTINATION);
        iast.getBody().accept(this, cb);
        emit("\n} else { \n");
        emit("    break; \n");
        emit("\n}\n}\n");
        whatever.accept(this, context);
        return null;
    }
    
    protected IASTboolean whatever =
            new ASTboolean("false");

    public Void visit(IASTtry iast, Context context)
            throws CompilationException {
        emit("{ struct ILP_catcher* current_catcher = ILP_current_catcher; \n");
        emit("  struct ILP_catcher new_catcher;  \n");
        emit("  if ( 0 == setjmp(new_catcher._jmp_buf) ) { \n");
        emit("      ILP_establish_catcher(&new_catcher); \n");
        iast.getBody().accept(this, context);
        emit("      ILP_current_exception = NULL; \n");
        emit("  }; \n");

        if ( iast.getCatcher() != null ) {
            IASTlambda catcher = iast.getCatcher();
            IASTvariable caughtVariable = catcher.getVariables()[0];
            emit("  ILP_reset_catcher(current_catcher); \n");
            emit("  if ( NULL != ILP_current_exception ) { \n");
            emit("      if ( 0 == setjmp(new_catcher._jmp_buf) ) { \n");
            emit("          ILP_establish_catcher(&new_catcher); \n");
            emit("          { ILP_Object ");
            emit(caughtVariable.getMangledName());
            emit(" = ILP_current_exception; \n");
            emit("            ILP_current_exception = NULL; \n");
            Context cc = context.redirect(VoidDestination.VOID_DESTINATION);
            catcher.getBody().accept(this, cc);
            emit("          } \n");
            emit("      }; \n");
            emit("  }; \n");
        }

        emit("  ILP_reset_catcher(current_catcher); \n");
        Context cc = context.redirect(VoidDestination.VOID_DESTINATION);
        if ( iast.getFinallyer() != null ) {
            iast.getFinallyer().accept(this, cc);
        }
        emit("  if ( NULL != ILP_current_exception ) { \n");
        emit("      ILP_throw(ILP_current_exception); \n");
        emit("  }; \n");
        whatever.accept(this, context);
        emit("}\n");
        return null;
    }
    
    // Class related

    public Void visit(IASTCclassDefinition iast, Context context)
            throws CompilationException {
        String lastFieldName = "NULL";
        int inheritedFieldsCount = 0;
        if ( ! "Object".equals(iast.getSuperClassName()) ) {
            IASTCclassDefinition superClass = iast.getSuperClass();
            String[] fieldNames = superClass.getTotalFieldNames();
            inheritedFieldsCount = fieldNames.length;
            if ( inheritedFieldsCount > 0 ) {
                String fieldName = fieldNames[inheritedFieldsCount - 1];
                String mangledFieldName = Inamed.computeMangledName(fieldName);
                lastFieldName = "&ILP_object_" + mangledFieldName + "_field";
            }
        }
        String[] fieldNames = iast.getProperFieldNames();
        for ( int i=0 ; i<fieldNames.length ; i++ ) {
            String mangledFieldName = Inamed.computeMangledName(fieldNames[i]);
            emit("\nstruct ILP_Field ILP_object_");
            emit(mangledFieldName);
            emit("_field = {\n  &ILP_object_Field_class,\n     { { ");
            emit("(ILP_Class) &ILP_object_");
            emit(iast.getMangledName());
            emit("_class,\n   ");
            emit(lastFieldName);
            emit(",\n    \"");
            emit(mangledFieldName);
            emit("\",\n  ");
            emit(i + inheritedFieldsCount);
            emit(" } }\n};\n");
            lastFieldName = "&ILP_object_" + mangledFieldName + "_field";
        }

        emit("\nstruct ILP_Class");
        emit(iast.getTotalMethodDefinitionsCount());
        emit(" ILP_object_");
        emit(iast.getMangledName());
        emit("_class = {\n  &ILP_object_Class_class,\n  { { ");
        emit("(ILP_Class) &ILP_object_");
        emit(iast.getSuperClass().getMangledName());
        emit("_class,\n         \"");
        emit(iast.getMangledName());
        emit("\",\n         ");
        emit(inheritedFieldsCount + fieldNames.length);
        emit(",\n         ");
        emit(lastFieldName);
        emit(",\n         ");
        emit(iast.getTotalMethodDefinitionsCount());
        emit(",\n { ");
        for ( IASTCmethodDefinition md : iast.getTotalMethodDefinitions() ) {
            emit(md.getCName());
            emit(", \n");
        }
        emit(" } } }\n};\n");
        
        IASTCmethodDefinition[] methods = iast.getNewProperMethodDefinitions();
        for ( int i = 0 ; i<methods.length ; i++ ) {
            IASTCmethodDefinition method = methods[i];
            if ( ! alreadyGeneratedMethodObject.containsKey(method.getName()) ) {
                emit("\nstruct ILP_Method ILP_object_");
                emit(Inamed.computeMangledName(method.getMethodName()));
                emit("_method = {\n  &ILP_object_Method_class,\n  { { ");
                emit("(struct ILP_Class*) &ILP_object_");
                emit(iast.getMangledName());
                emit("_class,\n  \"");
                emit(method.getMethodName());
                emit("\",\n  ");
                emit(method.getVariables().length);
                emit(",  /* arité */\n  ");
                emit(iast.getMethodOffset(method));
                emit(" /* offset */ \n    } }\n};\n");
            }
        }
        return null;
    }
    private Map<String, Boolean> alreadyGeneratedMethodObject = new HashMap<>();  
        
    public void emitClassHeader(IASTCclassDefinition iast)
            throws CompilationException {
        emitClassMacro(iast);
        int numberMethods = iast.getTotalMethodDefinitionsCount();
        emit("extern struct ILP_Class");
        emit(numberMethods);
        emit(" ILP_object_");
        emit(iast.getMangledName());
        emit("_class; \n");
        for ( String fieldName : iast.getProperFieldNames() ) {
            emit("extern struct ILP_Field ILP_object_");
            emit(Inamed.computeMangledName(fieldName)); 
            emit("_field; \n");
        }
        for ( IASTCmethodDefinition md : iast.getProperMethodDefinitions() ) {
            emitPrototype(md);
        }
    }
    protected void emitClassMacro(IASTCclassDefinition iast)
            throws CompilationException {
        int numberMethods = iast.getTotalMethodDefinitionsCount();
        numberMethods = (numberMethods==0) ? 1 : numberMethods;
        if ( ! alreadyDoneClassPrototypes.containsKey(numberMethods) ) {
            emit("ILP_GenerateClass(");
            emit(numberMethods);
            emit(");\n");
            alreadyDoneClassPrototypes.put(numberMethods, true);
        }
    }
    protected Map<Integer,Boolean> alreadyDoneClassPrototypes = new HashMap<>();

    public Void visit(IASTCmethodDefinition iast, Context context)
            throws CompilationException {
        emit("\nILP_Object ");
        emit(iast.getCName());
        emit("(ILP_Closure ilp_useless,\n");
        IASTvariable[] variables = iast.getVariables();
        for ( int i=0 ; i< variables.length ; i++ ) {
            IASTvariable variable = variables[i];
            emit("    ILP_Object ");
            emit(variable.getMangledName());
            if ( i < variables.length-1 ) {
                emit(",\n");
            }
        }
        emit(") {\n");
        
        IASTCmethodDefinition superMethod = iast.findSuperMethod();
        emit("static ILP_Method ilp_CurrentMethod = &ILP_object_");
        emit(Inamed.computeMangledName(iast.getMethodName()));
        emit("_method;\n");
        emit("static ILP_general_function ilp_SuperMethod = ");
        if ( superMethod != null ){
            emit(superMethod.getCName());
        } else {
            emit("NULL");
        }
        emit(";\n");
        
        emit("ILP_Object ilp_CurrentArguments[");
        emit(iast.getVariables().length);
        emit("];\n");
        for ( int i=0 ; i<iast.getVariables().length ; i++ ) {
            emit(" ilp_CurrentArguments[");
            emit(i);
            emit("] = ");
            emit(iast.getVariables()[i].getMangledName());
            emit(";\n");
        }

        emit("\n{\n");
        for ( IASTvariable variable : variables ) {
            try {
                // Cast ensured by normalizer:
                IASTClocalVariable lv = (IASTClocalVariable) variable;
                if ( lv.isClosed() ) {
                    emit(lv.getMangledName());
                    emit(" = ");
                    emit("ILP_Value2Box(");
                    emit(lv.getMangledName());
                    emit("); \n");
                }
            } catch (ClassCastException exc) {
                throw new RuntimeException("Should not occur");
            }
        }
        Context c = context.redirect(ReturnDestination.RETURN_DESTINATION);
        iast.getBody().accept(this, c);
        emit("}\n}\n");
        return null;
    }
    
    private void emitPrototype(IASTCmethodDefinition iast)
            throws CompilationException {
        emit("ILP_Object ilp__");
        emit(iast.getFunctionVariable().getMangledName()); 
        emit("(ILP_Closure ilp_useless, ");
        IASTvariable[] variables = iast.getVariables();
        for ( int i=0 ; i< variables.length ; i++ ) {
            IASTvariable variable = variables[i];
            emit("    ILP_Object ");
            emit(variable.getMangledName());
            if ( i < variables.length-1 ) {
                emit(",\n");
            }
        }
        emit(");\n");
    }
    
    public Void visit(IASTinstantiation iast, Context context)
            throws CompilationException {
        emit("{ \n");
        IASTvariable tmpInstance = context.newTemporaryVariable();
        emit("  ILP_Object " + tmpInstance.getMangledName() + "; \n");
        
        IASTexpression[] arguments = iast.getArguments();
        IASTvariable[] tmps = new IASTvariable[arguments.length];
        for ( int i=0 ; i<arguments.length ; i++ ) {
            IASTvariable tmp = context.newTemporaryVariable();
            emit("  ILP_Object " + tmp.getMangledName() + "; \n");
            tmps[i] = tmp;
        }
        for ( int i=0 ; i<arguments.length ; i++ ) {
            IASTexpression expression = arguments[i];
            IASTvariable tmp = tmps[i];
            Context c = context.redirect(new AssignDestination(tmp));
            expression.accept(this, c);
        }
        
        emit(tmpInstance.getMangledName());
        emit(" = ILP_MakeInstance(");
        emit(Inamed.computeMangledName(iast.getClassName()));
        emit("); \n");
        
        for ( int i=0 ; i<arguments.length ; i++ ) {
            emit(tmpInstance.getMangledName());
            emit("->_content.asInstance.field[");
            emit(i);
            emit("] = ");
            emit(tmps[i].getMangledName());
            emit("; \n");
        }
        
        emit(context.destination.compile());
        emit(tmpInstance.getMangledName());
        emit("; \n}\n");
        return null;
    }
    
    public Void visit(IASTCinstantiation iast, Context context)
            throws CompilationException {
        return visit((IASTinstantiation)iast, context);
    }
    
    public Void visit(IASTfieldRead iast, Context context)
            throws CompilationException {
        if ( iast instanceof IASTCfieldRead ) {
            return visit((IASTCfieldRead)iast, context);
        } else {
            String msg = "Should not occur";
            throw new CompilationException(msg);
        }
    }
 
    public Void visit(IASTCfieldRead iast, Context context)
            throws CompilationException {
        emit("{ \n");
        IASTvariable tmpInstance = context.newTemporaryVariable();
        emit("  ILP_Object " + tmpInstance.getMangledName() + "; \n");
        Context c = context.redirect(new AssignDestination(tmpInstance));
        iast.getTarget().accept(this, c);
        
        IASTCclassDefinition clazz = iast.getDefiningClass();
        emit("if ( ILP_IsA(");
        emit(tmpInstance.getMangledName());
        emit(", ");
        emit(clazz.getMangledName());
        emit(" ) ) {\n");
        emit(context.destination.compile());
        emit(tmpInstance.getMangledName());
        emit("->_content.asInstance.field[");
        emit(clazz.getFieldOffset(iast.getFieldName()));
        emit("];\n} else {\n");
        emit(context.destination.compile());
        emit(" ILP_UnknownFieldError(\"");
        emit(Inamed.computeMangledName(iast.getFieldName()));
        emit("\", ");
        emit(tmpInstance.getMangledName());
        emit(");\n}\n}\n");
        return null;
    }
    
    public Void visit(IASTfieldWrite iast, Context context)
            throws CompilationException {
        if ( iast instanceof IASTCfieldWrite ) {
            return visit((IASTCfieldWrite)iast, context);
        } else {
            String msg = "Should not occur";
            throw new CompilationException(msg);
        }
    }
    
    public Void visit(IASTCfieldWrite iast, Context context)
            throws CompilationException {
        emit("{ \n");
        IASTvariable tmpInstance = context.newTemporaryVariable();
        emit("  ILP_Object " + tmpInstance.getMangledName() + "; \n");
        Context c = context.redirect(new AssignDestination(tmpInstance));

        IASTvariable tmpValue = context.newTemporaryVariable();
        emit("  ILP_Object " + tmpValue.getMangledName() + "; \n");
        Context cv = context.redirect(new AssignDestination(tmpValue));
        
        iast.getTarget().accept(this, c);
        iast.getValue().accept(this, cv);
        
        IASTCclassDefinition clazz = iast.getDefiningClass();
        emit("if ( ILP_IsA(");
        emit(tmpInstance.getMangledName());
        emit(", ");
        emit(clazz.getMangledName());
        emit(" ) ) {\n");
        emit(context.destination.compile());
        emit(tmpInstance.getMangledName());
        emit("->_content.asInstance.field[");
        emit(clazz.getFieldOffset(iast.getFieldName()));
        emit("] = ");
        emit(tmpValue.getMangledName());
        emit(";\n} else {\n");
        emit(context.destination.compile());
        emit(" ILP_UnknownFieldError(\"");
        emit(Inamed.computeMangledName(iast.getFieldName()));
        emit("\", ");
        emit(tmpInstance.getMangledName());
        emit(");\n}\n}\n");
        return null;
    }

    public Void visit(IASTsend iast, Context context)
            throws CompilationException {
        emit("{ \n");
        IASTvariable tmpMethod = context.newTemporaryVariable();
        emit("  ILP_general_function " + tmpMethod.getMangledName() + "; \n");
        IASTvariable tmpReceiver = context.newTemporaryVariable();
        emit("  ILP_Object " + tmpReceiver.getMangledName() + "; \n");
        Context c = context.redirect(new AssignDestination(tmpReceiver));

        IASTexpression[] arguments = iast.getArguments();
        IASTvariable[] tmps = new IASTvariable[arguments.length];
        for ( int i=0 ; i<arguments.length ; i++ ) {
            IASTvariable tmp = context.newTemporaryVariable();
            emit("  ILP_Object " + tmp.getMangledName() + "; \n");
            tmps[i] = tmp;
        }
        
        iast.getReceiver().accept(this, c);
        for ( int i=0 ; i<arguments.length ; i++ ) {
            IASTexpression expression = arguments[i];
            IASTvariable tmp = tmps[i];
            Context c2 = context.redirect(new AssignDestination(tmp));
            expression.accept(this, c2);
        }

        emit(tmpMethod.getMangledName());
        emit(" = ILP_find_method(");
        emit(tmpReceiver.getMangledName());
        emit(", &ILP_object_");
        emit(Inamed.computeMangledName(iast.getMethodName()));
        emit("_method, ");
        emit(1 + arguments.length);
        emit(");\n");

        emit(context.destination.compile());
        emit(tmpMethod.getName());
        emit("(NULL, ");
        emit(tmpReceiver.getMangledName());
        for ( int i = 0 ; i<arguments.length ; i++ ) {
          emit(", ");
          emit(tmps[i].getMangledName());
        }
        emit(");\n}\n");
        return null;
    }
    
    public Void visit(IASTself iast, Context context)
            throws CompilationException {
        // Totally removed now, see Normalizer.visit(IASTself,...)
        throw new RuntimeException("NYI");
    }
        
    public Void visit(IASTsuper iast, Context context)
            throws CompilationException {
        emit(context.destination.compile());
        emit("ILP_FindAndCallSuperMethod(); \n");
        return null;
    }
	
}

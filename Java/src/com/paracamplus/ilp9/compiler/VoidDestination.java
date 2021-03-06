/* *****************************************************************
 * ILP9 - Implantation d'un langage de programmation.
 * by Christian.Queinnec@paracamplus.com
 * See http://mooc.paracamplus.com/ilp9
 * GPL version 3
 ***************************************************************** */
package com.paracamplus.ilp9.compiler;

import com.paracamplus.ilp9.compiler.interfaces.IDestination;

public class VoidDestination implements IDestination {

    private VoidDestination () {}
    
    public static final VoidDestination VOID_DESTINATION =
            new VoidDestination();
    
    public String compile() {
        return "(void)";
    }
}

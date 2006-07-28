/*BEGIN_COPYRIGHT_BLOCK
 *
 * This file is part of DrJava.  Download the current version of this project:
 * http://sourceforge.net/projects/drjava/ or http://www.drjava.org/
 *
 * DrJava Open Source License
 * 
 * Copyright (C) 2001-2005 JavaPLT group at Rice University (javaplt@rice.edu)
 * All rights reserved.
 *
 * Developed by:   Java Programming Languages Team
 *                 Rice University
 *                 http://www.cs.rice.edu/~javaplt/
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a 
 * copy of this software and associated documentation files (the "Software"),
 * to deal with the Software without restriction, including without 
 * limitation the rights to use, copy, modify, merge, publish, distribute, 
 * sublicense, and/or sell copies of the Software, and to permit persons to 
 * whom the Software is furnished to do so, subject to the following 
 * conditions:
 * 
 *     - Redistributions of source code must retain the above copyright 
 *       notice, this list of conditions and the following disclaimers.
 *     - Redistributions in binary form must reproduce the above copyright 
 *       notice, this list of conditions and the following disclaimers in the
 *       documentation and/or other materials provided with the distribution.
 *     - Neither the names of DrJava, the JavaPLT, Rice University, nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this Software without specific prior written permission.
 *     - Products derived from this software may not be called "DrJava" nor
 *       use the term "DrJava" as part of their names without prior written
 *       permission from the JavaPLT group.  For permission, write to
 *       javaplt@rice.edu.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL 
 * THE CONTRIBUTORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR 
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, 
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR 
 * OTHER DEALINGS WITH THE SOFTWARE.
 * 
END_COPYRIGHT_BLOCK*/

package edu.rice.cs.javalanglevels;

import edu.rice.cs.javalanglevels.tree.*;
import edu.rice.cs.javalanglevels.parser.JExprParser;
import java.util.*;
import java.io.*;

import junit.framework.TestCase;

/**Does TypeChecking for the context of a Try-Catch body.  Common to all LanguageLevels.*/
public class TryCatchBodyTypeChecker extends BodyTypeChecker {


  /* Constructor for TryCatchBodyTypeChecker.  Delegates the initialization to the BodyTypeChecker
   * @param bodyData  The enclosing BodyData for the context we are type checking.
   * @param file  The File corresponding to the source file.
   * @param packageName  The package name from the source file.
   * @param importedFiles  The names of the files that are specifically imported (through a class import statement) in the source file.
   * @param importedPackages  The names of all the packages that are imported through a package import statement in the source file.
   * @param vars  The list of VariableData that have already been defined (used so we can make sure we don't use a variable before it has been defined).
   * @param thrown  The list of exceptions thrown in this body
   */
  public TryCatchBodyTypeChecker(BodyData bodyData, File file, String packageName, LinkedList<String> importedFiles, LinkedList<String> importedPackages, LinkedList<VariableData> vars, LinkedList<Pair<SymbolData, JExpression>> thrown) {
    super(bodyData, file, packageName, importedFiles, importedPackages, vars, thrown);
  }
 
  
  /** Create a new instance of this class for visiting inner bodies. */
  protected BodyTypeChecker createANewInstanceOfMe(BodyData bodyData, File file, String pakage, LinkedList<String> importedFiles, LinkedList<String> importedPackages, LinkedList<VariableData> vars, LinkedList<Pair<SymbolData, JExpression>> thrown) {
    return new TryCatchBodyTypeChecker(bodyData, file, pakage, importedFiles, importedPackages, vars, thrown);
  }
  
  /**
   * Overwritten here, becuase it is okay for there to be thrown exceptions in the middle of a 
   * try catch.
   */
  public TypeData forBracedBody(BracedBody that) {
    final TypeData[] items_result = makeArrayOfRetType(that.getStatements().length);
    for (int i = 0; i < that.getStatements().length; i++) {
      items_result[i] = that.getStatements()[i].visit(this);
    }
    return forBracedBodyOnly(that, items_result);
  }
  
  /**
   * Make sure that every Exception in thrown is either in caught or in the list of what can be thrown from where we are.
   * Also make sure that every Exception that is declared to be thrown or caught is actually thrown.
   * Overrides the same method in BodyTypeChecker.
   * @param that  The TryCatchStatement we are currently working with
   * @param caught_array  The SymbolData[] of exceptions that are explicitely caught.
   * @param thrown  The LinkedList of SymbolData of exceptions that are thrown.  This will be modified.
   */
  protected void compareThrownAndCaught(TryCatchStatement that, SymbolData[] caught_array, LinkedList<Pair<SymbolData, JExpression>> thrown) {
    LinkedList<Pair<SymbolData, JExpression>> copyOfThrown = new LinkedList<Pair<SymbolData, JExpression>>();
    for (Pair<SymbolData, JExpression> p : thrown) {
      copyOfThrown.addLast(p);
    }
    //Make sure that every Exception in thrown is either caught or in the list of what can be thrown
    for (Pair<SymbolData, JExpression> p : copyOfThrown) {
      SymbolData sd = p.getFirst();
      // Iterate over the caught array and see if the current thrown exception is a subclass of one of the exceptions.
      for (SymbolData currCaughtSD : caught_array) {
        if (sd.isSubClassOf(currCaughtSD) || (!isUncaughtCheckedException(sd, new NullLiteral(JExprParser.NO_SOURCE_INFO)))) {
          thrown.remove(p);
        }
      }
    }
    makeSureCaughtStuffWasThrown(that, caught_array, copyOfThrown);
  }
  
   /**
    * Test the methods declared in the above class.
    */
  public static class TryCatchBodyTypeCheckerTest extends TestCase {
    
    private TryCatchBodyTypeChecker _tcbtc;
    
    private BodyData _bd1;
    private BodyData _bd2;
    
    private SymbolData _sd1;
    private SymbolData _sd2;
    private SymbolData _sd3;
    private SymbolData _sd4;
    private SymbolData _sd5;
    private SymbolData _sd6;
    private ModifiersAndVisibility _publicMav = new ModifiersAndVisibility(JExprParser.NO_SOURCE_INFO, new String[] {"public"});
    private ModifiersAndVisibility _protectedMav = new ModifiersAndVisibility(JExprParser.NO_SOURCE_INFO, new String[] {"protected"});
    private ModifiersAndVisibility _privateMav = new ModifiersAndVisibility(JExprParser.NO_SOURCE_INFO, new String[] {"private"});
    private ModifiersAndVisibility _packageMav = new ModifiersAndVisibility(JExprParser.NO_SOURCE_INFO, new String[0]);
    private ModifiersAndVisibility _abstractMav = new ModifiersAndVisibility(JExprParser.NO_SOURCE_INFO, new String[] {"abstract"});
    private ModifiersAndVisibility _finalMav = new ModifiersAndVisibility(JExprParser.NO_SOURCE_INFO, new String[] {"final"});
    
    
    public TryCatchBodyTypeCheckerTest() {
      this("");
    }
    public TryCatchBodyTypeCheckerTest(String name) {
      super(name);
    }
    
    public void setUp() {
      _sd1 = new SymbolData("i.like.monkey");
      _sd2 = new SymbolData("i.like.giraffe");
      _sd3 = new SymbolData("zebra");
      _sd4 = new SymbolData("u.like.emu");
      _sd5 = new SymbolData("");
      _sd6 = new SymbolData("cebu");

      _bd1 = new MethodData("monkey", 
                            _packageMav, 
                            new TypeParameter[0], 
                            _sd1, 
                            new VariableData[] { new VariableData("i", _publicMav, SymbolData.INT_TYPE, true, null), new VariableData(SymbolData.BOOLEAN_TYPE) },
                            new String[0],
                            _sd1,
                            null); // no SourceInfo
      ((MethodData) _bd1).getParams()[0].setEnclosingData(_bd1);
      ((MethodData) _bd1).getParams()[1].setEnclosingData(_bd1);
                            
      errors = new LinkedList<Pair<String, JExpressionIF>>();
      symbolTable = new Symboltable();
      _bd1.addEnclosingData(_sd1);
      _bd1.addFinalVars(((MethodData)_bd1).getParams());
      _tcbtc = new TryCatchBodyTypeChecker(_bd1, new File(""), "", new LinkedList<String>(), new LinkedList<String>(), new LinkedList<VariableData>(), new LinkedList<Pair<SymbolData, JExpression>>());
      _tcbtc._targetVersion = "1.5 version";
      _tcbtc._importedPackages.addFirst("java.lang");
    }
    
    
    public void testCreateANewInstanceOfMe() {
      //make sure that the correct visitor is returned from createANewInstanceOfMe
      BodyTypeChecker btc = _tcbtc.createANewInstanceOfMe(_tcbtc._bodyData, _tcbtc._file, _tcbtc._package, _tcbtc._importedFiles, _tcbtc._importedPackages, _tcbtc._vars, _tcbtc._thrown);
      assertTrue("Should be an instance of ConstructorBodyTypeChecker", btc instanceof TryCatchBodyTypeChecker);
    }
    
    public void testForBracedBody() {
      //make sure it is okay to have a uncaught exception in a braced body
      BracedBody bb = new BracedBody(JExprParser.NO_SOURCE_INFO, 
                                     new BodyItemI[] { 
        new ThrowStatement(JExprParser.NO_SOURCE_INFO, 
                           new SimpleNamedClassInstantiation(JExprParser.NO_SOURCE_INFO, 
                                         new ClassOrInterfaceType(JExprParser.NO_SOURCE_INFO, 
                                                                 "java.util.prefs.BackingStoreException", 
                                                                 new Type[0]), 
                                                             new ParenthesizedExpressionList(JExprParser.NO_SOURCE_INFO, new Expression[] {new StringLiteral(JExprParser.NO_SOURCE_INFO, "arg")})))});
      
      LanguageLevelVisitor llv = new LanguageLevelVisitor(new File(""), "", new LinkedList<String>(), new LinkedList<String>(), 
                                      new LinkedList<String>(), new Hashtable<String, Pair<SourceInfo, LanguageLevelVisitor>>());
      llv.errors = new LinkedList<Pair<String, JExpressionIF>>();
      llv._errorAdded=false;
      llv.symbolTable = symbolTable;
      llv.continuations = new Hashtable<String, Pair<SourceInfo, LanguageLevelVisitor>>();
      llv.visitedFiles = new LinkedList<Pair<LanguageLevelVisitor, edu.rice.cs.javalanglevels.tree.SourceFile>>();      
      llv._hierarchy = new Hashtable<String, TypeDefBase>();
      llv._classesToBeParsed = new Hashtable<String, Pair<TypeDefBase, LanguageLevelVisitor>>();

      SymbolData e = llv.getSymbolData("java.util.prefs.BackingStoreException", JExprParser.NO_SOURCE_INFO, true);
      
      bb.visit(_tcbtc);
      assertEquals("There should be no errors because it's ok to have uncaught exceptions in this visitor", 0, errors.size());
    }
    
    public void testCompareThrownAndCaught() {
      BracedBody emptyBody = new BracedBody(JExprParser.NO_SOURCE_INFO, new BodyItemI[0]);
      Block b = new Block(JExprParser.NO_SOURCE_INFO, emptyBody);

      PrimitiveType intt = new PrimitiveType(JExprParser.NO_SOURCE_INFO, "int");
      UninitializedVariableDeclarator uvd = new UninitializedVariableDeclarator(JExprParser.NO_SOURCE_INFO, intt, new Word(JExprParser.NO_SOURCE_INFO, "i"));
      FormalParameter param = new FormalParameter(JExprParser.NO_SOURCE_INFO, new UninitializedVariableDeclarator(JExprParser.NO_SOURCE_INFO, intt, new Word(JExprParser.NO_SOURCE_INFO, "j")), false);

      NormalTryCatchStatement ntcs = new NormalTryCatchStatement(JExprParser.NO_SOURCE_INFO, b, new CatchBlock[] {new CatchBlock(JExprParser.NO_SOURCE_INFO,  param, b)});

      SymbolData javaLangThrowable = new SymbolData("java.lang.Throwable");
      _tcbtc.symbolTable.put("java.lang.Throwable", javaLangThrowable);
      SymbolData exception = new SymbolData("my.crazy.exception");
      exception.setSuperClass(javaLangThrowable);
      SymbolData exception2 = new SymbolData("A&M.beat.Rice.in.BaseballException");
      exception2.setSuperClass(javaLangThrowable);
      SymbolData exception3 = new SymbolData("aegilha");
      exception3.setSuperClass(exception2);
      SymbolData[] caught_array = new SymbolData[] { exception, exception2 };
      LinkedList<Pair<SymbolData, JExpression>> thrown = new LinkedList<Pair<SymbolData, JExpression>>();
      thrown.addLast(new Pair<SymbolData, JExpression>(exception, ntcs));
      thrown.addLast(new Pair<SymbolData, JExpression>(exception2, ntcs));
      thrown.addLast(new Pair<SymbolData, JExpression>(exception3, ntcs));
      
      _tcbtc.compareThrownAndCaught(ntcs, caught_array, thrown);
      assertTrue("Thrown should have no elements", thrown.isEmpty());
      
      _tcbtc.compareThrownAndCaught(ntcs, new SymbolData[] {exception2}, thrown);
      assertEquals("There should be one error", 1, errors.size());
      assertEquals("The error message should be correct", "The exception A&M.beat.Rice.in.BaseballException is never thrown in the body of the corresponding try block", errors.get(0).getFirst());

    }
  }
}
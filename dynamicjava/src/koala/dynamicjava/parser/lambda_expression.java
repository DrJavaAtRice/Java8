package koala.dynamicjava.tree;
import edu.rice.cs.plt.tuple.Option;
import koala.dynamicjava.tree.visitor.Visitor;
import java.util.*;
public class LambdaExpression extends PrimaryExpression {
private Option<List<FormalParameter>> typedParams;
private Option<List<String>> inferredParams;
// A lambda expression have either the block as its body, the body could also be an expression, 
//refer to Java 8 documentation for details
//lambda expression for Java8
private Option<BlockStatement> blockBD;
private Option<Expression> exprBD;
public LambdaExpression(List<FormalParameter> typedParams,
List<String> inferredParams,
BlockStatement blockBD,
Expression exprBD) {
this(typedParams, inferredParams, blockBD, exprBD, SourceInfo.NONE);
}
/**
* This class creates new lambda expression, which is newly introduced by Java 8. 
*
* @param typedParams A list of parameters annotated with types.
* @param inferredParams A list of parameters without type annotations.
* @param blockBody A block, used as the body of the lambda.
* @param exprBody An expression, used as the body of the lambda
* @param si Source information.
* @throws java.lang.IllegalArgumentException Thrown if parameters or body arguments are both null or are both
* provided.
*/
public LambdaExpression(List<FormalParameter> typedParams,
List<String> inferredParams,
BlockStatement blockBD,
Expression exprBD,
SourceInfo si) {
super(si);
if (blockBD == null && exprBD == null) {
throw new IllegalArgumentException("No body was provided.");
}
if (typedParams != null && inferredParams != null) {
throw new IllegalArgumentException("Both kinds of Parameter List was provided");
}
if (blockBD != null && exprBD!= null) {
throw new IllegalArgumentException("Both kinds of Bodies have to be provided.");
}
if (blockBD == null) {
this.blockBD = Option.none();
} else {
this.blockBD = Option.wrap(blockBD);
}
if (exprBD == null) {
this.exprBD = Option.none();
} else {
this.exprBD = Option.wrap(exprBD);
}
if (typedParams == null) {
this.typedParams = Option.none();
} else {
this.typedParams = Option.wrap(typedParams);
}
if (inferredParams == null) {
this.inferredParams = Option.none();
} else {
this.inferredParams = Option.wrap(inferredParams);
}
}
@Override
public <T> T acceptVisitor(Visitor<T> visitor) {
return visitor.visit(this);
}
}

   

    


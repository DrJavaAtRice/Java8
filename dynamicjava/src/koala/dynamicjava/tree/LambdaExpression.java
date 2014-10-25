package koala.dynamicjava.tree;

import edu.rice.cs.plt.tuple.Option;
import koala.dynamicjava.tree.visitor.Visitor;

import java.util.*;

public class LambdaExpression extends PrimaryExpression {
    private Option<List<FormalParameter>> typedParams;
    private Option<List<String>> inferredParams;

    // A lambda expression can have either a block or an expression as its body.
    private Option<BlockStatement> blockBody;
    private Option<Expression> exprBody;

    public LambdaExpression(List<FormalParameter> typedParams,
                            List<String> inferredParams,
                            BlockStatement blockBody,
                            Expression exprBody) {
        this(typedParams, inferredParams, blockBody, exprBody, SourceInfo.NONE);
    }

    /**
     * Creates a new LambdaExpression.
     *
     * @param typedParams    A list of parameters annotated with types.
     * @param inferredParams A list of parameters without type annotations.
     * @param blockBody      A block, used as the body of the lambda.
     * @param exprBody       An expression, used as the body of the lambda
     * @param si             Source information.
     * @throws java.lang.IllegalArgumentException Thrown if parameters or body arguments are both null or are both
     *                                            provided.
     */
    public LambdaExpression(List<FormalParameter> typedParams,
                            List<String> inferredParams,
                            BlockStatement blockBody,
                            Expression exprBody,
                            SourceInfo si) {
        super(si);

        if (blockBody == null && exprBody == null) {
            throw new IllegalArgumentException("No body provided.");
        }
        if (typedParams != null && inferredParams != null) {
            throw new IllegalArgumentException("Provided both kinds of parameter list.");
        }
        if (blockBody != null && exprBody != null) {
            throw new IllegalArgumentException("Provided both kinds of body.");
        }

        if (blockBody == null) {
            this.blockBody = Option.none();
        } else {
            this.blockBody = Option.wrap(blockBody);
        }

        if (exprBody == null) {
            this.exprBody = Option.none();
        } else {
            this.exprBody = Option.wrap(exprBody);
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

package koala.dynamicjava.tree;
import edu.rice.cs.plt.tuple.Option;
import koala.dynamicjava.tree.visitor.Visitor;
import java.util.*;
public class MethodReference extends PrimaryExpression {
  private Option<TypeName> typ;
  private Option<String> methodName;
  public MethodReference(TypeName type,
                          String methodName) {
    this(type, methodName, SourceInfo.NONE);
  }
  /**
   * Creates a new Method Reference.
   *
   * @param type Containing type of method
   * @param methodname Name of method
   * @param si Source information.
   * @throws java.lang.IllegalArgumentException if containing type or method name is null
   * provided.
   */
   
   /*Method Reference for Lambda Expression */
  public MethodReference(TypeName type,
                          String method,
                          SourceInfo si) {
    super(si);
    if (type == null) {
      throw new IllegalArgumentException("No type provided.");
    }
    if (method == null) {
      throw new IllegalArgumentException("No method name provided.");
    }
    if (type != null) {
      this.typ = Option.wrap(type);
    } else {
      this.typ = Option.none();
    }
    if (methodName != null) {
      this.methodName = Option.wrap(method);
    } else {
      this.methodName = Option.none();
    }
  }
  @Override
  public <T> T acceptVisitor(Visitor<T> visitor) {
    return visitor.visit(this);
  }
}

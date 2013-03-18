package devcpu.assembler.expression;

import java.util.ArrayList;
import java.util.List;

import devcpu.lexer.tokens.GroupEndToken;
import devcpu.lexer.tokens.GroupStartToken;
import devcpu.lexer.tokens.LabelToken;
import devcpu.lexer.tokens.LexerToken;
import devcpu.lexer.tokens.LiteralToken;
import devcpu.lexer.tokens.OperatorToken;
import devcpu.lexer.tokens.PickValueEndToken;
import devcpu.lexer.tokens.PickValueStartToken;
import devcpu.lexer.tokens.RegisterToken;
import devcpu.lexer.tokens.UnaryOperatorToken;

public class Group implements Operand {
	private ArrayList<Value> values = new ArrayList<Value>();
	
	public Group (LexerToken[] tokens, int i, Class terminator) {
		LexerToken token = null;
		while (!terminator.isInstance(token = tokens[++i])) {
			if (token instanceof GroupStartToken) {
				values.add(new Group(tokens,i,GroupEndToken.class));
			} else if (token instanceof PickValueStartToken) {
				values.add(new PickValue(tokens, i, PickValueEndToken.class));
			} else if (token instanceof LabelToken) {
				values.add(new Literal(token,((LabelToken)token).getValue()));
			} else if (token instanceof RegisterToken) {
				values.add(new Register((RegisterToken)token));
			} else if (token instanceof LiteralToken) {
				values.add(new Literal(token, ((LiteralToken)token).getValue()));
			} else if (token instanceof OperatorToken) {
				values.add(new Operator(token));
			} else if (token instanceof UnaryOperatorToken) {
				values.add(new UnaryOperator(token));
			} else {
				System.out.println("ERMAHGERD YER FERGERT ERBERT " + token.getClass().getCanonicalName());
			}
		}
	}
	
	public List<Value> getValues() {
		return values;
	}

	@Override
	public boolean containsRegister() {
		for (Value value : values) {
			if (value instanceof Operand) {
				if (((Operand)value).containsRegister()) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public List<Register> getRegisters() {
		List<Register> list = new ArrayList<Register>();
		for (Value value : values) {
			if (value instanceof Group) {
				list.addAll(((Group)value).getRegisters());
			} else if (value instanceof Register) {
				list.add((Register) value);
			}
		}
		return list;
	}

	public boolean isExpression() {
		if (values.size() != 1) {
			return true;
		} else if (values.get(0) instanceof Group) {
			return true;
		}
		return false;
	}

	public String getExpression() {
		String expression = "";
		for (Value value : values) {
			expression += value.getExpression();
		}
		return expression;
	}
}

package devcpu.assembler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import devcpu.assembler.exceptions.InvalidDefineFormatException;
import devcpu.lexer.Lexer;

public class Define {
	private static final Pattern pattern = Pattern.compile("\\s*(" + Lexer.REGEX_IDENTIFIER + ")\\s*([^;\\r\\n]*)");
	private Directive directive;
	private String key;
	private String value;

	public Define(Directive directive) throws InvalidDefineFormatException {
		this.directive = directive;
		Matcher m = pattern.matcher(directive.getParametersToken().getText());
		if (m.find() && m.start() == 0) {
			this.key = m.group(1);
			this.value = m.group(2);
		} else {
			throw new InvalidDefineFormatException(directive);
		}
	}

	public Directive getDirective() {
		return directive;
	}

	public String getKey() {
		return key;
	}

	public String getValue() {
		return value;
	}
}

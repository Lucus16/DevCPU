package devcpu.assembler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;

import de.congrace.exp4j.ExpressionBuilder;
import devcpu.assembler.exceptions.DirectiveExpressionEvaluationException;
import devcpu.assembler.exceptions.DuplicateLabelDefinitionException;
import devcpu.assembler.exceptions.IncludeFileNotFoundException;
import devcpu.assembler.exceptions.InvalidDefineFormatException;
import devcpu.assembler.exceptions.OriginBacktrackException;
import devcpu.assembler.exceptions.RecursiveDefinitionException;
import devcpu.assembler.exceptions.RecursiveInclusionException;
import devcpu.emulation.DefaultControllableDCPU;
import devcpu.emulation.OpCodes;
import devcpu.lexer.Lexer;
import devcpu.lexer.tokens.AValueEndToken;
import devcpu.lexer.tokens.AValueStartToken;
import devcpu.lexer.tokens.AddressEndToken;
import devcpu.lexer.tokens.AddressStartToken;
import devcpu.lexer.tokens.BValueEndToken;
import devcpu.lexer.tokens.BValueStartToken;
import devcpu.lexer.tokens.BasicOpCodeToken;
import devcpu.lexer.tokens.DataToken;
import devcpu.lexer.tokens.DataValueEndToken;
import devcpu.lexer.tokens.DataValueStartToken;
import devcpu.lexer.tokens.ErrorToken;
import devcpu.lexer.tokens.LabelDefinitionToken;
import devcpu.lexer.tokens.LabelToken;
import devcpu.lexer.tokens.LexerToken;
import devcpu.lexer.tokens.LiteralToken;
import devcpu.lexer.tokens.RegisterToken;
import devcpu.lexer.tokens.SimpleStackAccessToken;
import devcpu.lexer.tokens.SpecialOpCodeToken;
import devcpu.lexer.tokens.StringToken;
import devcpu.util.Util;

public class Assembly {
	//Note: Defines will not be processed in directives
	public static final boolean DEFAULT_LABELS_CASE_SENSITIVE = true;
	private static final int TYPE_SPECIAL = 1;
	private static final int TYPE_BASIC = 2;
	private static final int TYPE_DATA = 3;
	private AssemblyDocument rootDocument;
	private ArrayList<AssemblyDocument> documents = new ArrayList<AssemblyDocument>();
	private boolean labelsCaseSensitive = DEFAULT_LABELS_CASE_SENSITIVE;
	
	public ArrayList<AssemblyLine> lines = new ArrayList<AssemblyLine>();
	public LinkedHashMap<String,String> defines = new LinkedHashMap<String, String>();
	public LinkedHashMap<String,LabelDefinition> labelDefs = new LinkedHashMap<String, LabelDefinition>();
	public LinkedHashMap<String,List<LabelUse>> labelUses = new LinkedHashMap<String, List<LabelUse>>();

	public Assembly(IFile file) throws IOException, DuplicateLabelDefinitionException, CoreException, IncludeFileNotFoundException, RecursiveInclusionException, InvalidDefineFormatException, RecursiveDefinitionException {
		rootDocument = new AssemblyDocument(file, this, null);
		documents.add(rootDocument);
		//TODO: Evaluate what should really be in the constructor and what should wait until assemble
		processDefinesAndCollectLabels();
		//TODO?
	}

	private void processDefinesAndCollectLabels() throws DuplicateLabelDefinitionException {
		//Note: Label collection can be done here now, but directives added later could necessitate
		//moving this until after all preprocessing is done.
		LinkedHashMap<Pattern,String> patterns = new LinkedHashMap<Pattern, String>();
		for (String key : defines.keySet()) {
			patterns.put(Pattern.compile("\\b"+Pattern.quote(key)+"\\b"), defines.get(key));
		}
		String lastDefinedGlobalLabel = null;
		for (AssemblyLine line : lines) {
			if (!line.isDirective() || (!line.getDirective().isDefine() && !line.getDirective().isInclude())) {
				boolean retokenize = false;
				String text = line.getText();
				for (Pattern pattern : patterns.keySet()) {
					if (pattern.matcher(text).find()) {
						retokenize = true;
						text = text.replaceAll(pattern.pattern(), patterns.get(pattern));
					}
				}
				if (retokenize) {
					line.setProcessedTokens(Lexer.get().generateTokens(text, true));
				}
				for (LexerToken token : line.getTokens()) {
					if (token instanceof LabelDefinitionToken) {
						LabelDefinition labelDef = new LabelDefinition(line, (LabelDefinitionToken) token, labelsCaseSensitive, lastDefinedGlobalLabel);
						if (!labelDef.isLocal()) {
							lastDefinedGlobalLabel = labelDef.getLabelName();
						}
						if (labelDefs.containsKey(labelDef.getLabelName())) {
							throw new DuplicateLabelDefinitionException(labelDefs.get(labelDef.getLabelName()),labelDef);
						}
						labelDefs.put(labelDef.getLabelName(), labelDef);
					} else if (token instanceof LabelToken) {
						LabelUse labelUse = new LabelUse(line, (LabelToken) token, labelsCaseSensitive, lastDefinedGlobalLabel);
						if (!labelUses.containsKey(labelUse.getLabelName())) {
							labelUses.put(labelUse.getLabelName(), new ArrayList<LabelUse>());
						}
						labelUses.get(labelUse.getLabelName()).add(labelUse);
					} else if (token instanceof ErrorToken) {
						//TODO Throw exception
						System.err.println("Error tokenizing line " + line.getLineNumber() + " in " + line.getDocument().getFile().getName() + ": " + line.getText());
					}
					//TODO: Additional validity checkes?
				}
			}
		}
	}

	public AssemblyDocument getRootDocument() {
		return rootDocument;
	}
	
	public IFile getFile() {
		return rootDocument.getFile();
	}

	public boolean isLabelsCaseSensitive() {
		return labelsCaseSensitive;
	}

	public void setLabelsCaseSensitive(boolean labelsCaseSensitive) {
		this.labelsCaseSensitive = labelsCaseSensitive;
	}

	public void assemble(DefaultControllableDCPU dcpu) throws OriginBacktrackException, DirectiveExpressionEvaluationException {
		sizeAndLocateLines();
		assignLabelValues();
		zeroRAM(dcpu.ram);
		assembleToRAM(dcpu.ram);
		Assembler assembler = new Assembler(dcpu.ram);
		//TODO
	}

	private void assembleToRAM(char[] ram) {
		int pc = 0;
		int type;
		int opCode;
		int a;
		int b;
		for (AssemblyLine line : lines) {
			pc = line.getOffset();
			if (line.isDirective()) {
				Directive directive = line.getDirective();
				if (directive.isAlign()) {
					int end = pc + line.getSize();
					while (pc < end) {
						ram[pc++] = 0;
					}
				} else if (directive.isAlign()) {
					int end = pc + line.getSize();
					while (pc < end) {
						ram[pc++] = 0;
					}
				}
			} else {
				type = 0;
				LexerToken[] tokens = line.getProcessedTokens();
				for (int i = 0; i < tokens.length; i++) {
					LexerToken token = tokens[i];
					if (token instanceof SpecialOpCodeToken) {
						type = TYPE_SPECIAL;
						opCode = OpCodes.special.getId(token.getText().toUpperCase());
						a = getA(tokens,i+1,((SpecialOpCodeToken)token).isNextWordA()?pc+1:0,ram);
						ram[pc] = (char)(opCode << 5 | a << 10);
					} else if (token instanceof BasicOpCodeToken) {
						type = TYPE_BASIC;
						opCode = OpCodes.basic.getId(token.getText().toUpperCase());
						a = getA(tokens,i+1,((SpecialOpCodeToken)token).isNextWordA()?pc+1:0,ram);
						b = getB(tokens,i+1,((BasicOpCodeToken)token).isNextWordB()?((SpecialOpCodeToken)token).isNextWordA()?pc+2:pc+1:0,ram);
						ram[pc] = (char)(opCode | b << 5 | a << 10);
					} else if (token instanceof DataToken) {
						type = TYPE_DATA;
						//TODO
					}
				}
			}
		}
	}

	private int getB(LexerToken[] tokens, int index, int offset, char[] ram) {
		// TODO Auto-generated method stub
		return 0;
	}

	private int getA(LexerToken[] tokens, int index, int offset, char[] ram) {
		// TODO Auto-generated method stub
		return 0;
	}

	private void zeroRAM(char[] ram) {
		for (int i = 0; i < ram.length; i++) {
			ram[i] = 0;
		}
	}

	private void assignLabelValues() {
		for (String label : labelUses.keySet()) {
			int o = labelDefs.get(label).getLine().getOffset();
			for (LabelUse use : labelUses.get(label)) {
				use.getToken().setValue(o);
			}
		}
	}

	private void sizeAndLocateLines() throws OriginBacktrackException, DirectiveExpressionEvaluationException {
		int o = 0;
		for (AssemblyLine line : lines) {
			line.setOffset(o);
			if (line.isDirective()) {
				Directive directive = line.getDirective();
				if (directive.isOrigin()) {
					int newO;
					try {
						newO = (int) new ExpressionBuilder(decimalize(directive.getParametersToken().getText())).build().calculate();
					} catch (Exception e) {
						throw new DirectiveExpressionEvaluationException(directive);
					}
					if (newO < o) {
						throw new OriginBacktrackException(directive);
					}
					line.setSize(newO - o);
					o = newO;
				} else if (directive.isAlign()) {
					int newO;
					try {
						newO = (int) new ExpressionBuilder(decimalize(directive.getParametersToken().getText())).build().calculate();
					} catch (Exception e) {
						throw new DirectiveExpressionEvaluationException(directive);
					}
					if (newO < o) {
						throw new OriginBacktrackException(directive);
					}
					line.setSize(newO - o);
					o = newO;
				} else if (directive.isReserve()) {
					int dO;
					try {
						dO = (int) new ExpressionBuilder(decimalize(directive.getParametersToken().getText())).build().calculate();
					} catch (Exception e) {
						throw new DirectiveExpressionEvaluationException(directive);
					}
					if (dO < 0) {
						throw new OriginBacktrackException(directive);
					}
					o += dO;
					line.setSize(dO);
				}
			} else {
				o += sizeLine(line);
				System.out.println(line.getOffset() + ": (" + line.getSize() + ") " + line.getText());
			}
		}
	}

	private String decimalize(String text) {
		//TODO Document that character literals (i.e. 1+'a' <--) are not allowed in directive parameter expressions
		String[] s = text.split("\\b");
		for (int i = 0; i < s.length; i++) {
			if (s[i].length() > 0) {
				if (s[i].startsWith("0x")) {
					s[i] = ""+Integer.parseInt(s[i].substring(2), 16);
		    } else if (s[i].startsWith("0b")) {
		    	s[i] = ""+ Integer.parseInt(s[i].substring(2), 2);
	//	    } else if (v.startsWith("'") && text.endsWith("'") && text.length()==3) {
	//				val = text.charAt(1);
				}
			}
		}
		return Util.join(s, ' ');
	}

	private int sizeLine(AssemblyLine line) {
		//TODO Note: Rule for now is: Use of labels or expressions disables short form literal optimization
		//TODO: Handle the -1 case (unary operator token)
		//Also, after looking over how you've done sizing here, you might want to check yourself into hospital for evaluation
		int size = 0;
		LexerToken[] tokens = line.getProcessedTokens();
		for (int i = 0; i < tokens.length; i++) {// LexerToken token : line.getProcessedTokens()) {
			LexerToken token = tokens[i];
			if (token instanceof BasicOpCodeToken) {
				size++;
				//Check if the b Value is a simple stack accessor or register
				if (tokens[(i+=2)] instanceof LiteralToken) {
					size++;
					((BasicOpCodeToken)token).setBValueNextWord(true);
				} else if (tokens[i] instanceof RegisterToken) {
					if (!(tokens[i+1] instanceof BValueEndToken)) {
						size++;
						((BasicOpCodeToken)token).setBValueNextWord(true);
					}
				} else if (tokens[i] instanceof AddressStartToken) {
					if (tokens[++i] instanceof RegisterToken) {
						if (!(tokens[++i] instanceof AddressEndToken)) {
							size++;
							((BasicOpCodeToken)token).setBValueNextWord(true);
						}
					} else {
						size++;
						((BasicOpCodeToken)token).setBValueNextWord(true);
					}
				} else if (tokens[i] instanceof SimpleStackAccessToken) {
				} else {
					size++;
					((BasicOpCodeToken)token).setBValueNextWord(true);
				}
				while (!(tokens[++i] instanceof AValueStartToken)) {}
				//Check the a Value (can also be a non-expression non-label literal and meet short literal requirements)
				if (tokens[++i] instanceof LiteralToken) {
					if (tokens[i+1] instanceof AValueEndToken) {
						char val = (char) (((LiteralToken)tokens[i]).getValue() & 0xFFFF);
						if (val >= 31 && val != 0xFFFF) {
							size++;
							((BasicOpCodeToken)token).setAValueNextWord(true);
						}
					} else {
						size++;
						((BasicOpCodeToken)token).setAValueNextWord(true);
					}
				} else if (tokens[i] instanceof RegisterToken) {
					if (!(tokens[i+1] instanceof AValueEndToken)) {
						size++;
						((BasicOpCodeToken)token).setAValueNextWord(true);
					}
				} else if (tokens[i] instanceof AddressStartToken) {
					if (tokens[++i] instanceof RegisterToken) {
						if (!(tokens[++i] instanceof AddressEndToken)) {
							size++;
							((BasicOpCodeToken)token).setAValueNextWord(true);
						}
					} else {
						size++;
						((BasicOpCodeToken)token).setAValueNextWord(true);
					}
				} else if (tokens[i] instanceof SimpleStackAccessToken) {
				} else {
					size++;
					((BasicOpCodeToken)token).setAValueNextWord(true);
				}
			} else if (token instanceof SpecialOpCodeToken) {
				size++;
				if (tokens[(i+=2)] instanceof LiteralToken) {
					if (tokens[i+1] instanceof AValueEndToken) {
						char val = (char) (((LiteralToken)tokens[i]).getValue() & 0xFFFF);
						if (val >= 31 && val != 0xFFFF) {
							size++;
							((SpecialOpCodeToken)token).setAValueNextWord(true);
						}
					} else {
						size++;
						((SpecialOpCodeToken)token).setAValueNextWord(true);
					}
				} else if (tokens[i] instanceof RegisterToken) {
					if (!(tokens[i+1] instanceof AValueEndToken)) {
						size++;
						((SpecialOpCodeToken)token).setAValueNextWord(true);
					}
				} else if (tokens[i] instanceof AddressStartToken) {
					if (tokens[++i] instanceof RegisterToken) {
						if (!(tokens[++i] instanceof AddressEndToken)) {
							size++;
							((SpecialOpCodeToken)token).setAValueNextWord(true);
						}
					} else {
						size++;
						((SpecialOpCodeToken)token).setAValueNextWord(true);
					}
				} else if (tokens[i] instanceof SimpleStackAccessToken) {
				} else {
					size++;
					((SpecialOpCodeToken)token).setAValueNextWord(true);
				}
			} else if (token instanceof DataToken) {
				i++;
				while (i < tokens.length && tokens[i] instanceof DataValueStartToken) {
					token = tokens[++i];
					if (!(tokens[i+1] instanceof DataValueEndToken)) {
						size++;
						while (!(tokens[++i] instanceof DataValueEndToken)) {}
					} else {
						if (token instanceof StringToken) {
							//TODO: Decide whether strings should default to packed or not (currently they are not, and non-ascii characters are allowed in the string)
							size += ((StringToken)token).getString().length();
						} else {
							//TODO: Do individual conditions for each possible value type?
							size++;
						}
						i++;
					}
					i++;
				}
			}
		}
		line.setSize(size);
		return size;
	}
}
package devcpu.assembler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;

import devcpu.assembler.exceptions.DuplicateLabelDefinitionException;
import devcpu.assembler.exceptions.IncludeFileNotFoundException;
import devcpu.assembler.exceptions.InvalidDefineFormatException;
import devcpu.assembler.exceptions.RecursiveInclusionException;
import devcpu.lexer.Lexer;
import devcpu.lexer.tokens.DirectiveParametersToken;
import devcpu.lexer.tokens.DirectiveToken;
import devcpu.lexer.tokens.LabelDefinitionToken;
import devcpu.lexer.tokens.LabelToken;
import devcpu.lexer.tokens.LexerToken;

public class AssemblyDocument {
	private IFile file;
	private Assembly assembly;
	private AssemblyDocument parent;
	private ArrayList<AssemblyLine> lines = new ArrayList<AssemblyLine>();
	private ArrayList<Directive> directives = new ArrayList<Directive>();
	private LinkedHashMap<Directive,AssemblyDocument> children = new LinkedHashMap<Directive, AssemblyDocument>();

	public AssemblyDocument(IFile file, Assembly assembly, AssemblyDocument parent) throws IOException, DuplicateLabelDefinitionException, CoreException, IncludeFileNotFoundException, RecursiveInclusionException, InvalidDefineFormatException {
		this.file = file;
		//TODO This setup sucks. Documents should be dumb and shouldn't need a reference to the assembly. Rework this in a later release.
		this.assembly = assembly;
		this.parent = parent;
		readLines();
	}

	private void readLines() throws IOException, DuplicateLabelDefinitionException, CoreException, IncludeFileNotFoundException, RecursiveInclusionException, InvalidDefineFormatException {
		//TODO prompt if unsync?
		BufferedReader isr = new BufferedReader(new InputStreamReader(file.getContents(true)));
		String lineText = null;
		int n = 0;
		while((lineText=isr.readLine()) != null) {
			AssemblyLine line = new AssemblyLine(this, ++n, lineText, Lexer.get().generateTokens(lineText, true));
			Directive directive = null;
			for (LexerToken token : line.getTokens()) {
				if (token instanceof DirectiveToken) {
					directive = new Directive(line, (DirectiveToken) token);
				} else if (token instanceof DirectiveParametersToken) {
					directive.setParameters((DirectiveParametersToken)token);
					directives.add(directive);
					if (directive.isInclude()) {
						children.put(directive,loadInclude(new Include(directive)));
					} else if (directive.isDefine()) {
						Define define = new Define(directive);
						assembly.defines.put(define.getKey(), define.getValue());
					}
				} else if (token instanceof LabelDefinitionToken) {
					LabelDefinition labelDef = new LabelDefinition(line, (LabelDefinitionToken) token, assembly.isLabelsCaseSensitive());
					if (assembly.labelDefs.containsKey(labelDef.getLabelName())) {
						throw new DuplicateLabelDefinitionException(assembly.labelDefs.get(labelDef.getLabelName()),labelDef);
					}
					assembly.labelDefs.put(labelDef.getLabelName(), labelDef);
				} else if (token instanceof LabelToken) {
					LabelUse labelUse = new LabelUse(line, (LabelToken) token, assembly.isLabelsCaseSensitive());
					if (!assembly.labelUses.containsKey(labelUse.getLabelName())) {
						assembly.labelUses.put(labelUse.getLabelName(), new ArrayList<LabelUse>());
					}
					assembly.labelUses.get(labelUse.getLabelName()).add(labelUse);
				}
			}
			lines.add(line);
			assembly.lines.add(line);
		}		
	}

	public IFile getFile() {
		return file;
	}

	public ArrayList<AssemblyLine> getLines() {
		return lines;
	}

	public ArrayList<Directive> getDirectives() {
		return directives;
	}

	public Assembly getAssembly() {
		return assembly;
	}
	
	public LinkedHashMap<Directive, AssemblyDocument> getChildren() {
		return children;
	}

	public boolean isRoot() {
		return parent == null;
	}
	
	public AssemblyDocument getParent() {
		return parent;
	}

	private AssemblyDocument loadInclude(Include include) throws IncludeFileNotFoundException, RecursiveInclusionException, IOException, DuplicateLabelDefinitionException, CoreException, InvalidDefineFormatException {
		IFile includeFile = locate(include);
		if (includeFile == null) {
			throw new IncludeFileNotFoundException(include);
		}
		if (checkForAncestor(includeFile)) {
			throw new RecursiveInclusionException(include, includeFile);
		}
		return new AssemblyDocument(includeFile, assembly, this);
	}

	private boolean checkForAncestor(IFile includeFile) {
		AssemblyDocument ancestor = this;
		while (ancestor != null) {
			if (ancestor.file.equals(includeFile)) {
				return true;
			}
			ancestor = ancestor.getParent();
		}
		return false;
	}

	private IFile locate(Include include) {
		Path path = new Path(include.getPath());
		//First, treat as path relative to current file ("../" is supported)
    IFile located = file.getParent().getFile(path);
    if (located.exists()) {
    	return located;
    }
    //Second, treat as path relative to project root
    located = file.getProject().getFile(path);
    if (located.exists()) {
    	return located;
    }
    //Third, treat as path relative to workspace root
    located = ResourcesPlugin.getWorkspace().getRoot().getFile(path);
    if (located.exists()) {
    	return located;
    }
    //TODO: Check for local filesystem paths too?
    //TODO: Maybe even Internet URLs?
    return null;
//    String osfile = file.getRawLocation().toOSString();
	}
}

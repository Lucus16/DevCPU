/*
* generated by Xtext
*/
package devcpu;

import org.eclipse.xtext.junit4.IInjectorProvider;

import com.google.inject.Injector;

public class DASMUiInjectorProvider implements IInjectorProvider {
	
	public Injector getInjector() {
		return devcpu.ui.internal.DASMActivator.getInstance().getInjector("devcpu.DASM");
	}
	
}
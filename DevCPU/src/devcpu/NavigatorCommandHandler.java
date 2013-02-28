package devcpu;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.IHandler;
import org.eclipse.core.commands.IHandlerListener;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.ui.dialogs.ElementListSelectionDialog;
import org.eclipse.ui.dialogs.ListSelectionDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import devcpu.emulation.Assembler;
import devcpu.emulation.DefaultControllableDCPU;
import devcpu.emulation.FloppyDisk;
import devcpu.views.DeviceManagerContentProvider;
import devcpu.views.DeviceManagerLabelProvider;

public class NavigatorCommandHandler implements IHandler {
	private static final String ASSEMBLE_TO_FLOPPY = "AssembleToFloppy";
	private static final String ASSEMBLE_TO_DCPU = "AssembleToDCPU";

	LinkedHashSet<IHandlerListener> listeners = new LinkedHashSet<IHandlerListener>();

	@Override
	public void addHandlerListener(IHandlerListener listener) {
		listeners.add(listener);
	}

	@Override
	public void dispose() {
	}

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		if (event.getCommand().getId().equals(ASSEMBLE_TO_FLOPPY)) {
			ISelection selection = HandlerUtil.getCurrentSelection(event);
			if (selection instanceof IStructuredSelection) {
				IStructuredSelection structuredSelection = (IStructuredSelection) selection;
				Object firstElement = structuredSelection.getFirstElement();
				if (firstElement instanceof IFile) {
					IFile file = (IFile) firstElement;
					ArrayList<FloppyDisk> disks = Activator.getDefault().getShip().getFloppyManager().getAvailableDisks();
					for (FloppyDisk disk : new ArrayList<FloppyDisk>(disks)) {
						if (disk.isWriteProtected()) {
							disks.remove(disk);
						}
					}
					final ElementListSelectionDialog listDialog = new ElementListSelectionDialog(HandlerUtil.getActiveShell(event), new DeviceManagerLabelProvider()); //$NON-NLS-1$
					listDialog.setElements(disks.toArray());
					listDialog.setEmptyListMessage("There aren't any unprotected floppies available that aren't currently inserted in a floppy drive.");
					listDialog.setEmptySelectionMessage("Select a floppy disk");
					listDialog.setMessage("Choose the floppy disk on which to assemble the file.\nExisting disk contents will be zeroed prior to assembly.");
					// listDialog.setMultipleSelection(false); //TODO consider allowing multiple selection. Why the hell not?
					listDialog.setTitle("Assemble to Floppy");
					int open = listDialog.open();
					if (open == ListSelectionDialog.OK) {
						Object[] res = listDialog.getResult();
						for (Object o : res) {
							if (o instanceof FloppyDisk) {
								Assembler a = new Assembler(((FloppyDisk) o).data);
								try {
									a.assemble(file.getContents(true));
								} catch (Exception e) {
									// TODO Error message
									e.printStackTrace();
								}
							}
						}
					}
				}
			}
		} else if (event.getCommand().getId().equals(ASSEMBLE_TO_DCPU)) {
			ISelection selection = HandlerUtil.getCurrentSelection(event);
			if (selection instanceof IStructuredSelection) {
				IStructuredSelection structuredSelection = (IStructuredSelection) selection;
				Object firstElement = structuredSelection.getFirstElement();
				if (firstElement instanceof IFile) {
					IFile file = (IFile) firstElement;
					ArrayList<DefaultControllableDCPU> dcpus = Activator.getDefault().getShip().getDCPUManager().getDCPUs();
					final ElementListSelectionDialog listDialog = new ElementListSelectionDialog(HandlerUtil.getActiveShell(event), new DeviceManagerLabelProvider()); //$NON-NLS-1$
					listDialog.setElements(dcpus.toArray());
					listDialog.setEmptyListMessage("There aren't any DCPUs available.");
					listDialog.setEmptySelectionMessage("Select a DCPU");
					listDialog.setMessage("Choose the DCPU on which to assemble the file.\nExisting memory contents will be zeroed prior to assembly.");
					// listDialog.setMultipleSelection(false); //TODO consider allowing multiple selection. Why the hell not?
					listDialog.setTitle("Assemble to DCPU");
					int open = listDialog.open();
					if (open == ListSelectionDialog.OK) {
						Object[] res = listDialog.getResult();
						for (Object o : res) {
							if (o instanceof DefaultControllableDCPU) {
								Assembler a = new Assembler(((DefaultControllableDCPU) o).ram);
								try {
									a.assemble(file.getContents(true));
								} catch (Exception e) {
									// TODO Error message
									e.printStackTrace();
								}
							}
						}
					}
				}
			}
		} 
		return null;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public boolean isHandled() {
		return true;
	}

	@Override
	public void removeHandlerListener(IHandlerListener listener) {
		listeners.remove(listener);
	}

}
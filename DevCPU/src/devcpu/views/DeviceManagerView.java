package devcpu.views;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListSelectionDialog;
import org.eclipse.ui.part.ViewPart;

import devcpu.Activator;
import devcpu.ViewMapper;
import devcpu.emulation.DCPUHardware;
import devcpu.emulation.DefaultControllableDCPU;
import devcpu.emulation.FloppyDisk;
import devcpu.emulation.Identifiable;
import devcpu.emulation.VirtualClock;
import devcpu.emulation.VirtualFloppyDrive;
import devcpu.emulation.VirtualKeyboard;
import devcpu.emulation.VirtualMonitor;
import devcpu.emulation.VirtualSleepChamber;
import devcpu.emulation.VirtualVectorDisplay;
import devcpu.managers.DCPUManager;
import devcpu.managers.FloppyManager;
import devcpu.managers.HardwareManager;
import devcpu.util.Util;

public class DeviceManagerView extends ViewPart {
	public static final String ID = "devcpu.views.DeviceManagerView";
	private TreeViewer treeViewer;
	private DeviceManagerContentProvider contentProvider;
	
	public void createPartControl(Composite parent) {
		final Composite container = new Composite(parent, SWT.NONE);
		container.setLayout(new FillLayout(SWT.HORIZONTAL));
		contentProvider = new DeviceManagerContentProvider();
		treeViewer = new TreeViewer(container, SWT.NONE);
		treeViewer.setContentProvider(contentProvider);
		treeViewer.setLabelProvider(new DeviceManagerLabelProvider());
		treeViewer.setInput(Activator.getDefault());
		treeViewer.expandAll();
		
	  final MenuManager menuMgr = new MenuManager();
	  Menu menu = menuMgr.createContextMenu(treeViewer.getControl());
	  menuMgr.addMenuListener(new IMenuListener() {
	    @Override
	    public void menuAboutToShow(IMenuManager manager) {
	      if(treeViewer.getSelection().isEmpty()) {
	        return;
	      }
	      
	      if(treeViewer.getSelection() instanceof IStructuredSelection) {
	        IStructuredSelection selection = (IStructuredSelection)treeViewer.getSelection();
	        Object o = selection.getFirstElement();
	        
	        if (o instanceof Identifiable)
	        {
	        	final Identifiable identifiable = (Identifiable) o;
	        	menuMgr.add(new Action("Rename...") {
	        		@Override
	        		public void run() {
	        			InputDialog dialog = new InputDialog(container.getShell(), "Rename", "New name:", identifiable.getID(), null);
								dialog.setBlockOnOpen(true);
								int result = dialog.open();
								if (result == Window.OK) {
									identifiable.setID(dialog.getValue());
									contentProvider.update();
								}
	        		}
	        	});
	        	
      			//TODO: Add confirm dialog for all of these destroys? Or keep it this way for quick destruction? Maybe use preference.
	        	if (o instanceof DefaultControllableDCPU) {
	          	final DefaultControllableDCPU dcpu = (DefaultControllableDCPU) o;
	          	manager.add(new Action("Destroy") {
	          		public void run() {
	          			Activator.getShip().getDCPUManager().destroyDCPU(dcpu);
	          			contentProvider.update();
	          		};
	  					});
	        	} else if (o instanceof DCPUHardware) {
		        	final DCPUHardware hardware = (DCPUHardware) o;
		        	manager.add(new Action("Destroy") {
		        		public void run() {
		        			Activator.getShip().getHardwareManager().destroyDevice(hardware);
		        			contentProvider.update();
		        		};
							});
	        	} else if (o instanceof FloppyDisk) {
		        	final FloppyDisk disk = (FloppyDisk) o;
		        	manager.add(new Action("Destroy") {
		        		public void run() {
		        			Activator.getShip().getFloppyManager().destroyDisk(disk);
		        			contentProvider.update();
		        		};
							});
	        	}
	        	
	        	menuMgr.add(new Separator());
	        }
	        
	        if (o instanceof DCPUHardware) {
	        	final DCPUHardware hardware = (DCPUHardware) o;
	        	if (hardware.isConnected()) {
		        	manager.add(new Action("Disconnect") {
		        		public void run() {
		        			hardware.disconnect();
		        			contentProvider.update();
		        		};
							});
	        	}
	        }
	        
	        if (o instanceof DCPUManager) {
	        	final DCPUManager dcpuManager = (DCPUManager) o;
	        	manager.add(new Action("Add DCPU") {
	        		@Override
    	    		public ImageDescriptor getImageDescriptor() {
    	    			return Util.getImageDescriptor("icons/dcpu.png");
    	    		}
    	    		public void run() {
	        			DefaultControllableDCPU dcpu = dcpuManager.createDCPU();
	        			treeViewer.expandToLevel(dcpu, 0);
	        			contentProvider.update();
	        		};
						});
	        } else if (o instanceof FloppyManager) {
	        	final FloppyManager floppyManager = (FloppyManager) o;
	        	manager.add(new Action("Add Floppy") {
	        		@Override
    	    		public ImageDescriptor getImageDescriptor() {
    	    			return Util.getImageDescriptor("icons/disk.png");
    	    		}
    	    		public void run() {
	        			FloppyDisk disk = floppyManager.createFloppyDisk();
	        			treeViewer.expandToLevel(disk, 0);
	        			contentProvider.update();
	        		};
						});
	        } else if (o instanceof FloppyDisk) {
	        	final FloppyDisk disk = (FloppyDisk) o;
	        	manager.add(new Action("Dump to file...") {
	        		public void run() {
	        			FileDialog fd = new FileDialog(container.getShell(), SWT.SAVE);
	              fd.setText("Save binary file (big endian)");
	              String selected = fd.open();
	              if (selected != null) {
	              	try {
										disk.save(new File(selected));
									} catch (IOException e) {
										e.printStackTrace();
									}
	              }
	        			contentProvider.update();
	        		};
						});
	        	if (disk.getDriveUsing() == null) {
		        	if (disk.isWriteProtected()) {
		        		manager.add(new Action("Unprotect") {
			        		public void run() {
			        			disk.setWriteProtected(false);
			        			contentProvider.update();
			        		};
								});
		        	} else {
		        		manager.add(new Action("Write from file...") {
			        		public void run() {
			        			FileDialog fd = new FileDialog(container.getShell(), SWT.OPEN);
			              fd.setText("Load binary file (big endian)");
			              String selected = fd.open();
			              if (selected != null) {
			              	try {
												disk.load(new File(selected));
											} catch (IOException e) {
												e.printStackTrace();
											}
			              }
			        			contentProvider.update();
			        		};
								});
		        		manager.add(new Action("Protect") {
			        		public void run() {
			        			disk.setWriteProtected(true);
			        			contentProvider.update();
			        		};
								});
		        	}
	        	} else {
	        		manager.add(new Action("Eject") {
		        		public void run() {
		        			disk.getDriveUsing().eject();
		        			contentProvider.update();
		        		};
							});
	        	}
	        } else if (o instanceof HardwareManager) {
        	final HardwareManager hardwareManager = (HardwareManager) o;
        	MenuManager hardwareMenu = new MenuManager("Add Hardware", Util.getImageDescriptor("icons/hw.png"),null);
      	  hardwareMenu.addMenuListener(new IMenuListener() {
      	    @Override
      	    public void menuAboutToShow(IMenuManager manager) {
      	    	manager.add(new Action("Generic Clock") {
      	    		@Override
      	    		public ImageDescriptor getImageDescriptor() {
      	    			return Util.getImageDescriptor("icons/clock.png");
      	    		}
      	    		public void run() {
      	    			VirtualClock vc = hardwareManager.createVirtualClock();
      	    			treeViewer.expandToLevel(vc, 0);
      	    			contentProvider.update();
      	    		};
      	    	});
      	    	manager.add(new Action("Generic Keyboard") {
      	    		@Override
      	    		public ImageDescriptor getImageDescriptor() {
      	    			return Util.getImageDescriptor("icons/keyboard.png");
      	    		}
      	    		public void run() {
      	    			VirtualKeyboard vk = hardwareManager.createVirtualKeyboard();
      	    			treeViewer.expandToLevel(vk, 0);
      	    			contentProvider.update();
      	    		};
      	    	});
      	    	manager.add(new Action("LEM1802") {
      	    		@Override
      	    		public ImageDescriptor getImageDescriptor() {
      	    			return Util.getImageDescriptor("icons/lem.png");
      	    		}
      	    		public void run() {
      	    			VirtualMonitor vm = hardwareManager.createVirtualMonitor();
      	    			treeViewer.expandToLevel(vm, 0);
      	    			contentProvider.update();
      	    		};
      	    	});
      	    	manager.add(new Action("M35FD") {
      	    		@Override
      	    		public ImageDescriptor getImageDescriptor() {
      	    			return Util.getImageDescriptor("icons/fd.png");
      	    		}
      	    		public void run() {
      	    			VirtualFloppyDrive vfd = hardwareManager.createVirtualFloppyDrive();
      	    			treeViewer.expandToLevel(vfd, 0);
      	    			contentProvider.update();
      	    		};
      	    	});
      	    	manager.add(new Action("SPC2000") {
      	    		@Override
      	    		public ImageDescriptor getImageDescriptor() {
      	    			return Util.getImageDescriptor("icons/spc.png");
      	    		}
      	    		public void run() {
      	    			VirtualSleepChamber vsc = hardwareManager.createVirtualSleepChamber();
      	    			treeViewer.expandToLevel(vsc, 0);
      	    			contentProvider.update();
      	    		};
      	    	});
      	    	manager.add(new Action("SPED-3") {
      	    		@Override
      	    		public ImageDescriptor getImageDescriptor() {
      	    			return Util.getImageDescriptor("icons/sped.png");
      	    		}
      	    		public void run() {
      	    			VirtualVectorDisplay vvd = hardwareManager.createVirtualVectorDisplay();
      	    			treeViewer.expandToLevel(vvd, 0);
      	    			contentProvider.update();
      	    		};
      	    	});
      	    }
      	  });
      	  hardwareMenu.setRemoveAllWhenShown(true);
      	  menuMgr.add(hardwareMenu);
        } else if (o instanceof DefaultControllableDCPU) {
        	final DefaultControllableDCPU dcpu = (DefaultControllableDCPU) o;
	    		if (dcpu.isRunning()) {
	    			manager.add(new Action("Stop") {
	    				@Override
    	    		public ImageDescriptor getImageDescriptor() {
    	    			return Util.getImageDescriptor("icons/stop.png");
    	    		}
    	    		public void run() {
    	    			dcpu.stop();
    	    			contentProvider.update();
    	    		};
    	    	});
	    		} else {
	 	    		manager.add(new Action("Start") {
	 	    			@Override
    	    		public ImageDescriptor getImageDescriptor() {
    	    			return Util.getImageDescriptor("icons/play.png");
    	    		}
    	    		public void run() {
    	    			dcpu.run();
    	    			contentProvider.update();
    	    		};
    	    	});
	    			manager.add(new Action("Clear RAM") {
	  	    		public void run() {
	  	    			for (int i = 0; i < 65536; i++) {
	  	    				dcpu.ram[i] = 0;
	  	    			}
	  	    			contentProvider.update();
	  	    		};
	  	    	});
		    		manager.add(new Action("Load from binary...") {
	        		public void run() {
	        			FileDialog fd = new FileDialog(container.getShell(), SWT.OPEN);
	              fd.setText("Load binary file (big endian)");
	              String selected = fd.open();
	              if (selected != null) {
										try {
											dcpu.load(new File(selected));
										} catch (IOException e) {
											e.printStackTrace();
										}
	              }
	        			contentProvider.update();
	        		};
						});
	    		}
	    		manager.add(new Action("Dump RAM to file...") {
        		public void run() {
        			FileDialog fd = new FileDialog(container.getShell(), SWT.SAVE);
              fd.setText("Save binary file (big endian)");
              String selected = fd.open();
              if (selected != null) {
              	try {
									dcpu.save(new File(selected));
								} catch (IOException e) {
									e.printStackTrace();
								}
              }
        			contentProvider.update();
        		};
					});
	    		
	    		manager.add(new Action("Connect hardware...") {
	    			@Override
  	    		public ImageDescriptor getImageDescriptor() {
  	    			return Util.getImageDescriptor("icons/hw.png");
  	    		}
  	    		public void run() {
        			ListSelectionDialog listDialog = new ListSelectionDialog(container.getShell(), Activator.getShip().getHardwareManager(), new DeviceManagerContentProvider(), new DeviceManagerLabelProvider(), "Choose hardware to connect to "+dcpu.getID()+".");
							listDialog.setTitle("Connect Hardware");
							int open = listDialog.open();
							if (open == ListSelectionDialog.OK) {
								Object[] res = listDialog.getResult();
								for (Object o : res) {
									if (o instanceof DCPUHardware) {
										((DCPUHardware) o).connectTo(dcpu);
									}
								}
							}
							contentProvider.update();
        		}
	    		});
        } else if (o instanceof VirtualFloppyDrive) {
        	final VirtualFloppyDrive vfd = (VirtualFloppyDrive) o;
        	if (vfd.getDisk() == null) {
        		final ArrayList<FloppyDisk> disks = Activator.getShip().getFloppyManager().getAvailableDisks();
        		if (disks.size() > 0)
        		{
	        		MenuManager attachMenu = new MenuManager("Insert Floppy",Util.getImageDescriptor("icons/disk.png"),null);
		      	  attachMenu.addMenuListener(new IMenuListener() {
		      	    @Override
		      	    public void menuAboutToShow(IMenuManager manager) {
		      	    	for (final FloppyDisk fd : disks)
		      	    	{
		      	    		manager.add(new Action(fd.getID()) {
		      	    			@Override
		        	    		public ImageDescriptor getImageDescriptor() {
		      	    				if (fd.isWriteProtected()) {
		      	    					return Util.getImageDescriptor("icons/protecteddisk.png");
		      	    				} else {
		      	    					return Util.getImageDescriptor("icons/disk.png");
		      	    				}
		        	    		}
		        	    		public void run() {
		        	    			vfd.insert(fd);
		        	    			contentProvider.update();
		        	    		};
		        	    	});
		      	    	}
		      	    }
		      	  });
		      	  attachMenu.setRemoveAllWhenShown(true);
		      	  menuMgr.add(attachMenu);
        		}  
        	} else {
        		menuMgr.add(new Action("Eject " + vfd.getDisk().getID()) {
        			@Override
    	    		public ImageDescriptor getImageDescriptor() {
        				if (vfd.getDisk().isWriteProtected()) {
        					return Util.getImageDescriptor("icons/protecteddisk.png");
        				} else {
        					return Util.getImageDescriptor("icons/disk.png");
        				}
    	    		}
    	    		public void run() {
    	    			vfd.eject();
    	    			contentProvider.update();
    	    		};
    	    	});
        	}
        }
	      }
	    }
	  });
	  menuMgr.setRemoveAllWhenShown(true);
	  treeViewer.getControl().setMenu(menu);
	  getSite().registerContextMenu(menuMgr,treeViewer);
	  
	  treeViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent e) {
				if (e.getSelection() instanceof IStructuredSelection) {
					Object o = ((IStructuredSelection)e.getSelection()).getFirstElement();
					if (o instanceof DefaultControllableDCPU) {
						DefaultControllableDCPU dcpu = (DefaultControllableDCPU) o;
						MappedView<DefaultControllableDCPU> view = ViewMapper.getFirstView(dcpu);
						if (view == null) {
							try {
								PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(
										ViewMapper.createMappedView(dcpu, DCPUView.ID));
							} catch (Exception e1) {
								e1.printStackTrace();
							}	
						} else {
							PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(view);
						}
					} else if (o instanceof VirtualMonitor) {
						VirtualMonitor vm = (VirtualMonitor) o;
						MappedView<VirtualMonitor> view = ViewMapper.getFirstView(vm);
						if (view == null) {
							try {
								PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(
										ViewMapper.createMappedView(vm, LEM1802View.ID));
							} catch (Exception e1) {
								e1.printStackTrace();
							}	
						} else {
							PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(view);
						}
					} else if (o instanceof VirtualKeyboard) {
						VirtualKeyboard vk = (VirtualKeyboard) o;
						MappedView<VirtualKeyboard> view = ViewMapper.getFirstView(vk);
						if (view == null) {
							try {
								PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(
										ViewMapper.createMappedView(vk, KeyboardView.ID));
							} catch (Exception e1) {
								e1.printStackTrace();
							}	
						} else {
							PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(view);
						}
					} else if (o instanceof VirtualVectorDisplay) {
						VirtualVectorDisplay vvd = (VirtualVectorDisplay) o;
						MappedView<VirtualVectorDisplay> view = ViewMapper.getFirstView(vvd);
						if (view == null) {
							//TODO Add special handling for SPED-3's Single-instance limitation.
							try {
								PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(
										ViewMapper.createMappedView(vvd, SPED3View.ID));
							} catch (Exception e1) {
								e1.printStackTrace();
							}	
						} else {
							PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().activate(view);
						}
					}
				}
			}
		});
	}

	public void setFocus() {
	}
}
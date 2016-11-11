package de.tunetown.nnpg.main;

import java.io.File;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import de.tunetown.nnpg.model.DataWrapper;
import de.tunetown.nnpg.model.NetworkWrapper;
import de.tunetown.nnpg.model.TrainingTracker;
import de.tunetown.nnpg.model.snipe.SNIPEDataWrapper;
import de.tunetown.nnpg.model.snipe.SNIPENetworkWrapper;
import de.tunetown.nnpg.view.MainFrame;
import de.tunetown.nnpg.view.Menu;
import de.tunetown.nnpg.view.TrainingWorker;

/**
 * Application class for neural network experimenter.
 * 
 * - TODO 3 Multiple networks of the same topology (slider? 1 - 10) with averaging of outputs
 * 
 * - TODO 4 Adaptive eta determination
 * 		-> Read papers about that!
 * - TODO 4 Multi-Threaded training? Any idea?
 * 		-> Create global Thread Pool, containing cores-1 threads
 * 		-> train method can be multithreaded with a thread pool
 * 		-> Averaging networks can of course be multithreaded
 * 
 * - TODO 5 Graph of errors: Reduce data points somehow, so that no growing of runtime occurs
 * 		- Perhaps cumulate iterations?
 * 
 * *******************************************************
 * 
 * - TODO X New tool: Grow/Reduce. Area: radius (see slider) 
 * 		- Slider for rate, from 0.5 to 2.
 * 		- Slider for radius, from 0 to range max.
 * 
 * - TODO X Multi-dimensional visualization
 * 
 * - TODO X Adaptive adding/removing of neurons
 * 
 * @author Thomas Weber, 2016
 * @see www.tunetown.de
 * @version 0.1
 *
 */
public class Main {

	/**
	 * Temporary file (here, the last used data will be saved and reloaded on next startup)
	 */
	private static final File TEMP_FILE = new File(System.getProperty("user.home") + File.separator + "SE.tmp");
	
	private NetworkWrapper net;
	private DataWrapper data;
	private TrainingTracker tracker;
	private ProjectLoader dataLoader;
	
	private MainFrame frame;
	private Menu menu;
	private TrainingWorker trainWorker;
	
	private Object trainLock = new Object();
	
	/**
	 * Main method
	 *  
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			// Use the native menu bar on mac os x
			System.setProperty("apple.laf.useScreenMenuBar", "true"); //$NON-NLS-1$
		
			// Set native look and feel 
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			
		} catch (Throwable t) {
			t.printStackTrace();
		} 
		
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				Main appl = new Main();
				appl.init();
			}
		});	
	}

	/**
	 * Initialize the application (called by main() method)
	 * 
	 */
	private void init() {
		// Create training data wrapper. Here it is possible to invoke also different network implementations.
		data = new SNIPEDataWrapper();
		dataLoader = new ProjectLoader(this);
		
		// Initialize the network, tracker and data instances
		initNetwork();
		
		// Load data from temporary file and take care that it is being saved on exit
		dataLoader.loadFromFile(TEMP_FILE);
		dataLoader.addShutdownHook(TEMP_FILE);
		
		// Create and initialize application frame and menu. Order is critical here for proper display.
		frame = new MainFrame(this);
		menu = new Menu(this, frame);
		
		menu.init();
		frame.init();
	}

	/**
	 * Sets up the network
	 * 
	 */
	public void initNetwork() {
		double eta = 0;
		int batchSize = 0;
		int behavior = -1;
		if (net != null) {
			eta = net.getEta();
			batchSize = net.getBatchSize();
			behavior = net.getBehavior();
		}
		
		// Create network instance wrapper. Here it is possible to invoke also different network implementations.
		if (net == null) {
			setNetwork(new SNIPENetworkWrapper());
		} else {
			setNetwork(new SNIPENetworkWrapper(net.getTopology()));
		}
		
		if (eta != 0) net.setEta(eta);
		if (batchSize != 0) net.setBatchSize(batchSize);
		if (behavior != -1) net.setBehavior(behavior);
		
		// Create training tracker. This stores information about the learning process (errors, iteration counter etc.)
		tracker = new TrainingTracker();
		
		updateView();
	}

	/**
	 * Set a new network wrapper instance
	 * 
	 * @param net
	 */
	public void setNetwork(NetworkWrapper net) {
		this.net = net;
	}

	/**
	 * Returns the network wrapper
	 * 
	 * @return
	 */
	public NetworkWrapper getNetwork() {
		return net;
	}
	
	/**
	 * Returns the training data wrapper
	 * 
	 * @return
	 */
	public DataWrapper getData() {
		return data;
	}
	
	/**
	 * Returns the tracker instance
	 * 
	 * @return
	 */
	public TrainingTracker getTracker() {
		return tracker;
	}
	
	/**
	 * Returns the data loader
	 * 
	 * @return
	 */
	public ProjectLoader getDataLoader() {
		return dataLoader;
	}

	/**
	 * Update statistics on UI elements according to the network state
	 * 
	 * @param resetGridSize
	 * @param updateTopology
	 */
	public void updateView() {
		updateView(false, false, false);
	}
	
	/**
	 * Update statistics on UI elements according to the network state
	 * 
	 * @param resetGridSize
	 * @param updateTopology
	 */
	public void updateView(boolean resetGridSize, boolean updateTopology, boolean updateControls) {
		if (frame == null || frame.getControlPanel() == null || frame.getTopologyPanel() == null) return;

		frame.getControlPanel().updateStats();
		if (updateControls) frame.getControlPanel().updateControls();
		if (updateTopology) frame.getTopologyPanel().update();
		if (resetGridSize) frame.getTopologyPanel().resetGridSize();

	}

	/**
	 * Starts the training Thread
	 *  
	 */
	public void startTraining() {
		trainWorker = new TrainingWorker(this, frame);
		trainWorker.execute();		
	}
	
	/**
	 * Stops the training Thread
	 * @param wait 
	 * 
	 */
	public void stopTraining(boolean wait) {
		if (trainWorker == null) return;
		trainWorker.kill();
		
		while(!trainWorker.isDone()) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Tell the UI elements that training has been stopped. This is called by the done() method of the
	 * swing worker after finishing. If you want to stop training, call stopTraining() ;-)
	 * 
	 */
	public void setTrainingStopped() {
		frame.getControlPanel().setTrainingStopped();
	}

	/**
	 * Set the selected tool for data editing
	 * 
	 * @param tool
	 */
	public void setDataTool(int tool) {
		frame.getDataPanel().setTool(tool);
	}
	
	/**
	 * Returns the lock object used during training. This is used to protect the cloning and publishing of the training clone network instance.
	 *  
	 * @return
	 */
	public Object getNetworkLock() {
		return trainLock;
	}
}


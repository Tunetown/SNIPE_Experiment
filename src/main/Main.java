package main;

import view.MainFrame;
import view.TrainingWorker;

import javax.swing.SwingUtilities;

import model.DataLoader;
import model.DataWrapper;
import model.NetworkWrapper;
import model.TrainingTracker;
import model.snipe.SNIPEDataWrapper;
import model.snipe.SNIPENetworkWrapper;

/**
 * Application class for neural network experimenter
 * 
 * @author xwebert
 *
 */
public class Main {

	private NetworkWrapper net;
	private DataWrapper data;
	private TrainingTracker tracker;
	
	private MainFrame frame;
	private TrainingWorker trainWorker;
	
	/**
	 * Initialize the application (called by main() method)
	 * 
	 */
	private void init() {
		// Create training data wrapper. Here it is possible to invoke also different network implementations.
		data = new SNIPEDataWrapper();
		
		initNetwork();
		
		// Load data from temporary file and take care that it is being saved on exit
		DataLoader dl = new DataLoader(data);
		dl.loadParams();
		dl.addShutdownHook();
		
		// Create application frame.
		frame = new MainFrame(this);
		frame.init();
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
	 * Update statistics on UI elements according to the network state
	 * 
	 */
	public void updateStats() {
		if (frame != null && frame.getControlPanel() != null) frame.getControlPanel().updateStats();
	}

	/**
	 * Tell the UI elements that training has been stopped
	 * 
	 */
	public void setTrainingStopped() {
		frame.getControlPanel().setTrainingStopped();
	}

	/**
	 * Main method
	 *  
	 * @param args
	 */
	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				Main appl = new Main();
				appl.init();
			}
		});	
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
	 * 
	 */
	public void stopTraining() {
		trainWorker.kill();
	}
	
	/**
	 * Sets up the network
	 * 
	 */
	public void initNetwork() {
		// Create network instance wrapper. Here it is possible to invoke also different network implementations.
		net = new SNIPENetworkWrapper();
		
		// Create training tracker. This stores information about the learning process (errors, iteration counter etc.)
		tracker = new TrainingTracker();
		
		updateStats();
	}
}

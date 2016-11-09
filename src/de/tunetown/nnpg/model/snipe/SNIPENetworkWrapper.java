package de.tunetown.nnpg.model.snipe;

import com.dkriesel.snipe.core.NeuralNetwork;
import com.dkriesel.snipe.core.NeuralNetworkDescriptor;
import com.dkriesel.snipe.neuronbehavior.Identity;
import com.dkriesel.snipe.neuronbehavior.TangensHyperbolicus;
import com.dkriesel.snipe.training.ErrorMeasurement;
import com.dkriesel.snipe.training.TrainingSampleLesson;

import de.tunetown.nnpg.model.DataWrapper;
import de.tunetown.nnpg.model.NetworkWrapper;
import de.tunetown.nnpg.model.TrainingTracker;

/**
 * Wrapper for SNIPE network.
 * 
 * @author xwebert
 *
 */
public class SNIPENetworkWrapper extends NetworkWrapper {

	private double eta = 0.002;
	private int batchSize = 10000;
	private double initialRange = 0.1;

	private NeuralNetwork net;
	
	public SNIPENetworkWrapper(int[] topology) {
		createNetwork(topology);
	}

	@Override
	public void createNetwork(int[] topology) {
		NeuralNetworkDescriptor desc = new NeuralNetworkDescriptor(topology);
		desc.setSettingsTopologyFeedForward();
		desc.setSynapseInitialRange(initialRange);
		
		desc.setNeuronBehaviorInputNeurons(new Identity());
		desc.setNeuronBehaviorHiddenNeurons(new TangensHyperbolicus());
		desc.setNeuronBehaviorOutputNeurons(new TangensHyperbolicus());
		
		net = desc.createNeuralNetwork();
	}

	@Override
	public int countNeurons() {
		return net.countNeurons();
	}

	@Override
	public int countLayers() {
		return net.countLayers();
	}

	@Override
	public int countNeuronsInLayer(int layer) {
		return net.countNeuronsInLayer(layer);
	}

	@Override
	public boolean isSynapseExistent(int fromNeuron, int toNeuron) {
		return net.isSynapseExistent(fromNeuron + 1, toNeuron + 1);
	}

	@Override
	public double getWeight(int fromNeuron, int toNeuron) {
		if (!net.isSynapseExistent(fromNeuron, toNeuron)) return Double.NaN;
		return net.getWeight(fromNeuron + 1,  toNeuron + 1);
	}

	@Override
	public int getLayerOfNeuron(int num) {
		return net.getLayerOfNeuron(num + 1);
	}

	@Override
	public int getFirstNeuronInLayer(int layer) {
		return net.getNeuronFirstInLayer(layer) - 1;
	}

	@Override
	public double[] propagate(double[] in) {
		return net.propagate(in);
	}

	@Override
	public void train(DataWrapper data, TrainingTracker tracker) {
		if (data.getTrainingLesson() == null || data.getTrainingLesson().size() == 0) return;

		tracker.addRecord(getTrainingError(data), getTestError(data));
		
		TrainingSampleLesson lesson = ((SNIPEDataWrapper)data).getSNIPETrainingLesson();
		
		long start = System.nanoTime();
		net.trainBackpropagationOfError(lesson, batchSize, eta);
		tracker.addNanoTime(System.nanoTime() - start);
	}

	@Override
	public double getTrainingError(DataWrapper data) {
		TrainingSampleLesson lesson = ((SNIPEDataWrapper)data).getSNIPETrainingLesson();
		if (lesson == null || lesson.countSamples() == 0) return 0;
		return ErrorMeasurement.getErrorSquaredPercentagePrechelt(net, lesson) / 100; //.getErrorRootMeanSquareSum(net, lesson);
	}

	@Override
	public double getTestError(DataWrapper data) {
		TrainingSampleLesson lesson = ((SNIPEDataWrapper)data).getSNIPETestLesson();
		if (lesson == null || lesson.countSamples() == 0) return 0;
		return ErrorMeasurement.getErrorSquaredPercentagePrechelt(net, lesson) / 100; //.getErrorRootMeanSquareSum(net, lesson);
	}

	@Override
	public double getBiasWeight(int num) {
		if (!net.isSynapseExistent(0, num + 1)) return Double.NaN;
		return net.getWeight(0, num + 1);
	}

	@Override
	public double getEta() {
		return eta;
	}

	@Override
	public void setEta(double eta) {
		this.eta = eta;
	}

	@Override
	public int getBatchSize() {
		return batchSize;
	}

	@Override
	public void setBatchSize(int size) {
		batchSize = size;
	}

	@Override
	public int getMaxNeuronsInLayers() {
		int max = 0;
		for(int i=0; i<countLayers(); i++) {
			if (countNeuronsInLayer(i) > max) max = countNeuronsInLayer(i);
		}
		return max;
	}

	@Override
	public NetworkWrapper clone() {
		SNIPENetworkWrapper ret = new SNIPENetworkWrapper(net.getDescriptor().getNeuronsPerLayer());
		ret.net = net.clone();
		ret.setParametersFrom(this);
		return ret;
	}

	@Override
	public void setParametersFrom(NetworkWrapper network) {
		SNIPENetworkWrapper n = (SNIPENetworkWrapper)network;
		
		setEta(n.getEta());
		setBatchSize(n.getBatchSize());
	}

	/**
	 * NOTE: For SNIPE, the reset flag is ignored in setTopology()!
	 */
	@Override
	public void addLayer(int position, int neurons, boolean reset) {
		if (position >= net.countLayers()) return;
		
		int[] t = net.getDescriptor().getNeuronsPerLayer();
		int[] nt = new int[t.length + 1];
		
		int nn = 0;
		for(int i=0; i<position; i++) {
			nt[nn] = t[i];
			nn++;
		}
		nt[nn] = neurons;
		nn++;
		for(int i=position; i<t.length; i++) {
			nt[nn] = t[i];
			nn++;
		}
		createNetwork(nt);
	}

	/**
	 * NOTE: For SNIPE, the reset flag is ignored in setTopology()!
	 */
	@Override
	public void removeLayer(int layer, boolean reset) {
		if (layer >= net.countLayers()) return;
		
		int[] t = net.getDescriptor().getNeuronsPerLayer();
		int[] nt = new int[t.length - 1];
		
		int nn = 0;
		for(int i=0; i<layer; i++) {
			nt[nn] = t[i];
			nn++;
		}
		for(int i=layer+1; i<t.length; i++) {
			nt[nn] = t[i];
			nn++;
		}
		createNetwork(nt);
	}

	@Override
	public void addNeuron(int layer, boolean reset) {
		int[] t = net.getDescriptor().getNeuronsPerLayer();
		if (layer >= t.length || layer < 0) return;
		t[layer]++;
		createNetwork(t);
	}

	@Override
	public void removeNeuron(int layer, boolean reset) {
		int[] t = net.getDescriptor().getNeuronsPerLayer();
		if (layer >= t.length || layer < 0) return;
		if (t[layer] < 2) return;
		t[layer]--;
		createNetwork(t);
	}

	@Override
	public int[] getTopology() {
		return net.getDescriptor().getNeuronsPerLayer();
	}
}


package de.tunetown.nnpg.model.neuroph;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.neuroph.core.Connection;
import org.neuroph.core.Layer;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.Neuron;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.input.WeightedSum;
import org.neuroph.core.learning.error.ErrorFunction;
import org.neuroph.core.learning.error.MeanSquaredError;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.neuroph.nnet.comp.neuron.BiasNeuron;
import org.neuroph.nnet.learning.BackPropagation;
import org.neuroph.util.NeuronProperties;
import org.neuroph.util.random.RangeRandomizer;

import de.tunetown.nnpg.model.DataContainer;
import de.tunetown.nnpg.model.DataModel;
import de.tunetown.nnpg.model.ModelProperties;
import de.tunetown.nnpg.model.NetworkWrapper;

/**
 * Wrapper for Neuroph network.
 * 
 * @author Thomas Weber
 *
 */
public class NeurophNetworkWrapper extends NetworkWrapper {
	private static final long serialVersionUID = 1L;

	private double eta = ModelProperties.NETWORK_DEFAULT_ETA;
	private int batchSize = ModelProperties.NETWORK_DEFAULT_BATCHSIZE;
	private double initialRange = ModelProperties.NETWORK_INITIAL_RANGE;

	@SuppressWarnings("rawtypes")
	private NeuralNetwork net;
	
	private int realBatchSize;
	
	private int behavior = 0;

	private TransferFunctionTypeExt[] behaviors = { 
			TransferFunctionTypeExt.RELU,
			TransferFunctionTypeExt.LINEAR,
			TransferFunctionTypeExt.RAMP,
			TransferFunctionTypeExt.STEP,
			TransferFunctionTypeExt.SIGMOID,
			TransferFunctionTypeExt.TANH,
			TransferFunctionTypeExt.TANH_JAFAMA,
			TransferFunctionTypeExt.GAUSSIAN,
			TransferFunctionTypeExt.TRAPEZOID,
			TransferFunctionTypeExt.SGN,
			TransferFunctionTypeExt.SIN,
			TransferFunctionTypeExt.LOG };

	public NeurophNetworkWrapper() {
		createNetwork(ModelProperties.NETWORK_DEFAULT_TOPOLOGY);
	}
		
	public NeurophNetworkWrapper(int[] topology, double initialRange, int behavior) {
		createNetwork(topology, initialRange, behavior);
	}

	@Override
	public void createNetwork(int[] topology) {
		createNetwork(topology, initialRange, behavior); 
	}
		
	@SuppressWarnings("unchecked")
	private void createNetwork(int[] topology, double initialRange, int behavior) {
		this.behavior = behavior;
		this.initialRange = initialRange;

		NeuronProperties neuronProperties = new NeuronProperties();
        neuronProperties.setProperty("useBias", true);
        neuronProperties.setProperty("transferFunction", behaviors[behavior].getTypeClass());
        neuronProperties.setProperty("inputFunction", WeightedSum.class);

        List<Integer> neuronsInLayersVector = new ArrayList<Integer>();
        for (int i = 0; i < topology.length; i++) {
            neuronsInLayersVector.add(Integer.valueOf(topology[i]));
        } 
		
		net = new MultiLayerPerceptron(neuronsInLayersVector, neuronProperties);
		net.setLearningRule(new BackPropagation());
		net.randomizeWeights(new RangeRandomizer(-this.initialRange, this.initialRange));
	}

	@Override
	public int countNeurons() { 
		int ret = 0;
		for(int i=0; i<net.getLayersCount(); i++) {
			ret += countNeuronsInLayer(i); 
		}
		return ret;
	}

	@Override
	public int countLayers() {
		return net.getLayersCount(); 
	}

	@Override
	public int countNeuronsInLayer(int layer) { 
		return net.getLayerAt(layer).getNeuronsCount() - getBiasNeuronsInLayer(layer); 
	}

	@Override
	public boolean isSynapseExistent(int fromNeuron, int toNeuron) {
		Neuron n1 = getNeuron(fromNeuron);
		Neuron n2 = getNeuron(toNeuron);
		Connection c = n2.getConnectionFrom(n1);
		return (c != null);
	}

	private Neuron getNeuron(int n) {
		int layer = getLayerOfNeuron(n);
		int index = n - getFirstNeuronInLayer(layer);
		return net.getLayerAt(layer).getNeuronAt(index);
	}

	@Override
	public double getWeight(int fromNeuron, int toNeuron) {
		Neuron n1 = getNeuron(fromNeuron);
		Neuron n2 = getNeuron(toNeuron);
		Connection c = n2.getConnectionFrom(n1);
		if(c != null) return c.getWeight().getValue(); 
		else return Double.NaN;
	}

	@Override
	public int getLayerOfNeuron(int num) {
		int l = 0;
		for(int i=0; i<net.getLayersCount(); i++) {
			l += net.getLayerAt(i).getNeuronsCount() - getBiasNeuronsInLayer(i);
			if(num < l) {
				return i;
			}
		}
		return -1; // Error
	}

	@Override
	public int getFirstNeuronInLayer(int layer) {
		int l = 0;
		for(int i=0; i<layer; i++) {
			l += net.getLayerAt(i).getNeuronsCount() - getBiasNeuronsInLayer(i);
		}
		return l;
	}

	@Override
	public double[] propagate(double[] in) {
		net.setInput(in);
		net.calculate();
		return net.getOutput();
	}

	@Override
	public void train(DataModel data) {
		if (data.getTrainingLesson() == null || data.getTrainingLesson().size() == 0) return;

		DataSet trainingSet = convertToInternal(data.getTrainingLesson());

		BackPropagation p = (BackPropagation)net.getLearningRule();
		p.setLearningRate(eta);
		
		int runs = this.batchSize / data.getNumOfSamples(true);
		
		realBatchSize = 0;
		for(int run=0; run<runs; run++) {
			p.doLearningEpoch(trainingSet);
			realBatchSize += trainingSet.size();
		}
	}

	private DataSet convertToInternal(DataContainer lesson) {
		if (lesson == null) return null;
		
		DataSet ret = new DataSet(2, 1);
		List<Double[]> in = lesson.getInputs();
		List<Double[]> out = lesson.getDesiredOutputs();
		for(int i=0; i<in.size(); i++) {
			ret.addRow(new DataSetRow(ArrayUtils.toPrimitive(in.get(i)), ArrayUtils.toPrimitive(out.get(i))));
		}
		return ret;
	}

	@Override
	public double getTrainingError(DataModel data) {
		if (data == null || data.getNumOfSamples(true) == 0) return 0;
		
		BackPropagation p = (BackPropagation)net.getLearningRule();
		ErrorFunction f = p.getErrorFunction();
		return f.getTotalError();
	}

	@Override
	public double getTestError(DataModel data) {
		if (data == null || data.getNumOfSamples(false) == 0) return 0;
		
		MeanSquaredError e = new MeanSquaredError();
		for(int i=0; i<data.getNumOfSamples(false); i++) {
			double[] in = ArrayUtils.toPrimitive(data.getTestLesson().getInputs().get(i));
			double[] out = ArrayUtils.toPrimitive(data.getTestLesson().getDesiredOutputs().get(i));
			double[] pred = this.propagate(in);
			e.calculatePatternError(pred, out);
		}
		
		return e.getTotalError();
	}

	@Override
	public double getBiasWeight(int num) {
		Neuron n1 = getBiasNeuron(num);
		Neuron n2 = getNeuron(num);
		Connection c = n2.getConnectionFrom(n1);
		if(c != null) return c.getWeight().getValue(); 
		else return Double.NaN;
	}

	/**
	 * Returns the bias neuron instance for the given neuron
	 * 
	 * @param num
	 * @return
	 */
	private Neuron getBiasNeuron(int num) {
		Neuron n = this.getNeuron(num);
		for(int i=0; i<n.getInputConnections().size(); i++) {
			Neuron r = n.getInputConnections().get(i).getFromNeuron();
			if (r instanceof BiasNeuron) return r;
		}
		return null;
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
	public int getOutputBatchSize() {
		return realBatchSize;
	}

	@Override
	public void setBatchSize(int size) {
		batchSize = size;
	}

	@Override
	public int[] getTopology() {
		@SuppressWarnings("unchecked")
		List<Layer> layers = net.getLayers();
		int[] ret = new int[layers.size()];
		for(int i=0; i<layers.size(); i++) {
			ret[i] = net.getLayerAt(i).getNeuronsCount() - getBiasNeuronsInLayer(i);
		}
		return ret; 
	}

	private int getBiasNeuronsInLayer(int layer) {
		int ret = 0;
		for(int i=0; i<net.getLayerAt(layer).getNeuronsCount(); i++) {
			if (net.getLayerAt(layer).getNeuronAt(i) instanceof BiasNeuron) ret++;
		}
		return ret;
	}

	@Override
	public int getMaxNeuronsInLayers() {
		int max = 0;
		for(int i=0; i<countLayers(); i++) {
			if (countNeuronsInLayer(i) > max) max = countNeuronsInLayer(i);
		}
		return max;
	}

	@SuppressWarnings("rawtypes")
	@Override
	public NetworkWrapper clone() {
		NeurophNetworkWrapper ret = new NeurophNetworkWrapper(this.getTopology(), initialRange, behavior);

		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos;
			oos = new ObjectOutputStream(baos);
			oos.writeObject(this.net);
	
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			ObjectInputStream ois = new ObjectInputStream(bais);
			ret.net = (NeuralNetwork)ois.readObject();
		
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		
		ret.setParametersFrom(this);
		return ret;
	}

	@Override
	public void setBehavior(int i) {
		if (i < 0 || i >= behaviors.length) return;
		if (i == behavior) return;
		behavior = i;
		createNetwork(this.getTopology());
	}

	@Override
	public String[] getBehaviorDescriptions() {
		String[] ret = new String[behaviors.length];
		for(int i=0; i<behaviors.length; i++) {
			ret[i] = behaviors[i].getTypeLabel();
		}
		return ret; 
	}

	@Override
	public int getBehavior() {
		return behavior; 
	}

	@Override
	public void setInitialRange(double range) {
		initialRange = range;
	}

	@Override
	public double getInitialRange() {
		return initialRange;
	}

	@Override
	public String getEngineName() {
		return "Neuroph v2.92";
	}
}


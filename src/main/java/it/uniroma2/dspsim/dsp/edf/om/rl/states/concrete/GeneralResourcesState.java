package it.uniroma2.dspsim.dsp.edf.om.rl.states.concrete;

import it.uniroma2.dspsim.dsp.Operator;
import it.uniroma2.dspsim.dsp.edf.om.rl.states.State;
import it.uniroma2.dspsim.infrastructure.ComputingInfrastructure;
import it.uniroma2.dspsim.infrastructure.NodeType;
import it.uniroma2.dspsim.utils.MathUtils;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import scala.reflect.macros.Infrastructure;

import java.util.Arrays;
import java.util.Objects;

public class GeneralResourcesState extends State {

    private double normalizedCPUSpeedup;

    private double maxCPUSpeedupInUse;

    private double minCPUSpeedupInUse;

    public GeneralResourcesState(int index, int[] k, int lambda, int maxLambda, Operator operator) {
        super(index, k, lambda, maxLambda, operator);

        this.maxCPUSpeedupInUse = ComputingInfrastructure.getInfrastructure().getNodeTypes()[maxNodeInUse(k)].getCpuSpeedup();
        this.minCPUSpeedupInUse = ComputingInfrastructure.getInfrastructure().getNodeTypes()[minNodeInUse(k)].getCpuSpeedup();

        this.normalizedCPUSpeedup = computeCPUSpeedupInUse(k) / computeMaxCPUSpeedup(operator);
    }

    /**
     * Computes biggest node in use by operator at the moment
     * @param k usage vector
     * @return int
     */
    private int maxNodeInUse(int[] k) {
        int index = 0;
        for (int i = 0; i < k.length; i++) {
            if (k[i] > 0)
                index = i;
        }
        return index;
    }

    /**
     * Computes smallest node in use by operator at the moment
     * @param k usage vector
     * @return int
     */
    private int minNodeInUse(int[] k) {
        int index;
        for (index = 0; index < k.length; index++) {
            if (k[index] > 0)
                return index;
        }
        return k.length - 1;
    }

    /**
     * Computes CPU speedup sum in use at the moment
     * @param k usage vector
     * @return double
     */
    private double computeCPUSpeedupInUse(int[] k) {
        double speedup = 0.0;
        for (int i = 0; i < k.length; i++) {
            speedup += ComputingInfrastructure.getInfrastructure().getNodeTypes()[i].getCpuSpeedup() * k[i];
        }
        return speedup;
    }

    /**
     * Compute max CPU speedup that can be used by operator multiplying max CPU's speedup by max operator's parallelism
     * @param o Operator
     * @return double
     */
    private double computeMaxCPUSpeedup(Operator o) {
        double max = 0.0;

        for (NodeType nodeType : ComputingInfrastructure.getInfrastructure().getNodeTypes()) {
            if (nodeType.getCpuSpeedup() > max)
                max = nodeType.getCpuSpeedup();
        }

        return max * o.getMaxParallelism();
    }

    @Override
    public int getArrayRepresentationLength() {
        return 4;
    }

    @Override
    public INDArray arrayRepresentation(int features) throws IllegalArgumentException {
        INDArray array = Nd4j.create(features);
        // normalized lambda
        array.put(0, 0, this.getNormalizedLambda());
        // normalized CPU speedup
        array.put(0, 1, this.normalizedCPUSpeedup);
        // normalized max CPU in use
        array.put(0, 2, MathUtils.normalizeValue(this.maxCPUSpeedupInUse, computeMaxCPUSpeedup(this.operator)));
        // normalized min CPU in use
        array.put(0, 3, MathUtils.normalizeValue(this.minCPUSpeedupInUse, computeMaxCPUSpeedup(this.operator)));

        return array;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeneralResourcesState)) return false;
        if (!super.equals(o)) return false;
        GeneralResourcesState that = (GeneralResourcesState) o;
        return Double.compare(that.normalizedCPUSpeedup, normalizedCPUSpeedup) == 0 &&
                Double.compare(that.maxCPUSpeedupInUse, maxCPUSpeedupInUse) == 0 &&
                Double.compare(that.minCPUSpeedupInUse, minCPUSpeedupInUse) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), normalizedCPUSpeedup, maxCPUSpeedupInUse, minCPUSpeedupInUse);
    }
}

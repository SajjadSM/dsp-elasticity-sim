package it.uniroma2.dspsim.dsp.edf.om;

public enum OperatorManagerType {
    DO_NOTHING,
    THRESHOLD_BASED,
    Q_LEARNING,
    RL_Q_LEARNING,
    DEEP_Q_LEARNING,
    DEEP_V_LEARNING;

    public static OperatorManagerType fromString(String str) throws IllegalArgumentException {
        if (str.equalsIgnoreCase("donothing")) {
            return DO_NOTHING;
        } else if (str.equalsIgnoreCase("threshold")) {
            return THRESHOLD_BASED;
        } else if (str.equalsIgnoreCase("q-learning")) {
            return Q_LEARNING;
        } else if (str.equalsIgnoreCase("rl-q-learning")) {
            return RL_Q_LEARNING;
        } else if (str.equalsIgnoreCase("deep-q-learning")) {
            return DEEP_Q_LEARNING;
        } else if (str.equalsIgnoreCase("deep-v-learning")) {
            return DEEP_V_LEARNING;
        } else {
            throw new IllegalArgumentException("Not valid operator manager type " + str);
        }
    }
}

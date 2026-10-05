package lk.sliit.prescription.extractor;

/** Accumulates token usage across the attempts of one scan (input/output tokens only). */
public final class TokenUsage {

    private int inputTokens;
    private int outputTokens;
    private int calls;

    void add(Integer input, Integer output) {
        inputTokens += input == null ? 0 : input;
        outputTokens += output == null ? 0 : output;
        calls++;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }

    public int calls() {
        return calls;
    }
}
